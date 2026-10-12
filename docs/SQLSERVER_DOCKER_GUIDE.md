# Hướng dẫn SQL Server, MongoDB và Docker cho nhóm Nexora

Tài liệu này là quy trình chung cho cả 5 thành viên. Mỗi máy chạy cùng SQL Server
và MongoDB bằng Docker; schema SQL do Flyway quản lý, còn Spring Data tạo các
collection/index MongoDB.

## 1. Các khái niệm cần hiểu

- **Image**: bộ cài đóng gói của SQL Server. Project pin một phiên bản cụ thể để máy mọi người chạy giống nhau.
- **Container**: tiến trình SQL Server được tạo từ image.
- **Volume**: nơi dữ liệu database được giữ lại khi container bị dừng hoặc tạo lại.
- **Docker Compose**: đọc `compose.yaml` và khởi động các service theo cùng cấu hình.
- **Flyway**: chạy các migration T-SQL có thứ tự để tạo và nâng cấp schema.

Nexora có ba service:

| Service | Vai trò | Trạng thái bình thường |
|---|---|---|
| `sqlserver` | Chạy SQL Server 2022 Developer | `healthy` |
| `sqlserver-init` | Chạy một lần để tạo database `nexora` | `Exited (0)` |
| `mongodb` | Lưu audit event, ticket comment và notification | `healthy` |

`sqlserver-init` kết thúc với mã `0` là thành công, không phải lỗi. Sau đó Spring Boot/Flyway mới tạo các bảng ứng dụng.

## 2. Yêu cầu máy

- Windows 10/11 64-bit.
- Docker Desktop đang chạy và dùng Linux containers.
- Tối thiểu khoảng 2 GB RAM khả dụng cho SQL Server; nên cấp Docker Desktop khoảng 4 GB trở lên để chạy ổn định cùng backend/frontend.
- Java 21, Node.js và Git.

Kiểm tra Docker:

```powershell
docker version
docker compose version
```

`docker version` phải hiện cả phần `Client` và `Server`. Nếu chỉ có Client hoặc báo không kết nối được engine, hãy mở Docker Desktop và chờ đến khi Docker sẵn sàng.

SQL Server Linux container được Microsoft hỗ trợ trên CPU Intel/AMD x86-64. Thành viên dùng Apple Silicon/ARM không nên mặc định cho rằng ép `linux/amd64` sẽ được hỗ trợ ổn định.

## 3. Cài đặt lần đầu

Tại thư mục gốc repository:

```powershell
Copy-Item .env.example .env
docker compose config
docker compose pull
docker compose up -d
docker compose ps -a
```

Theo dõi quá trình khởi động:

```powershell
docker compose logs -f sqlserver sqlserver-init mongodb
```

Nhấn `Ctrl+C` để thoát phần xem log; container vẫn tiếp tục chạy.

Kết quả mong đợi:

```text
sqlserver        Up ... (healthy)
sqlserver-init   Exited (0)
mongodb          Up ... (healthy)
```

Kiểm tra database đã được tạo mà không đưa password vào lịch sử lệnh của host:

```powershell
docker compose exec sqlserver /bin/bash -c '/opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "$MSSQL_SA_PASSWORD" -C -Q "SELECT name FROM sys.databases"'
```

Danh sách phải có `nexora`.

## 4. Chạy project hằng ngày

Terminal 1, tại thư mục gốc:

```powershell
docker compose up -d
docker compose ps -a
```

Terminal 2:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Terminal 3:

```powershell
cd frontend
npm run dev
```

Khi kết thúc:

```powershell
docker compose stop
```

Hoặc xóa container/network nhưng vẫn giữ database trong volume:

```powershell
docker compose down
```

Lần sau `docker compose up -d` sẽ sử dụng lại dữ liệu cũ.

## 5. Kết nối bằng SQL Server Management Studio

| Thuộc tính | Giá trị local |
|---|---|
| Server name | `localhost,1433` |
| Authentication | SQL Server Authentication |
| Login | `sa` |
| Password | `MSSQL_SA_PASSWORD` trong `.env` |
| Database | `nexora` |
| Encrypt | Bật |
| Trust server certificate | Bật, chỉ cho local |

Nếu dùng Azure Data Studio hoặc extension SQL Server của VS Code, dùng cùng host, port, username, password và database.

JDBC URL mặc định:

```text
jdbc:sqlserver://localhost:1433;databaseName=nexora;encrypt=true;trustServerCertificate=true
```

Trong môi trường deploy, không dùng `trustServerCertificate=true`; cần chứng chỉ TLS được tin cậy.

## 6. `.env` và Spring Boot

File `.env` ở root được cả Docker Compose và Spring Boot đọc. Backend tìm file
tại `.env` và `../.env`, nên lệnh chạy từ root hoặc thư mục `backend` đều dùng
cùng cấu hình. Biến môi trường hoặc IntelliJ Run Configuration có độ ưu tiên cao hơn.

Nếu đổi password hoặc port, hãy cấu hình cả backend trong PowerShell:

```powershell
$env:DB_URL='jdbc:sqlserver://localhost:1434;databaseName=nexora;encrypt=true;trustServerCertificate=true'
$env:DB_USERNAME='sa'
$env:DB_PASSWORD='mật-khẩu-local-của-bạn'
$env:MONGO_URI='mongodb://user:password@localhost:27018/nexora?authSource=admin'
.\mvnw.cmd spring-boot:run
```

Hoặc đặt các biến đó trong IntelliJ Run Configuration. Nếu `27017` đã được dùng,
đặt `MONGO_PORT=27018` và cập nhật cùng port trong `MONGO_URI`. Không commit `.env`
hoặc mật khẩu thật.

## 7. Quy trình Flyway cho cả nhóm

Migration nằm tại:

```text
backend/src/main/resources/db/migration
```

Quy tắc bắt buộc:

1. Pull code mới nhất trước khi tạo migration.
2. Thống nhất số version trên board/nhóm chat để tránh hai người cùng tạo một version.
3. Dùng T-SQL và kiểu dữ liệu SQL Server: `IDENTITY`, `BIT`, `NVARCHAR`, `DATETIMEOFFSET`.
4. Không dùng cú pháp PostgreSQL như `JSONB`, `SERIAL`, `ILIKE`, `RETURNING`, `ON CONFLICT`.
5. Migration đã merge hoặc đã chạy trên máy thành viên khác là bất biến; muốn sửa schema phải tạo file version mới.
6. Không bật `ddl-auto=update`; Hibernate chỉ `validate`, Flyway chịu trách nhiệm schema.
7. PR thay đổi schema phải gồm migration, code entity/repository liên quan và test.
8. Reviewer phải thử trên database trống hoặc bằng SQL Server Testcontainers.

Ví dụ migration tiếp theo sau baseline hiện tại:

```text
V9__add_ticket_search_indexes.sql
```

Form category đã publish cũng bất biến. Admin sửa form bằng cách tạo version mới; ticket cũ vẫn trỏ đúng version đã dùng.

## 8. Chạy test SQL Server thật

Test nhanh thường ngày dùng H2 compatibility mode. H2 không thể xác nhận đầy đủ cú pháp T-SQL, filtered index hoặc hành vi SQL Server.

Khi Docker Desktop đang chạy:

```powershell
cd backend
$env:RUN_SQLSERVER_IT='true'
.\mvnw.cmd -Dtest=SqlServerMigrationIntegrationTest test
Remove-Item Env:RUN_SQLSERVER_IT
```

Testcontainers sẽ tạo SQL Server tạm, chạy toàn bộ Flyway migration, kiểm tra schema/seed rồi tự dọn container.

## 9. Reset database local

Lệnh sau **xóa toàn bộ database local trong volume**:

```powershell
docker compose down -v
docker compose up -d
```

Chỉ dùng khi dữ liệu local có thể bỏ và cả tên volume đã được kiểm tra. `down -v` không phải lệnh dừng hằng ngày.

## 10. Lỗi thường gặp

### Docker báo thiếu `MSSQL_SA_PASSWORD`

Chưa có `.env`:

```powershell
Copy-Item .env.example .env
docker compose config
```

### Container SQL Server dừng ngay

Xem log:

```powershell
docker compose logs sqlserver
```

Mật khẩu `sa` phải có ít nhất 8 ký tự và đáp ứng tối thiểu 3 trong 4 nhóm: chữ hoa, chữ thường, số, ký tự đặc biệt.

### Port 1433 đã được sử dụng

Đổi trong `.env`:

```env
MSSQL_PORT=1434
DB_URL=jdbc:sqlserver://localhost:1434;databaseName=nexora;encrypt=true;trustServerCertificate=true
```

Sau đó cấu hình cùng `DB_URL` cho Spring Boot chạy ngoài Docker.

### Đã đổi password nhưng đăng nhập vẫn dùng password cũ

Password hệ thống đã được ghi trong volume ở lần khởi tạo đầu. Đổi `.env` không tự đổi password trong database cũ. Hãy dùng lại password cũ hoặc, nếu dữ liệu local không cần giữ, chủ động reset bằng `docker compose down -v`.

### `Cannot open database nexora`

Kiểm tra service init:

```powershell
docker compose ps -a
docker compose logs sqlserver-init
```

`sqlserver-init` phải kết thúc `Exited (0)`.

### Backend trong container khác không kết nối được `localhost`

- Backend chạy trên Windows: host là `localhost`.
- Backend chạy trong cùng Compose network: host phải là tên service `sqlserver`.

## 11. Checklist cho thành viên mới

- [ ] Clone repository và checkout branch được giao.
- [ ] Docker Desktop chạy; `docker version` có Client và Server.
- [ ] Copy `.env.example` thành `.env`.
- [ ] `docker compose config` hợp lệ.
- [ ] `sqlserver` healthy và `sqlserver-init` exited 0.
- [ ] `mongodb` healthy.
- [ ] Database `nexora` xuất hiện trong câu query kiểm tra.
- [ ] Backend chạy và Flyway không báo lỗi.
- [ ] Frontend gọi được backend.
- [ ] Không commit `.env`, password, token hoặc file dữ liệu local.

## 12. Tài liệu chính thức

- Microsoft SQL Server Linux container quickstart: <https://learn.microsoft.com/en-us/sql/linux/quickstart-install-connect-docker>
- Microsoft Artifact Registry tags: <https://mcr.microsoft.com/en-us/artifact/mar/mssql/server>
- SQL Server container environment variables: <https://learn.microsoft.com/en-us/sql/linux/sql-server-linux-configure-environment-variables>
- Flyway SQL Server support: <https://documentation.red-gate.com/fd/sql-server-database-277579330.html>
- Testcontainers SQL Server module: <https://java.testcontainers.org/modules/databases/mssqlserver/>
- Docker Compose `down`: <https://docs.docker.com/reference/cli/docker/compose/down/>
