# Hướng dẫn cài đặt, nâng cấp và chạy dự án Nexora

Tài liệu này dành cho cả thành viên mới và thành viên đã từng chạy phiên bản
cũ của dự án. Các lệnh ưu tiên Windows PowerShell và mặc định được chạy tại thư
mục gốc của repository.

> **Quan trọng:** nếu máy đã có dữ liệu SQL Server từ phiên bản cũ, không copy
> `.env.example` đè lên `.env` và không chạy `docker compose down -v`. Hãy làm
> theo mục **6. Nâng cấp từ phiên bản cũ có dữ liệu SQL** trước khi chạy backend
> mới.

## 1. Chọn đúng quy trình

| Trường hợp | Mục cần làm |
|---|---|
| Chưa từng chạy dự án trên máy | Mục 2 → 3 → 4 → 7 |
| Đã chạy bản hiện tại, SQL đã ở Flyway V8 | Mục 5 → 7 |
| Đã chạy bản cũ, chưa có MongoDB hoặc SQL mới ở V1–V7 | Mục 6 → 7 |
| Không biết máy thuộc trường hợp nào | Làm bước kiểm tra ở mục 6.4 trước |

Kiến trúc dữ liệu hiện tại:

- SQL Server lưu dữ liệu quan hệ: user, role, ticket, category, location, asset,
  assignment và các bảng nghiệp vụ liên quan.
- MongoDB lưu ba collection: `audit_events`, `ticket_comments` và
  `notifications`.
- Flyway quản lý schema SQL Server. Spring Data MongoDB tạo collection/index
  MongoDB khi backend khởi động.
- Dữ liệu Docker được giữ trong hai named volume riêng:
  `nexora_sqlserver_data` và `nexora_mongodb_data`.

## 2. Phần mềm cần cài

- Git.
- Java Development Kit (JDK) 21.
- Node.js 22.12 trở lên hoặc Node.js 24, kèm npm.
- Docker Desktop, sử dụng Linux containers.
- Tùy chọn: SQL Server Management Studio và MongoDB Compass.

Kiểm tra phiên bản:

```powershell
git --version
java -version
node --version
npm --version
docker version
docker compose version
```

Kết quả `java -version` phải là Java 21. `docker version` phải hiển thị cả
`Client` và `Server`; nếu thiếu `Server`, hãy mở Docker Desktop và đợi engine
khởi động xong.

Backend sử dụng Maven Wrapper nên không cần cài Maven toàn cục.

## 3. Cấu hình `.env`

### 3.1 Thành viên mới

Chỉ khi chưa có `.env`, chạy:

```powershell
Copy-Item .env.example .env
```

Kiểm tra các biến chính:

```env
MSSQL_DATABASE=nexora
MSSQL_SA_PASSWORD=change-me-local-sa-password-123!
MSSQL_PORT=1433

DB_URL=jdbc:sqlserver://localhost:1433;databaseName=nexora;encrypt=true;trustServerCertificate=true
DB_USERNAME=sa
DB_PASSWORD=change-me-local-sa-password-123!

MONGO_DATABASE=nexora
MONGO_USERNAME=nexora_root
MONGO_PASSWORD=change-me-local-mongo-password
MONGO_PORT=27017
MONGO_URI=mongodb://nexora_root:change-me-local-mongo-password@localhost:27017/nexora?authSource=admin
MONGO_BACKFILL_ENABLED=false

VITE_API_BASE_URL=/api/v1
```

### 3.2 Thành viên đã có `.env` từ phiên bản cũ

Không copy file mẫu đè lên file hiện tại. Sao lưu trước:

```powershell
Copy-Item .env .env.before-mongodb.bak
```

Giữ nguyên các giá trị SQL đang dùng, đặc biệt là:

- `MSSQL_SA_PASSWORD`
- `MSSQL_PORT`
- `DB_URL`
- `DB_PASSWORD`

Sau đó mở `.env.example`, copy riêng nhóm biến `MONGO_*` còn thiếu vào `.env`.
Database trong volume cũ vẫn giữ password từ lần khởi tạo đầu tiên; thay
password trong `.env` không tự thay password trong SQL Server đã tồn tại.

### 3.3 Quy tắc đồng bộ biến

- `MSSQL_SA_PASSWORD` và `DB_PASSWORD` phải cùng giá trị trên local.
- `MSSQL_PORT` phải khớp port trong `DB_URL`.
- `MONGO_USERNAME`, `MONGO_PASSWORD`, `MONGO_PORT` và `MONGO_DATABASE` phải
  khớp với `MONGO_URI`.
- Nếu password MongoDB chứa ký tự đặc biệt dùng trong URL, phải URL-encode ký
  tự đó trong `MONGO_URI`. Với local nên dùng chữ, số và dấu gạch ngang để dễ
  cấu hình.
- Không commit `.env`, file backup `.env`, password, token hoặc JWT secret.

Backend tự đọc `.env` tại root khi được chạy từ root hoặc thư mục `backend/`.
Biến môi trường PowerShell và IDE Run Configuration có độ ưu tiên cao hơn.

Kiểm tra Compose đọc được cấu hình mà không khởi động container:

```powershell
docker compose config -q
```

Không có output và exit code `0` nghĩa là cấu hình hợp lệ.

## 4. Cài đặt lần đầu trên máy mới

### 4.1 Tải image và tạo database

Mở Docker Desktop, sau đó chạy tại root:

```powershell
docker compose pull
docker compose up -d
docker compose ps -a
```

Kết quả mong đợi:

| Service | Trạng thái bình thường | Vai trò |
|---|---|---|
| `sqlserver` | `Up ... (healthy)` | SQL Server 2022 |
| `sqlserver-init` | `Exited (0)` | Tạo database `nexora` một lần |
| `mongodb` | `Up ... (healthy)` | MongoDB 8 |

`sqlserver-init` kết thúc với mã `0` là thành công, không phải lỗi.

Nếu service chưa healthy, theo dõi log:

```powershell
docker compose logs -f sqlserver sqlserver-init mongodb
```

Nhấn `Ctrl+C` chỉ dừng việc xem log; container vẫn chạy.

### 4.2 Cài dependency

Backend:

```powershell
cd backend
.\mvnw.cmd --version
cd ..
```

Maven Wrapper sẽ tự tải dependency ở lần build/chạy đầu.

Frontend:

```powershell
cd frontend
npm install
cd ..
```

### 4.3 Chạy backend lần đầu

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Chờ log có dòng `Started NexoraBackendApplication`. Ở database mới, Flyway sẽ
chạy từ V1 đến V8; ba bảng document legacy đang rỗng sẽ được V8 xóa và thay bằng
ba collection MongoDB.

Sau đó có thể chuyển sang mục 7 để chạy đầy đủ backend và frontend.

## 5. Chạy lại khi máy đã ở phiên bản hiện tại

Mục này áp dụng khi máy đã từng chạy thành công Flyway V8 và MongoDB.

### 5.1 Cập nhật source code an toàn

Kiểm tra branch và thay đổi local:

```powershell
git branch --show-current
git status
```

Nếu có code chưa commit, hãy commit hoặc xử lý nó trước khi pull. Sau đó:

```powershell
git pull --ff-only
```

Không xóa `.env` và không reset Docker volume sau khi pull.

### 5.2 Cập nhật dependency và container

```powershell
docker compose config -q
docker compose pull
docker compose up -d
docker compose ps -a
```

Maven tự cập nhật dependency khi chạy backend. Với frontend, chạy `npm install`
sau khi pull để cập nhật đúng theo `package.json`/lock file:

```powershell
cd frontend
npm install
cd ..
```

### 5.3 Quick start cho những lần chạy hằng ngày

Terminal 1:

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

Không cần chạy lại backfill nếu SQL đã ở V8.

## 6. Nâng cấp từ phiên bản cũ có dữ liệu SQL

Mục này áp dụng cho máy đã chạy bản cũ, còn volume SQL Server và có thể chứa dữ
liệu trong `audit_logs`, `ticket_comments`, `notifications`.

Quy trình an toàn:

```text
Dừng backend cũ
    ↓
Giữ nguyên volume và thông tin SQL trong .env
    ↓
Thêm cấu hình MongoDB, khởi động MongoDB
    ↓
Kiểm tra Flyway và số dòng legacy
    ↓
Nếu có dữ liệu: chạy backfill với Flyway target 7
    ↓
Xác nhận log MongoDB backfill verified
    ↓
Khởi động bình thường để V8 kiểm tra rồi xóa bảng legacy
```

### 6.1 Dừng ứng dụng cũ và bảo toàn dữ liệu

- Dừng backend/frontend cũ bằng `Ctrl+C`.
- Không chạy `docker compose down -v`.
- Không xóa volume `nexora_sqlserver_data`.
- Không copy `.env.example` đè lên `.env` cũ.

Kiểm tra volume vẫn tồn tại:

```powershell
docker volume ls | Select-String 'nexora.*sqlserver'
```

### 6.2 Cập nhật code và `.env`

```powershell
git status
git pull --ff-only
Copy-Item .env .env.before-mongodb.bak
```

Nếu `.env` chưa có MongoDB, thêm:

```env
MONGO_DATABASE=nexora
MONGO_USERNAME=nexora_root
MONGO_PASSWORD=change-me-local-mongo-password
MONGO_PORT=27017
MONGO_URI=mongodb://nexora_root:change-me-local-mongo-password@localhost:27017/nexora?authSource=admin
MONGO_BACKFILL_ENABLED=false
```

Nếu port `27017` đang bị ứng dụng khác dùng, xem mục 11.2 và đổi cả
`MONGO_PORT` lẫn port trong `MONGO_URI`.

### 6.3 Khởi động database, chưa chạy backend mới

```powershell
docker compose config -q
docker compose pull mongodb
docker compose up -d sqlserver sqlserver-init mongodb
docker compose ps -a
```

Đợi SQL Server và MongoDB cùng `healthy`. Chưa chạy backend bình thường ở bước
này vì cần kiểm tra dữ liệu legacy trước.

### 6.4 Kiểm tra phiên bản Flyway và bảng legacy

```powershell
docker compose exec -T sqlserver /bin/bash -c '/opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "$MSSQL_SA_PASSWORD" -C -d nexora -Q "SELECT TOP (1) version, description, success FROM dbo.flyway_schema_history ORDER BY installed_rank DESC"'
```

- Nếu version là `8` và `success = 1`: máy đã nâng cấp, bỏ qua backfill và sang
  mục 7.
- Nếu version từ `1` đến `7`: tiếp tục bước kiểm tra bảng và số dòng.
- Nếu báo không có `flyway_schema_history`: xác nhận `DB_URL` và database đang
  kết nối có đúng volume cũ không trước khi làm tiếp.

Kiểm tra ba bảng legacy còn tồn tại:

```powershell
docker compose exec -T sqlserver /bin/bash -c '/opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "$MSSQL_SA_PASSWORD" -C -d nexora -Q "SELECT OBJECT_ID(''dbo.audit_logs'', ''U'') auditLogsTable, OBJECT_ID(''dbo.ticket_comments'', ''U'') ticketCommentsTable, OBJECT_ID(''dbo.notifications'', ''U'') notificationsTable"'
```

Giá trị khác `NULL` nghĩa là bảng còn tồn tại. Nếu cả ba là `NULL` nhưng Flyway
chưa ở V8, dừng lại và kiểm tra lịch sử migration thay vì tự tạo lại bảng.

### 6.5 Sao lưu SQL trước khi migration

Đây là bước khuyến nghị khi database cũ có dữ liệu cần giữ. Tạo backup trong
container rồi copy ra thư mục nằm ngoài repository:

```powershell
$backupDir = Join-Path (Split-Path (Get-Location) -Parent) 'nexora-local-backups'
New-Item -ItemType Directory -Force -Path $backupDir | Out-Null

docker compose exec -T sqlserver /bin/bash -c 'mkdir -p /var/opt/mssql/backup && /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "$MSSQL_SA_PASSWORD" -C -b -Q "BACKUP DATABASE [nexora] TO DISK = N''/var/opt/mssql/backup/nexora_before_mongodb.bak'' WITH INIT, COPY_ONLY, CHECKSUM"'

docker compose cp sqlserver:/var/opt/mssql/backup/nexora_before_mongodb.bak "$backupDir\nexora_before_mongodb.bak"
```

Xác nhận file backup tồn tại và có kích thước lớn hơn `0`:

```powershell
Get-Item "$backupDir\nexora_before_mongodb.bak"
```

File backup chứa dữ liệu local; không commit hoặc chia sẻ công khai.

### 6.6 Đếm dữ liệu cần backfill

Chỉ chạy lệnh này khi ba bảng ở bước 6.4 còn tồn tại:

```powershell
docker compose exec -T sqlserver /bin/bash -c '/opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "$MSSQL_SA_PASSWORD" -C -d nexora -Q "SELECT (SELECT COUNT_BIG(*) FROM dbo.audit_logs) auditLogs, (SELECT COUNT_BIG(*) FROM dbo.ticket_comments) ticketComments, (SELECT COUNT_BIG(*) FROM dbo.notifications) notifications"'
```

Ghi lại ba con số.

- Nếu cả ba đều `0`: không cần backfill. Sang bước 6.8 và chạy backend bình
  thường để V8 xóa ba bảng rỗng.
- Nếu bất kỳ số nào lớn hơn `0`: bắt buộc làm bước 6.7.

### 6.7 Backfill SQL Server sang MongoDB

Chạy backend tạm thời với Flyway dừng ở V7 và bật backfill:

```powershell
cd backend
$env:MONGO_BACKFILL_ENABLED='true'
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.flyway.target=7"
```

Không dừng tiến trình ngay khi thấy dòng `Started`. Chờ log:

```text
MongoDB backfill verified: audit_events=..., ticket_comments=..., notifications=...
```

Ba số trong log phải bằng các số đã ghi ở bước 6.6. Backfill dùng
`legacySqlId` duy nhất nên nếu tiến trình bị ngắt, có thể chạy lại cùng lệnh mà
không tạo document trùng.

Sau khi có log xác nhận, nhấn `Ctrl+C`, rồi xóa biến tạm:

```powershell
Remove-Item Env:MONGO_BACKFILL_ENABLED
```

Nếu lệnh báo biến không tồn tại thì có thể bỏ qua; đảm bảo
`MONGO_BACKFILL_ENABLED=false` trong `.env`.

### 6.8 Áp dụng Flyway V8

Chạy backend bình thường, không còn `spring.flyway.target=7`:

```powershell
.\mvnw.cmd spring-boot:run
```

V8 sẽ so sánh số lượng đã xác minh với số dòng SQL hiện tại. Nếu không khớp,
migration chủ động thất bại và giữ nguyên ba bảng. Không sửa hoặc bỏ qua guard;
hãy chạy lại backfill và kiểm tra log.

Khi backend khởi động thành công, V8 đã xóa ba bảng legacy khỏi SQL Server.

### 6.9 Xác minh sau nâng cấp

Kiểm tra Flyway đã ở V8:

```powershell
docker compose exec -T sqlserver /bin/bash -c '/opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "$MSSQL_SA_PASSWORD" -C -d nexora -Q "SELECT TOP (1) version, description, success FROM dbo.flyway_schema_history ORDER BY installed_rank DESC"'
```

Kiểm tra ba bảng SQL đã được xóa bằng lệnh `OBJECT_ID` ở bước 6.4; cả ba kết
quả phải là `NULL`.

Kiểm tra collection và số document MongoDB:

```powershell
docker compose exec -T mongodb /bin/bash -c 'mongosh --quiet --username "$MONGO_INITDB_ROOT_USERNAME" --password "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin "$MONGO_INITDB_DATABASE" --eval "printjson({collections: db.getCollectionNames().sort(), auditEvents: db.audit_events.countDocuments({}), ticketComments: db.ticket_comments.countDocuments({}), notifications: db.notifications.countDocuments({})})"'
```

Số document legacy phải khớp số đã ghi ở bước 6.6. Sau khi xác minh xong, dừng
backend bằng `Ctrl+C` rồi chạy theo mục 7 như bình thường.

## 7. Chạy toàn bộ dự án

Mở ba terminal tại root repository.

### Terminal 1 — infrastructure

```powershell
docker compose up -d
docker compose ps -a
```

Chỉ chuyển bước khi `sqlserver` và `mongodb` đều healthy.

### Terminal 2 — backend

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Chờ dòng `Started NexoraBackendApplication` và không có lỗi Flyway/MongoDB.

### Terminal 3 — frontend

```powershell
cd frontend
npm run dev
```

Các URL local:

| Thành phần | URL |
|---|---|
| Frontend | <http://localhost:5173> |
| Backend health | <http://localhost:8080/api/v1/public/health> |
| Actuator health | <http://localhost:8080/actuator/health> |
| Swagger UI | <http://localhost:8080/swagger-ui.html> |

Kiểm tra nhanh:

```powershell
Invoke-RestMethod http://localhost:8080/api/v1/public/health
```

## 8. Kết nối công cụ quản trị database

### 8.1 SQL Server Management Studio

| Thuộc tính | Giá trị |
|---|---|
| Server name | `localhost,<MSSQL_PORT>`; mặc định `localhost,1433` |
| Authentication | SQL Server Authentication |
| Login | `sa` |
| Password | Giá trị `MSSQL_SA_PASSWORD` trong `.env` |
| Database | `nexora` |
| Encrypt | Bật |
| Trust server certificate | Bật, chỉ cho local |

### 8.2 MongoDB Compass

Copy nguyên giá trị `MONGO_URI` trong `.env` vào ô URI của Compass:

```powershell
Get-Content .env | Select-String '^MONGO_URI='
```

Ví dụ cổng mặc định:

```text
mongodb://nexora_root:change-me-local-mongo-password@localhost:27017/nexora?authSource=admin
```

Nếu Docker map MongoDB qua `27018`, URI cũng phải dùng `localhost:27018`.
`authSource=admin` là bắt buộc vì tài khoản root được tạo trong database
`admin`.

Sau khi kết nối, database `nexora` phải có `audit_events`, `ticket_comments`
và `notifications`.

## 9. Dừng dự án đúng cách

1. Nhấn `Ctrl+C` tại terminal frontend.
2. Nhấn `Ctrl+C` tại terminal backend.
3. Dừng database nhưng giữ dữ liệu:

```powershell
docker compose stop
```

Có thể xóa container/network mà vẫn giữ volume:

```powershell
docker compose down
```

Lần sau `docker compose up -d` sẽ dùng lại dữ liệu cũ.

> Không dùng `docker compose down -v` để dừng hằng ngày. Tùy chọn `-v` xóa cả
> dữ liệu SQL Server và MongoDB.

## 10. Chạy test trước khi đẩy code

Backend fast suite:

```powershell
cd backend
.\mvnw.cmd verify
```

Backend integration suite với database thật qua Testcontainers:

```powershell
cd backend
$env:RUN_SQLSERVER_IT='true'
$env:RUN_MONGODB_IT='true'
$env:RUN_MIGRATION_IT='true'
.\mvnw.cmd verify
Remove-Item Env:RUN_SQLSERVER_IT
Remove-Item Env:RUN_MONGODB_IT
Remove-Item Env:RUN_MIGRATION_IT
```

Frontend:

```powershell
cd frontend
npm run lint
npm run test
npm run build
```

Docker Desktop phải chạy khi thực hiện integration suite.

## 11. Xử lý lỗi thường gặp

### 11.1 Port SQL Server 1433 đã được sử dụng

Đổi đồng thời hai giá trị trong `.env`:

```env
MSSQL_PORT=1434
DB_URL=jdbc:sqlserver://localhost:1434;databaseName=nexora;encrypt=true;trustServerCertificate=true
```

Sau đó chạy `docker compose up -d` và `docker compose ps sqlserver`.

### 11.2 Port MongoDB 27017 đã được sử dụng

Đổi đồng thời port và URI:

```env
MONGO_PORT=27018
MONGO_URI=mongodb://nexora_root:change-me-local-mongo-password@localhost:27018/nexora?authSource=admin
```

Sau đó chạy `docker compose up -d mongodb` và `docker compose ps mongodb`.

### 11.3 Đổi password trong `.env` nhưng container không đăng nhập được

Credential được ghi vào database ở lần tạo volume đầu tiên. Đổi `.env` không
tự sửa credential trong volume cũ.

- Nếu cần giữ dữ liệu: khôi phục credential cũ trong `.env`.
- Nếu dữ liệu có thể bỏ: làm quy trình reset có cảnh báo ở mục 12.

### 11.4 `sqlserver-init` không phải `Exited (0)`

```powershell
docker compose logs sqlserver-init
```

Kiểm tra SQL Server đã healthy và `MSSQL_SA_PASSWORD` đúng password của volume.

### 11.5 Backend báo lỗi kết nối SQL hoặc MongoDB

```powershell
docker compose ps -a
docker compose logs --tail 100 sqlserver mongodb
```

Đối chiếu `DB_URL`, `DB_PASSWORD`, `MONGO_URI` với port/credential trong `.env`.
Backend chạy trên Windows dùng `localhost`; chỉ container trong cùng Compose
network mới dùng hostname `sqlserver` hoặc `mongodb`.

### 11.6 Flyway V8 báo `MongoDB backfill is not verified`

Đây là cơ chế bảo vệ dữ liệu, không phải lỗi cần bỏ qua. Quay lại mục 6.6–6.8,
chạy backfill ở target V7 và chỉ chạy bình thường sau khi có log verified.

### 11.7 Frontend không gọi được backend

- Mở <http://localhost:8080/api/v1/public/health> để kiểm tra backend.
- Kiểm tra terminal backend có lỗi Flyway hoặc database không.
- Giữ `VITE_API_BASE_URL=/api/v1` với cấu hình local mặc định.
- Dừng và chạy lại `npm run dev` nếu vừa sửa biến frontend.

### 11.8 Docker báo thiếu biến môi trường

Chạy `docker compose config`. Nếu máy mới, tạo `.env` từ `.env.example`. Nếu
máy đã chạy bản cũ, thêm riêng biến còn thiếu; không ghi đè toàn bộ `.env`.

## 12. Reset toàn bộ database local

Chỉ làm khi chắc chắn toàn bộ dữ liệu local có thể xóa. Lệnh sau xóa cả volume
SQL Server và MongoDB, không thể hoàn tác nếu không có backup:

```powershell
docker compose down -v
docker compose up -d
```

Trước khi reset, kiểm tra đúng project:

```powershell
docker compose ls
docker volume ls | Select-String 'nexora'
```

Sau reset, credential trong `.env` được dùng để tạo database lại từ đầu và
backend sẽ chạy toàn bộ Flyway V1–V8.

## 13. Checklist bàn giao

### Thành viên mới

- [ ] Cài JDK 21, Node.js, Git và Docker Desktop.
- [ ] Copy `.env.example` thành `.env`.
- [ ] `docker compose config -q` thành công.
- [ ] SQL Server và MongoDB healthy; init exited 0.
- [ ] Backend khởi động và Flyway lên V8.
- [ ] Frontend mở được và gọi được backend.

### Thành viên đã chạy phiên bản cũ

- [ ] Không xóa SQL volume và không ghi đè `.env`.
- [ ] Sao lưu `.env` và database cần giữ.
- [ ] Thêm đủ biến MongoDB.
- [ ] Kiểm tra version Flyway và số dòng legacy.
- [ ] Nếu có dữ liệu, backfill ở target V7 và thấy log verified.
- [ ] Chạy bình thường để V8 áp dụng.
- [ ] Xác nhận bảng SQL legacy đã xóa và collection MongoDB có dữ liệu.

### Trước mỗi pull request

- [ ] Không có `.env`, backup hoặc secret trong `git status`.
- [ ] Backend test qua.
- [ ] Frontend lint, test và build qua.
- [ ] Migration mới không sửa file Flyway đã được áp dụng.

## 14. Tài liệu liên quan

- `README.md`: tổng quan repository và quy trình backfill ngắn gọn.
- `docs/SQLSERVER_DOCKER_GUIDE.md`: Docker, SQL Server và Flyway chuyên sâu.
- `docs/ARCHITECTURE.md`: kiến trúc và ranh giới module.
- `docs/DATABASE_SCHEMA.md`: schema SQL Server, MongoDB và các index.

Nếu dùng macOS/Linux, thay `.\mvnw.cmd` bằng `./mvnw` và `Copy-Item` bằng
`cp`. Các lệnh PowerShell như `Select-String` cần được thay bằng công cụ tương
đương của shell đang dùng.
