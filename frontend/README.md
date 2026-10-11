# TaskFlow — Frontend Task Management

Frontend React + Vite cho backend Spring Boot trong repo này. Giao diện tiếng Việt, responsive, dùng Tailwind CSS v4, React Router, Axios và Lucide. Toàn bộ chức năng của ứng dụng gọi REST API thật; không có chế độ demo bằng dữ liệu giả.

## Chạy cục bộ

Yêu cầu Node.js 22.12+ (đã dùng Node 22.19) và npm. Khởi động backend theo [README của backend](../README.md) để API hoạt động tại `http://localhost:8080`. PostgreSQL/Redis và các biến môi trường của backend vẫn được quản lý ở thư mục gốc.

```powershell
cd D:\task-management\frontend
npm install
npm run dev
```

Mở **http://localhost:5173**. Vite cố định cổng 5173; nếu đang được sử dụng, dừng tiến trình cũ trước khi chạy. Mặc định request `/api` được Vite chuyển tới `http://localhost:8080`, nên không cần sửa backend/CORS.

Không bắt buộc tạo file `.env`. Để thay cấu hình:

```powershell
Copy-Item .env.example .env.local
```

```dotenv
VITE_API_BASE_URL=/api
API_PROXY_TARGET=http://localhost:8080
```

`VITE_API_BASE_URL` bao gồm tiền tố `/api`, không thêm `/auth` hoặc `/tasks`. `API_PROXY_TARGET` chỉ là origin backend, không thêm `/api`. Khởi động lại Vite sau khi đổi cấu hình. Không đặt JWT, mật khẩu hay secret vào biến `VITE_*`: các biến này được đưa vào bundle công khai.

## Chức năng

- Đăng nhập, đăng ký email/mật khẩu; đăng ký tạo USER theo backend. Không có lựa chọn tự cấp quyền ADMIN.
- Dashboard tính tổng công việc, đang làm, đã hoàn thành, quá hạn, tiến độ và thời hạn gần nhất từ danh sách API thật. ADMIN nhìn toàn hệ thống; USER chỉ nhìn dữ liệu backend cho phép.
- Task: danh sách, tạo, xem/chỉnh sửa đầy đủ, xóa, đổi trạng thái ngay trên bảng, ưu tiên, hạn hoàn thành, dự án và danh mục theo quyền.
- Project: danh sách, tìm kiếm, tạo, sửa, xóa, tiến độ từ Task liên kết và điều hướng tới công việc thuộc dự án.
- Category: danh sách và CRUD chỉ ADMIN; chặn cả menu lẫn route trực tiếp với USER.
- Tìm theo tên/mô tả; lọc trạng thái, ưu tiên, dự án, danh mục (ADMIN), quá hạn; thông báo kết quả, trạng thái tải/lỗi/rỗng, nút thử lại và xác nhận xóa.

Các giới hạn backend được giữ nguyên:

- Backend không có endpoint thống kê riêng. Dashboard tổng hợp từ `GET /api/tasks` và `GET /api/projects`; có thể tốn bộ nhớ khi dữ liệu rất lớn.
- `GET /api/tasks?userId=...&size=...&cursor=...` trả `CursorPage<TaskSummaryResponse>`, không có tổng số bản ghi/trang. Tab **Công việc của tôi**, khi không có bộ lọc, dùng API cursor, size 10/20/50/100, nút trước/sau và lịch sử cursor. Không bịa tổng số trang.
- Backend chưa hỗ trợ query tìm kiếm/lọc. Khi có bộ lọc, hoặc khi ADMIN xem **Toàn hệ thống**, frontend lấy danh sách đầy đủ được phép xem, lọc và phân trang tại client. Không giới hạn tìm kiếm trong trang cursor hiện tại. Không tự gửi tham số tìm kiếm mà backend chưa hỗ trợ.
- Project và Category trả mảng, không có phân trang API; tìm kiếm thực hiện ở client.
- USER không được gọi **bất kỳ** endpoint Category nào, kể cả GET. Frontend không gọi Category khi USER tạo/sửa Task; giữ nguyên `categoryId` của Task có sẵn. ADMIN được chọn danh mục. Mã danh mục là thông tin từ Task API, không phải tên giả.
- Không có API danh sách người dùng. ADMIN nhập ID chủ sở hữu đã tồn tại khi tạo/sửa Task hoặc Project; API xác minh ID và quyền. Không tạo danh sách người dùng giả.
- Không có API logout hoặc refresh token. Đăng xuất xóa JWT ở client; token đã phát hành vẫn có hiệu lực ở backend đến khi hết hạn. Hết hạn cần đăng nhập lại.
- Xóa Project/Category giữ lại Task và đặt khóa liên kết về null theo migration (`ON DELETE SET NULL`).

## JWT và phân quyền

- JWT chỉ lưu **trong bộ nhớ** của trang; không ghi `localStorage`, `sessionStorage`, cookie, URL hay log. Tải lại hoặc đóng trang phải đăng nhập lại. Đây là lựa chọn có chủ đích vì backend chưa có refresh token/cookie HttpOnly. Bộ nhớ vẫn không miễn nhiễm với XSS; nội dung API được render bằng React text, không chèn HTML.
- Axios gắn `Authorization: Bearer ...` vào request cần xác thực; không gắn vào login/register. Timeout 15 giây; request đọc bị hủy khi điều hướng hoặc bộ lọc thay đổi.
- Sau đăng nhập/đăng ký, frontend gọi `/api/auth/me` trước khi cấp giao diện theo quyền. Không tự giải mã JWT để tin cậy role. Đồng bộ `/me` khi quay lại cửa sổ và sau 403; backend vẫn là nơi quyết định quyền cuối cùng trên từng request.
- Bộ đếm `expiresIn` kết thúc phiên; 401 từ request của phiên hiện tại xóa phiên, báo hết hạn và chuyển về login, giữ đường dẫn đang mở để tiếp tục sau đăng nhập. 401 trả về muộn của phiên cũ không đăng xuất phiên mới.
- 403 hiển thị lỗi quyền và yêu cầu đồng bộ lại role, không tự xóa JWT. Khi ADMIN bị hạ quyền, trang Category bị chặn ngay sau đồng bộ. Lỗi mạng khi đồng bộ role không tự đăng xuất; thao tác vẫn được backend kiểm tra.
- Lỗi API dùng `ProblemDetail`; `errors` hiển thị cạnh trường form. Thông báo tổng quát được Việt hóa theo HTTP status, không in đối tượng Axios chứa header/body nhạy cảm.
- Mật khẩu không bị trim, đăng ký tối thiểu 8 ký tự, tối đa 72 ký tự và **72 byte UTF-8** theo DTO/BCrypt. Kiểm tra xác nhận mật khẩu phía frontend.
- Đổi trạng thái hoặc mở sửa từ trang cursor luôn lấy `GET /api/tasks/{id}` trước PUT vì summary không có description/userId/projectId. Gửi đủ DTO để tránh mất dữ liệu trong PUT thay thế.
- Tạo Task có `Idempotency-Key` UUID. Retry trong cùng form với payload không đổi giữ nguyên key; thay payload tạo key mới. Không tự động retry mutation.

## Hợp đồng API đã đối chiếu

Đọc toàn bộ `src/main/java/com/taskmanagement/controller`, `dto`, `config/SecurityConfig.java`, `CorsConfig.java`, `OpenApiConfig.java`; đồng thời đọc service, `CurrentUser`, handler `ProblemDetail`, migration và `OpenApiIntegrationTest`.

| Method | Endpoint | Dữ liệu / quyền |
| --- | --- | --- |
| POST | `/api/auth/login` | `{email,password}` → `{accessToken,tokenType,expiresIn,user}` |
| POST | `/api/auth/register` | Cùng payload login; luôn đăng ký USER |
| GET | `/api/auth/me` | Bearer → `{id,email,role}` |
| GET | `/api/tasks` | Mảng `TaskResponse`; ADMIN tất cả, USER dữ liệu của mình |
| GET | `/api/tasks?userId=&size=&cursor=` | `{content,nextCursor,hasNext}`; mặc định backend 20, tối đa 100 |
| GET | `/api/tasks/{id}` | Đầy đủ `TaskResponse` theo quyền |
| POST / PUT | `/api/tasks` / `/api/tasks/{id}` | `{title,description,status,priority,dueDate,userId,categoryId,projectId}` |
| DELETE | `/api/tasks/{id}` | 204 |
| GET / POST | `/api/projects` | Danh sách / tạo `{name,description,userId}` |
| GET / PUT / DELETE | `/api/projects/{id}` | Đọc / cập nhật / xóa theo quyền |
| GET / POST | `/api/categories` | ADMIN; tạo `{name,description}` |
| GET / PUT / DELETE | `/api/categories/{id}` | ADMIN |

`status`: `TODO`, `IN_PROGRESS`, `DONE`. `priority`: `LOW`, `MEDIUM`, `HIGH`. `dueDate` là ISO 8601 Instant hoặc null; ô datetime-local dùng múi giờ thiết bị và chuyển sang UTC khi gửi. Category/Project tùy chọn gửi null khi không chọn. POST thành công trả 201, PUT 200, DELETE 204.

Swagger: **http://localhost:8080/swagger-ui/index.html**; OpenAPI JSON: **http://localhost:8080/v3/api-docs**. Trong lúc triển khai, endpoint 8080 chưa truy cập được; đã đối chiếu cấu hình và kiểm thử OpenAPI trong source, chưa xác minh spec sinh trực tiếp hoặc CRUD với backend đang chạy.

## Kiểm tra và build

```powershell
npm run lint
npm test
npm run build
npm run preview
```

Preview tại **http://localhost:4173**, cũng proxy `/api` tới backend. `dist/` là output build, không commit. Test dùng Vitest + Testing Library: vòng đời JWT, interceptor Bearer/401/403, đồng bộ role, route bảo vệ, cursor, tìm kiếm trên toàn bộ dữ liệu, giữ nguyên DTO và validation UTF-8. Adapter/fixture chỉ có trong file test, không có trong mã chạy ứng dụng. Test frontend không thay thế kiểm thử tích hợp Spring Boot/PostgreSQL/Redis.

Đã kiểm tra hiển thị bằng Edge headless ở desktop 1440px và mobile 390px với fixture chỉ dùng cho QA: đăng nhập, Dashboard, bảng Task, form Task, không tràn ngang toàn trang và không có runtime exception. Không dùng kết quả này để xác nhận tích hợp backend thật.

Triển khai production cần web server phục vụ `dist`, fallback các route frontend về `index.html`, và reverse proxy `/api` tới Spring Boot. Hoặc build với `VITE_API_BASE_URL=https://backend.example.com/api` và đặt backend `CORS_ALLOWED_ORIGINS` đúng origin frontend. Vite proxy chỉ hoạt động với dev/preview, không được đóng gói vào static build. Dùng HTTPS và cấu hình security headers/CSP ở web server phù hợp môi trường.

## Cấu trúc

```text
src/
  components/    Layout, route bảo vệ, modal, form Task/Project/Category, UI dùng chung
  context/       AuthProvider, ToastProvider và hooks context
  hooks/         useResource: loading/error/data/reload và AbortController
  lib/           Enum tiếng Việt, ngày giờ, thống kê, filter, ánh xạ Task DTO
  pages/         Auth, Dashboard, Tasks, Projects, Categories, NotFound
  services/      Axios, session JWT trong bộ nhớ, API resources
  test/          Kiểm thử và fixture chỉ dùng khi test
```

## Kịch bản demo với backend thật

1. Chạy backend và mở Swagger để xác nhận API sẵn sàng; chạy frontend.
2. Đăng ký tài khoản USER mới, tạo Project rồi tạo Task có deadline/priority.
3. Đổi trạng thái trên bảng; kiểm tra Dashboard và tiến độ Project cập nhật khi mở/tải lại trang.
4. Thử tìm kiếm, lọc và xóa bộ lọc. Có hơn 10 Task để kiểm tra trang trước/sau bằng cursor.
5. Đăng xuất. Đăng nhập ADMIN được cấp bằng cơ chế của backend (xem README gốc); kiểm tra CRUD Category và dữ liệu toàn hệ thống.
6. Đăng nhập USER, mở trực tiếp `/categories` để kiểm tra chặn quyền. Tải lại trang để kiểm tra phiên kết thúc.

Không sửa backend, không tự commit/push. Mọi thay đổi frontend thực hiện trên nhánh `quang`.

Tham chiếu thư viện chính thức: [Tailwind v4 với Vite](https://tailwindcss.com/docs/installation/using-vite), [React Router declarative routing](https://reactrouter.com/start/declarative/routing), [Axios interceptors](https://axios-http.com/docs/interceptors), [Axios cancellation](https://axios-http.com/docs/cancellation).
