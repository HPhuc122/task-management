-- =========================
-- DEMO USERS
-- =========================

INSERT INTO users (email, password_hash, role)
VALUES
('quang@example.com', 'demo_password_hash_1', 'USER'),
('phuc@example.com', 'demo_password_hash_2', 'USER'),
('admin@example.com', 'demo_password_hash_3', 'ADMIN');


-- =========================
-- DEMO CATEGORIES
-- =========================

INSERT INTO categories (name, description)
VALUES
('Học tập', 'Các công việc liên quan đến học tập'),
('Cá nhân', 'Các công việc cá nhân'),
('Nhóm', 'Các công việc làm theo nhóm');


-- =========================
-- DEMO TASKS
-- =========================

INSERT INTO tasks
(title, description, status, priority, due_date, user_id, category_id)
VALUES
(
    'Hoàn thành báo cáo Spring Boot',
    'Hoàn thành các nội dung báo cáo môn Công nghệ lập trình hiện đại',
    'IN_PROGRESS',
    'HIGH',
    CURRENT_TIMESTAMP + INTERVAL '7 days',
    1,
    1
),
(
    'Học Spring Data JPA',
    'Tìm hiểu Repository và cách Hibernate sinh SQL',
    'TODO',
    'MEDIUM',
    CURRENT_TIMESTAMP + INTERVAL '5 days',
    1,
    1
),
(
    'Họp nhóm',
    'Trao đổi tiến độ đồ án Task Management API',
    'TODO',
    'HIGH',
    CURRENT_TIMESTAMP + INTERVAL '2 days',
    2,
    3
),
(
    'Cập nhật tài liệu',
    'Cập nhật README và tài liệu kỹ thuật',
    'DONE',
    'LOW',
    CURRENT_TIMESTAMP + INTERVAL '1 day',
    2,
    3
);