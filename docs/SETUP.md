# Cài đặt và chạy Nexora

Lệnh dùng PowerShell. Bắt đầu tại root repository; chạy backend và frontend trong hai terminal riêng. Linux/macOS dùng `./mvnw` thay cho `.\mvnw.cmd`.

## 1. Yêu cầu

| Công cụ  | Phiên bản / điều kiện                                                           |
| -------- | ------------------------------------------------------------------------------- |
| Java     | JDK 21, có `java` và `javac`                                                    |
| Node.js  | Node 24 theo `.nvmrc`; npm dùng lockfile                                        |
| Docker   | Docker Desktop chạy Linux containers; `docker version` hiện cả Client và Server |
| Database | SQL Server 2022 Developer và MongoDB 8 qua Compose                              |
| Khác     | Git; SSMS hoặc client SQL nếu cần kiểm tra dữ liệu                              |

Maven Wrapper có sẵn. Lần chạy đầu cần mạng để tải dependencies/images. SQL Server container cần đủ RAM; không coi H2 là database thay thế để demo tính năng SQL Server.

## 2. Chuẩn bị database

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
docker compose up -d
docker compose ps -a
```

Sửa giá trị local trong `.env` trước khi dùng. Không ghi đè `.env` đang có hoặc commit mật khẩu/JWT secret.

| Biến                                                          | Ý nghĩa                                                        |
| ------------------------------------------------------------- | -------------------------------------------------------------- |
| `MSSQL_DATABASE`, `MSSQL_PORT`, `MSSQL_SA_PASSWORD`           | Database, host port và mật khẩu SQL Server container           |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`                        | Backend JDBC; database/port/password phải khớp Compose         |
| `MONGO_PORT`, `MONGO_USERNAME`, `MONGO_PASSWORD`, `MONGO_URI` | Kết nối MongoDB; URI cần đúng credentials/port và authSource   |
| `JWT_SECRET`                                                  | Khóa HS256, ít nhất 32 byte; thay giá trị ví dụ khi triển khai |
| `FILE_STORAGE_ROOT`                                           | Thư mục ảnh riêng, không đặt trong static/public web root      |
| `MONGO_BACKFILL_ENABLED`                                      | Mặc định false; chỉ bật cho quy trình legacy ở phần 7          |

Trạng thái đúng: `sqlserver` và `mongodb` healthy; `sqlserver-init` Exited (0). Init chỉ tạo database rỗng. Backend mới chạy migrations V1–V11.

Nếu port 1433/27017 đã bị dùng, đổi cả host port và connection URL tương ứng. Ví dụ SQL dùng 1434 thì JDBC phải dùng localhost:1434. Volume đã có dữ liệu không tự đổi mật khẩu SQL chỉ vì sửa biến môi trường.

## 3. Chạy ứng dụng

Backend:

```powershell
Set-Location backend
.\mvnw.cmd spring-boot:run
```

Spring Boot đọc `.env` hoặc `../.env`. Environment/IDE configuration có thể ghi đè giá trị này. `ddl-auto=validate`; Flyway quản lý schema.

Frontend, terminal khác từ root:

```powershell
Set-Location frontend
npm ci
npm run dev
```

Vite mặc định gọi `/api/v1` và proxy tới backend localhost:8080. Root `.env` không tự được Vite đọc như file `frontend/.env`; khi cần đổi base URL, tạo `frontend/.env` từ mẫu hoặc đặt biến môi trường trước khi chạy/build.

| Thành phần        | URL mặc định                                                                                 |
| ----------------- | -------------------------------------------------------------------------------------------- |
| Frontend          | [localhost:5173](http://localhost:5173)                                                      |
| Login             | [localhost:5173/login](http://localhost:5173/login)                                          |
| Health            | [localhost:8080/actuator/health](http://localhost:8080/actuator/health)                      |
| Public health     | [localhost:8080/api/v1/public/health](http://localhost:8080/api/v1/public/health)            |
| Swagger / OpenAPI | [Swagger](http://localhost:8080/swagger-ui.html) / [JSON](http://localhost:8080/v3/api-docs) |

Khi backend chạy từ `backend/`, mặc định `../.data/uploads` trỏ vào `.data/uploads` ở root. Nếu chạy JAR từ thư mục khác, nên đặt FILE_STORAGE_ROOT là đường dẫn tuyệt đối.

## 4. Tài khoản đầu tiên và dữ liệu demo

### Admin bootstrap

Database mới chưa có tài khoản ứng dụng. Tại `backend/`, trước khi chạy:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'local'
$env:ADMIN_BOOTSTRAP_ENABLED = 'true'
$env:ADMIN_BOOTSTRAP_EMAIL = 'admin@example.test'
$bootstrapPassword = Read-Host 'Mật khẩu Admin local' -AsSecureString
$env:ADMIN_BOOTSTRAP_PASSWORD = [System.Net.NetworkCredential]::new('', $bootstrapPassword).Password
Remove-Variable bootstrapPassword
.\mvnw.cmd spring-boot:run
```

Dùng mật khẩu riêng: ít nhất 8 ký tự, tối đa 72 byte UTF-8. Chỉ khi log xác nhận tạo Admin và ứng dụng khởi động thành công mới thử đăng nhập. Bootstrap chỉ chạy trong profile local/demo khi được bật, và bỏ qua nếu đã có một tài khoản mang ADMIN; không đổi mật khẩu/quyền của tài khoản cũ.

Sau khi dừng app bằng Ctrl+C:

```powershell
Remove-Item Env:ADMIN_BOOTSTRAP_ENABLED, Env:ADMIN_BOOTSTRAP_EMAIL, Env:ADMIN_BOOTSTRAP_PASSWORD -ErrorAction SilentlyContinue
```

Nếu từng bật trong `.env` hoặc IDE, tắt tại đó nữa. Các lần sau chạy backend bình thường.

### Tạo các tài khoản khác

Đăng nhập Admin, dùng giao diện quản trị hoặc `POST /api/v1/admin/users` trong Swagger. API quản trị đã có; không cần insert SQL thủ công hoặc sinh BCrypt hash bằng tay.

Ví dụ cấu trúc body (thay email/mật khẩu trước khi gửi):

```json
{
  "email": "technician@example.test",
  "password": "<mat-khau-local-tu-chon>",
  "fullName": "Demo Technician",
  "status": "ACTIVE",
  "roles": ["TECHNICIAN"]
}
```

Tạo ít nhất REQUESTER, MANAGER và TECHNICIAN. Role chuẩn là REQUESTER, không phải USER. Thêm tài khoản khác khi kiểm quyền âm.

Chuẩn bị kỹ thuật viên có availability, capacity còn trống, skill phù hợp và service area bao phủ địa điểm. Chuẩn bị category active, form published có default skill/SLA hợp lệ và location active. Nếu có asset, bảo đảm đúng policy, địa điểm và trạng thái được chọn. Các điều kiện này được kiểm lại khi assign/tạo Ticket.

Frontend tự lưu access token trong sessionStorage, tự gắn Bearer header. Khi dùng Swagger/Postman, login lấy accessToken rồi dùng nút Authorize. Không dùng mật khẩu SQL Server để đăng nhập ứng dụng.

## 5. Production build và live demo

Tại `frontend/`:

```powershell
$env:VITE_TICKET_WORKFLOW_MOCK = 'false'
$env:VITE_DASHBOARD_MOCK = 'false'
$env:VITE_ADMIN_USER_MOCK = 'false'
$env:VITE_TECHNICIAN_MOCK = 'false'
npm run build
npm run preview -- --host localhost --port 5173
```

Giữ backend/database đang chạy. Vite preview dùng cấu hình proxy của dự án; đây là cách kiểm local, không phải hướng dẫn triển khai public. Production excludes DEV preview/MSW. Phạm vi action vẫn theo demoScope.

Backend đóng gói bằng `.\mvnw.cmd verify`, sau đó có thể chạy `java -jar target/nexora-backend-0.0.1-SNAPSHOT.jar` từ `backend/` với cấu hình phù hợp.

Nếu frontend/backend được deploy riêng, cấu hình VITE_API_BASE_URL lúc build, CORS_ALLOWED_ORIGINS và TLS chính xác. Sao lưu cả SQL, MongoDB và thư mục file; chỉ sao lưu metadata SQL không đủ phục hồi ảnh.

## 6. Vận hành và xử lý lỗi

```powershell
docker compose logs --tail 100 sqlserver sqlserver-init mongodb
docker compose stop
```

Ctrl+C dừng tiến trình backend/frontend. Compose stop giữ volume dữ liệu. Không dùng thao tác xóa volume như một cách sửa lỗi thông thường.

| Hiện tượng                      | Kiểm tra                                                                                        |
| ------------------------------- | ----------------------------------------------------------------------------------------------- |
| Không kết nối Docker            | Docker Desktop/engine đã sẵn sàng, đúng Linux containers                                        |
| SQL login/database failed       | DB_URL, host port, mật khẩu thật trong volume và kết quả sqlserver-init                         |
| Flyway checksum/validation fail | Không sửa migration đã áp dụng; đối chiếu schema và migration mới                               |
| Mongo authentication failed     | URI, authSource=admin, credentials và host port                                                 |
| Login 401                       | Đúng tài khoản ứng dụng, mật khẩu và trạng thái ACTIVE; kiểm log bootstrap nếu database mới     |
| Browser lỗi kết nối/403 CORS    | Backend đang chạy, base URL/proxy đúng; origin khớp CORS_ALLOWED_ORIGINS                        |
| Action không hiện               | Role, reporter/current assignment, trạng thái Ticket và demoScope                               |
| Upload thất bại                 | Count/MIME/signature/size; quyền ghi FILE_STORAGE_ROOT; tải lại Ticket khi 403/404/422          |
| Bootstrap báo lỗi persistence   | Kiểm migration/startup logs; không coi dòng Maven BUILD SUCCESS riêng lẻ là app chạy thành công |

Kết nối SSMS: `localhost,<MSSQL_PORT>`, SQL Server Authentication, user sa, database theo MSSQL_DATABASE, mật khẩu trong cấu hình local. Trust server certificate chỉ cho local self-signed container.

## 7. Database cũ trước V8

Chỉ áp dụng khi database chưa lên V8 và các bảng SQL audit_logs, ticket_comments hoặc notifications còn dữ liệu. Không chạy quy trình này trên database mới đã migrate đầy đủ.

1. Backup SQL và MongoDB; tạm dừng thay đổi dữ liệu cần chuyển.
2. Tại backend, bật backfill và giới hạn Flyway ở V7:

```powershell
$env:MONGO_BACKFILL_ENABLED = 'true'
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.flyway.target=7"
```

3. Chờ log MongoDB backfill verified. Runner upsert theo legacySqlId, đối chiếu counts và lưu marker xác nhận.
4. Dừng app, xóa biến tạm, chạy bình thường:

```powershell
Remove-Item Env:MONGO_BACKFILL_ENABLED
.\mvnw.cmd spring-boot:run
```

V8 chỉ loại ba bảng SQL khi rỗng hoặc marker/count được xác minh. Nếu kiểm tra không khớp, điều tra dữ liệu; không bỏ qua guard hoặc reset volume.
