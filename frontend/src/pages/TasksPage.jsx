import { useCallback, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { ChevronLeft, ChevronRight, Filter, ListTodo, LoaderCircle, Plus, RefreshCw, Search, X } from 'lucide-react'
import { useAuth } from '../context/auth-context'
import { useToast } from '../context/toast-context'
import { taskService, categoryService, projectService } from '../services/resources'
import { errorMessage } from '../services/api'
import { filterTasks, formatDate, isOverdue, PRIORITIES, STATUSES, taskPayload } from '../lib/tasks'
import useResource from '../hooks/useResource'
import TaskForm from '../components/TaskForm'
import DeleteDialog from '../components/DeleteDialog'
import { Badge, EditActions, EmptyState, ErrorState, PageHeader, Spinner } from '../components/ui'

export default function TasksPage() {
  const { user, isAdmin } = useAuth()
  const notify = useToast()
  const [params, setParams] = useSearchParams()
  const [scope, setScope] = useState(isAdmin ? 'all' : 'mine')
  const [size, setSize] = useState(10)
  const [paging, setPaging] = useState({ index: 0, cursors: [null] })
  const [editor, setEditor] = useState(null)
  const [deleting, setDeleting] = useState(null)
  const [updating, setUpdating] = useState(null)
  const filters = { q: params.get('q') || '', status: params.get('status') || '', priority: params.get('priority') || '', projectId: params.get('projectId') || '', categoryId: params.get('categoryId') || '', overdue: params.get('overdue') === 'true' }
  const hasFilters = Object.values(filters).some(Boolean)
  const serverPaging = scope === 'mine' && !hasFilters
  const cursor = paging.cursors[paging.index]
  const loader = useCallback(async (signal) => {
    if (serverPaging) {
      const result = await taskService.page(user.id, cursor, size, signal)
      return { rows: result.content, nextCursor: result.nextCursor, hasNext: result.hasNext }
    }
    const rows = await taskService.list(signal)
    return { rows: scope === 'mine' ? rows.filter((t) => t.userId === user.id) : rows }
  }, [serverPaging, user.id, cursor, size, scope])
  const state = useResource(loader)
  const referenceLoader = useCallback(async (signal) => {
    const [projects, categories] = await Promise.all([projectService.list(signal), isAdmin ? categoryService.list(signal) : Promise.resolve([])])
    return { projects, categories }
  }, [isAdmin])
  const references = useResource(referenceLoader)
  const projects = references.data?.projects || []
  const categories = references.data?.categories || []
  const resetPaging = () => setPaging({ index: 0, cursors: [null] })
  const changeFilter = (name, value) => {
    const next = new URLSearchParams(params)
    if (value) next.set(name, value)
    else next.delete(name)
    setParams(next, { replace: true }); resetPaging()
  }
  const refresh = () => { resetPaging(); state.reload(); references.reload() }
  const filtered = serverPaging ? (state.data?.rows || []) : filterTasks(state.data?.rows || [], filters).sort((a, b) => b.id - a.id)
  const totalPages = Math.max(1, Math.ceil(filtered.length / size))
  const pageIndex = serverPaging ? paging.index : Math.min(paging.index, totalPages - 1)
  const rows = serverPaging ? filtered : filtered.slice(pageIndex * size, (pageIndex + 1) * size)
  const next = () => setPaging((prev) => ({ index: prev.index + 1, cursors: [...prev.cursors.slice(0, prev.index + 1), state.data?.nextCursor] }))
  const updateStatus = async (task, status) => {
    if (updating || status === task.status) return
    setUpdating(task.id)
    try {
      const full = await taskService.get(task.id)
      await taskService.update(task.id, taskPayload({ ...full, status }))
      notify('Đã cập nhật trạng thái.'); state.reload()
    } catch (error) { notify(errorMessage(error), 'error') }
    finally { setUpdating(null) }
  }
  return <>
    <PageHeader title="Công việc" description="Sắp xếp rõ ràng. Ưu tiên đúng việc. Theo sát tiến độ."><button className="btn btn-secondary" onClick={refresh} disabled={state.loading}><RefreshCw size={16} />Tải lại</button><button className="btn btn-primary" onClick={() => setEditor({})}><Plus size={18} />Tạo công việc</button></PageHeader>
    <section className="panel task-panel">
      <div className="panel-tabs"><div className="tab-buttons"><button className={scope === 'mine' ? 'tab active' : 'tab'} onClick={() => { setScope('mine'); resetPaging() }}>Công việc của tôi</button>{isAdmin && <button className={scope === 'all' ? 'tab active' : 'tab'} onClick={() => { setScope('all'); resetPaging() }}>Toàn hệ thống</button>}</div><span className="panel-meta"><ListTodo size={16} />{serverPaging ? 'Danh sách công việc' : `${filtered.length} công việc`}</span></div>
      <div className="filter-toolbar"><div className="input-icon task-search"><Search size={18} /><input aria-label="Tìm theo tên hoặc mô tả" placeholder="Tìm theo tên hoặc mô tả…" value={filters.q} onChange={(e) => changeFilter('q', e.target.value)} /></div><div className="filter-controls"><Filter size={17} className="text-slate-400" />
        <select aria-label="Lọc trạng thái" value={filters.status} onChange={(e) => changeFilter('status', e.target.value)}><option value="">Mọi trạng thái</option>{Object.entries(STATUSES).map(([v, label]) => <option value={v} key={v}>{label}</option>)}</select>
        <select aria-label="Lọc độ ưu tiên" value={filters.priority} onChange={(e) => changeFilter('priority', e.target.value)}><option value="">Mọi ưu tiên</option>{Object.entries(PRIORITIES).map(([v, label]) => <option value={v} key={v}>{label}</option>)}</select>
        <select aria-label="Lọc dự án" value={filters.projectId} onChange={(e) => changeFilter('projectId', e.target.value)}><option value="">Mọi dự án</option>{projects.map((p) => <option value={p.id} key={p.id}>{p.name}</option>)}</select>
        {isAdmin && <select aria-label="Lọc danh mục" value={filters.categoryId} onChange={(e) => changeFilter('categoryId', e.target.value)}><option value="">Mọi danh mục</option>{categories.map((c) => <option value={c.id} key={c.id}>{c.name}</option>)}</select>}
      </div></div>
      <div className="filter-info"><label className="checkbox-label"><input type="checkbox" checked={filters.overdue} onChange={(e) => changeFilter('overdue', e.target.checked ? 'true' : '')} />Chỉ công việc quá hạn</label>{hasFilters && <button className="text-button" onClick={() => { setParams({}, { replace: true }); resetPaging() }}><X size={14} />Xóa bộ lọc</button>}<span>{serverPaging ? 'Phân trang theo người dùng' : 'Tìm kiếm và lọc trên toàn bộ dữ liệu được phép xem'}</span></div>
      {references.error && <div className="inline-warning" role="alert">Chưa tải được bộ lọc dự án/danh mục. <button className="text-button" onClick={references.reload}>Thử lại</button></div>}
      {state.loading ? <Spinner /> : state.error ? <ErrorState error={state.error} retry={state.reload} /> : rows.length === 0 ? <EmptyState title={hasFilters ? 'Không tìm thấy công việc phù hợp' : 'Bắt đầu với công việc đầu tiên'} description={hasFilters ? 'Thử thay đổi từ khóa hoặc xóa bớt bộ lọc.' : 'Thêm công việc, đặt ưu tiên và bắt đầu theo dõi tiến độ.'} action={!hasFilters ? () => setEditor({}) : undefined} actionLabel="Tạo công việc" /> : <div className="table-scroll"><table className="data-table"><thead><tr><th>Công việc</th><th>Trạng thái</th><th>Ưu tiên</th><th>Hạn hoàn thành</th>{!serverPaging && <th>Dự án</th>}<th className="text-right">Thao tác</th></tr></thead><tbody>{rows.map((task) => <tr key={task.id}>
        <td><button className="task-name" onClick={() => setEditor({ id: task.id })}><span className={`task-check ${task.status === 'DONE' ? 'checked' : ''}`}>{task.status === 'DONE' ? '✓' : ''}</span><span><strong>{task.title}</strong><small>#{task.id}{isAdmin && task.userId ? ` · Người sở hữu #${task.userId}` : ''}{task.categoryId ? ` · ${categories.find((c) => c.id === task.categoryId)?.name || `Danh mục #${task.categoryId}`}` : ''}</small></span></button></td>
        <td><div className="status-select-wrap"><select className={`status-select status-${task.status}`} aria-label={`Trạng thái ${task.title}`} value={task.status} disabled={updating !== null} onChange={(e) => updateStatus(task, e.target.value)}>{Object.entries(STATUSES).map(([v, label]) => <option value={v} key={v}>{label}</option>)}</select>{updating === task.id && <LoaderCircle size={14} className="animate-spin" />}</div></td>
        <td><Badge value={task.priority} priority /></td><td><span className={isOverdue(task) ? 'overdue-date' : 'table-date'} title={formatDate(task.dueDate, true)}>{formatDate(task.dueDate)}{isOverdue(task) && <small>Quá hạn</small>}</span></td>
        {!serverPaging && <td className="project-cell">{projects.find((p) => p.id === task.projectId)?.name || (task.projectId ? `Dự án #${task.projectId}` : '—')}</td>}
        <td><EditActions label={task.title} onEdit={() => setEditor({ id: task.id })} onDelete={() => setDeleting(task)} /></td>
      </tr>)}</tbody></table></div>}
      <div className="pagination"><label>Hiển thị <select aria-label="Số công việc mỗi trang" value={size} onChange={(e) => { setSize(Number(e.target.value)); resetPaging() }}>{[10, 20, 50, 100].map((v) => <option value={v} key={v}>{v}</option>)}</select> / trang</label><span>{serverPaging ? `Trang ${pageIndex + 1} · ${rows.length} công việc` : `${filtered.length ? pageIndex * size + 1 : 0}–${Math.min((pageIndex + 1) * size, filtered.length)} trong ${filtered.length}`}</span><div className="pagination-buttons"><button className="icon-button" disabled={pageIndex === 0 || state.loading} onClick={() => setPaging((prev) => ({ ...prev, index: Math.max(0, pageIndex - 1) }))} aria-label="Trang trước"><ChevronLeft size={18} /></button><span className="current-page">{pageIndex + 1}</span><button className="icon-button" disabled={state.loading || state.error || (serverPaging ? !state.data?.hasNext : pageIndex >= totalPages - 1)} onClick={next} aria-label="Trang sau"><ChevronRight size={18} /></button></div></div>
    </section>
    {editor && <TaskForm taskId={editor.id} onClose={() => setEditor(null)} onSuccess={refresh} />}
    {deleting && <DeleteDialog name={deleting.title} onDelete={() => taskService.remove(deleting.id)} onClose={() => setDeleting(null)} onSuccess={refresh} />}
  </>
}
