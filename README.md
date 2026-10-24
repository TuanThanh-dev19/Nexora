# Nexora

Nexora là hệ thống báo cáo sự cố và điều phối bảo trì trong campus, xây dựng cho môn **SBA301 – Integrate Single Page Application with Spring Boot**.

Người báo tạo phiếu theo biểu mẫu động; quản lý tiếp nhận và phân công; kỹ thuật viên ghi nhật ký, tải ảnh kết quả và hoàn tất xử lý; người báo đối chiếu rồi đóng phiếu.

## Công nghệ và cấu trúc

Java 21 · Spring Boot 3.5.16 · SQL Server 2022 · MongoDB 8 · React 19 (JavaScript/JSX) · Vite · Docker Compose.

```text
backend/          REST API, nghiệp vụ, Flyway migrations, tests
frontend/         React SPA, API clients, route guards, tests
docs/             Báo cáo, thiết kế, hướng dẫn chạy, kiểm thử và slide
infrastructure/   Khởi tạo và kiểm tra database
.github/          CI và mẫu pull request
```

## Chạy nhanh

Cần JDK 21, Node.js 24, Docker Desktop chạy Linux containers và Git. Lệnh dưới dùng PowerShell, bắt đầu tại root repository.

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
docker compose up -d
docker compose ps -a
```

Cấu hình thông tin local trong `.env`. SQL Server và MongoDB cần healthy; `sqlserver-init` kết thúc với mã 0 là bình thường.

Terminal backend:

```powershell
Set-Location backend
.\mvnw.cmd spring-boot:run
```

Terminal frontend:

```powershell
Set-Location frontend
npm ci
npm run dev
```

Mở [frontend](http://localhost:5173), [Swagger](http://localhost:8080/swagger-ui.html) hoặc [health](http://localhost:8080/actuator/health). Database mới cần tạo Admin đầu tiên theo [SETUP](docs/SETUP.md); không có tài khoản ứng dụng mặc định.

## Tài liệu chính thức

| Tài liệu                                   | Mục đích                                                          |
| ------------------------------------------ | ----------------------------------------------------------------- |
| [PROJECT_REPORT](docs/PROJECT_REPORT.md)   | Báo cáo đủ 7 phần theo Part 4.1; nơi điền thông tin nhóm          |
| [SETUP](docs/SETUP.md)                     | Cài đặt, tài khoản, chạy local/production build và xử lý sự cố    |
| [ARCHITECTURE](docs/ARCHITECTURE.md)       | Kiến trúc, luồng xử lý, quyết định kỹ thuật và quy ước phát triển |
| [DATABASE_SCHEMA](docs/DATABASE_SCHEMA.md) | ERD, dữ liệu SQL/MongoDB và quy tắc migration                     |
| [API_CONVENTIONS](docs/API_CONVENTIONS.md) | API thật, phân quyền, DTO, validation và lỗi                      |
| [TESTING](docs/TESTING.md)                 | Test cases, lệnh kiểm thử, kết quả và kịch bản demo trực tiếp     |
| [PRESENTATION](docs/PRESENTATION.md)       | Nội dung 18 slide, phân công trình bày và chuẩn bị Q&A            |

Mã nguồn và migrations xác định hành vi đã triển khai. `frontend/src/shared/config/demoScope.js` xác định phạm vi demo. File mô phỏng hoặc bảng database có sẵn không tự chứng minh một tính năng đã hoàn chỉnh.

## Kiểm tra chất lượng

Tại `backend/`: `.\mvnw.cmd verify`.

Tại `frontend/`: `npm run lint`, `npm run test -- --maxWorkers=2`, `npm run build`.

Kiểm thử SQL Server/MongoDB thật cần Docker và cờ opt-in; xem [TESTING](docs/TESTING.md). Không commit secret, sửa migration đã áp dụng, hoặc trả JPA entity trực tiếp qua API. Thay đổi nghiệp vụ phải cập nhật tài liệu liên quan và có test thích hợp.

Repository: [TuanThanh-dev19/Nexora](https://github.com/TuanThanh-dev19/Nexora).
