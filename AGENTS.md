# Quy định bắt buộc của Task Management API

Áp dụng cho mọi người và công cụ AI tham gia phát triển, review hoặc chuẩn bị bàn giao dự án. Đây là các điều kiện bắt buộc, không phải danh sách tính năng đã hoàn thành. Không đánh dấu một mục là đạt khi chưa có mã nguồn, tài liệu hoặc kết quả chạy để kiểm chứng. Khi một mục chưa đạt, ghi rõ phần còn thiếu và tiếp tục hoàn thiện trước khi coi công việc là hoàn tất.

`docs/architecture.md` là nguồn tham chiếu cho kiến trúc và phạm vi nghiệp vụ. Mọi thay đổi kiến trúc lớn phải cập nhật tài liệu đó trước khi triển khai. Nếu có mâu thuẫn về tiêu chuẩn hoàn thành, áp dụng các yêu cầu bắt buộc trong file này.

## 1. Kỹ thuật Spring Boot

- **IoC và Dependency Injection:** Các thành phần ứng dụng cần được Spring quản lý dưới dạng bean và nhận phụ thuộc qua constructor. Người triển khai phải giải thích được bean được tạo khi nào, ai quản lý vòng đời của nó, và điều gì mất đi khi tự `new` một thành phần lẽ ra do Spring quản lý. Không tự `new` service hoặc repository để né Dependency Injection.
- **REST controller và service:** Controller chỉ nhận request, kiểm tra dữ liệu đầu vào ở ranh giới API, gọi service và trả response. Logic nghiệp vụ và quy tắc phân quyền của nghiệp vụ nằm ở service, không viết trực tiếp trong controller.
- **Spring Data JPA:** Truy cập cơ sở dữ liệu qua repository. Khi thêm hoặc sửa truy vấn, phải hiểu SQL do JPA/Hibernate tạo ra và kiểm tra các rủi ro liên quan như N+1, truy vấn thừa và phạm vi transaction.

**Câu hỏi tự kiểm:** “Bean này được tạo ra lúc nào và ai tạo? Nếu em tự `new` nó thì mất gì?”

## 2. Quy trình phát triển và bàn giao

- **Git:** Làm việc trên nhánh riêng, mở pull request và có review chéo trước khi gộp. Giải quyết xung đột trên nhánh làm việc; không commit trực tiếp vào nhánh chính.
- **Chạy lại được:** Đóng gói ứng dụng và các dịch vụ phụ thuộc bằng Docker/Docker Compose để khởi chạy bằng một lệnh được ghi rõ trong README. Cung cấp dữ liệu mẫu và tài khoản test cho môi trường local/demo; không dùng thông tin đăng nhập demo ở production.
- **Bí mật:** Lấy mật khẩu, token, API key và cấu hình nhạy cảm từ biến môi trường hoặc cơ chế quản lý bí mật phù hợp. Không commit giá trị bí mật vào mã nguồn, ví dụ cấu hình, dữ liệu seed, test fixture hoặc log. File ví dụ chỉ dùng giá trị giả dành cho local.
- **Kiểm thử:** Mỗi thay đổi nghiệp vụ phải có kiểm thử phù hợp. Toàn dự án phải có unit test, test API hoặc ít nhất một luồng end-to-end có thể chạy lại; kết quả kiểm thử phải được kiểm tra trước khi gộp.
- **CI:** GitHub Actions phải chạy build và test cho mỗi lần push. Pull request cũng phải được kiểm tra trước khi gộp; lỗi build hoặc test phải được xử lý.
- **Bảo mật cơ bản:** Người triển khai phải nhận biết các nhóm rủi ro trong OWASP Top 10 và áp dụng biện pháp phù hợp. Nếu tự quản lý tài khoản, chỉ lưu mật khẩu đã hash bằng thuật toán phù hợp; không lưu dạng rõ. Dùng truy vấn có tham số/JPA để chống SQL injection, xử lý dữ liệu đầu ra để giảm XSS và giới hạn CORS theo các origin, method, header thực sự cần thiết.
- **Tài liệu:** README phải nêu yêu cầu môi trường, lệnh khởi chạy, seed, tài khoản test, cách chạy test và các biến môi trường cần thiết. API phải có tài liệu Swagger/OpenAPI hoặc tài liệu tương đương được cập nhật theo endpoint thực tế.
- **AI hỗ trợ code:** Người sử dụng AI phải tự đọc, chạy và kiểm chứng mã được gợi ý. Trong pull request, khai báo trung thực phần nào do AI gợi ý và cách đã kiểm chứng; trách nhiệm về thay đổi vẫn thuộc người gửi pull request.

## 3. Điều kiện hoàn thành

Trước khi tuyên bố một thay đổi hoặc dự án đã sẵn sàng, kiểm tra các mục liên quan ở trên bằng bằng chứng cụ thể: diff và review của pull request, lệnh chạy từ README, kết quả test/CI, cấu hình bảo mật và tài liệu API. Những mục chưa được triển khai phải được ghi là **chưa đạt**; tài liệu quy định này không thay thế việc triển khai hoặc kiểm chứng chúng.
