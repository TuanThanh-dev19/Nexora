# Hướng dẫn tạo tài khoản local và lấy token để test Nexora

Tài liệu dành cho thành viên chạy dự án trên máy riêng, đặc biệt khi database
chưa có Admin để đăng nhập. Các bước dùng PowerShell trên Windows và code hiện
tại của repository.

Mỗi máy có database và Docker volume riêng. Tài khoản tạo trên máy một thành
viên không tự xuất hiện trên máy thành viên khác khi pull code. Tài khoản `sa`
của SQL Server dùng để quản lý database; muốn đăng nhập ứng dụng phải có user
trong `dbo.app_users` và quyền trong `dbo.user_roles`.

## 1. Chọn quy trình cần dùng

| Trường hợp | Cách làm |
| --- | --- |
| Database mới, chưa có Admin | Làm mục 2–4: bootstrap tạo Admin, đăng nhập và lấy JWT. |
| Đã có Admin và biết mật khẩu | Chạy backend bình thường rồi làm mục 4. |
| Cần thêm Admin, Requester, Manager hoặc Technician để test | Làm mục 6: tạo hash BCrypt rồi tạo tài khoản test bằng SQL. |
| Đã có tài khoản nhưng quên mật khẩu | Làm mục 7: đặt lại mật khẩu trên database local. |
| Đã có token và muốn kiểm tra quyền | Làm mục 4.2 hoặc 4.3 với token của đúng tài khoản. |

Hiện có `POST /api/v1/auth/login`, `GET /api/v1/auth/me` và bootstrap Admin.
`POST /api/v1/admin/users` còn thuộc [issue #29](https://github.com/TuanThanh-dev19/Nexora/issues/29),
chưa có controller trong code hiện tại. Vì vậy, mục 6 là cách chuẩn bị dữ liệu
test local trong thời gian chờ API quản lý user; không phải API đăng ký công khai.

## 2. Chuẩn bị và bật database

Cần Java 21 có cả `java` và `jshell`, Docker Desktop đang chạy Linux containers,
và checkout có `AdminBootstrapRunner`, `AdminBootstrapService`. Node.js chỉ cần
nếu muốn chạy frontend.

Mở PowerShell **tại thư mục gốc repository**, nơi có `compose.yaml`, `backend/`
và `frontend/`, rồi chạy:

```powershell
$repoRoot = (Get-Location).Path
if (-not (Test-Path -LiteralPath '.\compose.yaml')) {
    throw 'Hãy mở terminal tại thư mục gốc repository trước.'
}
if (-not (Test-Path -LiteralPath '.\.env')) {
    Copy-Item -LiteralPath '.\.env.example' -Destination '.\.env'
}
docker compose up -d
docker compose ps -a
```

Kết quả cần có:

- `sqlserver`: `healthy`.
- `mongodb`: `healthy`.
- `sqlserver-init`: `Exited (0)`, tức database đã được tạo thành công.

Schema và role được Flyway tạo khi backend khởi động. Seed role `ADMIN` chỉ
tạo loại quyền; bootstrap ở mục 3 mới tạo tài khoản có quyền đó.

Cấu hình mặc định dùng SQL Server port `1433`, MongoDB port `27017`, backend
port `8080`. Nếu máy đã dùng các port database đó, sửa `.env` cho nhất quán:

- `MSSQL_PORT=1434` thì `DB_URL` cũng phải dùng `localhost:1434`.
- `MONGO_PORT=27018` thì `MONGO_URI` cũng phải dùng `localhost:27018`.

Các lệnh `docker compose exec` trong tài liệu vẫn kết nối SQL Server qua port
nội bộ của container nên không cần sửa theo host port. Chi tiết cài đặt nằm
trong [hướng dẫn database](SQLSERVER_DOCKER_GUIDE.md).

## 3. Tạo Admin đầu tiên bằng bootstrap

Trong cùng terminal ở mục 2, chạy:

```powershell
Set-Location (Join-Path $repoRoot 'backend')

$env:SPRING_PROFILES_ACTIVE = 'local'
$env:ADMIN_BOOTSTRAP_ENABLED = 'true'
$env:ADMIN_BOOTSTRAP_EMAIL = 'admin@example.test'

$adminPassword = Read-Host 'Nhập mật khẩu Admin rồi nhấn Enter' -AsSecureString
$env:ADMIN_BOOTSTRAP_PASSWORD = [System.Net.NetworkCredential]::new('', $adminPassword).Password
Remove-Variable adminPassword

.\mvnw.cmd spring-boot:run
```

Đổi `admin@example.test` nếu muốn dùng địa chỉ khác. Mật khẩu phải không trống,
ít nhất **8 ký tự**, tối đa **72 byte UTF-8**. Hãy nhớ đúng mật khẩu đã gõ để
đăng nhập; không có mật khẩu Admin mặc định trong Git.

**Khi hiện lời nhắc nhập mật khẩu, phải gõ giá trị rồi nhấn Enter.** Ký tự được
ẩn khi nhập. Ví dụ, `Read-Host 'Nhập mật khẩu Admin'` chỉ hiển thị câu nhắc;
nội dung nằm trong dấu nháy không tự trở thành mật khẩu.

Giữ terminal backend mở và đợi log:

```text
Local/demo Admin bootstrap created an account.
```

Đồng thời backend phải khởi động thành công, không có
`APPLICATION FAILED TO START`. Dòng Maven `BUILD SUCCESS` riêng lẻ chưa xác nhận
ứng dụng đang chạy. Có thể mở [health endpoint](http://localhost:8080/actuator/health)
để kiểm tra trạng thái `UP`.

Bootstrap băm mật khẩu bằng BCrypt, lưu user `ACTIVE` và gán role `ADMIN` trong
cùng transaction. Nó chỉ chạy với profile `local` hoặc `demo` và cờ enable bật.

Nếu log là:

```text
Local/demo Admin bootstrap skipped: an Admin already exists.
```

database đã có một user mang role Admin, kể cả Admin đang bị khóa/vô hiệu hóa.
Bootstrap giữ nguyên tài khoản đó. Đổi email hoặc mật khẩu trong biến môi
trường rồi chạy lại **không tạo Admin mới và không đổi mật khẩu Admin cũ**.
Dùng mục 6 để tạo thêm tài khoản, mục 7 để đổi mật khẩu local.

## 4. Đăng nhập và dùng token

### 4.1. Lấy token bằng Swagger

Mở [Swagger UI](http://localhost:8080/swagger-ui.html):

1. Mở `POST /api/v1/auth/login` trong nhóm Authentication.
2. Chọn **Try it out**.
3. Sửa body theo tài khoản vừa tạo. Thay `MAT_KHAU_DA_NHAP` bằng mật khẩu thực tế:

```json
{
  "email": "admin@example.test",
  "password": "MAT_KHAU_DA_NHAP"
}
```

4. Chọn **Execute**. Đăng nhập thành công trả `200 OK`, với `accessToken`,
   `tokenType: "Bearer"`, `expiresIn: 28800` và `user.roles: ["ADMIN"]`.
5. Sao chép riêng giá trị `accessToken`, bỏ dấu nháy JSON.

API login không cần token trước. JWT hiện có hạn **8 giờ**; hết hạn thì đăng
nhập lại. Mật khẩu và token thực tế chỉ dùng trên máy test, không đưa vào
tài liệu, commit hoặc ảnh chia sẻ cho nhóm.

### 4.2. Gắn token trong Swagger hoặc Postman

Trong Swagger, bấm **Authorize**, dán **chỉ chuỗi token** vào ô `bearerAuth`,
chọn Authorize rồi Close. Swagger tự thêm tiền tố `Bearer`.

Chạy `GET /api/v1/auth/me`. Kết quả cần là `200 OK`, đúng email vừa đăng nhập,
`status: "ACTIVE"`, `roles: ["ADMIN"]`. Sau đó mới gọi API nghiệp vụ đã được
triển khai và cần quyền Admin.

Trong Postman, chọn **Authorization → Bearer Token**, dán token vào ô Token.
Nếu nhập header thủ công, dùng:

```text
Authorization: Bearer <accessToken>
```

Muốn test Requester/Manager/Technician, đăng nhập bằng tài khoản mang role đó
rồi thay token đang dùng. API chưa được triển khai có thể trả `404`; có token
Admin không làm endpoint xuất hiện.

### 4.3. Lấy và kiểm tra token bằng PowerShell

Mở **terminal khác** khi backend đang chạy. Biến môi trường từ terminal bootstrap
không tự xuất hiện trong terminal mới, nên nhập lại thông tin đăng nhập:

```powershell
$apiBase = 'http://localhost:8080/api/v1'
$loginEmail = Read-Host 'Nhập email đã tạo'
$loginPassword = Read-Host 'Nhập mật khẩu đã tạo' -AsSecureString
$loginBody = @{
    email = $loginEmail
    password = [System.Net.NetworkCredential]::new('', $loginPassword).Password
} | ConvertTo-Json -Compress

$login = Invoke-RestMethod -Uri "$apiBase/auth/login" -Method Post `
    -ContentType 'application/json' -Body $loginBody
$token = $login.accessToken
$headers = @{ Authorization = "Bearer $token" }
Remove-Variable loginBody, loginPassword

Invoke-RestMethod -Uri "$apiBase/auth/me" -Headers $headers
```

Token được giữ trong `$token`, header trong `$headers`. Với API cần token,
truyền `-Headers $headers` vào `Invoke-RestMethod`.

## 5. Đăng nhập trên frontend và chạy lại hằng ngày

Trong terminal mới tại root repository:

```powershell
Set-Location '.\frontend'
npm install
npm run dev
```

Mở [trang đăng nhập](http://localhost:5173/login), nhập đúng email/mật khẩu đã
tạo. Frontend tự lưu access token trong `sessionStorage` và gửi Bearer header;
không cần dán token Swagger sang frontend. Các màn hình quản trị chưa triển
khai đầy đủ có thể vẫn là placeholder.

Sau khi kết thúc lần chạy bootstrap, nhấn `Ctrl+C` trong terminal backend và
xóa cấu hình một lần:

```powershell
Remove-Item Env:ADMIN_BOOTSTRAP_ENABLED, Env:ADMIN_BOOTSTRAP_EMAIL, Env:ADMIN_BOOTSTRAP_PASSWORD -ErrorAction SilentlyContinue
```

Những lần sau, tại root bật database bằng `docker compose up -d`; tại `backend/`
chỉ chạy `.\mvnw.cmd spring-boot:run`. Tài khoản được giữ trong SQL Server volume.
Nếu từng bật bootstrap trong `.env` hoặc IDE, tắt cờ tại đúng nơi đó nữa.

## 6. Tạo thêm tài khoản test khi chưa có API quản lý user

Thao tác trên **database local của thành viên**. Khi API của issue #29 hoàn
thành, dùng API đó để test luồng quản trị thật; insert trực tiếp SQL chỉ chuẩn
bị dữ liệu, không kiểm thử validation/authorization của API tạo user.

Role hợp lệ là `REQUESTER`, `TECHNICIAN`, `MANAGER`, `ADMIN`. Role người dùng
thông thường là **`REQUESTER`**, không phải `USER`. Role không phụ thuộc tên/email:
địa chỉ chứa chữ `admin` vẫn cần bản ghi quyền tương ứng.

### 6.1. Sinh hash BCrypt cho mật khẩu tự chọn

Mở terminal khác tại `backend/`. Lệnh đầu có thể tải Maven Dependency Plugin
ở lần chạy đầu; không cần cài thư viện Python hay viết mật khẩu vào file:

```powershell
.\mvnw.cmd dependency:build-classpath '-Dmdep.outputFile=target/local-account-classpath.txt'
if ($LASTEXITCODE -ne 0) { throw 'Không tạo được classpath Maven.' }
$classpath = (Get-Content -LiteralPath '.\target\local-account-classpath.txt' -Raw).Trim()

$testPassword = Read-Host 'Nhập mật khẩu tài khoản test' -AsSecureString
$env:NEXORA_LOCAL_PASSWORD = [System.Net.NetworkCredential]::new('', $testPassword).Password
Remove-Variable testPassword

$hashScript = @'
import org.springframework.security.crypto.bcrypt.BCrypt;
String password = System.getenv("NEXORA_LOCAL_PASSWORD");
if (password == null || password.isBlank() || password.length() < 8 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) { System.out.println("INVALID_PASSWORD"); } else { System.out.println("BCRYPT_HASH=" + BCrypt.hashpw(password, BCrypt.gensalt(10))); }
/exit
'@

try {
    $hashOutput = ($hashScript | jshell --class-path $classpath -) -join "`n"
    $hashMatch = [regex]::Match($hashOutput, 'BCRYPT_HASH=(\$2a\$10\$[./0-9A-Za-z]{53})')
    if (-not $hashMatch.Success) {
        throw 'Không tạo được BCrypt hash. Kiểm tra Java 21 và mật khẩu dài 8 ký tự, tối đa 72 byte UTF-8.'
    }
    $bcryptHash = $hashMatch.Groups[1].Value
    $bcryptHash | Set-Clipboard
    Write-Output 'Đã sao chép BCrypt hash vào clipboard. Dán vào biến @passwordHash trong SQL.'
} finally {
    Remove-Item Env:NEXORA_LOCAL_PASSWORD -ErrorAction SilentlyContinue
}
```

Mỗi lần BCrypt sinh hash có thể cho chuỗi khác dù mật khẩu giống nhau. Đăng
nhập kiểm tra bằng `matches()`, không so sánh hai chuỗi hash bằng dấu bằng.

### 6.2. Insert user và gán role trong một transaction

Mở SQL Server Management Studio, kết nối `localhost,<MSSQL_PORT trong .env>`,
SQL Server Authentication, user `sa`, mật khẩu từ `MSSQL_SA_PASSWORD` trong
`.env`, bật Trust server certificate cho local. Chọn database **`nexora`**.

Trong đoạn SQL dưới, sửa **bốn biến đầu**: email, hash vừa copy, tên hiển thị,
role. Không dán mật khẩu gốc vào `@passwordHash`. Mỗi tài khoản cần email riêng;
đoạn SQL báo lỗi khi email đã tồn tại và không tự đổi quyền của user cũ.

```sql
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;
SET XACT_ABORT ON;
SET NOCOUNT ON;

DECLARE @email NVARCHAR(254) = N'requester@example.test';
DECLARE @passwordHash NVARCHAR(100) = N'DAN_HASH_BCRYPT_VAO_DAY';
DECLARE @fullName NVARCHAR(120) = N'Local Requester';
DECLARE @roleCode VARCHAR(30) = 'REQUESTER';

IF LEN(@passwordHash) <> 60 OR LEFT(@passwordHash, 4) NOT IN (N'$2a$', N'$2b$', N'$2y$')
    THROW 51000, 'Replace @passwordHash with the generated BCrypt hash.', 1;

BEGIN TRY
    BEGIN TRANSACTION;
    SET @email = LTRIM(RTRIM(@email));

    IF EXISTS (SELECT 1 FROM dbo.app_users WHERE normalized_email = LOWER(@email))
        THROW 51001, 'Email already exists. Choose another email.', 1;

    DECLARE @roleId SMALLINT;
    SELECT @roleId = id FROM dbo.roles WHERE code = @roleCode;
    IF @roleId IS NULL
        THROW 51002, 'Role is missing or invalid. Check Flyway and @roleCode.', 1;

    DECLARE @assignedBy BIGINT;
    SELECT TOP (1) @assignedBy = u.id
    FROM dbo.app_users u
    JOIN dbo.user_roles ur ON ur.user_id = u.id
    JOIN dbo.roles r ON r.id = ur.role_id
    WHERE r.code = 'ADMIN' AND u.status = 'ACTIVE'
    ORDER BY u.id;

    INSERT INTO dbo.app_users (email, password_hash, full_name, status)
    VALUES (@email, @passwordHash, @fullName, 'ACTIVE');
    DECLARE @userId BIGINT = CONVERT(BIGINT, SCOPE_IDENTITY());

    INSERT INTO dbo.user_roles (user_id, role_id, assigned_by)
    VALUES (@userId, @roleId, @assignedBy);

    IF @roleCode = 'TECHNICIAN'
        INSERT INTO dbo.technician_profiles (technician_id) VALUES (@userId);

    COMMIT TRANSACTION;

    SELECT u.id, u.email, u.full_name, u.status, r.code AS role_code
    FROM dbo.app_users u
    JOIN dbo.user_roles ur ON ur.user_id = u.id
    JOIN dbo.roles r ON r.id = ur.role_id
    WHERE u.id = @userId;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
```

Ví dụ cấu hình các tài khoản (mỗi lần sửa biến và chạy một lần):

| Email ví dụ | Tên | `@roleCode` |
| --- | --- | --- |
| `second-admin@example.test` | Local Admin 2 | `ADMIN` |
| `requester@example.test` | Local Requester | `REQUESTER` |
| `manager@example.test` | Local Manager | `MANAGER` |
| `technician@example.test` | Local Technician | `TECHNICIAN` |

Đoạn SQL tạo thêm profile cơ bản cho Technician, có capacity mặc định và trạng
thái available. Skill và service area chưa được gán; nghiệp vụ assignment cần
chuẩn bị thêm các dữ liệu đó theo feature đang test.

Sau khi insert thành công, đăng nhập bằng **email mới và mật khẩu gốc đã nhập
ở mục 6.1**, rồi lấy token theo mục 4. Không dùng BCrypt hash làm mật khẩu login.

## 7. Đặt lại mật khẩu tài khoản local đã tồn tại

Sinh hash mới theo mục 6.1, mở SSMS vào database `nexora`, sửa email và dán hash
vào hai biến dưới. Thao tác chỉ đổi mật khẩu, tăng `version` và cập nhật thời
gian; giữ nguyên role và trạng thái tài khoản.

```sql
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;
SET XACT_ABORT ON;
SET NOCOUNT ON;

DECLARE @email NVARCHAR(254) = N'admin@example.test';
DECLARE @passwordHash NVARCHAR(100) = N'DAN_HASH_BCRYPT_MOI_VAO_DAY';

IF LEN(@passwordHash) <> 60 OR LEFT(@passwordHash, 4) NOT IN (N'$2a$', N'$2b$', N'$2y$')
    THROW 51000, 'Replace @passwordHash with the generated BCrypt hash.', 1;

BEGIN TRY
    BEGIN TRANSACTION;
    UPDATE dbo.app_users
    SET password_hash = @passwordHash, version = version + 1, updated_at = SYSUTCDATETIME()
    WHERE normalized_email = LOWER(LTRIM(RTRIM(@email)));

    IF @@ROWCOUNT <> 1
        THROW 51001, 'Account not found. Check the selected database and email.', 1;

    COMMIT TRANSACTION;
    SELECT id, email, status, version
    FROM dbo.app_users WHERE normalized_email = LOWER(LTRIM(RTRIM(@email)));
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
```

Đăng nhập lại bằng mật khẩu mới để lấy token. Không cần khởi động lại backend
để nó đọc password hash mới. Các lệnh `SET` cần thiết cho index trên computed
column `normalized_email`; khi chạy bằng `sqlcmd`, cũng có thể bật
`QUOTED_IDENTIFIER` bằng tùy chọn `-I`.

## 8. Kiểm tra dữ liệu và xử lý lỗi thường gặp

Query dưới chỉ hiển thị thông tin tài khoản/quyền, không trả password hash:

```sql
SELECT u.id, u.email, u.status, r.code AS role_code, u.version
FROM dbo.app_users u
LEFT JOIN dbo.user_roles ur ON ur.user_id = u.id
LEFT JOIN dbo.roles r ON r.id = ur.role_id
ORDER BY u.id, r.code;
```

| Hiện tượng | Kiểm tra và cách xử lý |
| --- | --- |
| `401 invalid-credentials` khi login | Kiểm tra đúng email, mật khẩu đã gõ và user `ACTIVE`. Nếu quên mật khẩu, dùng mục 7; chạy lại bootstrap không đổi mật khẩu cũ. |
| `401` ở API cần token | Login lại, gắn Bearer token và kiểm tra `/auth/me` trước. |
| `403` ở API nghiệp vụ | Kiểm tra role và phạm vi thao tác của user; login lại sau khi đổi quyền để lấy JWT mới. |
| Bootstrap không chạy | Kiểm tra profile `local/demo` và `ADMIN_BOOTSTRAP_ENABLED=true` trong đúng terminal đang chạy Maven. |
| Bootstrap báo thiếu/sai cấu hình | Nhập lại email hợp lệ và mật khẩu 8 ký tự trở lên, tối đa 72 byte UTF-8. `Read-Host` vẫn cần nhập giá trị sau lời nhắc. |
| `email already belongs to an existing account` | Email đã có user nhưng chưa có Admin. Chọn email Admin khác; bootstrap không tự nâng quyền user đó. |
| `found duplicate key app` | Gộp `bootstrap` vào khối `app:` sẵn có trong `application.yml`, không thêm khối `app:` thứ hai. |
| Lỗi bind `app.file-storage.root` | Dùng code có converter trong `FileStorageConfig`; mặc định `../.data/uploads` được xử lý như đường dẫn filesystem. |
| `QUOTED_IDENTIFIER` khi chạy UPDATE/INSERT SQL | Chạy đủ các lệnh `SET` trong mẫu hoặc dùng `sqlcmd -I`. |
| `backend/backend does not exist` | Terminal đã ở `backend/`; chạy `.\mvnw.cmd spring-boot:run` trực tiếp. |
| API tạo user không xuất hiện trong Swagger | API issue #29 chưa triển khai; dùng mục 6 để chuẩn bị tài khoản test local. |

Hoàn tất khi login trả `200`, `/auth/me` trả đúng email/role/trạng thái, và API
đang test nhận token của đúng tài khoản. Các bước này chuẩn bị tài khoản và
phiên đăng nhập; feature nghiệp vụ vẫn cần được triển khai và có dữ liệu phù hợp.
