import { useCallback, useState } from 'react'
import { Plus, RefreshCw, Search, ShieldCheck, Tags } from 'lucide-react'
import { categoryService } from '../services/resources'
import useResource from '../hooks/useResource'
import ResourceForm from '../components/ResourceForm'
import DeleteDialog from '../components/DeleteDialog'
import { EditActions, EmptyState, ErrorState, PageHeader, Spinner } from '../components/ui'

export default function CategoriesPage() {
  const [query, setQuery] = useState('')
  const [editor, setEditor] = useState(null)
  const [deleting, setDeleting] = useState(null)
  const loader = useCallback((signal) => categoryService.list(signal), [])
  const state = useResource(loader)
  const categories = state.data?.filter((c) => `${c.name} ${c.description || ''}`.toLocaleLowerCase('vi').includes(query.trim().toLocaleLowerCase('vi'))) || []
  return <>
    <PageHeader eyebrow="QUẢN TRỊ HỆ THỐNG" title="Danh mục công việc" description="Xây dựng hệ thống phân loại nhất quán cho các công việc."><button className="btn btn-secondary" onClick={state.reload} disabled={state.loading}><RefreshCw size={16} />Tải lại</button><button className="btn btn-primary" onClick={() => setEditor({})}><Plus size={18} />Tạo danh mục</button></PageHeader>
    <div className="admin-notice"><ShieldCheck size={21} /><div><strong>Chức năng dành cho quản trị viên</strong><p>Danh mục được dùng chung trong hệ thống và chỉ ADMIN được truy cập, chỉnh sửa.</p></div></div>
    <section className="panel"><div className="panel-heading"><h2>Tất cả danh mục <span className="count-pill">{state.data?.length ?? '—'}</span></h2><div className="input-icon"><Search size={17} /><input aria-label="Tìm danh mục" placeholder="Tìm kiếm danh mục…" value={query} onChange={(e) => setQuery(e.target.value)} /></div></div>
      {state.loading ? <Spinner /> : state.error ? <ErrorState error={state.error} retry={state.reload} /> : !categories.length ? <EmptyState title={query ? 'Không tìm thấy danh mục' : 'Chưa có danh mục nào'} description={query ? 'Hãy thử một từ khóa khác.' : 'Tạo danh mục đầu tiên để phân loại công việc.'} action={!query ? () => setEditor({}) : undefined} actionLabel="Tạo danh mục" /> : <div className="table-scroll"><table className="data-table"><thead><tr><th>Mã</th><th>Tên danh mục</th><th>Mô tả</th><th className="text-right">Thao tác</th></tr></thead><tbody>{categories.map((category) => <tr key={category.id}><td className="muted">#{category.id}</td><td><div className="category-name"><span className="category-icon"><Tags size={17} /></span><strong>{category.name}</strong></div></td><td className="description-cell">{category.description || 'Chưa có mô tả'}</td><td><EditActions label={category.name} onEdit={() => setEditor({ item: category })} onDelete={() => setDeleting(category)} /></td></tr>)}</tbody></table></div>}
      <div className="panel-bottom muted">{categories.length} danh mục{query ? ' phù hợp' : ' trong hệ thống'}</div>
    </section>
    {editor && <ResourceForm kind="category" item={editor.item} onClose={() => setEditor(null)} onSuccess={state.reload} />}
    {deleting && <DeleteDialog name={deleting.name} onDelete={() => categoryService.remove(deleting.id)} onClose={() => setDeleting(null)} onSuccess={state.reload} description="Các công việc vẫn được giữ lại và sẽ không còn liên kết với danh mục này. Thao tác xóa danh mục không thể hoàn tác." />}
  </>
}
