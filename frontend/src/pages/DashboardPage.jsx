import { useCallback, useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowRight, ArrowUpRight, CalendarClock, CheckCheck, CheckCircle2, Circle, Clock3, FolderKanban, ListTodo, Plus, RefreshCw } from 'lucide-react'
import { useAuth } from '../context/auth-context'
import { projectService, taskService } from '../services/resources'
import { formatDate, isOverdue, summarize } from '../lib/tasks'
import useResource from '../hooks/useResource'
import { Badge, EmptyState, ErrorState, PageHeader, Spinner } from '../components/ui'
import TaskForm from '../components/TaskForm'

export default function DashboardPage() {
  const { user, isAdmin } = useAuth()
  const [editor, setEditor] = useState(null)
  const loader = useCallback(async (signal) => {
    const [tasks, projects] = await Promise.all([taskService.list(signal), projectService.list(signal)])
    return { tasks, projects }
  }, [])
  const state = useResource(loader)
  const stats = state.data ? summarize(state.data.tasks) : null
  const completion = stats?.total ? Math.round(stats.done / stats.total * 100) : 0
  const upcoming = state.data?.tasks.filter((t) => t.dueDate && t.status !== 'DONE').sort((a, b) => new Date(a.dueDate) - new Date(b.dueDate)).slice(0, 4) || []
  const recent = state.data?.tasks.toSorted((a, b) => b.id - a.id).slice(0, 5) || []
  const date = new Intl.DateTimeFormat('vi-VN', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' }).format(new Date())
  return <>
    <PageHeader eyebrow={date} title="Tổng quan công việc" description={`Chào ${user.email.split('@')[0]}, cùng tạo nên một ngày làm việc hiệu quả.`}><button className="btn btn-secondary" onClick={state.reload} disabled={state.loading} aria-label="Tải lại tổng quan"><RefreshCw size={17} /></button><button className="btn btn-primary" onClick={() => setEditor({})}><Plus size={18} />Tạo công việc</button></PageHeader>
    <section className="welcome-banner"><div><span className="banner-tag"><span />KHÔNG GIAN CỦA BẠN</span><h2>Mỗi việc hoàn thành,<br />một bước tiến về phía trước.</h2><p>Theo dõi tiến độ, kết nối dự án và tập trung vào mục tiêu.</p><Link to="/tasks">Khám phá công việc <ArrowRight size={17} /></Link></div><div className="banner-visual" aria-hidden="true"><div className="banner-orbit orbit-one" /><div className="banner-orbit orbit-two" /><div className="floating-check"><CheckCheck size={43} /></div><span className="floating-square square-one" /><span className="floating-square square-two" /><span className="floating-dot" /></div></section>
    {state.loading ? <div className="panel"><Spinner /></div> : state.error ? <div className="panel"><ErrorState error={state.error} retry={state.reload} /></div> : <>
      <div className="stats-grid">{[
        { label: 'Tổng công việc', value: stats.total, icon: ListTodo, color: 'blue', hint: isAdmin ? 'Toàn bộ hệ thống' : 'Trong không gian của bạn', to: '/tasks' },
        { label: 'Đang thực hiện', value: stats.inProgress, icon: Clock3, color: 'amber', hint: 'Đang tiến gần đến mục tiêu', to: '/tasks?status=IN_PROGRESS' },
        { label: 'Đã hoàn thành', value: stats.done, icon: CheckCircle2, color: 'green', hint: `${completion}% tổng số công việc`, to: '/tasks?status=DONE' },
        { label: 'Quá hạn', value: stats.overdue, icon: CalendarClock, color: 'rose', hint: 'Cần được ưu tiên xử lý', to: '/tasks?overdue=true' },
      ].map(({ label, value, icon: Icon, color, hint, to }) => <Link key={label} to={to} className={`stat-card stat-${color}`}><div className="stat-top"><span>{label}</span><span className="stat-icon"><Icon size={21} /></span></div><strong>{value}</strong><div className="stat-bottom"><span>{hint}</span><ArrowUpRight size={16} /></div></Link>)}</div>
      <div className="dashboard-middle"><section className="panel progress-panel"><div className="panel-heading"><div><h2>Tiến độ tổng thể</h2><p>Phân bổ công việc theo trạng thái</p></div><span className="subtle-icon"><ListTodo size={18} /></span></div><div className="progress-chart"><div className="donut" role="img" aria-label={`${completion}% hoàn thành; ${stats.done} hoàn thành, ${stats.inProgress} đang thực hiện, ${stats.todo} chưa bắt đầu`} style={{ '--done': `${completion}%`, '--progress': `${stats.total ? (stats.done + stats.inProgress) / stats.total * 100 : 0}%` }}><div><strong>{completion}<small>%</small></strong><span>Hoàn thành</span></div></div><div className="chart-legend">{[{ name: 'Chưa bắt đầu', value: stats.todo, color: 'slate' }, { name: 'Đang thực hiện', value: stats.inProgress, color: 'blue' }, { name: 'Hoàn thành', value: stats.done, color: 'green' }].map((item) => <div key={item.name}><span className={`legend-dot dot-${item.color}`} /><span>{item.name}</span><strong>{item.value}</strong></div>)}</div></div></section>
      <section className="panel deadlines-panel"><div className="panel-heading"><div><h2>Thời hạn cần chú ý</h2><p>Ưu tiên những công việc gần hạn nhất</p></div><CalendarClock size={20} className="text-slate-400" /></div>{upcoming.length ? <div className="deadline-list">{upcoming.map((task) => <button key={task.id} className="deadline-item" onClick={() => setEditor({ id: task.id })}><span className={`deadline-icon ${isOverdue(task) ? 'late' : ''}`}><CalendarClock size={18} /></span><span><strong>{task.title}</strong><small className={isOverdue(task) ? 'text-rose-600' : ''}>{formatDate(task.dueDate, true)}{isOverdue(task) ? ' · Quá hạn' : ''}</small></span><ArrowUpRight size={16} /></button>)}</div> : <EmptyState title="Chưa có thời hạn cần chú ý" description="Các công việc có hạn hoàn thành sẽ xuất hiện ở đây." />}</section></div>
      <section className="panel"><div className="panel-heading"><div><h2>Công việc gần đây</h2><p>Các công việc mới nhất trong không gian của bạn</p></div><Link className="text-link" to="/tasks">Xem tất cả <ArrowRight size={16} /></Link></div>{recent.length ? <div className="table-scroll"><table className="data-table"><thead><tr><th>Công việc</th><th>Trạng thái</th><th>Ưu tiên</th><th>Hạn hoàn thành</th><th /></tr></thead><tbody>{recent.map((task) => <tr key={task.id}><td><button className="task-name" onClick={() => setEditor({ id: task.id })}><span className={`task-check ${task.status === 'DONE' ? 'checked' : ''}`}>{task.status === 'DONE' ? '✓' : ''}</span><span><strong>{task.title}</strong><small>{state.data.projects.find((p) => p.id === task.projectId)?.name || 'Công việc độc lập'}</small></span></button></td><td><Badge value={task.status} /></td><td><Badge value={task.priority} priority /></td><td className={isOverdue(task) ? 'overdue-date' : 'table-date'}>{formatDate(task.dueDate)}</td><td><button className="icon-button" aria-label={`Xem ${task.title}`} onClick={() => setEditor({ id: task.id })}><ArrowUpRight size={17} /></button></td></tr>)}</tbody></table></div> : <EmptyState title="Mọi hành trình bắt đầu từ một công việc" description="Tạo công việc đầu tiên để bắt đầu theo dõi tiến độ." action={() => setEditor({})} actionLabel="Tạo công việc" />}</section>
      <section className="dashboard-projects"><div className="section-heading"><h2>Dự án của bạn <span>{state.data.projects.length}</span></h2><Link className="text-link" to="/projects">Tất cả dự án <ArrowRight size={16} /></Link></div><div className="project-preview-grid">{state.data.projects.slice(0, 3).map((project) => {
        const tasks = state.data.tasks.filter((t) => t.projectId === project.id)
        const done = tasks.filter((t) => t.status === 'DONE').length
        return <Link key={project.id} to={`/tasks?projectId=${project.id}`} className="panel project-preview"><span className="project-icon"><FolderKanban size={21} /></span><h3>{project.name}</h3><p>{project.description || 'Chưa có mô tả dự án.'}</p><div className="project-progress-label"><span>{tasks.length} công việc</span><strong>{tasks.length ? Math.round(done / tasks.length * 100) : 0}%</strong></div><div className="progress-track"><span style={{ width: `${tasks.length ? done / tasks.length * 100 : 0}%` }} /></div><div className="project-preview-footer"><span>{done > 0 ? <CheckCircle2 size={14} /> : <Circle size={14} />}{done} đã hoàn thành</span><ArrowUpRight size={16} /></div></Link>
      })}{!state.data.projects.length && <Link className="project-create-card" to="/projects"><Plus size={24} /><strong>Tạo dự án đầu tiên</strong><span>Nhóm các công việc theo mục tiêu</span></Link>}</div></section>
    </>}
    {editor && <TaskForm taskId={editor.id} onClose={() => setEditor(null)} onSuccess={state.reload} />}
  </>
}
