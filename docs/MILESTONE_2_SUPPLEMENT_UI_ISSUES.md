# Kế hoạch Milestone 2 bổ sung — hoàn thiện UI từ 09/10 đến 15/10/2026

> **Bản nháp để duyệt.** Chưa tạo/chỉnh sửa issue hoặc milestone trên GitHub.
> Chỉ mục issue đã có được đối chiếu theo hai ảnh GitHub người dùng cung cấp; xem [chỉ mục tổng hợp](MILESTONE_2_SUPPLEMENT_ISSUES.md). Không dùng mã planning thay cho số issue GitHub.

## 1. Thông tin Milestone

- **Tên:** Milestone 2 bổ sung — Hoàn thiện UI MVP.
- **Bắt đầu:** **09/10/2026**.
- **Hoàn thành:** **15/10/2026**.
- **Timezone:** Asia/Bangkok.
- **Phạm vi:** 12 issue UI; các API/tích hợp mới có thể thực hiện trong Milestone 3, lịch chưa chốt.
- **Mẫu issue:** Owner, module, loại, ngày, branch, phụ thuộc, mục tiêu, API, frontend/UI, công việc, business rule, đạt yêu cầu khi, cách test, ngoài phạm vi.
- **Phân công:** Owner đề xuất theo module; nhóm tự sắp xếp công việc.

### Cách mô tả API và phụ thuộc

- **Đã có API:** Ghi endpoint đầy đủ và tên issue trước đã triển khai/giao API; UI tái sử dụng.
- **Đã giao nhưng chưa có endpoint:** Ghi tên issue backend trước cần tiếp tục hoàn tất, không tạo task trùng. UI dùng mock đúng contract trong lúc chờ.
- **API mới chưa có:** Mô tả endpoint, đầu vào/đầu ra và tên issue backend sau sẽ triển khai. Issue workflow chưa có trên GitHub chỉ ghi tên đề xuất, không tự gán số.
- **Mục Phụ thuộc:** Dùng số issue GitHub đã đối chiếu; số của issue bổ sung vẫn là dự kiến cho đến khi tạo. Nội dung mô tả dùng tên chức năng hoặc tên issue, không dùng số làm shorthand.
- “Issue đang mở/đã đóng” trong ảnh không đồng nghĩa mọi API đều đã chạy trong nhánh local; mô tả bên dưới cũng đối chiếu code hiện tại.

### Nghiệm thu UI ngày 15/10

UI có đầy đủ controls, tương tác, validation, responsive và loading/empty/error/success. Màn có API hiện hữu dùng thật; màn chưa có API dùng MSW/mock theo contract trong development preview/test. Báo cáo rõ API thật/mock/chưa tích hợp. Không chờ backend mới để đóng phần UI, không bật mock hoặc báo lưu thành công giả trong production.

## 2. Chỉ mục issue UI bổ sung

| Số issue dự kiến | Owner | Nội dung | Bắt đầu | Hoàn thành |
|---|---|---|---|---|
| #42 | Member 1 | Hoàn thiện Login/Session và UI Admin tạo, quản lý tài khoản/role | 09/10/2026 | **15/10/2026** |
| #43 | Member 1 | Xây dựng UI quản lý Technician, Skill và Service Area | 09/10/2026 | **15/10/2026** |
| #44 | Member 2 | Hoàn thiện UI Category và Dynamic Form | 09/10/2026 | **15/10/2026** |
| #45 | Member 4 | Xây dựng UI quản trị Location và Equipment Type | 09/10/2026 | **15/10/2026** |
| #46 | Member 4 | Xây dựng UI quản trị Asset và lịch sử trạng thái | 09/10/2026 | **15/10/2026** |
| #47 | Member 4 | Xây dựng Location Selector và Eligible Asset Selector | 09/10/2026 | **15/10/2026** |
| #50 | Member 2 | Xây dựng UI Create Ticket theo published dynamic form | 09/10/2026 | **15/10/2026** |
| #52 | Member 3 | Hoàn thiện UI Manager review và assignment | 09/10/2026 | **15/10/2026** |
| #53 | Member 3 | Xây dựng My Assigned Work và UI Technician xử lý Ticket | 09/10/2026 | **15/10/2026** |
| #54 | Member 3 | Hoàn thiện Ticket Detail actions, ảnh, comments và feedback UI | 09/10/2026 | **15/10/2026** |
| #55 | Member 5 | Hoàn thiện Dashboard shell theo role | 09/10/2026 | **15/10/2026** |
| #56 | Member 5 | Tích hợp navigation, thống nhất UI và nghiệm thu UI MVP | 09/10/2026 | **15/10/2026** |

Số trên là thứ tự dự kiến kế tiếp, không xác nhận đã tồn tại trên GitHub. Scope chi tiết bên dưới dùng tên issue; số liên kết chỉ dùng trong mục Phụ thuộc.

## 3. Quy ước API và yêu cầu đồng nhất UI

### API

- Mọi endpoint được ghi đầy đủ với base path `/api/v1`; nêu rõ HTTP method, đầu vào, đầu ra và trạng thái triển khai.
- DTO/enum tiếng Anh, errors Spring ProblemDetail/RFC 9457, không entity trực tiếp/password hash.
- Page DTO `{content,page,size,totalElements,totalPages}`; page từ 0, size mặc định 20/tối đa 100. API hiện có trả array vẫn giữ array, không tự đổi thành page.
- Current business user ACTIVE; backend kiểm tra role/visibility/business preconditions.
- Optimistic lock gửi `version` nếu entity/API có; Category/Form dùng `revision` theo DTO hiện có. Không tự thêm version vào endpoint chưa hỗ trợ.
- Contract **hiện có** theo DTO/OpenAPI thật; contract **đề xuất/tương lai** được đánh dấu, chốt trước mock/client/backend implementation.
- Mật khẩu/token trong ví dụ chỉ placeholder; không log/commit credential thật hoặc đường dẫn máy cá nhân.

### UI — đồng nhất bắt buộc

| Yếu tố | Quy chuẩn từ UI hiện có |
|---|---|
| Shell | `AppLayout`: logo Nexora, navbar theo role, account menu/logout và footer; màn mới không tạo shell khác |
| Màu/chữ | Tokens `frontend/src/shared/styles/global.css`: tím `--nexa-brand`, text/border/background hiện có, font Segoe UI/system; không palette riêng |
| Page header | `Container.page-container`, `page-heading`, eyebrow/title/mô tả/icon Fi như Ticket List/Detail |
| Content | React Bootstrap Card/Row/Col/Stack; spacing/radius/shadow theo CSS hiện có |
| List | Table responsive hover, toolbar filters, count/pagination như Ticket List; URL filters/page khi API hỗ trợ |
| Form | Labels/required marker, Control/Select/Check, isInvalid/Feedback; primary action, outline secondary, tiếng Việt |
| Modal | Title/content, Hủy/Xác nhận, pending/disabled; confirmation khi đổi state/publish/rời dirty form |
| States | LoadingSpinner, EmptyState, error/retry, Alert/success; không che lỗi quyền bằng cached rows |
| Badges | TicketStatusBadge, AssetStatusBadge và mappings priority/form/user; cùng status có cùng màu/nhãn |
| Ngôn ngữ/time | Nhãn tiếng Việt; enums gửi vẫn tiếng Anh; vi-VN/Asia-Bangkok explicit; DATE thuần không đổi ngày do timezone |
| Accessibility | Label/control IDs, keyboard/focus, role alert/status; không chỉ dùng màu |
| Responsive | Desktop/tablet đầy đủ; mobile dùng được Create Ticket/Technician actions; bảng scroll không tràn page |
| Data | Feature API client + TanStack Query hooks; cache không lộ data giữa user; MSW chỉ development/test |

Mỗi issue UI có mục **Đồng nhất với UI hiện có** chỉ rõ components/pages cần reuse ngoài bảng chung.



## 4. Definition of Done UI

- Hoàn thành màn/components và các tiêu chí riêng trước hoặc trong 15/10/2026.
- Có loading/empty/error/retry/success, validation tại field, role/state presentation, responsive và keyboard access.
- Dùng API hiện có; API thiếu có client/hooks/mock cùng contract và tên task backend sẽ triển khai rõ ràng.
- Frontend lint/test/build xanh; test happy path, invalid input, permission/state/conflict UI phù hợp.
- Giữ AppLayout, palette Nexora, React Bootstrap, shared components và tiếng Việt.
- Mock không phải bằng chứng backend persistence/authorization; production không fake mutations.
- PR review theo quy ước nhóm, trạng thái API dependency ghi rõ; integration thật nghiệm thu khi backend sẵn sàng.

---

# Hoàn thiện Login/Session và UI Admin tạo, quản lý tài khoản/role

**Owner:** Member 1  
**Module:** frontend `auth`, `administration`, `app/routes`  
**Loại issue:** UI  
**Bắt đầu:** 09/10/2026  
**Hoàn thành:** 15/10/2026 — hạn hoàn thành UI  
**Branch gợi ý:** `feature/42-auth-admin-user-ui`  
**Phụ thuộc:** #27, #28, #29.

## Mục tiêu

Hoàn thiện login thật và UI để Admin tạo tài khoản, xem user, đổi trạng thái và gán role. User không tự đăng ký hoặc tự chọn role lúc đăng nhập.

## API sử dụng — đầu vào/đầu ra và nơi thực hiện

### Đăng nhập và xác định người dùng hiện tại

Đã có trong issue trước **“Xây dựng API đăng nhập và phát hành JWT”** và **“Xây dựng API Current User và kết nối phiên đăng nhập Frontend”**; tái sử dụng, không làm lại backend.

| API sử dụng | Đầu vào | Đầu ra |
|---|---|---|
| `POST /api/v1/auth/login` | `{email,password}` | 200 `{accessToken,tokenType,expiresIn,user:{id,email,fullName,roles}}` |
| `GET /api/v1/auth/me` | Bearer JWT; không có body | 200 `{id,email,fullName,status,roles}` |

### Quản trị tài khoản

Các API đã được mô tả trong issue trước **“Xây dựng chức năng quản lý User, Role và Technician”**. Chưa có endpoint trong code hiện tại; phần backend tiếp tục hoàn tất trong issue quản lý User, Role và Technician đã được giao. UI dùng mock đúng contract khi chưa có API thật; không mở nhiệm vụ backend trùng.

| API cần dùng | Đầu vào dự kiến | Đầu ra dự kiến |
|---|---|---|
| `GET /api/v1/admin/users?page=0&size=20` | page/size; filter/sort chỉ khi backend hỗ trợ | 200 page DTO chứa identity, status, roles |
| `POST /api/v1/admin/users` | `{email,fullName,password,roles}`; confirm password chỉ ở UI | 201 user DTO, mặc định ACTIVE, không password/hash |
| `GET /api/v1/admin/users/{userId}` | userId | 200 user DTO |
| `PATCH /api/v1/admin/users/{userId}/status` | `{status}`; thêm version nếu contract backend hỗ trợ | 200 user DTO mới |
| `PUT /api/v1/admin/users/{userId}/roles` | `{roles:[...]}`; thêm version nếu có | 200 user DTO với roles mới |

Đầu vào/đầu ra quản trị phải chốt cùng backend trước tích hợp. Lỗi cần hiển thị: 400 validation, 401 phiên không hợp lệ, 403 sai quyền, 404 user không tồn tại, 409 email trùng hoặc dữ liệu cũ nếu có optimistic lock.

## Frontend/UI

- **Login:** card hiện tại; email/password, hiện/ẩn password, nút Đăng nhập, required/field errors, pending và alert sai credentials/lỗi mạng.
- **Session:** loading restore, tên/roles trong account menu, logout, thông báo hết phiên và trang 403.
- **User List:** header/nút Tạo tài khoản; bảng fullName/email/roles/status/actions, result count và pagination.
- **Create User modal/form:** fullName/email/password/confirm password/roles; Hủy/Tạo, pending và success/error; xóa password khỏi state khi đóng hoặc tạo thành công.
- **User Detail:** identity card, status/role badges; modal đổi status và gán role với confirmation.
- Routes đề xuất `/administration/users`, `/administration/users/:userId`; Administration không còn placeholder.

### Đồng nhất với UI hiện có

Giữ LoginPage/LoginForm và account menu; list/table/pagination theo Ticket List, modal/form theo Category List, status badges và AppLayout/tokens chung.

## Công việc

- Hoàn thiện login email/password, validation tiếng Việt, loading, lỗi sai thông tin đăng nhập, lỗi mạng và phản hồi thành công.
- Kiểm tra login, `/auth/me`, refresh cùng tab, logout, token hết hạn và trạng thái user bị khóa/disable.
- Hoàn thiện 401/403, loading khi phục hồi phiên, navigation và các thao tác theo role do backend trả về.
- Thay placeholder Administration bằng user list có phân trang, tạo user, xem chi tiết, đổi status và gán role.
- Form tạo tài khoản gồm thông tin identity, mật khẩu ban đầu và role theo contract API quản lý User, Role và Technician; có xác nhận mật khẩu tại UI, lỗi email trùng và validation từng trường.
- Không trả hoặc lưu password hash; sau tạo thành công xóa mật khẩu khỏi form. Không bổ sung đăng ký công khai, reset password hoặc OAuth.

## Business rule

- Chỉ ADMIN tạo tài khoản, gán role và đổi trạng thái user; frontend guard không thay kiểm tra quyền backend.
- Email được normalize và unique; trạng thái chỉ ACTIVE, LOCKED, DISABLED; không xóa vật lý user.
- Roles chỉ REQUESTER, TECHNICIAN, MANAGER, ADMIN; user có nhiều role được hiển thị đúng.
- 401 xóa phiên; 403 giữ phiên hợp lệ. Không lưu/log password hoặc token trong URL/HTML.

## Đạt yêu cầu khi

- Đến **15/10/2026**, Login/Session đầy đủ controls/states trên API thật hiện có; Admin User/Role đủ màn list/create/detail/status/roles.
- Khi API User chưa sẵn sàng, admin UI được demo/test bằng mock đúng contract, ghi rõ chưa lưu backend thật.
- Client/hook sẵn sàng nối API provisioning; hành trình Admin tạo Requester rồi Requester đăng nhập thật được nghiệm thu khi backend hoàn tất.

## Cách test

- Login hợp lệ/sai thông tin/lỗi mạng; restore/logout/401/403 và trạng thái checking session.
- Create user với confirm password sai/email trùng; list/pagination/detail/status/roles và quyền Admin bằng API thật hoặc mock.
- Khi backend sẵn sàng, nghiệm thu Admin tạo Requester rồi Requester đăng nhập bằng tài khoản đó.

## Ngoài phạm vi

Đăng ký công khai, reset password, OAuth, refresh token; không viết lại API quản lý tài khoản đã giao.

---

# Xây dựng UI quản lý Technician, Skill và Service Area

**Owner:** Member 1  
**Module:** frontend `technicians`, `locations`  
**Loại issue:** UI  
**Bắt đầu:** 09/10/2026  
**Hoàn thành:** 15/10/2026 — hạn hoàn thành UI  
**Branch gợi ý:** `feature/43-technician-configuration-ui`  
**Phụ thuộc:** #29, #36, #47, #57.

## Mục tiêu

Manager/Admin cấu hình lực lượng Technician; Technician đọc hồ sơ của chính mình.

## API sử dụng — đầu vào/đầu ra và nơi thực hiện

### Profile, skill và vùng phục vụ

API đã được mô tả trong issue trước **“Xây dựng chức năng quản lý User, Role và Technician”**. Chưa có endpoint trong code hiện tại; backend sẽ tiếp tục hoàn tất trong issue **“Xây dựng chức năng quản lý User, Role và Technician”** đã được giao. Không gọi endpoint quản trị Admin bằng quyền Manager.

| API cần dùng | Đầu vào dự kiến | Đầu ra dự kiến |
|---|---|---|
| `GET /api/v1/technicians/{technicianId}` | Technician user ID | Profile identity, available, maxActiveTickets, version, skills/serviceAreas theo DTO chốt |
| `PUT /api/v1/technicians/{technicianId}` | `{available,maxActiveTickets,version}` | Profile và version mới |
| `GET /api/v1/skills?active=true` | active | Array `{id,code,name,active}` |
| `PUT /api/v1/technicians/{technicianId}/skills` | `{skills:[{skillId,proficiencyLevel}],version}` | Profile hoặc skill list cùng version mới |
| `PUT /api/v1/technicians/{technicianId}/service-areas` | `{locationIds:[...],version}` | Profile hoặc areas cùng version mới |

### Danh sách Technician

**Chưa có API này.** Sẽ thực hiện ở issue sau **“Xây dựng API danh sách Technician phục vụ UI Manager/Admin”**, dự kiến thuộc Milestone 3.

- API: `GET /api/v1/technicians?page=0&size=20&query=an&available=true`.
- Đầu vào: page, size, query theo tên/email, available tùy chọn.
- Đầu ra: page DTO; mỗi item gồm technicianId, fullName, email, status, available, maxActiveTickets, version.
- API Location phục vụ chọn Service Area đã có trong issue trước **“Xây dựng CRUD Location và Equipment Type”**: `GET /api/v1/locations?parentId={parentId}&active=true` → LocationResponse array.
- Cần xử lý 400/401/403/404/409; profile list phục vụ cấu hình, không tự coi là danh sách đủ điều kiện phân công.

## Frontend/UI

- **List:** header, query/available filters, bảng tên/email/available/capacity/actions, result count/pagination.
- **Profile:** identity card, availability switch, input maxActiveTickets, nút Lưu và feedback.
- **Skills panel:** skill select, proficiency 1–5, bảng skills đã chọn, thêm/bỏ/Lưu.
- **Service Areas panel:** location tree/selector, vùng đã chọn, bỏ chọn/Lưu và giải thích coverage bao gồm descendants.
- **Technician own-profile view:** chỉ đọc, không có edit controls.
- 409 có thông báo và tải lại; loading/empty/error/success cho mỗi nhóm dữ liệu.

### Đồng nhất với UI hiện có

List giống Ticket List; detail Cards/Row/Col như Ticket Detail; forms theo Category editor; dùng selector Location Selector và Eligible Asset Selector và AppLayout.

## Công việc

- Danh sách/chi tiết Technician; cấu hình available, maxActiveTickets, skill/proficiency và service area.
- Technician chỉ đọc hồ sơ chính mình; Manager/Admin cấu hình theo API quản lý User, Role và Technician.
- Tái sử dụng các endpoint profile/skills/service-areas của API quản lý User, Role và Technician và location reference API Location và Equipment Type.
- Hỗ trợ validation capacity > 0, proficiency 1–5, location tồn tại và stale version 409.
- Profile chỉ được tạo/cấu hình cho user có role TECHNICIAN. Contract query bổ sung phục vụ UI cấu hình, chưa khẳng định technician eligible để assign.

## Business rule

- MANAGER/ADMIN cấu hình; TECHNICIAN chỉ đọc hồ sơ chính mình.
- Profile chỉ cho user có role TECHNICIAN; maxActiveTickets > 0, proficiencyLevel từ 1 đến 5, location tồn tại.
- Danh sách profile phục vụ quản trị không phải kết quả kiểm tra eligibility cho assignment.

## Đạt yêu cầu khi

- Đến **15/10/2026**, có list/detail và ba nhóm cấu hình availability/capacity, skills, service areas; Technician own profile chỉ đọc.
- Validation, loading/empty/error/success, pagination và conflict UI hoạt động bằng API thật hoặc mock đúng contract.
- Lưu/đọc lại cấu hình qua backend sẽ nghiệm thu khi API profile/list sẵn sàng.

## Cách test

- List/query/page; đổi available/capacity; thêm/bỏ skill và service area.
- Dữ liệu sai, 403/404/409; Technician own-profile chỉ đọc; layout tablet.
- Khi API sẵn sàng, lưu và đọc lại cấu hình thật.

## Ngoài phạm vi

Tự động phân công, eligibility engine, CRUD Skill/SLA mới; không viết lại API profile đã giao.

---

# Hoàn thiện UI Category và Dynamic Form

**Owner:** Member 2  
**Module:** frontend `categories`  
**Loại issue:** UI  
**Bắt đầu:** 09/10/2026  
**Hoàn thành:** 15/10/2026 — hạn hoàn thành UI  
**Branch gợi ý:** `feature/44-category-form-ui-completion`  
**Phụ thuộc:** #30, #31, #32, #48.

## Mục tiêu

Tiếp tục từ giao diện quản trị Category và Dynamic Form đã hoàn thành: bổ sung preview đúng controls, thống nhất tiếng Việt, xử lý lỗi và trải nghiệm còn thiếu. Không dựng lại chức năng đã có.

## API sử dụng — đầu vào/đầu ra và nơi thực hiện

### Quản trị Category và Dynamic Form

Đã có trong issue trước **“Xây dựng API quản trị Category và Dynamic Form”**. Giao diện quản trị cũng đã có trong issue trước **“Xây dựng giao diện quản trị Category và Dynamic Form”**; issue hiện tại chỉ hoàn thiện preview, trải nghiệm, tiếng Việt và các trạng thái còn thiếu.

| API sử dụng | Đầu vào | Đầu ra |
|---|---|---|
| `GET /api/v1/admin/categories` | Không body | CategoryResponse array; không phải page DTO |
| `POST /api/v1/admin/categories` | `{code,name,description,assetPolicy,locationRequired,defaultPriority,active}` | 201 CategoryResponse |
| `PUT /api/v1/admin/categories/{categoryId}` | CategoryRequest theo DTO hiện có | 200 CategoryResponse |
| `GET /api/v1/admin/categories/{categoryId}/form-versions` | categoryId | VersionResponse array gồm id/versionNumber/status/revision/timestamps |
| `POST /api/v1/admin/categories/{categoryId}/form-versions/drafts` | Không body | 201 FormResponse |
| `GET /api/v1/admin/form-versions/{formVersionId}` | formVersionId | 200 FormResponse |
| `PUT /api/v1/admin/form-versions/{formVersionId}` | `{revision,requiresEquipment,minAttachments,defaultSkillId,defaultSlaPolicyId,fields}` | 200 FormResponse |
| `POST /api/v1/admin/form-versions/{formVersionId}/publish` | `{revision}` | 200 FormResponse mới |
| `POST /api/v1/admin/form-versions/{formVersionId}/archive` | `{revision}` | 200 FormResponse mới |
| `GET /api/v1/admin/skills` | Không body, quyền Admin | SkillResponse array |

API SLA đã có trong issue trước **“Xây dựng API đọc Category, Published Form và SLA”**: `GET /api/v1/sla-policies?active=true` → SLA policy array.

Field dùng fieldKey/label/fieldType/helpText/placeholder/required/active/displayOrder/validationRules/options; option dùng optionValue/optionLabel/displayOrder/active. Giữ đúng `revision` của API hiện có.

Phần đồng bộ backend quy tắc IMAGE/DATETIME chưa thuộc UI này; sẽ thực hiện ở issue sau **“Xây dựng validation dynamic values/evidence và đồng bộ contract form”**. UI và mock theo contract thống nhất trước khi tích hợp thật.

## Frontend/UI

- **Category List:** bảng tên/code/asset policy/status/actions; modal category đủ fields request; link versions.
- **Versions:** header category, nút Tạo draft, bảng version/status/ngày tạo/ngày publish và Sửa/Xem.
- **Editor:** requirements/defaults card; field list thêm/bỏ/di chuyển; editor key/label/type/required/active/help/placeholder/rules.
- **Options editor:** chỉ SELECT/MULTI_SELECT; value/label/active/order.
- **Preview:** controls đúng chín field types, required marker/help/options; panel cạnh editor hoặc tab trên tablet/mobile, không chỉ badge liệt kê type.
- **Action bar:** Lưu draft/Preview/Publish/Archive theo state; confirmation/dirty warning/field errors/409 reload.
- Labels/alerts/loading/empty/error/success tiếng Việt.

### Đồng nhất với UI hiện có

Mở rộng pages/FieldEditor/FormPreview hiện có; giữ Card/Form/Modal và tokens; chuẩn hóa header theo Ticket pages, chia sẻ dynamic renderer với UI Create Ticket nếu phù hợp.

## Công việc

- Hoàn thiện controls, ordering, preview và labels trong implementation hiện có.
- Dùng API thật cho contract hiện có; UI/mock theo contract chuẩn validator submission backend cho phần backend chưa sửa.
- Backend IMAGE/DATETIME alignment nằm ở validator submission backend, không gộp hạn API vào issue UI.
- Khi publish/read-only/conflict, feedback rõ và không làm mất draft vô tình.

## Business rule

- Chỉ ADMIN quản trị; chỉ DRAFT được sửa, PUBLISHED/ARCHIVED chỉ đọc.
- Server cấp versionNumber; API dùng revision cho optimistic lock.
- Publish form mới và archive form cũ theo contract đã có; không sửa âm thầm published form cũ.

## Đạt yêu cầu khi

- Đến **15/10/2026**, hoàn thiện UI đang có, không mở lại một implementation CRUD/form editor trùng.
- Preview đúng controls, field/option ordering, dirty warning, publish/archive/read-only và lỗi revision có kiểm thử.
- API Category/Form hiện hữu dùng thật; phần rules mới chờ backend được mô phỏng theo contract và ghi rõ.

## Cách test

- Tạo/sửa category; thêm/sửa/di chuyển field/option; preview tất cả field types.
- Published/archived read-only; lỗi publish, conflict revision và cảnh báo draft chưa lưu.
- Kiểm tra tiếng Việt, loading/empty/error/success và layout desktop/tablet.

## Ngoài phạm vi

Viết lại Category API/UI đã hoàn thành, sửa phá hủy published form, Ticket workflow và CRUD Skill/SLA mới.

---

# Xây dựng UI quản trị Location và Equipment Type

**Owner:** Member 4  
**Module:** frontend `locations`, `equipment-types`  
**Loại issue:** UI  
**Bắt đầu:** 09/10/2026  
**Hoàn thành:** 15/10/2026 — hạn hoàn thành UI  
**Branch gợi ý:** `feature/45-location-equipment-ui`  
**Phụ thuộc:** #36.

## Mục tiêu

Admin quản lý cây địa điểm và danh mục equipment type; dữ liệu dùng được cho Asset, Technician và Ticket.

## API sử dụng — đầu vào/đầu ra và nơi thực hiện

Đã có trong issue trước **“Xây dựng CRUD Location và Equipment Type”**; tái sử dụng các endpoint hiện có.

| API sử dụng | Đầu vào | Đầu ra |
|---|---|---|
| `GET /api/v1/locations?parentId={parentId}&active=true` | parentId tùy chọn; thiếu parentId lấy roots | LocationResponse array |
| `GET /api/v1/locations/{locationId}` | locationId | LocationResponse |
| `POST /api/v1/admin/locations` | `{code,name,type,parentId,description}` | 201 LocationResponse |
| `PUT /api/v1/admin/locations/{locationId}` | `{name,parentId,description,active}` | 200 LocationResponse |
| `GET /api/v1/equipment-types?active=true` | active | EquipmentTypeResponse array |
| `POST /api/v1/admin/equipment-types` | `{code,name,description}` | 201 EquipmentTypeResponse |
| `PUT /api/v1/admin/equipment-types/{equipmentTypeId}` | `{name,description,active}` | 200 EquipmentTypeResponse |

LocationResponse gồm id/code/name/type/parentId/description/active/createdAt/updatedAt; EquipmentTypeResponse tương tự, không có type/parentId. Code không sửa khi update; API này chưa dùng version. UI không gửi code hoặc version trong update.

## Frontend/UI

- **Locations:** header/nút thêm, cây lazy load bên trái, selected detail/children bên phải, breadcrumb và active filter.
- **Location form/modal:** code lúc tạo, name/type/parent/description; active switch lúc sửa, hierarchy hints/field errors.
- **Equipment Types:** table code/name/description/status/actions; create/edit modal và confirm deactivate.
- Loading khi expand, empty children/catalog, retry tại nhánh lỗi, success khi lưu; deactivate thay vì delete.
- Routes đề xuất `/administration/locations`, `/administration/equipment-types`.

### Đồng nhất với UI hiện có

Header/Card theo Ticket List, table/modal/badges theo Category List; React Bootstrap Forms và AppLayout; reference mapping dùng chung Location Selector và Eligible Asset Selector.

## Công việc

- Location tree/list tải theo parent: CAMPUS → BUILDING → FLOOR → ROOM/AREA.
- Admin tạo/sửa/deactivate location; chọn parent hợp lệ và xử lý lỗi duplicate/hierarchy/cycle từ backend.
- Equipment type list/create/edit/deactivate theo API hiện có.
- Áp dụng quyền Admin cho thao tác quản trị; catalog/selector đọc theo quyền authenticated của API.
- Có loading/empty/error/success; không tải toàn bộ cây không giới hạn.

## Business rule

- ADMIN quản trị; authenticated user đọc reference theo quyền API.
- Hierarchy CAMPUS → BUILDING → FLOOR → ROOM/AREA; không tạo cycle.
- Code unique và readonly sau tạo theo DTO hiện có; deactivate thay vì hard-delete.

## Đạt yêu cầu khi

- Đến **15/10/2026**, location tree/children/detail và equipment catalog có đầy đủ create/edit/deactivate trên API hiện có.
- Code readonly lúc update; tree tải theo parent, states/errors/hierarchy hints rõ và responsive.
- UI không gửi field/query mà API chưa hỗ trợ.

## Cách test

- Tree expand/breadcrumb/children; tạo/sửa/deactivate Location và Equipment Type.
- Duplicate code, hierarchy/cycle/parent lỗi; active filter và sai quyền.
- Keyboard access, loading tại nhánh cây, retry và layout tablet.

## Ngoài phạm vi

API xóa vật lý, hierarchy/schema/version mới; component chọn Location được làm ở nhiệm vụ selector.

---

# Xây dựng UI quản trị Asset và lịch sử trạng thái

**Owner:** Member 4  
**Module:** frontend `assets`  
**Loại issue:** UI  
**Bắt đầu:** 09/10/2026  
**Hoàn thành:** 15/10/2026 — hạn hoàn thành UI  
**Branch gợi ý:** `feature/46-asset-management-ui`  
**Phụ thuộc:** #36, #37, #47.

## Mục tiêu

Thay Asset placeholder bằng list/form/detail/status-history thật cho Manager/Admin.

## API sử dụng — đầu vào/đầu ra và nơi thực hiện

Đã có trong issue trước **“Xây dựng CRUD Asset và lịch sử trạng thái”**; location/equipment selectors dùng API của issue trước **“Xây dựng CRUD Location và Equipment Type”**.

| API sử dụng | Đầu vào | Đầu ra |
|---|---|---|
| `GET /api/v1/assets` | page/size/locationId/status/sort | Page AssetResponse |
| `GET /api/v1/assets/{assetId}` | assetId | AssetResponse |
| `POST /api/v1/assets` | `{assetCode,equipmentTypeId,locationId,serialNumber,description,purchasedAt,warrantyUntil}` | 201 AssetResponse |
| `PUT /api/v1/assets/{assetId}` | `{version,equipmentTypeId,locationId,serialNumber,description,purchasedAt,warrantyUntil}` | 200 AssetResponse |
| `POST /api/v1/assets/{assetId}/status` | `{targetStatus,reason,version}` | 200 AssetResponse với trạng thái/version mới |
| `GET /api/v1/assets/{assetId}/status-history` | assetId | History array gồm trạng thái cũ/mới, reason, actor và thời gian |
| `GET /api/v1/locations?parentId={parentId}&active=true` | parentId/active | LocationResponse array |
| `GET /api/v1/equipment-types?active=true` | active | EquipmentTypeResponse array |

AssetResponse gồm id/assetCode/serialNumber/equipmentType/location/status/description/purchasedAt/warrantyUntil/version/createdAt/updatedAt. Không có field name; assetCode không sửa, status đổi bằng action riêng. Sort allowlist: createdAt/updatedAt/assetCode/id. Chưa có list search hay equipmentType filter nên UI không tự gửi các query này.

## Frontend/UI

- **List:** header/nút Tạo, location/status/sort/size filters, table code/type/location/serial/status/actions, count/pagination; URL filters.
- **Create/Edit:** fields đúng request, location/equipment selectors, date inputs; code readonly khi edit, không đổi status trong form edit thường.
- **Detail:** metadata cards, status badge, history timeline/table với reason/actor/time.
- **Status modal:** targetStatus, reason, Hủy/Xác nhận, pending; loại status hiện tại khỏi lựa chọn.
- Duplicate/reference errors, 403/404/409 reload, empty list/history và success/refetch.

### Đồng nhất với UI hiện có

Tái sử dụng AssetStatusBadge; list/pagination như Ticket List; Cards/history như Ticket Detail; form/modal như Category editor, tokens chung.

## Công việc

- Asset list phân trang/filter/sort theo allowlist backend; create/edit/detail và status history.
- Chọn location/equipment type từ API; hiển thị code, serial, name và status.
- Modal đổi trạng thái gửi targetStatus, reason và version; reason bắt buộc, không chọn lại status hiện tại.
- Xử lý duplicate code/serial, reference không hợp lệ và 409 bằng thông báo cùng lựa chọn tải lại dữ liệu.
- Manager/Admin thực hiện thao tác theo API; thay placeholder Asset hiện tại.

## Business rule

- MANAGER/ADMIN quản lý Asset theo permission API đã có.
- Asset code/serial unique; không sửa assetCode trong update, không hard-delete.
- Status chỉ ACTIVE, UNDER_MAINTENANCE, OUT_OF_SERVICE, RETIRED; reason bắt buộc và không đổi sang status hiện tại.
- Update/status mutation gửi version; stale version 409; history phải đọc lại sau thành công.

## Đạt yêu cầu khi

- Đến **15/10/2026**, list/form/detail/history/status modal hoàn chỉnh, thay Asset placeholder.
- API hiện có tạo/sửa/đổi status và đọc lại history; version/refetch/error handling đúng contract.
- Filters/sort/pagination theo allowlist, không thêm name/search unsupported; keyboard/tablet dùng được.

## Cách test

- Create/edit/detail; location/status filters, sort/page; duplicate code/serial.
- Status modal với reason trống/status hiện tại; 403/409 và history refetch.
- Loading/empty/error/success, responsive table và keyboard.

## Ngoài phạm vi

Xóa vật lý, bulk import, thêm name/search API; API Asset hợp lệ theo Location tiếp tục ở nhiệm vụ backend đã giao.

---

# Xây dựng Location Selector và Eligible Asset Selector

**Owner:** Member 4  
**Module:** frontend `locations`, `assets`, shared selectors  
**Loại issue:** UI component  
**Bắt đầu:** 09/10/2026  
**Hoàn thành:** 15/10/2026 — hạn hoàn thành UI  
**Branch gợi ý:** `feature/47-location-eligible-asset-selectors`  
**Phụ thuộc:** #36, #38.

## Mục tiêu

Tạo controls tái sử dụng để chọn địa điểm/asset đúng policy, dùng trong Create Ticket và quản trị.

## API sử dụng — đầu vào/đầu ra và nơi thực hiện

### Chọn Location

Đã có trong issue trước **“Xây dựng CRUD Location và Equipment Type”**:

- `GET /api/v1/locations?parentId={parentId}&active=true`: đầu vào parentId/active; trả LocationResponse array, tải từng cấp.
- `GET /api/v1/locations/{locationId}`: đầu vào locationId; trả chi tiết location.
- `GET /api/v1/equipment-types?active=true`: trả equipment type array để lọc asset.

### Chọn Asset hợp lệ

API đã được mô tả trong issue trước **“Xây dựng API lấy Asset hợp lệ theo Location”**. Chưa có endpoint trong code hiện tại; backend sẽ tiếp tục thực hiện trong issue **“Xây dựng API lấy Asset hợp lệ theo Location”** đã được giao.

- API cần dùng: `GET /api/v1/locations/{locationId}/eligible-assets?page=0&size=20&equipmentTypeId={equipmentTypeId}&query={text}`.
- Đầu vào: locationId bắt buộc; page/size; equipmentTypeId/query tùy chọn.
- Đầu ra dự kiến: page DTO với item `{id,assetCode,equipmentType:{id,code,name},location:{id,code,name},status,description}`; DTO chính thức chốt với backend.
- Chỉ asset đúng location, ACTIVE/UNDER_MAINTENANCE; 404 location không tồn tại, 400 query sai/không hỗ trợ, 401 phiên không hợp lệ.
- UI dùng mock đúng contract đến khi backend hoàn tất; không thay bằng endpoint list toàn bộ asset rồi lọc tùy ý ở frontend.

## Frontend/UI

- **LocationSelector:** dependent selects campus/building/floor/room-or-area, labels/breadcrumb, clear/reset, loading/error từng cấp; node selector mode cho service area/quản trị.
- **EligibleAssetSelector:** search code/tên nếu DTO/allowlist gốc có tên; nếu không thì label code + description, equipment filter, list/select và pagination.
- Selected asset summary với code/type/status; missing-location hint, empty/no match, retry và disabled khi submit.
- Đổi location bỏ asset cũ và cấp con; REQUIRED/OPTIONAL/FORBIDDEN kết hợp requiresEquipment đúng policy.
- Controls render field errors từ Create Ticket validator; asset hết hiệu lực yêu cầu chọn lại.

### Đồng nhất với UI hiện có

Form.Select/Control/Label/Feedback, AssetStatusBadge, LoadingSpinner/EmptyState; spacing theo TicketFilters/TicketLocationFilter; nhúng trong Card/Form của page đang dùng.

## Công việc

- Component chọn campus/building/floor/room/area tái sử dụng cho Ticket và các màn quản trị.
- Eligible Asset Selector dùng `GET /api/v1/locations/{locationId}/eligible-assets` của API Asset hợp lệ theo Location.
- Hỗ trợ search, equipment type filter và pagination đúng contract API.
- Khi đổi location phải bỏ asset cũ; asset chỉ thuộc đúng location, status ACTIVE/UNDER_MAINTENANCE.
- REQUIRED/OPTIONAL/FORBIDDEN kết hợp requiresEquipment theo quy tắc đã duyệt.
- Loading/empty/error, không có asset phù hợp và reference vừa mất hiệu lực có thông báo rõ.

## Business rule

- Asset phải thuộc đúng Location và có status ACTIVE hoặc UNDER_MAINTENANCE; không lấy asset cha/con.
- REQUIRED/OPTIONAL/FORBIDDEN kết hợp requiresEquipment; đổi Location phải bỏ asset cũ.
- UI kiểm tra selection để hỗ trợ nhập liệu; API tạo Ticket vẫn kiểm tra lại eligibility khi submit.

## Đạt yêu cầu khi

- Đến **15/10/2026**, location/asset selectors đủ controls, cascade resets, required/policy/states.
- Location dùng API hiện có; Eligible Asset dùng thật nếu sẵn sàng hoặc mock đúng contract đã giao.
- Khi đổi location/parent không giữ selection sai; lựa chọn asset vẫn phải được backend kiểm tra lại lúc tạo Ticket.

## Cách test

- Cascade reset khi đổi parent/location; required/optional/forbidden asset policy.
- Hai Location và đủ bốn asset statuses; search/equipment filter/page, selected asset mất hiệu lực.
- Field errors, loading/no-match/retry, keyboard và nhúng selector trong các form.

## Ngoài phạm vi

Viết lại Eligible Asset API, lấy asset theo ancestor/descendant và service tạo Ticket.

---

# Xây dựng UI Create Ticket theo published dynamic form

**Owner:** Member 2  
**Module:** frontend `tickets`, `categories`, `locations`, `assets`  
**Loại issue:** UI  
**Bắt đầu:** 09/10/2026  
**Hoàn thành:** 15/10/2026 — hạn hoàn thành UI  
**Branch gợi ý:** `feature/50-create-ticket-ui`  
**Phụ thuộc:** #28, #30, #35, #44, #47, #48, #49.

## Mục tiêu

Requester điền published form, chọn location/asset và ảnh để gửi Ticket; UI đầy đủ ngày 15 bằng contract/mock, API thật nối sau API tạo Ticket.

## API sử dụng — đầu vào/đầu ra và nơi thực hiện

### Dữ liệu để dựng form

Đã có trong issue trước **“Xây dựng API đọc Category, Published Form và SLA”** và **“Xây dựng CRUD Location và Equipment Type”**:

| API sử dụng | Đầu vào | Đầu ra |
|---|---|---|
| `GET /api/v1/categories?active=true` | active | CategoryResponse array |
| `GET /api/v1/categories/{categoryId}/published-form` | categoryId | FormResponse với version/fields/options/policy/defaults |
| `GET /api/v1/locations?parentId={parentId}&active=true` | parentId/active | LocationResponse array |

`GET /api/v1/locations/{locationId}/eligible-assets` đã có kế hoạch trong issue trước **“Xây dựng API lấy Asset hợp lệ theo Location”**, nhưng endpoint chưa có trong code hiện tại; backend tiếp tục hoàn tất ở issue đó. Đầu vào/đầu ra theo contract selector đã mô tả.

### Gửi Ticket mới

**Chưa có API này.** Sẽ thực hiện ở issue sau **“Xây dựng API tạo Ticket với sequence, SLA và ảnh minh chứng”**, dự kiến thuộc Milestone 3. UI chuẩn bị API client và mock multipart đúng contract.

- API: `POST /api/v1/tickets`.
- Đầu vào: multipart gồm part `ticket` (JSON) và part `files` (JPEG/PNG nếu có).
- JSON: `{categoryId,formVersionId,locationId,assetId?,title,description,dynamicValues:[{fieldKey,value}],attachments:[{fileIndex,fieldKey?}]}`.
- Đầu ra 201: `{id,ticketNumber,status:"SUBMITTED",version}`, header Location tới chi tiết Ticket.
- Client không gửi reporterId, ticketNumber, status, priority, SLA hoặc storageKey; backend quyết định.
- Lỗi cần dựng UI: 400 fieldErrors; 401/403 quyền; 404 reference; 409 form đổi version; 413 quá dung lượng; 415 media type sai.
- Validation backend sẽ thực hiện ở issue sau **“Xây dựng validation dynamic values/evidence và đồng bộ contract form”**. Không coi submit mock là Ticket đã được lưu thật.

## Frontend/UI

- **Route `/tickets/new`:** header/back link, sections/cards theo trình tự Thông tin sự cố → Địa điểm/tài sản → Nội dung biểu mẫu → Ảnh minh chứng.
- **Thông tin:** category select, title, description, published version summary và hướng dẫn.
- **Location/Asset:** selectors Location Selector và Eligible Asset Selector, required/optional/forbidden hints.
- **Dynamic fields:** TEXT input; TEXTAREA; NUMBER; SELECT; MULTI_SELECT; DATE; DATETIME; BOOLEAN; IMAGE; active/order/labels/help/validation rules.
- **Ảnh:** file picker, thumbnail, filename/size, bỏ ảnh, count/limits và field-linked photos; accessible validation.
- **Action bar:** Hủy/Quay lại và Gửi phiếu, pending/disabled, dirty warning; error summary + field errors; form conflict có tải lại giữ input tương thích.
- **Success:** ticketNumber và chuyển Detail/invalidate List; mock preview chuyển tới fixture detail nhất quán, không trộn mock mutation vào runtime thật.

### Đồng nhất với UI hiện có

Header/summary/form Cards theo Ticket Detail, back-link hiện có; React Bootstrap Form như Category editor, shared dynamic renderer và selectors; TicketStatusBadge/priority labels, tokens/AppLayout.

## Công việc

- Route Create Ticket cho REQUESTER; nút tạo từ Ticket List và navigation phù hợp.
- Chọn active category và tải current published form; chọn location/eligible asset qua Location Selector và Eligible Asset Selector.
- Nhập title/description; render chín field type theo backend contract, active fields/options và displayOrder.
- DATE/DATETIME serialize đúng contract; BOOLEAN/NUMBER/MULTI_SELECT không mất giá trị hợp lệ.
- Chọn/preview/xóa ảnh trước submit; ánh xạ ảnh minh chứng chung và ảnh liên kết field IMAGE.
- Gửi multipart theo API tạo Ticket, hiển thị validation tại field, lỗi file, lỗi mạng và form version conflict.
- Chống double-submit; khi lỗi giữ input còn phù hợp, khi đổi category reset dữ liệu phụ thuộc có xác nhận nếu đã nhập.
- Tạo thành công hiển thị ticket number, điều hướng detail thật và invalidate list/query liên quan.

## Business rule

- Chỉ REQUESTER có action tạo; UI lấy current published form, không lấy draft.
- BOOLEAN false, NUMBER 0, dates/options và IMAGE giữ đúng contract; IMAGE không scalar value.
- Đổi category/location reset dữ liệu phụ thuộc có cảnh báo khi mất input; chống double-submit.
- Client không gửi reporter/roles/status/priority/SLA/storageKey do server quyết định.

## Đạt yêu cầu khi

- Đến **15/10/2026**, Create Ticket UI có đầy đủ published form, location/asset, chín field types, preview ảnh và submit/error/success flows.
- Nếu API tạo chưa có, multipart payload và các lỗi được kiểm thử bằng mock; không báo đã lưu SQL Server trong production.
- Client/hook có thể nối API thật mà không thiết kế lại màn hình; hành trình lưu/đọc ảnh thật nghiệm thu sau.

## Cách test

- Chín field types, đổi category/form, asset policy và location reset.
- Ảnh add/preview/remove/limits; payload multipart, fieldErrors, form conflict, lỗi mạng và pending.
- Success routing/fixture detail, giữ input khi lỗi và mobile submission; lưu/đọc thật nghiệm thu khi API có.

## Ngoài phạm vi

Draft Ticket lưu server, workflow, WORK_RESULT upload và implementation API tạo Ticket.

---

# Hoàn thiện UI Manager review và assignment

**Owner:** Member 3  
**Module:** frontend `tickets`, contract workflow  
**Loại issue:** UI + contract/mock  
**Bắt đầu:** 09/10/2026  
**Hoàn thành:** 15/10/2026 — hạn hoàn thành UI  
**Branch gợi ý:** `feature/52-manager-ticket-workflow-ui`  
**Phụ thuộc:** #29, #35, #43.

## Mục tiêu

Manager/Admin có action panel/form đầy đủ để demo review/reject/assign/reassign/unassign theo contract, không chờ API workflow.

## API sử dụng — đầu vào/đầu ra và nơi thực hiện

### Dữ liệu đọc hiện có

Đã có trong issue trước **“Xây dựng API đọc danh sách và chi tiết Ticket”**:

- `GET /api/v1/tickets`: page/size và các filter/sort được hỗ trợ → page TicketSummaryResponse.
- `GET /api/v1/tickets/{ticketId}`: ticketId → TicketDetailResponse, gồm trạng thái, version và history.

### Review và assignment

**Các API dưới đây chưa có.** Mô tả đầu vào/đầu ra để dựng UI/mock; sẽ thực hiện ở các issue backend sau **“API review, reject và cancel Ticket”** và **“API phân công, đổi và gỡ phân công Technician”**. Các issue backend workflow này chưa có số được xác nhận trong ảnh, không tự gán số.

| API dự kiến | Đầu vào dự kiến | Đầu ra dự kiến |
|---|---|---|
| `POST /api/v1/tickets/{ticketId}/review` | `{version}` | `{id,status,version}` sau action |
| `POST /api/v1/tickets/{ticketId}/reject` | `{version,reason}` | Action result |
| `POST /api/v1/tickets/{ticketId}/cancel` | `{version,reason}` | Action result |
| `GET /api/v1/tickets/{ticketId}/eligible-technicians` | page/size/query theo contract chốt | Page candidate DTO với identity, availability, capacity, eligibility reasons |
| `POST /api/v1/tickets/{ticketId}/assign` | `{version,technicianId,overrideReason?}` | Action result và assignment DTO |
| `POST /api/v1/tickets/{ticketId}/reassign` | `{version,technicianId,reason,overrideReason?}` | Action result và assignment mới |
| `POST /api/v1/tickets/{ticketId}/unassign` | `{version,reason}` | Action result |

Dựng các lỗi 400/403/404/409, mock cập nhật status/history/assignment nhất quán. API danh sách Technician phục vụ quản trị không thay thế API kiểm tra eligibility cho assignment.

## Frontend/UI

- Action panel trên Detail: chỉ actions role/state cho phép; current status/assignee context.
- Review action confirmation; Reject/Cancel modal có reason.
- Assignment dialog: search/filter candidates, bảng identity/availability/current workload/capacity và mismatch reasons, chọn technician.
- Override section: reason bắt buộc khi mismatch có thể override; không cho override thiếu role/profile hoặc inactive user.
- Reassign/Unassign có reason và confirmation; pending/success/error/409 reload.
- Panel không làm lỗi đọc Detail thật khi future query/action chưa khả dụng; production có thông báo chưa hỗ trợ.

### Đồng nhất với UI hiện có

Gắn vào TicketDetailPage existing Cards/badges/history, Form/Modal giống Category publish; candidate table theo Ticket List; không tạo second Detail layout.

## Công việc

- Bổ sung action panel trong Ticket Detail cho Manager/Admin theo role và trạng thái.
- Form/modal begin review, reject, cancel, assign, reassign, unassign; reason bắt buộc theo transition matrix.
- UI tìm/chọn technician, hiển thị eligibility/capacity/skills/service area và override reason theo contract dự kiến.
- Phân biệt thông tin profile phục vụ UI cấu hình với kết quả eligibility phục vụ assignment; hai API có mục đích khác nhau.
- Chốt DTO/query/action contracts cho backend milestone sau; mutations gửi expected version.
- Development/test mock trả success/400/403/404/409 và cập nhật fixture/history nhất quán sau action.
- Không triển khai workflow API thật trong issue này; production chưa có API phải thể hiện action chưa khả dụng.

## Business rule

- MANAGER/ADMIN thao tác đúng transition matrix; không generic status patch.
- Mismatch availability/capacity/skill/coverage có thể override với reason theo contract; thiếu role/profile hoặc user inactive không bypass.
- UI kiểm thử role/state presentation bằng mock không xác nhận authorization/concurrency backend.

## Đạt yêu cầu khi

- Đến **15/10/2026**, Manager/Admin action panel, review/reject/cancel và assignment dialogs đủ controls/validation/states theo role/state.
- Preview/mock cập nhật fixture status/history/assignment nhất quán, có lỗi quyền/version/capacity.
- Backend workflow chưa có được ghi rõ; production không giả lập mutation thành công.

## Cách test

- Actions theo role/state; Review/Reject/Cancel reason và Assignment/Reassign/Unassign dialogs.
- Candidate search/selection, mismatch/override validation, 400/403/404/409 và reload.
- Mock status/history/assignment nhất quán; detail thật không hỏng khi workflow API chưa có.

## Ngoài phạm vi

Backend workflow/eligibility/concurrency engine và tự động chọn Technician.

---

# Xây dựng My Assigned Work và UI Technician xử lý Ticket

**Owner:** Member 3  
**Module:** frontend `tickets`, `technician-work`  
**Loại issue:** UI + contract/mock  
**Bắt đầu:** 09/10/2026  
**Hoàn thành:** 15/10/2026 — hạn hoàn thành UI  
**Branch gợi ý:** `feature/53-technician-assigned-work-ui`  
**Phụ thuộc:** #35, #50.

## Mục tiêu

Technician có màn công việc được giao và controls start/work-log/evidence/resolve hoàn chỉnh trong preview/test ngày 15.

## API sử dụng — đầu vào/đầu ra và nơi thực hiện

### Ticket đọc được

Đã có trong issue trước **“Xây dựng API đọc danh sách và chi tiết Ticket”**: `GET /api/v1/tickets` → page summary; `GET /api/v1/tickets/{ticketId}` → detail. Visibility Technician gồm assignment hiện tại và lịch sử; không đủ để xác định riêng công việc đang được giao.

### Công việc hiện tại và xử lý Ticket

**Các API này chưa có.** UI/mock dùng contract dự kiến; sẽ thực hiện ở các issue backend sau **“API công việc hiện tại của Technician”**, **“API bắt đầu xử lý và Work Log”**, **“API ảnh kết quả và resolve Ticket”**. Chưa gán số cho các issue chưa xuất hiện trong ảnh.

| API dự kiến | Đầu vào dự kiến | Đầu ra dự kiến |
|---|---|---|
| `GET /api/v1/technicians/me/assigned-tickets` | page/size và filter theo allowlist chốt | Page summary + current assignment |
| `POST /api/v1/tickets/{ticketId}/start` | `{version}` | `{id,status,version}` và assignment result |
| `GET /api/v1/tickets/{ticketId}/work-logs` | ticketId, phân trang nếu contract có | Ordered WorkLog DTO list/page |
| `POST /api/v1/tickets/{ticketId}/work-logs` | `{version,logType,content,startedAt,endedAt,minutesSpent}` | 201 WorkLog DTO và version mới nếu aggregate contract yêu cầu |
| `POST /api/v1/tickets/{ticketId}/attachments` | Multipart metadata `{attachmentType:"WORK_RESULT"}` + files | Attachment metadata |
| `POST /api/v1/tickets/{ticketId}/resolve` | `{version,resolutionOutcome,resolutionSummary}` | Action result với status RESOLVED |

Chốt DTO/version và lỗi 400/403/404/409 trước tích hợp thật. Không báo work log, ảnh kết quả hoặc resolve đã lưu server khi chỉ chạy mock.

## Frontend/UI

- **`/my-work`:** header, current assignment filter/status/priority/sort theo future query allowlist; list/table hoặc mobile cards; SLA indicator/count/pagination.
- **Work detail:** tái sử dụng Ticket Detail, current assignment card, nút Bắt đầu xử lý.
- **Work Logs:** ordered list/timeline với type/content/time/minutes; add log form/modal với required/validation.
- **Work Result images:** picker/preview/remove/limits và upload pending/error theo future contract.
- **Resolve modal:** outcome dropdown FIXED/TEMPORARY_FIX/NO_FAULT_FOUND/UNRESOLVED, summary textarea, điều kiện tối thiểu một work log và confirmation.
- Mất assignment/409 có feedback và reload; production missing APIs hiển thị chưa khả dụng.

### Đồng nhất với UI hiện có

Ticket List filters/table/URL convention; Ticket Detail Cards/StatusBadge/time/history; form/modal/ảnh pattern dùng chung UI Create Ticket/UI Detail/actions/ảnh/comments/feedback, AppLayout nav có My Work theo role.

## Công việc

- Route My Assigned Work cho Technician; hiển thị assignment hiện tại, status, priority, location và SLA.
- Tái sử dụng list/detail components hiện có. API read API danh sách/chi tiết Ticket cho thấy current/historical visibility, không mặc nhiên thay thế query current assignment; chốt query contract còn thiếu và dùng mock có phân biệt current/history.
- UI start work, thêm/xem work log, thời gian thực hiện, ảnh kết quả và resolve với outcome/summary.
- Resolve UI yêu cầu ít nhất một work log và các điều kiện đã duyệt; không hiển thị outcome DUPLICATE của P1.
- Chốt DTO/query/action contracts; mock các trạng thái ASSIGNED/IN_PROGRESS/RESOLVED, stale version và mất assignment.
- Production chưa có query/action API tương ứng thể hiện trạng thái chưa khả dụng.

## Business rule

- Chỉ current assigned TECHNICIAN thao tác Start/Work Log/Resolve đúng state; historical visibility không cho quyền làm việc.
- Resolve có outcome/summary và tối thiểu một work log; không expose DUPLICATE của P1.
- Work log end không trước start, minutesSpent > 0 khi gửi; dữ liệu/ảnh theo contract.
- Các API chưa có dùng mock development/test; production không báo đã lưu work/ảnh/resolve thật.

## Đạt yêu cầu khi

- Đến **15/10/2026**, có My Assigned Work, current assignment view, Start, Work Log, ảnh kết quả và Resolve UI.
- Preview phân biệt current/historical assignment, xử lý missing assignment/version và validation work-log/resolve.
- Actions và dữ liệu work chưa có API dùng mock trong development/test; không tuyên bố đã lưu server thật.

## Cách test

- Current vs historical assignment; actions ở ASSIGNED/IN_PROGRESS/RESOLVED.
- Work-log/time validation, ảnh pending/error; resolve thiếu work log/summary, mất assignment và 409.
- List/page, mobile work controls, keyboard và trạng thái API chưa khả dụng.

## Ngoài phạm vi

API current assignment, Work Log, WORK_RESULT, Resolve và assignment engine thật.

---

# Hoàn thiện Ticket Detail actions, ảnh, comments và feedback UI

**Owner:** Member 3  
**Module:** frontend `tickets`  
**Loại issue:** UI + contract/mock  
**Bắt đầu:** 09/10/2026  
**Hoàn thành:** 15/10/2026 — hạn hoàn thành UI  
**Branch gợi ý:** `feature/54-ticket-detail-collaboration-ui`  
**Phụ thuộc:** #35, #50, #51, #52, #53.

## Mục tiêu

Hoàn thiện Detail hiện có với preview/tải ảnh, actions theo role/state, comments và feedback; giữ phần đọc thật hoạt động khi subresource API chưa có.

## API sử dụng — đầu vào/đầu ra và nơi thực hiện

### Chi tiết Ticket hiện có

Đã có trong issue trước **“Xây dựng API đọc danh sách và chi tiết Ticket”**: `GET /api/v1/tickets/{ticketId}` → TicketDetailResponse gồm summary, description, stored form, dynamic values, history và attachment metadata.

### Xem/tải ảnh

**Chưa có API đọc nội dung ảnh.** Sẽ thực hiện ở issue sau **“Xây dựng API đọc attachment có phân quyền”**:

- `GET /api/v1/tickets/{ticketId}/attachments/{attachmentId}/content`: ticketId/attachmentId + Bearer JWT → binary JPEG/PNG với Content-Type, Content-Length, Content-Disposition.
- UI lấy blob bằng HTTP client có Authorization; preview/tải bằng object URL, không gắn JWT vào URL ảnh.

### Actions, comments và feedback

**Các API này chưa có.** Sẽ thực hiện ở các issue backend sau **“API close, reopen và cancel Ticket”**, **“API comments Ticket”** và **“API feedback Ticket”**; chưa gán số khi chưa có issue xác nhận.

| API dự kiến | Đầu vào dự kiến | Đầu ra dự kiến |
|---|---|---|
| `POST /api/v1/tickets/{ticketId}/cancel` | `{version,reason}` | Updated action result |
| `POST /api/v1/tickets/{ticketId}/close` | `{version,reason?}`; Manager/Admin force-close bắt buộc reason | Updated action result |
| `POST /api/v1/tickets/{ticketId}/reopen` | `{version,reason}` | Updated action result |
| `GET /api/v1/tickets/{ticketId}/comments` | ticketId/page theo contract chốt | Visible comment list/page |
| `POST /api/v1/tickets/{ticketId}/comments` | `{content,visibility}` | 201 Comment DTO |
| `PUT /api/v1/tickets/{ticketId}/comments/{commentId}` | `{content}` + version nếu contract có | Updated Comment DTO |
| `GET /api/v1/tickets/{ticketId}/feedback` | ticketId | Feedback DTO hoặc trạng thái chưa có feedback theo contract chốt |
| `POST /api/v1/tickets/{ticketId}/feedback` | `{rating,comment?}` | 201 Feedback DTO |

Comment DTO gồm id/author/content/visibility/createdAt/editedAt; feedback gồm id/rating/comment/createdAt. Không có delete comment hoặc sửa/xóa feedback trong MVP. Mock không trả INTERNAL comments cho Requester; errors/unavailable subresource không làm hỏng detail đọc thật.

## Frontend/UI

- Giữ summary/description/stored form/dynamic values/history đang có; cùng back-link trở về list filters.
- **Attachment section:** thumbnails/cards với filename/type/size/uploader/time; mở preview modal, nút Tải xuống, loading/missing-file/error/retry.
- Fetch binary bằng Authorization client; tạo object URL, revoke khi đóng/unmount; không token trong img URL.
- **Requester actions:** Cancel trước assignment, Close/Reopen RESOLVED; confirmation/reason/version conflict theo rule. Manager/Admin actions dùng chung panel action panel Manager, Technician UI công việc của Technician.
- **Comments:** list/timeline author/time/visibility badge, composer textarea, PUBLIC/INTERNAL selector theo role, edit-own control và loading/empty/error/success.
- **Feedback:** rating 1–5 keyboard accessible, optional comment, Gửi; existing feedback read-only, chỉ reporter CLOSED.
- Failed/unavailable comments/feedback/ảnh không làm hỏng detail đọc thật. Preview dùng consistent fixtures cho CLOSED/RESOLVED/history.

### Đồng nhất với UI hiện có

Mở rộng TicketDetailPage/TicketDetailSections và badges hiện có, giữ grid/cards/history; Modal/Form/Alert pattern thống nhất UI Category/Form hoàn thiện/UI Create Ticket, shared action panel không trùng controls.

## Công việc

- Giữ phần detail đọc thật, bổ sung action theo role/state: Requester cancel trước assignment, close/reopen từ RESOLVED; Manager/Admin cancel/force close/reopen theo matrix.
- Dùng chung action panel/contracts với action panel Manager/UI công việc của Technician, không tạo hai bộ controls cùng chức năng.
- Comments PUBLIC/INTERNAL và edit-own theo quyền/state; Requester chỉ public. Không có comment delete hoặc comment attachment trong MVP.
- Feedback form 1–5 và optional comment cho reporter khi CLOSED; chỉ một feedback, không update/delete.
- Chốt DTO/query contracts cho comments/feedback cùng visibility; MSW/mock cho pending/success/field errors/conflict và dữ liệu rỗng.
- Tách lỗi phần comments/feedback chưa có API để không làm hỏng detail đọc thật.
- Production chưa có backend tương ứng thể hiện chưa khả dụng; không fake comment/feedback submission.

## Business rule

- Ảnh theo Ticket visibility; JWT chỉ trong Authorization header.
- Requester cancel trước assignment, close/reopen RESOLVED; Manager/Admin force-close cần reason theo matrix.
- Comments PUBLIC/INTERNAL theo role; author edit trên Ticket nonterminal; không xóa comment/comment attachment.
- Feedback chỉ reporter khi CLOSED, rating 1–5, một lần và không sửa/xóa trong MVP.

## Đạt yêu cầu khi

- Đến **15/10/2026**, phần Detail đọc hiện có vẫn hoạt động; bổ sung ảnh, actions, comments và feedback UI đủ states.
- Preview/test có binary image/object URL lifecycle, role/state action matrix, comment visibility/edit và feedback rules.
- Lỗi/missing API của từng subresource không làm hỏng Detail; tích hợp API thật được nghiệm thu riêng.

## Cách test

- Binary image preview/download, object URL revoke, missing file/permission errors.
- Action role/state/reason/409; comments visibility/edit-own và feedback rating/one-only.
- Lỗi/unavailable subresource không phá Detail thật; loading/empty/error/success và responsive.

## Ngoài phạm vi

Backend workflow/comments/feedback thật, COMMENT attachments và dựng một Detail page trùng.

---

# Hoàn thiện Dashboard shell theo role

**Owner:** Member 5  
**Module:** frontend `dashboard`, backend DTO contract hiện có  
**Loại issue:** UI + contract/mock  
**Bắt đầu:** 09/10/2026  
**Hoàn thành:** 15/10/2026 — hạn hoàn thành UI  
**Branch gợi ý:** `feature/55-dashboard-shell-ui`  
**Phụ thuộc:** #41.

## Mục tiêu

Dashboard đầy đủ bốn metric và state presentation theo contract, đồng nhất ngôn ngữ/layout.

## API sử dụng — đầu vào/đầu ra và nơi thực hiện

**Đã có contract, API client và UI shell** trong issue trước **“Xây dựng Contract và giao diện khung Dashboard”**. **Chưa có endpoint/query dữ liệu thật.** API sẽ thực hiện ở issue backend sau **“API Dashboard summary theo role”**; chưa gán số khi issue chưa được tạo.

- API dự kiến: `GET /api/v1/dashboard/summary`.
- Đầu vào: Bearer JWT, không body; phạm vi dữ liệu backend xác định theo current user/role.
- Đầu ra 200:
```json
{
  "openTickets": 12,
  "inProgressTickets": 4,
  "slaAtRiskTickets": 2,
  "resolvedThisWeek": 7,
  "generatedAt": "2026-10-15T10:30:00+07:00"
}
```
- Metrics là số nguyên không âm; generatedAt là ISO timestamp. Định nghĩa metrics giữ contract Dashboard đã thống nhất.
- Preview/test dùng MSW cho loading, zero, success, error/retry. Production không đăng ký controller trả số liệu giả.

## Frontend/UI

- Header Tổng quan, scope text Requester own/Technician assigned-history/Manager-Admin all.
- Four cards: Phiếu đang mở, Đang xử lý, Có nguy cơ trễ SLA, Đã giải quyết tuần này; icon/accent hiện có.
- Skeleton/loading; zero/empty hint; error alert + Thử lại; success values.
- Thời điểm cập nhật format vi-VN với timezone Asia/Bangkok explicit.
- Desktop 4 cards; tablet/mobile grid dễ đọc; development preview scenarios theo MSW, runtime chưa có API thể hiện unavailable/error rõ.

### Đồng nhất với UI hiện có

Giữ DashboardPage/MetricCard/useDashboardSummary/dashboardApi; primary label/heading/spacing/tokens cùng Ticket pages, icon Fi và AppLayout.

## Công việc

- Hoàn thiện bốn metric card theo contract Dashboard đã có, label tiếng Việt và giải thích phạm vi dữ liệu của Requester/Technician/Manager/Admin.
- Hiển thị generatedAt bằng timezone Asia/Bangkok rõ ràng, không phụ thuộc timezone thiết bị.
- Hoàn thiện loading/skeleton, zero/empty, error/retry và success; responsive desktop/tablet.
- Development preview và test dùng MSW/mock theo contract; production giữ client thật, hiển thị trạng thái chưa có dữ liệu khi API chưa triển khai.
- Tài liệu hóa metric và role scope nhất quán với contract và Dashboard shell trước khi query thật được triển khai.

## Business rule

- Bốn metrics/field names theo Dashboard contract đã thống nhất.
- Role scope ở UI chỉ là presentation; authorization backend vẫn phải triển khai khi có query thật.
- Mock chỉ development/test; không hard-code success metrics trong production.

## Đạt yêu cầu khi

- Đến **15/10/2026**, bốn metric cards, role scope, timestamp và loading/zero/error/retry/success hoàn chỉnh.
- Labels tiếng Việt, thời gian Asia/Bangkok và layout đồng nhất với UI hiện tại.
- Success metrics lấy từ contract/mock preview/test; không hard-code dữ liệu giả trong production.

## Cách test

- Bốn cards với loading/zero/success/error/retry, role scope labels.
- generatedAt format vi-VN/Asia-Bangkok khi thiết bị ở timezone khác.
- Desktop/tablet/mobile layout và production chưa có API không trả số liệu giả.

## Ngoài phạm vi

Dashboard query/controller thật, charts/export/advanced reporting và notification/audit UI.

---

# Tích hợp navigation, thống nhất UI và nghiệm thu UI MVP

**Owner:** Member 5  
**Module:** frontend `app`, `shared`, toàn bộ feature UI  
**Loại issue:** UI/QA  
**Bắt đầu:** 09/10/2026  
**Hoàn thành:** 15/10/2026 — hạn hoàn thành UI  
**Branch gợi ý:** `feature/56-ui-navigation-quality`  
**Phụ thuộc:** #42, #43, #44, #45, #46, #47, #50, #52, #53, #54, #55.

## Mục tiêu

Tất cả pages/components thuộc bổ sung được truy cập theo role, đồng nhất và demo UI được ngày 15.

## API sử dụng — đầu vào/đầu ra và nơi thực hiện

Không tạo API mới. Rà soát các API client/hooks đã mô tả ở từng màn: sử dụng API hiện có, hoặc mock đúng request/response của API sẽ làm sau.

- Auth: `POST /api/v1/auth/login`, `GET /api/v1/auth/me` — đã có ở các issue Auth trước.
- Catalog/Asset/Category/Ticket read — đã có ở các issue backend trước với tên và endpoint ghi ở từng màn.
- User/Technician/Eligible Asset chưa có endpoint thì theo kế hoạch backend đã giao.
- Create Ticket, attachment content và workflow/comments/feedback/Dashboard thật — thực hiện ở các issue backend sau theo mô tả API của từng UI.

Nghiệm thu UI không xác nhận mutation/persistence/authorization backend của API chưa triển khai.

## Frontend/UI

- Navigation theo REQUESTER/TECHNICIAN/MANAGER/ADMIN và user nhiều role; link Administration/Users, Technicians, Locations/Equipment, Assets, Categories, Create Ticket/My Work đúng quyền.
- Active nav, back-link/breadcrumb, account/logout, session checking; 403/404/error pages thống nhất, không redirect mọi lỗi khiến người dùng mất ngữ cảnh.
- Đồng nhất titles/labels/button text/forms/tables/badges/date display; dùng cùng tokens và shared loading/empty/error/success.
- Review desktop/tablet/mobile, keyboard/tab/focus, form errors và ảnh preview.
- Preview routes/scenarios cho future workflows; ghi rõ mock, không mở mock trong production.

### Đồng nhất với UI hiện có

Issue nghiệm thu toàn bộ quy chuẩn UI chung; không thay palette/shell/UI framework hiện tại.

## Công việc

- Rà từng màn/components theo checklist UI chung và các acceptance của issue.
- Chạy frontend lint/test/build; kiểm tra production build/mock guard.
- Demo UI real where available và contract/mock where pending; lưu bảng coverage và trạng thái API dependency.
- Chuyển backend verify/SQL Server/end-to-end thật sau API sang nghiệm thu tích hợp thật, không kéo hạn UI qua ngày 15.
- Hướng dẫn bật preview và phân biệt dữ liệu thật/mock bằng tài liệu dễ hiểu.

## Business rule

- Navigation cho user nhiều role là hợp các quyền; từng action vẫn theo business preconditions.
- Ẩn controls không xác nhận backend quyền; mock phải có explicit development/test gate.
- Không fake production mutation; coverage luôn ghi API thật/mock/pending.

## Đạt yêu cầu khi

- Đến **15/10/2026**, navigation/guards, labels, badges, forms/states và responsive giữa các màn đồng nhất.
- Có bảng coverage cho từng màn/hành trình ghi API thật, mock hoặc pending; frontend lint/test/build xanh.
- Không cần chờ API backend sau để đóng phần UI; mock chỉ development/test, integration thật nghiệm thu riêng.

## Cách test

- Routes/roles/restore/logout/401/403/404; labels/badges/forms/states giữa các màn.
- Desktop/tablet/mobile, keyboard/focus, loading/error/retry/empty.
- Frontend lint/test/build và kiểm tra production build không bật mock.

## Ngoài phạm vi

Business API mới và nghiệm thu thật của backend chưa sẵn sàng.

---

## 5. Phụ thuộc và thứ tự triển khai UI

- Login/Admin User trước phần cấu hình Technician.
- UI Category/Form và selectors cung cấp controls cho UI Create Ticket.
- Ticket List/Detail hiện có làm nền cho action panel Manager, công việc Technician, ảnh/comments/feedback.
- Dashboard tiếp tục từ contract và shell đã có.
- Cuối cùng nghiệm thu navigation, UX, trạng thái và responsive của toàn bộ màn.

**Phụ thuộc backend mới:** #48, #49, #51, #57, #58.

Những dependency này là phần backend hoặc tích hợp thật trong [kế hoạch ứng viên Milestone 3](MILESTONE_3_CANDIDATE_API_ISSUES.md), không phải điều kiện hoàn thành UI ngày 15/10. Các nhiệm vụ backend trước chưa hoàn tất vẫn được thực hiện ở issue gốc.

## 6. Tiêu chí đóng phần UI ngày 15/10

1. Login/session thật đầy đủ; Admin User/Role và Technician UI có đủ chức năng.
2. Category/Form được hoàn thiện từ giao diện đã có; Location/Equipment và Asset/History dùng các API hiện hữu.
3. Selectors/Create Ticket render published form, chín field types, policy/evidence và lỗi; submission mock khi API chưa có.
4. Ticket Detail thật tiếp tục hoạt động; các phần ảnh/actions/comments/feedback có UI/preview/test và trạng thái API rõ.
5. Manager/Technician workflow và Dashboard có UI đầy đủ theo contract.
6. Các màn đồng nhất navigation/layout/màu/components, tiếng Việt, badges/time/states, responsive và keyboard; frontend checks xanh.
7. Báo cáo coverage phân biệt dữ liệu thật/mock/pending; tích hợp backend thực hiện theo kế hoạch tiếp theo.

**Cách báo cáo:** “Hoàn thiện phần lớn UI MVP trong giai đoạn 09–15/10; các màn chưa có API đã có contract/preview/test và sẽ tích hợp backend khi sẵn sàng.”

## 7. Công việc backend/tích hợp ở giai đoạn sau

Validator submission, API tạo Ticket, API đọc nội dung attachment, API danh sách Technician và nghiệm thu tích hợp thật nằm ở [file ứng viên Milestone 3](MILESTONE_3_CANDIDATE_API_ISSUES.md). API workflow/comments/feedback/Dashboard thật được lập issue sau bằng tên đã ghi trong từng phần API; chưa tự gán số hoặc deadline.
