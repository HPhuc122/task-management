import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AxiosError } from 'axios'
import TaskForm from '../components/TaskForm'
import ResourceForm from '../components/ResourceForm'
import DeleteDialog from '../components/DeleteDialog'
import { AuthContext } from '../context/auth-context'
import { ToastContext } from '../context/toast-context'
import { attachmentService, categoryService, projectService, taskService } from '../services/resources'

const owner = { id: 4, email: 'member@example.com', role: 'USER' }
function renderForm(node, admin = false) {
  return render(<ToastContext.Provider value={vi.fn()}><AuthContext.Provider value={{ user: { ...owner, role: admin ? 'ADMIN' : 'USER' }, isAdmin: admin }}>{node}</AuthContext.Provider></ToastContext.Provider>)
}
beforeEach(() => {
  vi.spyOn(attachmentService, 'list').mockResolvedValue([])
  vi.spyOn(projectService, 'list').mockResolvedValue([{ id: 9, name: 'Đồ án', userId: 4 }])
  vi.spyOn(categoryService, 'list').mockResolvedValue([{ id: 2, name: 'Học tập' }])
})
describe('Mutation forms', () => {
  it('reuses the idempotency key after a failed Task creation with unchanged payload', async () => {
    const create = vi.spyOn(taskService, 'create').mockRejectedValueOnce(new AxiosError('Network error', 'ERR_NETWORK')).mockResolvedValueOnce({ id: 99 })
    const onSuccess = vi.fn()
    const onClose = vi.fn()
    renderForm(<TaskForm onSuccess={onSuccess} onClose={onClose} />)
    const user = userEvent.setup()
    await user.type(await screen.findByLabelText('Tên công việc *'), '  Viết README  ')
    await user.selectOptions(screen.getByLabelText('Dự án'), '9')
    await user.selectOptions(screen.getByLabelText('Độ ưu tiên'), 'HIGH')
    await user.click(screen.getByRole('button', { name: 'Tạo công việc', exact: true }))
    await screen.findByText('Không kết nối được máy chủ. Kiểm tra backend và kết nối mạng.')
    expect(onClose).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: 'Tạo công việc', exact: true }))
    await waitFor(() => expect(onSuccess).toHaveBeenCalledOnce())
    expect(create).toHaveBeenCalledTimes(2)
    expect(create.mock.calls[0][0]).toEqual({ title: 'Viết README', description: null, status: 'TODO', priority: 'HIGH', userId: 4, categoryId: null, projectId: 9, dueDate: null })
    expect(create.mock.calls[0][1].headers['Idempotency-Key']).toBe(create.mock.calls[1][1].headers['Idempotency-Key'])
    expect(categoryService.list).not.toHaveBeenCalled()
  })
  it('uses a fresh idempotency key after editing a failed creation payload', async () => {
    const create = vi.spyOn(taskService, 'create').mockRejectedValueOnce(new AxiosError('Network error', 'ERR_NETWORK')).mockResolvedValueOnce({ id: 99 })
    renderForm(<TaskForm onSuccess={vi.fn()} onClose={vi.fn()} />)
    const user = userEvent.setup()
    const title = await screen.findByLabelText('Tên công việc *')
    await user.type(title, 'Công việc ban đầu')
    await user.click(screen.getByRole('button', { name: 'Tạo công việc', exact: true }))
    await screen.findByRole('alert')
    await user.clear(title)
    await user.type(title, 'Công việc đã sửa')
    await user.click(screen.getByRole('button', { name: 'Tạo công việc', exact: true }))
    await waitFor(() => expect(create).toHaveBeenCalledTimes(2))
    expect(create.mock.calls[0][1].headers['Idempotency-Key']).not.toBe(create.mock.calls[1][1].headers['Idempotency-Key'])
  })
  it('keeps existing USER category and project associations on a full edit', async () => {
    vi.spyOn(taskService, 'get').mockResolvedValue({ id: 15, title: 'Ban đầu', description: 'Mô tả', status: 'IN_PROGRESS', priority: 'MEDIUM', userId: 4, categoryId: 2, projectId: 9, dueDate: '2030-10-12T02:30:45.123Z' })
    const update = vi.spyOn(taskService, 'update').mockResolvedValue({ id: 15 })
    renderForm(<TaskForm taskId={15} onSuccess={vi.fn()} onClose={vi.fn()} />)
    const user = userEvent.setup()
    const title = await screen.findByLabelText('Tên công việc *')
    await user.clear(title)
    await user.type(title, 'Tên mới')
    await user.click(screen.getByRole('button', { name: 'Lưu thay đổi' }))
    await waitFor(() => expect(update).toHaveBeenCalledWith(15, { title: 'Tên mới', description: 'Mô tả', status: 'IN_PROGRESS', priority: 'MEDIUM', userId: 4, categoryId: 2, projectId: 9, dueDate: '2030-10-12T02:30:45.123Z' }))
    expect(categoryService.list).not.toHaveBeenCalled()
  })
  it('sends current user ID when creating a Project as USER', async () => {
    const create = vi.spyOn(projectService, 'create').mockResolvedValue({ id: 10 })
    renderForm(<ResourceForm kind="project" onSuccess={vi.fn()} onClose={vi.fn()} />)
    const user = userEvent.setup()
    await user.type(screen.getByLabelText('Tên dự án *'), 'Nhóm React')
    await user.type(screen.getByLabelText('Mô tả'), 'Dự án học tập')
    await user.click(screen.getByRole('button', { name: 'Tạo dự án', exact: true }))
    await waitFor(() => expect(create).toHaveBeenCalledWith({ name: 'Nhóm React', description: 'Dự án học tập', userId: 4 }))
    expect(screen.queryByLabelText('ID người sở hữu *')).not.toBeInTheDocument()
  })
  it('updates Category with only the supported DTO fields', async () => {
    const update = vi.spyOn(categoryService, 'update').mockResolvedValue({ id: 2 })
    renderForm(<ResourceForm kind="category" item={{ id: 2, name: 'Cũ', description: 'Mô tả cũ' }} onSuccess={vi.fn()} onClose={vi.fn()} />, true)
    fireEvent.change(screen.getByLabelText('Tên danh mục *'), { target: { value: 'Mới' } })
    fireEvent.submit(screen.getByRole('button', { name: 'Lưu thay đổi' }).closest('form'))
    await waitFor(() => expect(update).toHaveBeenCalledWith(2, { name: 'Mới', description: 'Mô tả cũ' }))
  })
  it('does not delete until confirmed and preserves the dialog on a conflict', async () => {
    const onDelete = vi.fn().mockRejectedValue(new AxiosError('Conflict', 'ERR_BAD_RESPONSE', {}, null, { status: 409, data: {}, headers: {} }))
    const onClose = vi.fn()
    renderForm(<DeleteDialog name="Dự án" onDelete={onDelete} onClose={onClose} onSuccess={vi.fn()} />)
    expect(onDelete).not.toHaveBeenCalled()
    await userEvent.click(screen.getByRole('button', { name: 'Xóa dữ liệu' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Dữ liệu bị trùng hoặc đang được sử dụng')
    expect(onDelete).toHaveBeenCalledOnce()
    expect(onClose).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: 'Xóa dữ liệu' })).toBeEnabled()
  })
})
