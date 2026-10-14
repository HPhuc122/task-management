import TaskAttachments from './TaskAttachments'
import { useCallback, useRef, useState } from 'react'
import { LoaderCircle, Save } from 'lucide-react'
import Modal from './Modal'
import { ErrorState, Field, FormError, Spinner } from './ui'
import { useAuth } from '../context/auth-context'
import { useToast } from '../context/toast-context'
import useResource from '../hooks/useResource'
import { categoryService, projectService, taskService } from '../services/resources'
import { errorMessage, fieldErrors } from '../services/api'
import { PRIORITIES, STATUSES, taskPayload, toLocalInput } from '../lib/tasks'

export default function TaskForm({ taskId, onClose, onSuccess }) {
  const { user, isAdmin } = useAuth()
  const loader = useCallback(async (signal) => {
    const [task, projects, categories] = await Promise.all([
      taskId ? taskService.get(taskId, signal) : Promise.resolve(null),
      projectService.list(signal), isAdmin ? categoryService.list(signal) : Promise.resolve([]),
    ])
    return { task, projects, categories }
  }, [taskId, isAdmin])
  const state = useResource(loader)
  if (!state.data) return <Modal title={taskId ? 'Chỉnh sửa công việc' : 'Tạo công việc mới'} onClose={onClose} wide>{state.error ? <ErrorState error={state.error} retry={state.reload} /> : <Spinner />}</Modal>
  return <TaskEditor {...state.data} user={user} isAdmin={isAdmin} onClose={onClose} onSuccess={onSuccess} />
}

function TaskEditor({ task, projects, categories, user, isAdmin, onClose, onSuccess }) {
  const [values, setValues] = useState(() => ({
    title: task?.title || '', description: task?.description || '', status: task?.status || 'TODO', priority: task?.priority || 'MEDIUM',
    dueDate: toLocalInput(task?.dueDate), userId: task?.userId || user.id, categoryId: task?.categoryId || '', projectId: task?.projectId || '',
  }))
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [errors, setErrors] = useState({})
  const idempotency = useRef({ body: null, key: null })
  const notify = useToast()
  const change = (event) => setValues((prev) => ({ ...prev, [event.target.name]: event.target.value }))
  const submit = async (event) => {
    event.preventDefault()
    if (busy) return
    setErrors({}); setError('')
    if (!values.title.trim()) { setErrors({ title: 'Vui lòng nhập tên công việc.' }); return }
    setBusy(true)
    try {
      const dueDate = task && values.dueDate === toLocalInput(task.dueDate)
        ? task.dueDate
        : values.dueDate ? new Date(values.dueDate).toISOString() : null
      const body = taskPayload({ ...values, dueDate })
      if (task) await taskService.update(task.id, body)
      else {
        const serialized = JSON.stringify(body)
        // Same payload retry uses the same key; edited payload gets a fresh key.
        if (idempotency.current.body !== serialized) idempotency.current = { body: serialized, key: crypto.randomUUID() }
        await taskService.create(body, { headers: { 'Idempotency-Key': idempotency.current.key } })
      }
      notify(task ? 'Đã cập nhật công việc.' : 'Đã tạo công việc mới.'); onSuccess(); onClose()
    } catch (err) { setError(errorMessage(err)); setErrors(fieldErrors(err)); setBusy(false) }
  }
  return <Modal title={task ? 'Chỉnh sửa công việc' : 'Tạo công việc mới'} description="Điền thông tin để sắp xếp công việc rõ ràng hơn." onClose={onClose} busy={busy} wide>
    <form onSubmit={submit}>
      <div className="modal-body">
      <fieldset disabled={busy} className="form-grid"><FormError message={error} />
      <Field label="Tên công việc *" error={errors.title} className="col-span-full"><input name="title" autoFocus required maxLength={255} placeholder="Ví dụ: Hoàn thiện giao diện đăng nhập" value={values.title} onChange={change} /></Field>
      <Field label="Mô tả" error={errors.description} className="col-span-full"><textarea name="description" rows={3} placeholder="Nội dung, yêu cầu hoặc ghi chú cho công việc…" value={values.description} onChange={change} /></Field>
      <Field label="Trạng thái" error={errors.status}><select name="status" value={values.status} onChange={change}>{Object.entries(STATUSES).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></Field>
      <Field label="Độ ưu tiên" error={errors.priority}><select name="priority" value={values.priority} onChange={change}>{Object.entries(PRIORITIES).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></Field>
      <Field label="Hạn hoàn thành" error={errors.dueDate} hint="Theo giờ địa phương của thiết bị."><input name="dueDate" type="datetime-local" value={values.dueDate} onChange={change} /></Field>
      <Field label="Dự án" error={errors.projectId}><select name="projectId" value={values.projectId} onChange={change}><option value="">Không thuộc dự án</option>{projects.map((p) => <option key={p.id} value={p.id}>{p.name}{isAdmin ? ` · #${p.userId}` : ''}</option>)}</select></Field>
      {isAdmin && <Field label="Danh mục" error={errors.categoryId}><select name="categoryId" value={values.categoryId} onChange={change}><option value="">Không có danh mục</option>{task?.categoryId && !categories.some((c) => c.id === task.categoryId) && <option value={task.categoryId}>Danh mục #{task.categoryId}</option>}{categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</select></Field>}
      {isAdmin && <Field label="ID người sở hữu *" error={errors.userId} hint="Nhập ID tài khoản đã tồn tại trong hệ thống."><input name="userId" type="number" min={1} step={1} required value={values.userId} onChange={change} /></Field>}
      {!isAdmin && task?.categoryId && <p className="field-hint col-span-full">Danh mục #{task.categoryId} do quản trị viên quản lý sẽ được giữ nguyên.</p>}
      {task ? <TaskAttachments key={task.id} taskId={task.id} /> : <p className="field-hint col-span-full">Tạo công việc thành công trước, sau đó mở chỉnh sửa để thêm tệp đính kèm.</p>}</fieldset></div>
      <div className="modal-footer"><button type="button" className="btn btn-secondary" disabled={busy} onClick={onClose}>Hủy</button><button className="btn btn-primary" disabled={busy}>{busy ? <LoaderCircle size={17} className="animate-spin" /> : <Save size={17} />}{task ? 'Lưu thay đổi' : 'Tạo công việc'}</button></div></form>
  </Modal>
}
