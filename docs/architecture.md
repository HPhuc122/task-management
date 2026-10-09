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

Spring AOP áp dụng một aspect cho các phương thức public của REST controller
để ghi thời gian thực thi và trạng thái thành công/lỗi. Aspect không ghi dữ liệu
request, response hoặc chi tiết exception; thời gian đo không bao gồm toàn bộ
vòng đời HTTP như xác thực và tuần tự hóa response.

## 3.4. Database

PostgreSQL lưu bốn bảng `users`, `categories`, `tasks`, `projects`. Flyway quản lý schema
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

## 3.6. CRUD API

- Controller nhận request DTO có Bean Validation và trả response DTO; không trả entity.
- Service xử lý nghiệp vụ và transaction; Repository kế thừa Spring Data `JpaRepository`.
- Task có các endpoint tại `/api/tasks`: POST, GET danh sách, GET theo ID, PUT, DELETE.
- POST trả 201 kèm Location, GET/PUT trả 200, DELETE trả 204.
- PUT thay thế dữ liệu: trường tùy chọn bị bỏ qua được đặt về null; status/priority
  bị bỏ qua hoặc null dùng TODO/MEDIUM như khi tạo mới.
- ID tham chiếu không tồn tại trả 404; request không hợp lệ trả 400; xung đột
  ràng buộc database trả 409. Lỗi dùng ProblemDetail, validation thêm `errors`.
- Timestamp lấy từ database sau khi flush. Mapping DTO diễn ra trong transaction
  để phù hợp với `open-in-view: false`.
- API CRUD yêu cầu JWT; `userId` là dữ liệu liên kết, không phải bằng chứng xác thực.
  Quyền sở hữu được kiểm tra ở service theo danh tính đã xác thực.

## 3.7. Project

Theo yêu cầu mở rộng CRUD Project, migration V4 thêm `projects` và `tasks.project_id`.
Project có name (bắt buộc, tối đa 255 ký tự), description tùy chọn và user sở hữu
bắt buộc. Một project có nhiều task; task có thể không thuộc project. Chủ sở hữu
task và project có thể khác nhau. Không đặt ràng buộc duy nhất cho tên project.
Xóa project đặt project_id về NULL, giữ task. Không xóa user đang sở hữu project.
Entity ánh xạ LAZY từ Project đến User và Task đến Project, không cascade xóa.
CRUD Project dùng `/api/projects` với cùng phương thức và quy ước HTTP như Task.
GET danh sách trả mảng theo id tăng dần; phân trang/lọc chưa thuộc phạm vi này.

## 3.8. Security, validation và lỗi

- `POST /api/auth/register` và `POST /api/auth/login` public; `GET /api/auth/me`
  và các API khác yêu cầu `Authorization: Bearer <accessToken>`.
- Đăng ký chỉ tạo USER, không nhận role từ client. Email giữ nguyên hoa/thường
  theo unique constraint hiện tại. Password tối thiểu 8 ký tự, tối đa 72 byte UTF-8,
  lưu bằng BCrypt. Seed V2 là placeholder, không dùng để đăng nhập.
- Profile `demo` nạp thông tin đăng nhập thử nghiệm từ biến môi trường cho đúng
  hai tài khoản seed USER (`phuc@example.com`) và ADMIN (`admin@example.com`).
  Nếu hash vẫn là placeholder của V2, ứng dụng thay bằng BCrypt; nếu hash đã
  khớp mật khẩu cấu hình thì giữ nguyên. Tài khoản có role hoặc mật khẩu khác
  sẽ làm startup thất bại để tránh ghi đè tài khoản thực. Ngoài profile
  `demo & !prod`, cả đăng nhập bằng mật khẩu và xác thực JWT của hai tài khoản
  demo đều bị từ chối, kể cả khi database đã từng được chạy bằng profile demo.
- Spring Security Resource Server xác minh JWT HS256 (signature, issuer, thời hạn,
  subject user ID). Secret Base64 ít nhất 32 byte lấy từ `JWT_SECRET`, bắt buộc ở
  mọi profile; không có secret mặc định. Access token mặc định sống 1 giờ.
- Stateless, không session/cookie authentication; CSRF tắt cho Bearer API. CORS
  do Security filter chain xử lý với danh sách origin cấu hình theo profile.
- Mỗi request lấy user/role hiện tại từ DB: user bị xóa không xác thực được,
  thay đổi role có hiệu lực ngay. Chưa có refresh token/logout/revocation riêng;
  client xóa token khi logout, token đã cấp sống đến hết hạn.
- USER chỉ liệt kê/đọc/sửa/xóa task và project mình sở hữu, chỉ tạo hoặc cập nhật
  với `userId` của mình; gắn task vào project cũng yêu cầu sở hữu project.
  ADMIN truy cập toàn bộ và được gán user/project khác nhau, giữ quan hệ DB cũ.
  Truy cập resource của người khác trả 403; resource không tồn tại trả 404.
- Quyền quản trị user/category được dành cho ADMIN; API category hiện có chỉ cho
  ADMIN truy cập, API user chưa được triển khai. Tài khoản ADMIN được cấp ngoài API
  đăng ký bằng quy trình quản trị DB; không seed mật khẩu công khai.
- DTO và path ID được Bean Validation kiểm tra. ProblemDetail thống nhất cho
  MVC và Security: 400 input, 401 authentication, 403 authorization, 404 missing,
  409 conflict, 500 unexpected. Validation thêm `errors` theo field/parameter;
  lỗi không trả SQL, stack trace, password hay chi tiết token.
