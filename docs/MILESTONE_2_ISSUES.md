# Kế hoạch Issue Milestone 2

## 1. Thông tin Milestone

- **Tên milestone:** Milestone 2 — Hoàn thiện Backend API và chuẩn bị luồng Ticket
- **Ngày bắt đầu:** 02/10/2026
- **Ngày kết thúc:** 10/10/2026
- **Ngôn ngữ API:** Enum code bằng tiếng Anh; thông báo kỹ thuật theo API contract chung.
- **Giả định đầu vào:** Toàn bộ issue của Milestone 1 đã hoàn thành, được review và merge vào `develop`.

### Mục tiêu

Milestone 2 tập trung hoàn thành các module mà luồng Ticket phụ thuộc vào:

- đăng nhập thật, current-user, frontend session và nền tảng phân quyền backend;
- quản lý user, role và technician;
- quản lý category và dynamic form;
- quản lý location, equipment type và asset;
- nền tảng lưu file và sinh ticket number;
- persistence và API đọc Ticket để Member 3 chuẩn bị cho luồng nghiệp vụ ở milestone sau;
- contract Dashboard và giao diện Dashboard shell dùng mock/test data an toàn.

Milestone này không triển khai frontend feature UI cho các module User Administration, Category, Ticket, Location và Asset; các giao diện đó được tách thành issue riêng cho Milestone 3. **M2-02 và M2-15 là ngoại lệ:** frontend session phải được nối với authentication thật trong M2, còn Dashboard contract/frontend shell vẫn nằm trong M2 nhưng chỉ dùng mock/test data và không đăng ký production API trả dữ liệu giả. Create Ticket, review, assign, work log, resolve, close, feedback và Dashboard dùng dữ liệu thật được chuyển sang milestone sau.

## 2. Phân công và lịch tổng thể

| Mã | Owner | Issue | Bắt đầu | Hoàn thành |
|---|---|---|---|---|
| M2-01 | Member 1 | Xây dựng API đăng nhập và phát hành JWT | 02/10/2026 | 05/10/2026 |
| M2-02 | Member 1 | Xây dựng API current-user và kết nối phiên đăng nhập frontend | 06/10/2026 | 07/10/2026 |
| M2-03 | Member 1 | Xây dựng API quản lý user, role và technician | 08/10/2026 | 09/10/2026 |
| M2-04 | Member 2 | Xây dựng API đọc category, published form và SLA | 02/10/2026 | 05/10/2026 |
| M2-05 | Member 2 | Xây dựng API quản trị category và dynamic form | 06/10/2026 | 07/10/2026 |
| M2-07 | Member 3 | Xây dựng persistence nền tảng cho Ticket | 02/10/2026 | 05/10/2026 |
| M2-08 | Member 3 | Xây dựng persistence cho dữ liệu động, lịch sử và attachment của Ticket | 06/10/2026 | 07/10/2026 |
| M2-09 | Member 3 | Xây dựng API đọc danh sách và chi tiết Ticket | 08/10/2026 | 09/10/2026 |
| M2-10 | Member 4 | Xây dựng API CRUD Location và Equipment Type | 02/10/2026 | 05/10/2026 |
| M2-11 | Member 4 | Xây dựng API CRUD Asset và lịch sử trạng thái | 06/10/2026 | 07/10/2026 |
| M2-12 | Member 4 | Xây dựng API lấy Asset hợp lệ theo Location | 08/10/2026 | 09/10/2026 |
| M2-13 | Member 5 | Tạo SQL Server sequence cho Ticket Number | 02/10/2026 | 05/10/2026 |
| M2-14 | Member 5 | Xây dựng nền tảng lưu trữ file đính kèm | 06/10/2026 | 07/10/2026 |
| M2-15 | Member 5 | Xây dựng contract và giao diện khung Dashboard | 08/10/2026 | 09/10/2026 |

### Phân loại phạm vi

Các issue M2 khác là **Backend/API/Persistence**. M2-02 là **Full-stack Auth Session** và M2-15 là **Contract + Frontend shell**; hai issue này phải hoàn thành rõ cả backend contract/API lẫn frontend UI/test. Frontend toàn repository vẫn phải build/lint/test thành công.

### Frontend chuyển sang Milestone 3

| Mã đề xuất | Frontend issue M3 | Phụ thuộc Backend M2 |
|---|---|---|
| M3-FE02 | Giao diện quản lý User, Role và Technician | M2-03 |
| M3-FE03 | Giao diện quản trị Category và Dynamic Form | M2-04, M2-05 |
| M3-FE04 | Giao diện Ticket List và Ticket Detail chỉ đọc — được bao gồm trong GitHub #35 | M2-09 |
| M3-FE05 | Giao diện quản trị Location và Equipment Type | M2-10 |
| M3-FE06 | Giao diện quản trị Asset và lịch sử trạng thái | M2-11 |
| M3-FE07 | Component chọn Asset hợp lệ theo Location | M2-12 |

Planning ID M2-06 không còn thuộc phạm vi M2. Nội dung cũ của M2-06 được chuyển thành M3-FE03. Các mã M2 còn lại được giữ nguyên để tránh thay đổi dependency/reference đã được dùng trong kế hoạch.

Ngày **10/10/2026** được dành cho:

- review chéo các Pull Request;
- merge theo đúng thứ tự phụ thuộc;
- chạy backend verify, frontend lint/test/build;
- kiểm tra SQL Server từ database sạch;
- sửa lỗi tích hợp nhỏ;
- cập nhật trạng thái issue và đóng milestone.

## 3. Thứ tự phụ thuộc

```text
M2-01 Login
   └── M2-02 Current User API + Frontend Session
          ├── M2-03 User/Role/Technician Management
          └── M2-09 Ticket Read API

M2-04 Category/Form Read API
   └── M2-05 Category/Form Administration API

M2-07 Ticket Core Persistence
   └── M2-08 Ticket Supporting Persistence
          └── M2-09 Ticket Read API

M2-10 Location/Equipment CRUD
   └── M2-11 Asset CRUD/Status History
          └── M2-12 Eligible Asset API

M2-13 Ticket Number Sequence ──┐
M2-14 File Storage Foundation ─┼── Chuẩn bị cho Create Ticket ở milestone sau
M2-15 Dashboard Contract ──────┘
```

## 4. Quy ước chung cho API

- Base path: `/api/v1`.
- Request/response sử dụng DTO; không trả trực tiếp JPA entity.
- Lỗi sử dụng Spring `ProblemDetail` theo RFC 9457.
- Phân trang bắt đầu từ `page=0`, mặc định `size=20`, tối đa `size=100`.
- Danh sách phân trang có dạng:

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

- API thay đổi dữ liệu phải kiểm tra quyền ở backend.
- Khi entity có optimistic-lock `version`, request thay đổi phải gửi `version`; dữ liệu cũ trả `409 Conflict`.
- Không sử dụng endpoint kiểu “đổi trạng thái tùy ý” cho nghiệp vụ có workflow.
- Mã enum trong API dùng tiếng Anh; Milestone 3 chịu trách nhiệm ánh xạ thành nhãn giao diện tiếng Việt.

## 5. Definition of Done chung

Một issue chỉ được xem là hoàn thành khi các mục phù hợp sau đã đạt:

- code đúng phạm vi và không kèm thay đổi ngoài issue;
- backend validation và authorization đã được thực hiện;
- có test cho happy path, dữ liệu sai và sai quyền;
- migration chỉ được bổ sung mới, không sửa migration đã chạy;
- OpenAPI và tài liệu liên quan được cập nhật khi contract thay đổi;
- không commit mật khẩu, JWT secret hoặc đường dẫn máy cá nhân;
- backend verify thành công; frontend lint/test/build vẫn phải xanh để bảo đảm repository không bị regression;
- Pull Request được ít nhất một người khác review;
- security change được Member 1 review;
- schema/migration change được Member 4 review;
- integration/test foundation change được Member 5 review.

---

# M2-01 — Xây dựng API đăng nhập và phát hành JWT

**Owner:** Member 1  
**Module:** `auth`, `user`, `config`  
**Bắt đầu:** 02/10/2026  
**Hoàn thành:** 05/10/2026  
**Branch gợi ý:** `feature/<issue-number>-auth-login-jwt`  
**Phụ thuộc:** Milestone 1 — Identity Persistence và Auth Skeleton đã hoàn thành.

## Mục tiêu

Thay thế đăng nhập demo bằng đăng nhập thật sử dụng email, mật khẩu BCrypt và access JWT.

## API

### `POST /api/v1/auth/login`

Request:

```json
{
  "email": "requester@nexora.local",
  "password": "example-password"
}
```

Response thành công `200 OK`:

```json
{
  "accessToken": "<jwt>",
  "tokenType": "Bearer",
  "expiresIn": 28800,
  "user": {
    "id": 10,
    "email": "requester@nexora.local",
    "fullName": "Nguyễn Văn A",
    "roles": ["REQUESTER"]
  }
}
```

Sai thông tin đăng nhập trả `401 Unauthorized` bằng `ProblemDetail` và không cho biết email hay mật khẩu sai.

## Công việc

- Tìm user bằng normalized email.
- Kiểm tra mật khẩu bằng BCrypt.
- Phát hành HS256 JWT có thời hạn 8 giờ.
- JWT `sub` là user ID và chứa danh sách role code.
- Cấu hình JWT secret từ environment, không đặt secret thật trong source.
- Ghi log an toàn; không log mật khẩu hoặc token đầy đủ.
- Cập nhật OpenAPI cho endpoint login.

## Business rule

- Chỉ user `ACTIVE` được đăng nhập.
- User `LOCKED` hoặc `DISABLED` bị từ chối.
- Role hợp lệ chỉ gồm `REQUESTER`, `TECHNICIAN`, `MANAGER`, `ADMIN`.
- Không có refresh token trong MVP.
- User không được tự chọn hoặc tự gửi role khi đăng nhập.

## Đạt yêu cầu khi

- Email/mật khẩu hợp lệ trả JWT sử dụng được.
- Mật khẩu sai, email không tồn tại và user không active đều trả lỗi an toàn.
- Response không chứa `passwordHash`.
- JWT có đúng `sub`, role và thời gian hết hạn.
- Endpoint public duy nhất được thêm là login; các API nghiệp vụ vẫn được bảo vệ.

## Cách test

- Unit test password verification và JWT claims.
- MVC/integration test login thành công.
- Test email viết hoa/thường được normalize đúng.
- Test sai mật khẩu, email không tồn tại, user `LOCKED`, user `DISABLED`.
- Test JWT hết hạn và JWT sai chữ ký bị từ chối.

## Ngoài phạm vi

- Refresh token.
- Quên mật khẩu.
- Xác minh email.
- OAuth/Google login.

---

# M2-02 — Xây dựng API Current User và kết nối phiên đăng nhập Frontend

**Owner:** Member 1  
**Module:** `auth`, `user`, `config`, frontend `auth`  
**Loại issue:** Full-stack Auth Session  
**Bắt đầu:** 06/10/2026  
**Hoàn thành:** 07/10/2026  
**Branch gợi ý:** `feature/<issue-number>-current-user-session`  
**Phụ thuộc:** M2-01.

## Mục tiêu

Cung cấp API để xác định người dùng hiện tại và kết nối frontend với phiên đăng nhập JWT thật. Sau issue này, người dùng có thể đăng nhập bằng email/password, refresh trang trong cùng tab mà không mất phiên hợp lệ, đăng xuất và được điều hướng theo trạng thái/role do backend trả về.

## API

### `GET /api/v1/auth/me`

Header:

```http
Authorization: Bearer <access-token>
```

Response `200 OK`:

```json
{
  "id": 10,
  "email": "requester@nexora.local",
  "fullName": "Nguyễn Văn A",
  "status": "ACTIVE",
  "roles": ["REQUESTER"]
}
```

### API requirements

- Lấy user ID từ JWT và tải lại user từ database.
- Kiểm tra user vẫn tồn tại và vẫn `ACTIVE`.
- Trả DTO chỉ chứa ID, email, full name, status và canonical role codes.
- Không trả password hash hoặc entity trực tiếp.
- Chuẩn hóa response `401`, `403` và user không còn tồn tại theo API contract chung.
- Cập nhật OpenAPI cho endpoint `/auth/me`.

## Frontend/UI

- Cập nhật `LoginForm` để chỉ nhận email/password, gọi API login của M2-01 và loại bỏ khả năng tự chọn role.
- Lưu access token trong `sessionStorage`; không đưa token vào URL, HTML hoặc log.
- Sau login, gọi `/api/v1/auth/me` để lấy identity và roles chính thức từ backend.
- Khi ứng dụng khởi động lại trong cùng tab, đọc token rồi gọi `/auth/me` trước khi quyết định trạng thái đăng nhập.
- Cấu hình Axios gửi `Authorization: Bearer <token>` và giữ `withCredentials=false`.
- Khi nhận `401`, xóa token/user và chuyển về login; khi nhận `403`, giữ phiên hợp lệ và hiển thị trang không có quyền.
- Hoàn thiện `AuthProvider`, logout, `ProtectedRoute`, `RequireRole` và trạng thái checking/restoring session để tránh nháy sai màn hình.
- Hiển thị loading, invalid-credentials error, network error và login success feedback bằng tiếng Việt.

## Business rule

- Quyền backend dựa trên user hiện tại trong database, không chỉ dựa vào role cũ trong JWT.
- Ẩn route/nút trên frontend không thay thế kiểm tra quyền ở backend.
- `sessionStorage` chỉ duy trì token trong phiên tab hiện tại.

## Đạt yêu cầu khi

### Backend

- Token hợp lệ trả đúng user và role đang lưu trong database.
- Token thiếu, sai chữ ký hoặc hết hạn trả `401` theo `ProblemDetail` convention.
- User đã bị `LOCKED`, `DISABLED` hoặc không còn tồn tại không được xem là authenticated business user.
- Response không chứa password hash hoặc dữ liệu persistence nội bộ.
- OpenAPI mô tả đầy đủ success/error response.

### Frontend

- Login thành công hiển thị đúng tên và roles từ `/auth/me`.
- Refresh trang trong cùng tab phục hồi session nếu token còn hiệu lực.
- Token sai/hết hạn đưa user về login và xóa session an toàn.
- Route không đúng role hiển thị trạng thái không có quyền mà không xóa phiên hợp lệ.
- Logout xóa token/user và quay về login.
- Demo login và chức năng tự chọn role được loại bỏ.

### Integration

- Frontend sử dụng đúng response contract của M2-01 và `/auth/me`.
- Backend verify và frontend lint/test/build thành công.

## Cách test

- Backend test `/auth/me` với token hợp lệ, thiếu token, token hết hạn và user bị disable sau khi token được phát hành.
- Frontend test login success/failure, restore session, logout, `401`, `403` và role guard.
- Kiểm tra token không xuất hiện trong log, URL hoặc HTML render.

## Ngoài phạm vi

- Refresh token hoặc logout endpoint phía server.
- Password reset, email verification và OAuth login.

---

# M2-03 — Xây dựng API quản lý User, Role và Technician

**Owner:** Member 1  
**Module:** `user`, `auth`  
**Bắt đầu:** 08/10/2026  
**Hoàn thành:** 09/10/2026  
**Branch gợi ý:** `feature/<issue-number>-user-technician-management`  
**Phụ thuộc:** M2-02.

## Mục tiêu

Cung cấp chức năng quản trị tối thiểu để tạo tài khoản demo, gán role và cấu hình technician cho quá trình assignment ở milestone sau.

## API chính

```http
GET   /api/v1/admin/users
POST  /api/v1/admin/users
GET   /api/v1/admin/users/{userId}
PATCH /api/v1/admin/users/{userId}/status
PUT   /api/v1/admin/users/{userId}/roles

GET   /api/v1/skills?active=true
GET   /api/v1/technicians/{technicianId}
PUT   /api/v1/technicians/{technicianId}
PUT   /api/v1/technicians/{technicianId}/skills
PUT   /api/v1/technicians/{technicianId}/service-areas
```

Ví dụ cập nhật technician:

```json
{
  "available": true,
  "maxActiveTickets": 5,
  "version": 0
}
```

## Phân quyền

- Chỉ `ADMIN` được tạo user, đổi trạng thái user và gán role.
- `MANAGER` và `ADMIN` được cấu hình technician, skill và service area.
- Technician chỉ được đọc hồ sơ của chính mình.
- Người dùng authenticated được đọc danh sách skill active khi nghiệp vụ cần.

## Business rule

- Email user là duy nhất sau khi normalize.
- Không xóa vật lý user; sử dụng trạng thái `ACTIVE`, `LOCKED`, `DISABLED`.
- Technician profile chỉ được tạo cho user có role `TECHNICIAN`.
- `maxActiveTickets` phải lớn hơn 0.
- Proficiency level nằm trong khoảng 1–5.
- Service area phải tham chiếu location đang tồn tại.
- Không trả password hash qua bất kỳ API nào.

## Đạt yêu cầu khi

- Admin có thể tạo tài khoản và gán role.
- Manager/Admin có thể cấu hình technician, skill và service area.
- Sai quyền trả `403`; dữ liệu không tồn tại trả `404`; version cũ trả `409`.
- Danh sách user có phân trang và không tải toàn bộ dữ liệu một lần.

## Cách test

- Test tạo user hợp lệ và email trùng.
- Test Admin, Manager, Technician và Requester theo permission matrix.
- Test không thể tạo technician cho user thiếu role `TECHNICIAN`.
- Test proficiency, capacity, service area và optimistic locking.
- API smoke test cấu hình một technician dùng được cho milestone sau.

## Ngoài phạm vi

- Password reset.
- Xóa user.
- Tự động chọn technician.
- Tính eligibility hoàn chỉnh khi assign.

---

# M2-04 — Xây dựng API đọc Category, Published Form và SLA

**Owner:** Member 2  
**Module:** `category`  
**Bắt đầu:** 02/10/2026  
**Hoàn thành:** 05/10/2026  
**Branch gợi ý:** `feature/<issue-number>-category-form-read-api`  
**Phụ thuộc:** Milestone 1 — Dynamic Form Persistence đã hoàn thành.

## Mục tiêu

Cung cấp dữ liệu category và form đang được phát hành để frontend chuẩn bị màn hình tạo Ticket trong milestone sau.

## API

```http
GET /api/v1/categories?active=true
GET /api/v1/categories/{categoryId}/published-form
GET /api/v1/sla-policies?active=true
```

Response published form cần chứa:

- category và asset policy;
- form version;
- `requiresEquipment`, `minAttachments`;
- default skill và SLA policy;
- danh sách field đúng `displayOrder`;
- option active đúng `displayOrder`;
- field type và validation rules cần thiết cho frontend render.

## Business rule

- Requester chỉ thấy category active và form `PUBLISHED` hiện tại.
- Không trả draft hoặc archived form qua API public/reference.
- Field/option inactive không được sử dụng cho Ticket mới.
- `validationRules` phải được trả dưới dạng JSON có kiểm soát, không sửa nội dung âm thầm.
- SLA của Ticket mới sau này lấy từ `default_sla_policy_id` của published form.

## Đạt yêu cầu khi

- Frontend có thể dựng form chỉ từ response của backend.
- Category không có published form trả lỗi nghiệp vụ rõ ràng.
- Thứ tự field và option ổn định.
- API không gây vòng lặp JSON từ quan hệ entity.

## Cách test

- Test category active/inactive.
- Test chỉ lấy đúng một published form hiện tại.
- Test field/option ordering.
- Test category không tồn tại và chưa có published form.
- Test authenticated user được đọc; anonymous bị từ chối.

---

# M2-05 — Xây dựng API quản trị Category và Dynamic Form

**Owner:** Member 2  
**Module:** `category`  
**Bắt đầu:** 06/10/2026  
**Hoàn thành:** 07/10/2026  
**Branch gợi ý:** `feature/<issue-number>-category-form-admin-api`  
**Phụ thuộc:** M2-04.

## Mục tiêu

Cho phép Admin tạo category, tạo draft form, sửa field/option và publish một form hợp lệ.

## API chính

```http
GET  /api/v1/admin/categories
POST /api/v1/admin/categories
PUT  /api/v1/admin/categories/{categoryId}

GET  /api/v1/admin/categories/{categoryId}/form-versions
POST /api/v1/admin/categories/{categoryId}/form-versions/drafts
GET  /api/v1/admin/form-versions/{formVersionId}
PUT  /api/v1/admin/form-versions/{formVersionId}
POST /api/v1/admin/form-versions/{formVersionId}/publish
POST /api/v1/admin/form-versions/{formVersionId}/archive
```

`PUT` draft form có thể nhận toàn bộ cấu trúc field và option trong một transaction để giảm số request và tránh lưu form dở dang.

## Business rule

- Chỉ `ADMIN` được quản trị category/form.
- Chỉ draft form được sửa.
- Published và archived form là immutable.
- Tạo draft mới dùng version number tiếp theo do server sinh.
- Publish form mới và archive published form cũ phải cùng transaction.
- Publish chỉ thành công khi:
  - category active;
  - field key không trống và không trùng;
  - field type và validation rule hợp lệ;
  - `SELECT`/`MULTI_SELECT` có option active;
  - field không phải select không có option;
  - asset policy không mâu thuẫn `requiresEquipment`;
  - default skill và SLA policy đang active;
  - `minAttachments` không âm.

## Đạt yêu cầu khi

- Admin có thể hoàn thành vòng đời draft → published → archived.
- Form cũ đã gắn Ticket sau này không bị thay đổi.
- Publish lỗi trả field errors dễ hiểu.
- Hai request publish đồng thời không tạo hai published form.

## Cách test

- Test quyền Admin và từ chối các role khác.
- Test create/update draft.
- Test published form không sửa được.
- Test từng nhóm publish validation.
- Integration test transaction archive cũ + publish mới.
- Test stale version trả `409` nếu form có optimistic locking.

---

# M2-07 — Xây dựng Persistence nền tảng cho Ticket

**Owner:** Member 3  
**Module:** `ticket`  
**Bắt đầu:** 02/10/2026  
**Hoàn thành:** 05/10/2026  
**Branch gợi ý:** `feature/<issue-number>-ticket-core-persistence`  
**Phụ thuộc:** Giả định toàn bộ persistence của M1 đã hoàn thành.

## Mục tiêu

Mapping bảng `tickets` và repository nền tảng để milestone sau có thể triển khai Create Ticket và workflow mà không phải thiết kế lại persistence.

## Công việc

- Tạo `Ticket` entity khớp schema SQL Server.
- Mapping reporter, category, form version, location và optional asset.
- Mapping status, priority, description, SLA due time, resolution/closed timestamps và `version`.
- Không tạo quan hệ hai chiều lớn gây tải toàn bộ aggregate.
- Tạo repository tìm theo ID và ticket number.
- Chuẩn bị query foundation cho ticket visibility nhưng chưa triển khai workflow ghi.

## Business rule cần thể hiện ở model

- Ticket luôn có reporter, category/form version và location.
- Asset có thể null tùy policy; validation nghiệp vụ thực hiện ở create service sau.
- Ticket mới sau này bắt đầu ở `SUBMITTED`.
- Ticket giữ form version cố định để render lịch sử.
- `version` dùng cho optimistic locking.
- Ticket number là duy nhất nhưng generator được kết nối ở milestone sau.

## Đạt yêu cầu khi

- Entity mapping khớp Flyway schema hiện tại.
- Lưu/đọc được Ticket bằng repository test.
- Optimistic locking hoạt động.
- Không có controller create/update Ticket trong issue này.

## Cách test

- Repository test lưu Ticket đầy đủ và Ticket không có optional asset.
- Test unique ticket number.
- Test quan hệ reporter/form/location/asset.
- Test stale update gây optimistic-lock conflict.
- SQL Server integration test kiểm tra mapping thực tế.

## Ngoài phạm vi

- Create Ticket API.
- Review/assignment/workflow.
- SLA runtime calculation.

---

# M2-08 — Persistence dữ liệu động, lịch sử và Attachment của Ticket

**Owner:** Member 3  
**Module:** `ticket`  
**Bắt đầu:** 06/10/2026  
**Hoàn thành:** 07/10/2026  
**Branch gợi ý:** `feature/<issue-number>-ticket-supporting-persistence`  
**Phụ thuộc:** M2-07.

## Mục tiêu

Mapping các bảng phụ thuộc trực tiếp vào Ticket để luồng tạo và xử lý Ticket ở milestone sau chỉ cần bổ sung service nghiệp vụ.

## Công việc

- Mapping `ticket_field_values`.
- Mapping `ticket_status_history`.
- Mapping metadata `ticket_attachments`.
- Repository lấy dynamic values theo field order.
- Repository lấy status history theo thời gian tăng dần.
- Repository lấy attachment metadata theo Ticket và attachment type.
- Không lưu binary file vào SQL Server.

## Business rule

- `value_json` giữ đúng JSON contract của từng field type.
- `IMAGE` được biểu diễn bằng attachment liên kết field; không bắt buộc có scalar value row.
- Mỗi workflow transition sau này phải ghi status history cùng transaction với Ticket update.
- Attachment chỉ lưu metadata và opaque storage key.
- Original filename chỉ là dữ liệu hiển thị, không được dùng làm filesystem path.

## Đạt yêu cầu khi

- Lưu/đọc được dynamic value, history và attachment metadata.
- Kết quả trả theo thứ tự ổn định.
- Mapping không kéo binary content vào database.
- Không có upload endpoint hoặc workflow service trong issue này.

## Cách test

- Test từng dạng JSON đại diện: string, number, boolean, array và date string.
- Test ordered history.
- Test attachment type `REPORT_EVIDENCE`, field-linked `IMAGE`, `WORK_RESULT`.
- Test FK và cascade behavior không xóa nhầm reference data.
- SQL Server integration test cho tên cột và constraint.

---

# M2-09 — Xây dựng API đọc danh sách và chi tiết Ticket

> GitHub #35 bao gồm cả Ticket List/Detail Frontend theo scope issue đã cập nhật.
> Nội dung M3-FE04 trùng với phần này, không tạo implementation độc lập.

**Owner:** Member 3  
**Module:** `ticket`  
**Loại issue:** Backend  
**Bắt đầu:** 08/10/2026  
**Hoàn thành:** 09/10/2026  
**Branch gợi ý:** `feature/<issue-number>-ticket-read-api`  
**Phụ thuộc:** M2-02, M2-07, M2-08.

## Mục tiêu

Cho phép client đọc dữ liệu Ticket đã seed/fixture bằng API có phân trang, filter và authorization đúng. API này chuẩn bị contract ổn định cho Ticket List/Detail UI ở Milestone 3 và workflow ở milestone sau.

## API

```http
GET /api/v1/tickets?page=0&size=20&sort=createdAt,desc&status=SUBMITTED&priority=HIGH&locationId=1
GET /api/v1/tickets/{ticketId}
```

Danh sách trả dữ liệu tóm tắt:

- ID và ticket number;
- status, priority;
- category, location và optional asset;
- reporter;
- SLA due time;
- created/updated time.

Chi tiết trả thêm:

- description;
- stored form version;
- dynamic values;
- status history;
- attachment metadata;
- `version` để frontend dùng cho action ở milestone sau.

## Business rule về quyền xem

- `REQUESTER`: chỉ Ticket do chính mình báo.
- `TECHNICIAN`: Ticket được phân công hiện tại hoặc đã từng phân công.
- `MANAGER`: xem tất cả.
- `ADMIN`: xem tất cả.
- Visibility phải được áp dụng trong query, không tải tất cả rồi lọc bằng Java.

## Đạt yêu cầu khi

- API phân trang đúng contract chung.
- Sort/filter không được hỗ trợ trả `400`, không bỏ qua âm thầm.
- User không có quyền xem một Ticket trả `404` hoặc response an toàn theo convention, không làm lộ sự tồn tại của Ticket.
- OpenAPI mô tả đầy đủ list/detail response và các filter/sort được hỗ trợ.

## Cách test

- Integration test visibility cho bốn role.
- Test pagination, sort allowlist và filter.
- Test Ticket không tồn tại và Ticket không có quyền xem.
- Test detail response chứa đúng dynamic values/history/attachment metadata.

## Ngoài phạm vi

- `POST /tickets`.
- Review, assign, start, resolve, close.
- Comment và feedback.
- Các màn hình/action workflow Ticket ngoài list/detail chỉ đọc.

---

# M2-10 — Xây dựng API CRUD Location và Equipment Type

**Owner:** Member 4  
**Module:** `location`, `asset`  
**Loại issue:** Backend  
**Bắt đầu:** 02/10/2026  
**Hoàn thành:** 05/10/2026  
**Branch gợi ý:** `feature/<issue-number>-location-equipment-crud`  
**Phụ thuộc:** Milestone 1 — Location/Equipment Persistence đã hoàn thành.

## Mục tiêu

Cung cấp API backend để quản trị cây campus/building/floor/room/area và equipment type dùng cho Asset, Technician Service Area và Ticket. Giao diện quản trị và Location Selector được triển khai riêng trong Milestone 3.

## API

```http
GET  /api/v1/locations?parentId={id}&active=true
GET  /api/v1/locations/{locationId}
POST /api/v1/admin/locations
PUT  /api/v1/admin/locations/{locationId}

GET  /api/v1/equipment-types?active=true
POST /api/v1/admin/equipment-types
PUT  /api/v1/admin/equipment-types/{equipmentTypeId}
```

## Business rule

- Chỉ `ADMIN` quản trị reference catalog.
- Authenticated user được đọc location/equipment active khi nghiệp vụ cần.
- Location code và equipment type code là duy nhất.
- Không tạo vòng lặp trong cây location.
- Parent phải phù hợp với hierarchy đã chấp nhận.
- Không xóa vật lý record đã được tham chiếu; sử dụng `active=false`.
- API cây location tải theo parent hoặc DTO tree có giới hạn, không serialize entity đệ quy vô hạn.

## Đạt yêu cầu khi

- Admin tạo/sửa/deactivate được location và equipment type.
- Anonymous bị từ chối; role không phải Admin không được thay đổi dữ liệu.
- Không thể tạo hierarchy cycle.
- OpenAPI mô tả request/response và hierarchy rules.

## Cách test

- Test CAMPUS → BUILDING → FLOOR → ROOM/AREA.
- Test duplicate code và parent không tồn tại.
- Test cycle detection.
- Test active filtering và quyền truy cập.

## Ngoài phạm vi

- Location/Equipment Administration UI; chuyển sang M3-FE05.
- `LocationSelector` và frontend component tests.

---

# M2-11 — Xây dựng API CRUD Asset và lịch sử trạng thái

**Owner:** Member 4  
**Module:** `asset`  
**Loại issue:** Backend  
**Bắt đầu:** 06/10/2026  
**Hoàn thành:** 07/10/2026  
**Branch gợi ý:** `feature/<issue-number>-asset-crud-status-history`  
**Phụ thuộc:** M2-10.

## Mục tiêu

Cung cấp API backend để Manager/Admin quản lý Asset và bảo đảm mọi thay đổi trạng thái đều được ghi lịch sử trong cùng transaction. Giao diện Asset Administration được triển khai riêng trong Milestone 3.

## API

```http
GET  /api/v1/assets?page=0&size=20&locationId={id}&status=ACTIVE
GET  /api/v1/assets/{assetId}
POST /api/v1/assets
PUT  /api/v1/assets/{assetId}
POST /api/v1/assets/{assetId}/status
GET  /api/v1/assets/{assetId}/status-history
```

Ví dụ đổi trạng thái:

```json
{
  "targetStatus": "UNDER_MAINTENANCE",
  "reason": "Thiết bị cần kiểm tra nguồn điện",
  "version": 2
}
```

## Business rule

- `MANAGER` và `ADMIN` được quản lý Asset/status.
- Asset code là duy nhất; serial number nếu có phải tuân thủ uniqueness của schema.
- Asset phải tham chiếu location và equipment type hợp lệ.
- Status chỉ gồm `ACTIVE`, `UNDER_MAINTENANCE`, `OUT_OF_SERVICE`, `RETIRED`.
- Không cho đổi sang chính trạng thái hiện tại.
- Đổi status và ghi `asset_status_history` phải cùng transaction.
- Reason đổi trạng thái không được để trống.
- Update gửi `version`; stale version trả `409`.

## Đạt yêu cầu khi

- Tạo/sửa/xem Asset được qua API.
- Mỗi lần đổi status có lịch sử gồm trạng thái cũ, mới, lý do, người thực hiện và thời gian.
- Role không hợp lệ bị từ chối.
- Không hard-delete Asset đã được sử dụng.

## Cách test

- Test CRUD Asset và duplicate code.
- Test quyền Manager/Admin và từ chối Requester/Technician.
- Test status history được ghi atomically.
- Test blank reason, invalid status và stale version.

## Ngoài phạm vi

- Asset list/form/detail/status-history UI; chuyển sang M3-FE06.
- Modal đổi trạng thái và frontend component tests.

---

# M2-12 — Xây dựng API lấy Asset hợp lệ theo Location

**Owner:** Member 4  
**Module:** `asset`, `location`  
**Loại issue:** Backend  
**Bắt đầu:** 08/10/2026  
**Hoàn thành:** 09/10/2026  
**Branch gợi ý:** `feature/<issue-number>-eligible-assets-by-location`  
**Phụ thuộc:** M2-11.

## Mục tiêu

Cung cấp API backend trả danh sách Asset mà Requester có thể chọn khi tạo Ticket ở milestone sau. Component chọn Asset được triển khai riêng trong Milestone 3.

## API

```http
GET /api/v1/locations/{locationId}/eligible-assets?page=0&size=20&equipmentTypeId={id}&query={text}
```

Response chỉ cần dữ liệu chọn lựa:

- asset ID và code;
- equipment type;
- location;
- current status;
- tên/mô tả ngắn cần cho UI.

## Business rule

- Asset phải thuộc **đúng location** được chọn, không tự động lấy asset của location cha/con.
- Chỉ `ACTIVE` và `UNDER_MAINTENANCE` là eligible cho Ticket mới.
- `OUT_OF_SERVICE` và `RETIRED` không xuất hiện trong API eligible.
- Chỉ authenticated user được gọi.
- Filter equipment type phải thuộc allowlist hỗ trợ.

## Đạt yêu cầu khi

- API không trả Asset của location khác.
- API không trả Asset có trạng thái không hợp lệ.
- Có phân trang và tìm kiếm cơ bản theo code/tên.
- OpenAPI mô tả đầy đủ query parameters và response lựa chọn Asset.

## Cách test

- Test đủ bốn status.
- Test hai location khác nhau.
- Test equipment type filter và pagination.
- Test location không tồn tại.

## Ngoài phạm vi

- `EligibleAssetSelector`; chuyển sang M3-FE07.
- Create Ticket UI và frontend component tests.

---

# M2-13 — Tạo SQL Server Sequence cho Ticket Number

**Owner:** Member 5  
**Module:** `migration`, `common`  
**Bắt đầu:** 02/10/2026  
**Hoàn thành:** 05/10/2026  
**Branch gợi ý:** `feature/<issue-number>-ticket-number-sequence`  
**Phụ thuộc:** Không có dependency M2; Milestone 1 được giả định đã hoàn thành.

## Mục tiêu

Chuẩn bị cơ chế sinh ticket number an toàn khi có nhiều request đồng thời.

## Công việc

- Thêm Flyway migration với version kế tiếp đang còn trống.
- Tạo SQL Server sequence làm bộ đếm toàn cục.
- Bổ sung component đọc `NEXT VALUE FOR` bằng native SQL/JDBC phù hợp.
- Format kết quả theo `NX-{year}-{six-digit-sequence}`.
- Viết tài liệu ngắn về việc sequence không reset theo năm.

## Business rule

- Ví dụ hợp lệ: `NX-2026-000123`.
- Năm là năm tạo Ticket theo timezone Asia/Bangkok.
- Phần sequence tăng toàn cục và không reset mỗi năm.
- Không dùng `MAX(id) + 1`.
- Không yêu cầu số Ticket liên tục tuyệt đối; transaction rollback có thể tạo gap.

## Đạt yêu cầu khi

- Database sạch migrate thành công.
- Gọi đồng thời không tạo ticket number trùng.
- Format đúng và có unique constraint/index hiện hành hỗ trợ.
- Không sửa migration V1–V8.

## Cách test

- Test Flyway migration trên SQL Server Testcontainers.
- Test sinh nhiều sequence value liên tiếp.
- Test concurrency bằng nhiều thread/request.
- Test format năm và padding sáu chữ số.

## Ngoài phạm vi

- Create Ticket transaction.
- Idempotency của Create Ticket.

---

# M2-14 — Xây dựng nền tảng lưu trữ File đính kèm

**Owner:** Member 5  
**Module:** `common`, `config`  
**Bắt đầu:** 06/10/2026  
**Hoàn thành:** 07/10/2026  
**Branch gợi ý:** `feature/<issue-number>-local-file-storage`  
**Phụ thuộc:** Không phụ thuộc Ticket service.

## Mục tiêu

Xây dựng service lưu file an toàn để milestone sau dùng cho report evidence và work-result attachment.

## Contract nội bộ

Service tối thiểu hỗ trợ:

```text
store(file) -> storageKey + metadata
open(storageKey) -> stream/resource
delete(storageKey)
```

Chưa tạo public upload/download endpoint trong issue này.

## Công việc

- Tạo `FileStorageService` và local-filesystem implementation.
- Thư mục lưu file lấy từ cấu hình/environment và nằm ngoài public web root.
- Sinh opaque storage key ngẫu nhiên.
- Validate MIME type, kích thước và số lượng ở reusable validator.
- Chuẩn bị cơ chế cleanup file khi business transaction thất bại.
- Không ghép đường dẫn trực tiếp từ filename do người dùng gửi.

## Business rule

- Chỉ chấp nhận `image/jpeg` và `image/png`.
- Tối đa 5 MiB mỗi file.
- Tối đa 5 file trong một upload request sau này.
- Original filename chỉ để hiển thị.
- Không lưu binary file trong SQL Server.
- Download sau này chỉ qua API có authorization.

## Đạt yêu cầu khi

- Lưu, đọc và xóa file bằng storage key.
- Không thể path traversal bằng filename hoặc storage key giả.
- File vượt dung lượng hoặc sai MIME type bị từ chối.
- Không có public static route làm lộ file.

## Cách test

- Unit test JPEG/PNG hợp lệ.
- Test MIME type sai, file quá lớn và storage key không tồn tại.
- Test filename chứa `../`, ký tự đặc biệt và Unicode.
- Test hai file trùng tên gốc không ghi đè nhau.
- Test cleanup trong thư mục tạm.

## Ngoài phạm vi

- Ticket upload controller.
- Cloud/S3 storage.
- Thumbnail và media preview.

---

# M2-15 — Xây dựng Contract và giao diện khung Dashboard

**Owner:** Member 5  
**Module:** `dashboard`, frontend `dashboard`  
**Loại issue:** Contract + Frontend shell  
**Bắt đầu:** 08/10/2026  
**Hoàn thành:** 09/10/2026  
**Branch gợi ý:** `feature/<issue-number>-dashboard-contract-shell`  
**Phụ thuộc:** Không phụ thuộc luồng ghi Ticket; sử dụng mock/test data cho frontend.

## Mục tiêu

Chốt backend response contract/query boundary và xây dựng frontend Dashboard shell theo cùng contract. Sau issue này, milestone sau chỉ cần kết nối query dữ liệu Ticket thật mà không phải thiết kế lại DTO, API client hoặc giao diện bốn metric.

## API contract dự kiến cho milestone sau

```http
GET /api/v1/dashboard/summary
```

Response contract:

```json
{
  "openTickets": 12,
  "inProgressTickets": 4,
  "slaAtRiskTickets": 2,
  "resolvedThisWeek": 7,
  "generatedAt": "2026-10-09T10:30:00+07:00"
}
```

M2 chỉ tạo DTO/port/service boundary và frontend client/UI contract. **Không đăng ký controller trả số liệu giả hoặc hard-code số liệu trong runtime.** API thật được kích hoạt ở milestone sau khi Ticket workflow có dữ liệu.

## Định nghĩa metric

- `openTickets`: Ticket không ở trạng thái terminal và không phải `RESOLVED`.
- `inProgressTickets`: status `IN_PROGRESS`.
- `slaAtRiskTickets`: Ticket active có `slaDueAt` trong 2 giờ tiếp theo hoặc đã quá hạn theo contract hiển thị đã thống nhất.
- `resolvedThisWeek`: `resolvedAt` thuộc ISO week hiện tại, hiển thị theo Asia/Bangkok.

Phạm vi dữ liệu tương lai:

- Requester: Ticket của chính mình.
- Technician: Ticket được phân công hiện tại hoặc trong lịch sử.
- Manager/Admin: toàn bộ Ticket.

## Công việc Backend Contract

- Tạo `DashboardSummaryResponse` hoặc contract tương đương.
- Tạo query port/interface để tránh Dashboard phụ thuộc trực tiếp vào controller/repository của Ticket.
- Viết contract/serialization test cho DTO.
- Ghi rõ định nghĩa metric, role scope và timezone trong OpenAPI hoặc Project Knowledge.

## Frontend/UI

- Tạo frontend API client và TanStack Query hook theo contract `GET /api/v1/dashboard/summary`.
- Hoàn thiện bốn metric card: open tickets, in-progress tickets, SLA at risk và resolved this week.
- Hiển thị `generatedAt` hoặc thời điểm cập nhật gần nhất theo timezone Asia/Bangkok khi có dữ liệu.
- Có loading/skeleton, zero/empty, error với retry và success state.
- Dùng label tiếng Việt; giữ nguyên tên field/enum tiếng Anh trong contract.
- Chuẩn bị role-aware layout cho Requester, Technician và Manager/Admin nhưng không tự giả lập authorization ở frontend.
- Dùng MSW/mock adapter trong test và development preview; mock không được trở thành dữ liệu runtime production.
- Bảo đảm layout đọc được trên desktop và tablet.

## Đạt yêu cầu khi

### Backend Contract

- Contract bốn metric được chốt và có test serialization.
- Không có endpoint production trả dữ liệu giả.
- Query boundary không phụ thuộc trực tiếp vào controller hoặc frontend.

### Frontend

- Dashboard UI render đúng bốn metric theo response contract.
- Loading, zero/empty, error/retry và success state hoạt động đúng.
- Không hard-code số metric trong component runtime.
- Frontend API client/hook có thể chuyển sang API thật mà không thay đổi component contract.

### Integration

- Backend DTO và frontend mock dùng cùng tên field và kiểu dữ liệu.
- Backend verify và frontend lint/test/build thành công.
- Code sẵn sàng để Member 5 nối query implementation thật ở milestone sau.

## Cách test

- Backend contract/serialization test.
- Frontend component test bốn metric.
- Test loading, zero values, API error/retry và success bằng MSW.
- Test role-aware presentation nhưng không khẳng định backend authorization bằng mock frontend.

## Ngoài phạm vi

- Query dữ liệu Dashboard thật.
- Notification và Audit UI.
- Chart nâng cao, export và báo cáo lịch sử.

---

## 6. Tiêu chí đóng Milestone 2

Milestone 2 được đóng khi:

1. API login phát hành JWT hợp lệ, API current-user trả đúng user/role và frontend phục hồi/xóa phiên đăng nhập đúng contract.
2. API cho phép Admin quản lý user/role và Manager/Admin cấu hình technician đúng permission matrix.
3. API cho phép Admin quản lý và publish dynamic form đúng validation/lifecycle.
4. API cho phép quản lý location/equipment, asset và lịch sử trạng thái đúng authorization.
5. API reference trả đúng published form, location và eligible asset.
6. Ticket core/supporting persistence hoạt động trên SQL Server.
7. API list/detail Ticket áp dụng đúng visibility theo role.
8. Ticket-number sequence và file-storage foundation sẵn sàng cho Create Ticket.
9. Dashboard response contract, query boundary và frontend UI shell hoàn thành; không có endpoint production trả dữ liệu giả.
10. Toàn bộ PR cần thiết đã review và merge; CI xanh vào ngày 10/10/2026.

## 7. Công việc chuyển sang Milestone sau

### Frontend Milestone 3

- M3-FE02: User/Role/Technician Administration UI.
- M3-FE03: Category/Dynamic Form Administration UI.
- M3-FE04: Ticket List/Detail UI — đã được gộp vào scope GitHub #35.
- M3-FE05: Location/Equipment Administration UI.
- M3-FE06: Asset Administration/Status History UI.
- M3-FE07: Eligible Asset Selector.

### Member 3

- `POST /api/v1/tickets` và transaction tạo Ticket.
- Dynamic-value validation khi submit.
- Evidence upload gắn với Ticket.
- Review, reject, cancel.
- Assign/reassign/unassign và eligibility/override.
- Start work, work log, work-result upload và resolve.
- Close, reopen, comment và feedback.

### Member 5

- Implementation thật cho `GET /api/v1/dashboard/summary`.
- Integration/E2E cho toàn bộ happy path.
- Notification và Audit UI nếu được đưa lại vào scope.
- Hardening về security, accessibility và performance.

Việc chuyển các phần này sang milestone sau là có chủ đích: Member 3 và Member 5 cần các module Auth, Category/Form, Location/Asset và các contract nền tảng của M2 trước khi hoàn thành luồng end-to-end.
