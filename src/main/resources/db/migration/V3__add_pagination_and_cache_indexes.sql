-- File: src/main/resources/db/migration/V3__add_pagination_and_cache_indexes.sql

-- Hỗ trợ cursor (keyset) pagination cho GET /api/tasks?userId=...&cursor=...
-- Query thực tế: WHERE user_id = ? AND id < ? ORDER BY id DESC LIMIT ?
-- Composite index (user_id, id DESC) cho phép Postgres trả kết quả đã
-- đúng thứ tự cần, không phải Sort riêng và không quét toàn bộ task của
-- user đó -> chi phí O(log n + page_size) thay vì O(n) như OFFSET lớn dần.
CREATE INDEX idx_tasks_user_id_id_desc ON tasks (user_id, id DESC);

-- Hỗ trợ lọc/sắp xếp theo hạn chót (ví dụ "các task sắp tới hạn").
-- Partial index: chỉ đánh index các dòng due_date khác NULL, vì phần lớn
-- truy vấn thực tế loại NULL trước khi filter/sort, nên index nhỏ hơn và
-- rẻ hơn khi ghi (insert/update) so với index đầy đủ trên toàn bộ cột.
CREATE INDEX idx_tasks_due_date ON tasks (due_date) WHERE due_date IS NOT NULL;

-- Ghi chú: cột id đã là PRIMARY KEY (tự động có unique btree index), nên
-- cursor pagination KHÔNG lọc theo user (ví dụ trang admin xem toàn bộ
-- task) có thể dùng thẳng "WHERE id < :cursor ORDER BY id DESC" mà không
-- cần thêm index nào khác.
