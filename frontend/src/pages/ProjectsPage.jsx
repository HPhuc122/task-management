import { useCallback, useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowUpRight, CheckCircle2, FolderKanban, Plus, RefreshCw, Search } from 'lucide-react'
import { useAuth } from '../context/auth-context'
import { projectService, taskService } from '../services/resources'
import { formatDate } from '../lib/tasks'
import useResource from '../hooks/useResource'
import ResourceForm from '../components/ResourceForm'
import DeleteDialog from '../components/DeleteDialog'
import { EditActions, EmptyState, ErrorState, PageHeader, Spinner } from '../components/ui'

export default function ProjectsPage() {
  const { isAdmin } = useAuth()
  const [query, setQuery] = useState('')
  const [editor, setEditor] = useState(null)
  const [deleting, setDeleting] = useState(null)
  const loader = useCallback(async (signal) => {
    const [projects, tasks] = await Promise.all([projectService.list(signal), taskService.list(signal)])
    return { projects, tasks }
  }, [])
  const state = useResource(loader)
  const projects = state.data?.projects.filter((p) => `${p.name} ${p.description || ''}`.toLocaleLowerCase('vi').includes(query.trim().toLocaleLowerCase('vi'))).toSorted((a, b) => b.id - a.id) || []
  return <>
    <PageHeader title="Dự án" description="Kết nối các công việc. Biến mục tiêu lớn thành từng bước nhỏ."><button className="btn btn-secondary" onClick={state.reload} disabled={state.loading}><RefreshCw size={16} />Tải lại</button><button className="btn btn-primary" onClick={() => setEditor({})}><Plus size={18} />Tạo dự án</button></PageHeader>
    <div className="list-toolbar"><div className="input-icon"><Search size={18} /><input aria-label="Tìm dự án" placeholder="Tìm kiếm dự án…" value={query} onChange={(e) => setQuery(e.target.value)} /></div><span className="muted">{state.data ? `${projects.length} dự án${isAdmin ? ' · Toàn hệ thống' : ''}` : 'Danh sách dự án'}</span></div>
    {state.loading ? <div className="panel"><Spinner /></div> : state.error ? <div className="panel"><ErrorState error={state.error} retry={state.reload} /></div> : !projects.length ? <div className="panel"><EmptyState title={query ? 'Không tìm thấy dự án' : 'Bắt đầu một mục tiêu mới'} description={query ? 'Hãy thử một từ khóa khác.' : 'Tạo dự án để nhóm các công việc có cùng mục tiêu.'} action={!query ? () => setEditor({}) : undefined} actionLabel="Tạo dự án" /></div> : <div className="projects-grid">{projects.map((project) => {
      const tasks = state.data.tasks.filter((t) => t.projectId === project.id)
      const done = tasks.filter((t) => t.status === 'DONE').length
      const percent = tasks.length ? Math.round(done / tasks.length * 100) : 0
      return <article className="panel project-card" key={project.id}><div className="project-card-top"><span className="project-icon"><FolderKanban size={24} /></span><EditActions label={project.name} onEdit={() => setEditor({ item: project })} onDelete={() => setDeleting(project)} /></div><h2>{project.name}</h2><p className="project-description">{project.description || 'Chưa có mô tả cho dự án này.'}</p><div className="project-card-meta"><span>#{project.id}</span><span>{isAdmin ? `Người sở hữu #${project.userId}` : `Tạo ngày ${formatDate(project.createdAt)}`}</span></div><div className="project-progress-label"><span>Tiến độ hoàn thành</span><strong>{percent}%</strong></div><div className="progress-track"><span style={{ width: `${percent}%` }} /></div><div className="project-task-counts"><span>{tasks.length} công việc</span><span><CheckCircle2 size={14} />{done} hoàn thành</span></div><Link className="project-card-link" to={`/tasks?projectId=${project.id}`}>Xem công việc của dự án <ArrowUpRight size={17} /></Link></article>
    })}</div>}
    {editor && <ResourceForm kind="project" item={editor.item} onClose={() => setEditor(null)} onSuccess={state.reload} />}
    {deleting && <DeleteDialog name={deleting.name} onDelete={() => projectService.remove(deleting.id)} onClose={() => setDeleting(null)} onSuccess={state.reload} description="Các công việc vẫn được giữ lại và sẽ được gỡ khỏi dự án này. Thao tác xóa dự án không thể hoàn tác." />}
  </>
}
