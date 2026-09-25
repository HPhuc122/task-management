# Task Management API - Architecture

## 1. Project Overview

Task Management API là một backend REST API được xây dựng bằng Java
và Spring Boot.

Mục tiêu chính của project:

1. Học Java OOP từ nền tảng.
2. Hiểu kiến trúc ứng dụng backend 3 lớp.
3. Hiểu bản chất của Spring IoC và Dependency Injection.
4. Xây dựng REST API bằng Spring Boot.
5. Làm việc với PostgreSQL thông qua Spring Data JPA/Hibernate.
6. Học Validation, Exception Handling, Configuration và Profiles.
7. Học Authentication và Authorization.
8. Học Transaction Management.
9. Học AOP và các Cross-cutting Concerns.
10. Viết Unit Test, Integration Test và Web Layer Test.
11. Sử dụng Docker để chạy PostgreSQL và cuối cùng Dockerize Backend.

Frontend của project chỉ được sử dụng để minh họa hệ thống.
Frontend không phải trọng tâm lập trình.

Frontend có thể được xây dựng bằng prototype/mockup và được trình bày
thông qua video demo.


---

# 2. Project Scope

## Backend

Backend là phần chính của project.

Backend phải được code bằng:

- Java
- Spring Boot
- Spring Web
- Spring Data JPA
- Hibernate
- PostgreSQL

Các nội dung nâng cao dự kiến:

- Validation
- Exception Handling
- Configuration
- Profiles
- Spring Security
- JWT
- Transaction
- AOP
- Testing
- Docker
- CI/CD


## Frontend

Frontend không yêu cầu code.

Frontend chỉ cần có prototype/mockup cho các màn hình chính:

- Login
- Dashboard
- Task List
- Create Task
- Edit Task
- Task Detail
- Admin/User Management

Frontend phải phản ánh được các API mà Backend cung cấp.


---

# 3. Business Domain

Hệ thống quản lý công việc có nghiệp vụ đơn giản.

Các đối tượng chính:

- User
- Task
- Category

Quan hệ dự kiến:

User 1 ---- N Task

Category 1 ---- N Task


## Task

Một Task có thể có:

- id
- title
- description
- status
- priority
- dueDate
- user
- category
- createdAt
- updatedAt


## Task Status

- TODO
- IN_PROGRESS
- DONE


## Task Priority

- LOW
- MEDIUM
- HIGH


## User

User có thể có:

- id
- username
- email
- password
- role


## Role

- USER
- ADMIN


## Category

Category có:

- id
- name
- description


---

# 4. Architecture Principle

Project sử dụng kiến trúc 3 lớp.

Presentation Layer
        ↓
Business Layer
        ↓
Data Access Layer


Tương ứng:

Controller
    ↓
Service
    ↓
Repository


## Presentation Layer

Chịu trách nhiệm:

- nhận HTTP Request
- validate input ở mức API
- gọi Service
- trả HTTP Response

Không đặt business logic phức tạp trong Controller.


## Business Layer

Chịu trách nhiệm:

- xử lý nghiệp vụ
- kiểm tra business rules
- điều phối Repository
- transaction

Đây là layer quan trọng nhất về mặt nghiệp vụ.


## Data Access Layer

Chịu trách nhiệm:

- truy vấn dữ liệu
- lưu dữ liệu
- cập nhật dữ liệu
- xóa dữ liệu

Layer này sử dụng Spring Data JPA/Hibernate.


---

# 5. Dependency Direction

Dependency phải đi theo hướng:

Controller
    ↓
Service
    ↓
Repository
    ↓
Database


Không được để:

Controller
    ↓
Repository


trong các nghiệp vụ thông thường.

Controller không được tự xử lý database.

Repository không được chứa business logic.


---

# 6. Java OOP Principles

Project phải sử dụng và minh họa các nguyên tắc OOP của Java.

## Encapsulation

Các thuộc tính của class nên được đóng gói.

Ví dụ:

private String title;


Không cho phép thay đổi state của object một cách tùy tiện
từ bên ngoài nếu business rule yêu cầu kiểm soát.


## Abstraction

Sử dụng interface khi phù hợp.

Ví dụ:

TaskRepository
NotificationService


Implementation cụ thể không nên bị phụ thuộc cứng vào client.


## Inheritance

Có thể sử dụng inheritance khi có quan hệ "is-a" hợp lý.

Ví dụ dự kiến:

BaseEntity
    ↑
    ├── User
    ├── Task
    └── Category


Không sử dụng inheritance chỉ để chứng minh rằng project có OOP.


## Polymorphism

Project phải có ít nhất một trường hợp có thể minh họa
polymorphism.

Ví dụ:

NotificationService
    ↑
    ├── EmailNotificationService
    └── ConsoleNotificationService


Mục tiêu là hiểu interface, implementation và Dependency Injection.


---

# 7. Spring IoC / Dependency Injection

Một mục tiêu quan trọng của project là hiểu bản chất của IoC và DI.

Ví dụ:

TaskService
    ↓
TaskRepository


TaskService không tự tạo TaskRepository bằng:

new TaskRepositoryImplementation()


Thay vào đó Spring Container chịu trách nhiệm quản lý dependency.


Concept cần hiểu:

- Bean
- ApplicationContext
- IoC Container
- Dependency Injection
- Constructor Injection
- @Component
- @Service
- @Repository
- @Controller


Ưu tiên Constructor Injection.

Không sử dụng Field Injection làm cách mặc định.


---

# 8. Expected Package Structure

Package structure sẽ phát triển dần trong quá trình học.

Không tạo tất cả package ngay từ đầu.

Kiến trúc cuối cùng dự kiến:

com.example.taskmanagement

├── controller
├── service
├── repository
├── entity
├── dto
├── mapper
├── exception
├── config
├── security
├── aspect
├── enums
└── common


Các package chỉ được tạo khi project thực sự cần chúng.

Mục tiêu là người học hiểu:

"Vì sao package này tồn tại?"

thay vì chỉ học thuộc folder structure.


---

# 9. Entity Layer

Entity đại diện cho dữ liệu được lưu trong database.

Dự kiến:

User
Task
Category


Có thể có:

BaseEntity


BaseEntity có thể chứa các thuộc tính dùng chung:

- id
- createdAt
- updatedAt


Entity không nên chứa logic liên quan đến HTTP.


---

# 10. DTO Layer

API không nên expose trực tiếp Entity trong mọi trường hợp.

DTO được sử dụng để kiểm soát dữ liệu đi vào và đi ra API.

Dự kiến:

Request DTO
    ↓
Controller
    ↓
Service


và:

Service
    ↓
Response DTO
    ↓
Controller
    ↓
JSON


Ví dụ:

CreateTaskRequest
UpdateTaskRequest
TaskResponse


---

# 11. Repository Layer

Repository chịu trách nhiệm giao tiếp với database.

Ví dụ:

TaskRepository
UserRepository
CategoryRepository


Repository dự kiến sử dụng:

Spring Data JPA


Ví dụ:

JpaRepository<Task, Long>


Repository không chịu trách nhiệm:

- authentication
- authorization
- business rule
- HTTP response


---

# 12. Service Layer

Service là nơi xử lý business logic.

Ví dụ:

TaskService

có thể chịu trách nhiệm:

- tạo task
- cập nhật task
- xóa task
- thay đổi status
- kiểm tra quyền thao tác
- kiểm tra category
- kiểm tra user


Service không nên phụ thuộc vào HTTP-specific logic.


---

# 13. Controller Layer

Controller cung cấp REST API.

Dự kiến:

/api/auth
/api/tasks
/api/categories
/api/users
/api/admin


Ví dụ:

GET    /api/tasks
GET    /api/tasks/{id}
POST   /api/tasks
PUT    /api/tasks/{id}
DELETE /api/tasks/{id}


Controller chỉ nên:

1. nhận request
2. chuyển dữ liệu vào Service
3. nhận kết quả
4. trả response


---

# 14. Validation

Request DTO sẽ được validate.

Ví dụ:

CreateTaskRequest

- title không được rỗng
- title có độ dài hợp lệ
- priority hợp lệ
- dueDate hợp lệ


Các annotation có thể sử dụng:

@NotBlank
@NotNull
@Size
@Email
@Pattern


Validation không thay thế business validation.

Cần phân biệt:

Input Validation

và

Business Validation.


---

# 15. Exception Handling

Project sử dụng centralized exception handling.

Dự kiến:

GlobalExceptionHandler


Ví dụ:

Task không tồn tại

→ TaskNotFoundException

→ HTTP 404


Request không hợp lệ

→ HTTP 400


Không có quyền

→ HTTP 403


Chưa đăng nhập

→ HTTP 401


Mục tiêu là API trả lỗi có cấu trúc và nhất quán.


---

# 16. Authentication and Authorization

Phần này được triển khai sau khi hoàn thành CRUD cơ bản.

Authentication:

"Bạn là ai?"


Authorization:

"Bạn được phép làm gì?"


Dự kiến sử dụng:

Spring Security
JWT


Role:

USER
ADMIN


Ví dụ:

USER:

- xem task của mình
- tạo task
- sửa task của mình
- xóa task của mình


ADMIN:

- quản lý user
- xem tất cả task
- quản lý category


---

# 17. Transaction

Transaction được học sau khi đã hiểu:

- database
- JPA
- Service


Transaction thường được đặt ở Service Layer.

Ví dụ:

Một nghiệp vụ có:

1. tạo task
2. cập nhật một dữ liệu liên quan
3. ghi log


Nếu bước cuối thất bại thì các thay đổi trước đó có thể phải rollback.


Mục tiêu:

Hiểu @Transactional không chỉ là học thuộc annotation.


---

# 18. AOP

AOP được sử dụng cho Cross-cutting Concerns.

Ví dụ:

- Logging
- Execution time
- Audit


Ví dụ:

TaskService.createTask()

        ↓

Logging Aspect

        ↓

TaskService.createTask()


AOP không được sử dụng để thay thế business logic.


---

# 19. Configuration

Project sử dụng:

application.yml

và Profiles:

application-dev.yml
application-test.yml
application-prod.yml


Các thông tin môi trường như:

- database URL
- username
- password
- JWT secret

không nên hard-code trong source code production.


---

# 20. Database

Database:

PostgreSQL


Development database được chạy bằng:

Docker Compose


Ban đầu:

Spring Boot
    ↓
localhost:5432
    ↓
PostgreSQL Container


Sau khi hoàn thiện:

Docker Compose

├── Spring Boot
└── PostgreSQL


---

# 21. Testing Strategy

Project dự kiến có nhiều loại test.

## Unit Test

Tập trung vào:

Service


Ví dụ:

TaskServiceTest


## Web Layer Test

Tập trung vào:

Controller


Có thể sử dụng:

@WebMvcTest


## Repository Test

Tập trung vào:

JPA Repository


Có thể sử dụng:

@DataJpaTest


## Integration Test

Kiểm tra nhiều layer kết hợp.

Ví dụ:

Controller
    ↓
Service
    ↓
Repository
    ↓
Database


Mục tiêu là hiểu sự khác nhau giữa các loại test.


---

# 22. Frontend Prototype

Frontend không được code.

Frontend chỉ mô phỏng Client.

Các màn hình:

1. Login
2. Dashboard
3. Task List
4. Create Task
5. Edit Task
6. Task Detail
7. Admin


Prototype phải tương ứng với API Backend.


---

# 23. Docker

Docker Compose được sử dụng để chạy PostgreSQL trong giai đoạn đầu.

Sau khi Backend hoàn thiện, có thể Dockerize Spring Boot.

Final architecture:

Docker Compose

    ┌─────────────────────┐
    │ Spring Boot         │
    │ Container           │
    └──────────┬──────────┘
               │
               ▼
    ┌─────────────────────┐
    │ PostgreSQL          │
    │ Container           │
    └─────────────────────┘


---

# 24. Development Rules

## Rule 1

Không viết business logic trong Controller.


## Rule 2

Không truy cập Repository trực tiếp từ Controller.


## Rule 3

Service chịu trách nhiệm business logic.


## Rule 4

Repository chịu trách nhiệm data access.


## Rule 5

Ưu tiên Constructor Injection.


## Rule 6

Không tạo package chỉ vì "architecture template" yêu cầu.

Package chỉ được tạo khi có nhu cầu thực tế.


## Rule 7

Không thêm dependency nếu chưa hiểu mục đích.


## Rule 8

Không sử dụng design pattern chỉ để làm project phức tạp.


## Rule 9

Ưu tiên code đơn giản, dễ đọc.


## Rule 10

Mỗi tính năng mới phải giải thích được:

- Nó giải quyết vấn đề gì?
- Vì sao đặt ở layer này?
- Vì sao cần abstraction này?
- Nếu không sử dụng nó thì vấn đề gì xảy ra?


---

# 25. Development Philosophy

Project được xây dựng theo hướng:

Understand → Design → Implement → Test


Không làm theo hướng:

Copy code → chạy được → chuyển sang phần khác.


Mỗi công nghệ phải được học theo ba mức:

Level 1:
Biết sử dụng.


Level 2:
Hiểu cách nó hoạt động.


Level 3:
Hiểu tại sao kiến trúc Spring được thiết kế như vậy.


Mục tiêu cuối cùng là đạt Level 3.


---

# 26. Development Roadmap

## Phase 1 - Java OOP

Học:

- Class
- Object
- Constructor
- Encapsulation
- Interface
- Abstract class
- Inheritance
- Polymorphism
- Exception
- Collection
- Generics
- Lambda cơ bản


## Phase 2 - 3 Layer Architecture bằng Java thuần

Xây:

Controller
    ↓
Service
    ↓
Repository


Chưa sử dụng Spring.


## Phase 3 - Spring Core

Học:

- IoC
- DI
- Bean
- ApplicationContext
- Component Scan
- @Component
- @Service
- @Repository
- Constructor Injection
- @Primary
- @Qualifier


## Phase 4 - Spring Boot Web

Học:

- REST
- HTTP
- Controller
- Request
- Response
- JSON
- PathVariable
- RequestParam
- RequestBody
- HTTP Status


## Phase 5 - Database

Học:

- PostgreSQL
- JPA
- Hibernate
- Entity
- Relationship
- JpaRepository
- Query
- Lazy/Eager


## Phase 6 - Production-style Backend

Học:

- DTO
- Validation
- Exception Handling
- Configuration
- Profiles


## Phase 7 - Security

Học:

- Authentication
- Authorization
- Spring Security
- JWT
- Password Hashing
- Role


## Phase 8 - Advanced Spring

Học:

- Transaction
- AOP
- Logging
- Cross-cutting Concerns


## Phase 9 - Testing

Học:

- JUnit
- Mockito
- @WebMvcTest
- @DataJpaTest
- Integration Test


## Phase 10 - Docker

Học:

- Dockerfile
- Docker Compose
- Container
- Network
- Environment Variables


## Phase 11 - CI/CD

Học:

- GitHub Actions
- Build
- Test
- Package


---

# 27. Definition of Done

Project được xem là hoàn thành khi:

- Backend chạy được.
- PostgreSQL chạy bằng Docker.
- REST API hoạt động.
- CRUD Task hoạt động.
- User và Category hoạt động.
- Validation hoạt động.
- Exception Handling hoạt động.
- Authentication hoạt động.
- Authorization hoạt động.
- Transaction được sử dụng hợp lý.
- Có ít nhất một ví dụ AOP.
- Có Unit Test.
- Có Controller Test.
- Có Repository Test.
- Có Integration Test.
- Có Dockerfile.
- Docker Compose chạy được toàn bộ hệ thống.
- Có GitHub repository.
- Có README.
- Có Architecture Documentation.
- Có Frontend Prototype.
- Có video demo.


---

# 28. AI Development Instructions

AI hỗ trợ project phải tuân thủ architecture.md này.

Khi được yêu cầu thêm hoặc sửa code, AI phải:

1. Đọc architecture.md trước.
2. Xác định layer mà code thuộc về.
3. Không tự ý tạo package mới nếu chưa cần thiết.
4. Không đưa business logic vào Controller.
5. Không truy cập Repository trực tiếp từ Controller.
6. Ưu tiên Constructor Injection.
7. Không thêm dependency nếu không cần thiết.
8. Không thay đổi kiến trúc lớn mà không giải thích.
9. Khi đề xuất một abstraction, phải giải thích lý do cần abstraction.
10. Khi có nhiều cách triển khai, ưu tiên cách đơn giản và dễ hiểu đối với người đang học.


## AI phải ưu tiên việc giải thích bản chất

Khi người học hỏi:

"Đoạn code này tại sao phải viết như vậy?"

AI không chỉ đưa code sửa.

AI phải giải thích:

- vấn đề ban đầu
- nguyên nhân
- nguyên lý Spring/Java liên quan
- giải pháp
- tại sao giải pháp này phù hợp với architecture
- nếu bỏ thành phần đó thì chuyện gì xảy ra


## Không over-engineering

Không thêm:

- Microservices
- Kafka
- Redis
- Kubernetes
- CQRS
- Event Sourcing

trừ khi được yêu cầu riêng.

Project này ưu tiên:

Simple
→ Understandable
→ Correct
→ Testable
→ Maintainable