-- Hỗ trợ cursor (keyset) pagination cho GET /api/tasks?userId=...&cursor=...
-- Query: WHERE user_id = ? AND id < ? ORDER BY id DESC LIMIT ?
CREATE INDEX idx_tasks_user_id_id_desc ON tasks (user_id, id DESC);

-- Hỗ trợ lọc/sắp xếp các task có hạn chót.
CREATE INDEX idx_tasks_due_date ON tasks (due_date) WHERE due_date IS NOT NULL;
