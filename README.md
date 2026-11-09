# Nexora

Nexora là hệ thống báo cáo sự cố và điều phối bảo trì trong campus, xây dựng cho môn **SBA301 – Integrate Single Page Application with Spring Boot**.

Người báo tạo phiếu theo biểu mẫu động; quản lý tiếp nhận và phân công; kỹ thuật viên ghi nhật ký, tải ảnh kết quả và hoàn tất xử lý; người báo đối chiếu rồi đóng phiếu.

## Công nghệ và cấu trúc

Java 21 · Spring Boot 3.5.16 · SQL Server 2022 · MongoDB 8 · React 19 (JavaScript/JSX) · Vite · Docker Compose.

```text
backend/          REST API, nghiệp vụ, Flyway migrations, tests
frontend/         React SPA, API clients, route guards, tests
docs/             Báo cáo Word/PDF và PowerPoint thuyết trình
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

Mở [frontend](http://localhost:5173), [Swagger](http://localhost:8080/swagger-ui.html) hoặc [health](http://localhost:8080/actuator/health). Database mới cần tạo Admin đầu tiên theo phụ lục 7.2 trong [báo cáo](docs/Nexora_Project_Report.pdf); không có tài khoản ứng dụng mặc định.

Để có dữ liệu trình diễn, sau khi backend chạy, dùng `.\infrastructure\demo\seed-demo.ps1`. Script bổ sung 13 tài khoản, 24 địa điểm, 36 thiết bị và 24 phiếu qua API thật. Tài khoản và kịch bản demo nằm ở phụ lục 7.2–7.3 trong báo cáo.

## Tài liệu chính thức

Bộ tài liệu cho lớp **SE1910**, **Group 2**, giảng viên **PhucPT**. MSSV để trống. Thư mục `docs` chỉ giữ báo cáo theo Part 4.1 và PowerPoint theo Part 4.2 của đề SBA301.

| Tài liệu | Nội dung |
| --- | --- |
| [Báo cáo PDF](docs/Nexora_Project_Report.pdf) | 24 trang, đủ 7 phần, kiến trúc, API, ERD, implementation, test cases/kết quả và phụ lục chạy/demo |
| [Báo cáo Word](docs/Nexora_Project_Report.docx) | Bản chỉnh sửa của báo cáo, có bảng thành viên và MSSV trống |
| [PowerPoint](docs/Nexora_Presentation.pptx) | 18 slide, ghi chú trình bày, khoảng 18 phút gồm live demo và 10 phút Q&A |

Nội dung đặc tả SWD392 liên quan được tích hợp trong phần phân tích/thiết kế, implementation và phụ lục của báo cáo. Hướng dẫn cài đặt, tài khoản demo, lệnh test và supporting evidence được gom vào báo cáo.

Mã nguồn và migrations xác định hành vi đã triển khai. `frontend/src/shared/config/demoScope.js` xác định phạm vi demo. File mô phỏng hoặc bảng database có sẵn không tự chứng minh một tính năng đã hoàn chỉnh.

## Giới hạn hiện tại

Unassign chưa có API thực thi; reject/cancel/reopen/force-close, comments và feedback chưa bật trong demo. SOA/Service Discovery ở SWD392 là thiết kế đề xuất. CRUD đủ 5 entity và yêu cầu modal/Delete còn cần đối chiếu với cách deactivate/archive của domain; xem phần 6 của báo cáo. File storage local và sự kiện MongoDB sau commit chưa có bảo đảm giao dịch xuyên stores hoặc durable outbox.

## Kiểm tra chất lượng

Tại `backend/`: `.\mvnw.cmd verify`.

Tại `frontend/`: `npm run lint`, `npm run test -- --maxWorkers=2`, `npm run build`.

Kiểm thử SQL Server/MongoDB thật cần Docker và cờ opt-in; xem phụ lục 7.3 trong báo cáo. Không commit secret, sửa migration đã áp dụng, hoặc trả JPA entity trực tiếp qua API. Thay đổi nghiệp vụ phải cập nhật báo cáo và có test thích hợp. Kết quả test trong báo cáo ghi mốc lịch sử, cần chạy lại trên commit nộp.

Repository: [TuanThanh-dev19/Nexora](https://github.com/TuanThanh-dev19/Nexora).
