# Task Management API - Architecture

> Tài liệu kiến trúc chính thức của project.
>
> File này là nguồn tham chiếu chính (Single Source of Truth) cho kiến trúc,
> phạm vi nghiệp vụ, công nghệ, cấu trúc source code và nguyên tắc phát triển.
>
> Mọi thay đổi lớn về kiến trúc phải được cập nhật vào file này trước khi
> triển khai vào source code.

---

# 1. Project Overview

## 1.1. Tên project

**Task Management API**

## 1.2. Loại project

Backend REST API được xây dựng bằng **Java Spring Boot**.

Frontend chỉ được sử dụng ở mức prototype/demo để minh họa khả năng gọi API.

Trọng tâm của project là:

- Java OOP
- Spring Boot
- Dependency Injection
- REST API
- Layered Architecture
- JPA / Hibernate
- PostgreSQL
- Validation
- Exception Handling
- Authentication / Authorization
- JWT
- Transaction
- Testing
- Docker

Project không tập trung vào độ phức tạp của nghiệp vụ.

---

# 2. Mục tiêu của project

## 2.1. Mục tiêu chính

Xây dựng một hệ thống quản lý công việc đơn giản nhằm minh họa cách xây dựng một backend REST API bằng Spring Boot theo kiến trúc nhiều tầng.

Project phải thể hiện được khả năng:

1. Thiết kế backend theo kiến trúc rõ ràng.
2. Sử dụng Java OOP trong quá trình xây dựng hệ thống.
3. Sử dụng Spring Boot để xây dựng REST API.
4. Sử dụng Dependency Injection.
5. Kết nối Spring Boot với PostgreSQL.
6. Sử dụng JPA / Hibernate để thao tác database.
7. Tách Entity, DTO, Service, Repository và Controller.
8. Validate dữ liệu đầu vào.
9. Xử lý exception tập trung.
10. Xây dựng authentication và authorization.
11. Sử dụng JWT cho xác thực API.
12. Quản lý transaction.
13. Viết unit test và integration test ở mức phù hợp.
14. Đóng gói và chạy project bằng Docker.

---

# 3. Nguyên tắc thiết kế

Project tuân thủ các nguyên tắc sau:

## 3.1. Backend-first

Backend là thành phần chính của project.

Frontend chỉ có nhiệm vụ:

- minh họa API;
- kiểm tra luồng nghiệp vụ;
- hỗ trợ demo.

Frontend không quyết định kiến trúc backend.

---

## 3.2. Đơn giản hóa nghiệp vụ

Nghiệp vụ chỉ bao gồm các chức năng cần thiết cho Task Management.

Không mở rộng project bằng các chức năng không phục vụ mục tiêu học Spring Boot.

Không tự ý thêm:

- Chat
- Notification
- File upload
- Team management
- Project management
- Comment
- Payment
- Email service
- Microservices

nếu không có yêu cầu mới được xác nhận.

---

## 3.3. Layered Architecture

Backend sử dụng kiến trúc nhiều tầng:

```text
Controller
    ↓
Service
    ↓
Repository
    ↓
Database
```

## 3.4. Database

PostgreSQL lưu ba bảng `users`, `categories`, `tasks`. Flyway quản lý schema
qua migration trong `src/main/resources/db/migration`; Hibernate chỉ validate.
Entity JPA đặt trong package `com.taskmanagement.entity`.

- Mỗi user có email duy nhất, password hash và role USER hoặc ADMIN.
- Mỗi category có tên duy nhất.
- Task bắt buộc có title và user sở hữu; description, due date và category là tùy chọn.
- Status: TODO (mặc định), IN_PROGRESS, DONE. Priority: LOW, MEDIUM (mặc định), HIGH.
- Không xóa user đang có task (foreign key RESTRICT); cần xử lý task trước.
- Xóa category sẽ đặt category_id của task về NULL và giữ lại task.
- Khóa chính BIGINT identity. Thời gian dùng TIMESTAMPTZ và Java Instant.
- Database tự cập nhật updated_at khi UPDATE; không lưu mật khẩu dạng rõ.
- Entity chỉ ánh xạ quan hệ từ Task đến User/Category, tải LAZY.
- Quyền quản trị user/category sẽ được thực thi ở tầng service/security khi triển khai API.

## 3.5. API, cache và phân trang hiện có

- `CategoryController` chỉ nhận request/response; `CategoryService` xử lý nghiệp vụ và gọi `CategoryRepository`.
- `TaskController` gọi `TaskService`; service lấy dữ liệu qua `TaskRepository` và trả DTO, không trả JPA entity trực tiếp.
- Spring Cache dùng Redis cho danh sách và từng category. Service khai báo `@Cacheable`, `@CachePut`, `@CacheEvict`; `RedisCacheConfig` cấu hình nơi lưu và thời gian sống. Khi ghi category, cache liên quan được cập nhật hoặc xóa.
- API `GET /api/tasks?userId=...&size=...&cursor=...` phân trang theo `id` giảm dần. Truy vấn trang sau dùng `user_id = ? AND id < ? ORDER BY id DESC LIMIT ?`; migration `V3` tạo index `(user_id, id DESC)`. Đọc thêm một bản ghi để xác định `hasNext`.
- Docker Compose khởi chạy ứng dụng, PostgreSQL và Redis. Ứng dụng chờ hai dịch vụ phụ thuộc healthy trước khi khởi động.
