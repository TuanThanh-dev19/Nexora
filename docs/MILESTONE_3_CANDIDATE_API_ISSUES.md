# Đề xuất API và tích hợp để cân nhắc cho Milestone 3

> **Ứng viên backlog, chưa chốt milestone hoặc lịch.** Chưa tạo/chuyển issue trên GitHub.
> UI tương ứng có thời gian **09/10/2026–15/10/2026**; các issue ở file này là backend/tích hợp ở giai đoạn sau, không bắt buộc hoàn thành trong khoảng UI đó.

## 1. Mục tiêu và cách sử dụng

Giữ scope/API contracts rõ ràng để nhóm chọn một số hoặc toàn bộ nhiệm vụ vào Milestone 3. UI đã được mô tả ở [kế hoạch UI bổ sung](MILESTONE_2_SUPPLEMENT_UI_ISSUES.md). Chỉ mục issue đã có trên GitHub nằm ở [file tổng hợp](MILESTONE_2_SUPPLEMENT_ISSUES.md), được đối chiếu theo ảnh.

Số issue bổ sung là dự kiến; chỉ mục dùng số để nhận diện. Mục Phụ thuộc dùng số issue đã đối chiếu; nội dung khác dùng tên công việc. Không giữ mã planning hoặc deadline cũ 14–18/10.

## 2. Chỉ mục ứng viên backend/tích hợp

| Số issue dự kiến | Owner | Nội dung |
|---|---|---|
| #48 | Member 2 | Xây dựng validation dynamic values/evidence và đồng bộ contract form |
| #49 | Member 3 | Xây dựng API tạo Ticket với sequence, SLA và ảnh minh chứng |
| #51 | Member 5 | Xây dựng API đọc attachment có phân quyền |
| #57 | Member 1 | Xây dựng API danh sách Technician phục vụ UI Manager/Admin |
| #58 | Member 5 | Nghiệm thu tích hợp API thật và cập nhật tài liệu |

**Bắt đầu/hoàn thành:** Chưa chốt theo kế hoạch Milestone 3. Không tự đổi lịch hoặc milestone của issue đã có trên GitHub.

## 3. Quy ước chung

- API mới ghi rõ HTTP method/endpoint đầy đủ, đầu vào, đầu ra, lỗi và thời điểm triển khai.
- API đã có ghi tên issue trước để tái sử dụng; API mới chưa có triển khai trong issue tương ứng khi nhóm chọn backend scope.
- DTO/enum tiếng Anh, ProblemDetail/RFC 9457, không expose entity/password hash; pagination từ 0, size 20/tối đa 100.
- Backend kiểm tra current user ACTIVE, roles/visibility/business preconditions; version/revision đúng contract.
- Migration additive; SQL Server checks, transaction, file cleanup và test/review phù hợp.
- Backend User/Technician, Eligible Assets, Ticket Number còn thiếu tiếp tục trong các issue đã được giao, không tạo implementation trùng.
- Phần UI đã xong và phần API thật đã xong được báo cáo riêng; UI/mock không chứng minh backend workflow hoàn thành.

---

# Xây dựng validation dynamic values/evidence và đồng bộ contract form

**Owner:** Member 2  
**Module:** backend `ticket`, `category`, `common/storage`  
**Loại issue:** Backend  
**Bắt đầu:** Chưa chốt — theo lịch backend/Milestone 3 nếu nhóm chọn  
**Hoàn thành:** Chưa chốt — theo lịch backend/Milestone 3  
**Milestone đề xuất:** Milestone 3; chưa gán/chuyển trên GitHub  
**Branch gợi ý:** `feature/48-ticket-submission-validation`  
**Phụ thuộc:** #30, #31, #40.

## Mục tiêu

Validate dữ liệu submission ở backend, tách với validation cấu hình form; đồng bộ IMAGE/DATETIME để UI và API hiểu cùng dữ liệu.

## API — đầu vào/đầu ra và nơi thực hiện

API đọc published form và quản trị form đã có trong các issue trước **“Xây dựng API đọc Category, Published Form và SLA”** và **“Xây dựng API quản trị Category và Dynamic Form”**. Nền tảng lưu file đã có trong issue trước **“Xây dựng nền tảng lưu trữ File đính kèm”**; đây là service nội bộ, không phải public upload API.

**Validator submission chưa có.** Sẽ triển khai ở issue hiện tại khi nhóm chọn kế hoạch backend/Milestone 3; không tạo endpoint validate public mới. Kết quả được API tạo Ticket sử dụng.

**Contract nội bộ, không tạo endpoint validate public mới.**

Đầu vào: current published FormResponse/entity snapshot; `dynamicValues:[{fieldKey,value}]`; `attachments:[{fileIndex,fieldKey?}]`; file metadata/bytes cần kiểm tra. Ví dụ BOOLEAN false và NUMBER 0 đều là giá trị hợp lệ.

Đầu ra hợp lệ: normalized/validated values và attachment-field mapping cho service API tạo Ticket. Đầu ra lỗi: exception/result được API tạo Ticket chuyển thành ProblemDetail:
```json
{
  "type": "<stable-validation-problem-type>",
  "title": "Validation failed",
  "status": 400,
  "detail": "Ticket submission contains invalid fields.",
  "fieldErrors": {
    "dynamicValues.affectedDevices": "Value must be greater than or equal to 1.",
    "attachments": "At least one evidence image is required."
  }
}
```

Field error là contract đề xuất cần dùng nhất quán trong API tạo Ticket/UI Create Ticket; message UI ánh xạ tiếng Việt. Form đổi version sau load trả 409 problem type riêng, không âm thầm submit sang version khác.

## Công việc

- Kiểm tra category active, form hiện tại PUBLISHED và formVersionId gửi lên còn là version hiện tại.
- Kiểm tra required/type/range/length/pattern/date/datetime/options và các validation rules được contract hỗ trợ.
- Kiểm tra BOOLEAN false và NUMBER 0 theo đúng giá trị, không coi là rỗng.
- SELECT/MULTI_SELECT chỉ nhận option active thuộc đúng field; MULTI_SELECT không trùng giá trị.
- Từ chối field key không tồn tại, key trùng và dữ liệu cho field inactive.
- IMAGE dùng attachment liên kết field; kiểm tra fileIndex, mapping file/field, minFiles/maxFiles và required.
- Kiểm tra tổng evidence tối thiểu, giới hạn file chung và mọi giới hạn field; rule field không được nới lỏng giới hạn storage.
- Error trả ProblemDetail với đường dẫn field ổn định cho UI Create Ticket ánh xạ vào form.
- Form đã thay đổi giữa load/submit trả conflict có problem type rõ để UI yêu cầu tải lại; không âm thầm lưu sang version khác.
- Đồng bộ publish-validator và response contract: JPEG/PNG, 5 MiB/file, tổng tối đa 5 file; minAttachments/minFiles không vượt giới hạn chung; field rule không nới storage limits.
- DATETIME/rules theo ISO-8601 có offset; UI serialize cùng contract. Kiểm tra form đã publish cũ và báo incompatibility nếu có; sửa bằng version mới, không đổi dữ liệu immutable.
- Tổng minAttachments tính số file REPORT_EVIDENCE duy nhất, gồm evidence chung và field-linked IMAGE; không đếm một file hai lần.
- Chốt đầu vào/đầu ra và error paths sớm để UI dùng mock trước 15/10.

## Business rule

- Backend kiểm tra required/type/options/constraints, không dựa riêng vào UI.
- MULTI_SELECT không trùng option và chỉ lấy option active; unknown/inactive/duplicate field keys bị từ chối.
- IMAGE dùng attachment liên kết field, không cần scalar row; mỗi file có đúng một mapping thuộc cùng form.
- BOOLEAN false và NUMBER 0 là giá trị hợp lệ; giới hạn field không được nới giới hạn storage.

## Đạt yêu cầu khi

Validator xử lý tất cả field types và evidence; contract IMAGE/DATETIME nhất quán; backend tests pass; API tạo Ticket nhận normalized data/errors đúng shape.

## Cách test

- Tất cả field types với valid/invalid/required, BOOLEAN false/NUMBER 0, ranges/pattern/date/datetime.
- Options/keys inactive, unknown hoặc trùng; IMAGE mapping/minFiles/maxFiles/minAttachments và giới hạn file.
- Form đổi version, cấu hình cũ không tương thích và shape fieldErrors dùng cho UI.

## Ngoài phạm vi

Controller/transaction tạo Ticket, workflow và public validate endpoint.

---

# Xây dựng API tạo Ticket với sequence, SLA và ảnh minh chứng

**Owner:** Member 3  
**Module:** backend `ticket`, `common`, `config`  
**Loại issue:** Backend/API  
**Bắt đầu:** Chưa chốt — theo lịch backend/Milestone 3 nếu nhóm chọn  
**Hoàn thành:** Chưa chốt — theo lịch backend/Milestone 3  
**Milestone đề xuất:** Milestone 3; chưa gán/chuyển trên GitHub  
**Branch gợi ý:** `feature/49-create-ticket-api`  
**Phụ thuộc:** #28, #30, #31, #33, #34, #36, #38, #39, #40, #48.

## Mục tiêu

Tạo Ticket thật bằng một request gồm thông tin, dynamic values và ảnh; lưu đủ dữ liệu để đọc ngay qua API List/Detail.

## API — đầu vào/đầu ra và nơi thực hiện

**`POST /api/v1/tickets` chưa có.** Sẽ triển khai trong issue hiện tại nếu nhóm chọn Milestone 3. Không trùng API đọc Ticket đã có ở issue trước **“Xây dựng API đọc danh sách và chi tiết Ticket”**.

Tái sử dụng current-user, published form, persistence và file storage từ các issue trước **“Xây dựng API Current User và kết nối phiên đăng nhập Frontend”**, **“Xây dựng API đọc Category, Published Form và SLA”**, **“Xây dựng Persistence nền tảng cho Ticket”**, **“Persistence dữ liệu động, lịch sử và Attachment của Ticket”** và **“Xây dựng nền tảng lưu trữ File đính kèm”**. Generator Ticket Number đã có kế hoạch trong issue trước **“Tạo SQL Server Sequence cho Ticket Number”**; phải hoàn tất trước khi nghiệm thu tạo Ticket thật.

### `POST /api/v1/tickets` — API mới

Headers: Bearer token; Content-Type `multipart/form-data`. Frontend để thư viện HTTP/browser tự đặt multipart boundary.

**Đầu vào:** part `ticket` có Content-Type application/json:
```json
{
  "categoryId": 3,
  "formVersionId": 8,
  "locationId": 12,
  "assetId": 25,
  "title": "Máy chiếu không lên nguồn",
  "description": "Máy chiếu tại phòng A101 không hoạt động.",
  "dynamicValues": [
    {"fieldKey": "affectedDevices", "value": 1},
    {"fieldKey": "powerOn", "value": false},
    {"fieldKey": "observedAt", "value": "2026-10-09T09:30:00+07:00"}
  ],
  "attachments": [
    {"fileIndex": 0, "fieldKey": "evidencePhoto"},
    {"fileIndex": 1}
  ]
}
```

Part `files` là các file theo thứ tự fileIndex (từ 0). Ví dụ giả định published form có đúng các field/option liên quan; không phải dữ liệu seed bắt buộc. Không gửi file/attachments khi form không yêu cầu và người dùng không chọn. assetId null/không gửi khi policy cho phép.

Client không gửi reporterId/ticketNumber/status/priority/SLA/storageKey. Server lấy reporter từ current user, priority mặc định category, SLA/skill từ published form. Mỗi file mapping đúng một lần; có fieldKey thì field phải IMAGE cùng form.

**Đầu ra:** 201 Created, `Location: /api/v1/tickets/101`:
```json
{
  "id": 101,
  "ticketNumber": "NX-2026-000123",
  "status": "SUBMITTED",
  "version": 0
}
```

**Lỗi:** 400 fields/mapping/policy invalid; 401 session; 403 role không được tạo; 404 reference không tồn tại; 409 form đã thay đổi/conflict; 413 file/request quá dung lượng; 415 media type không hỗ trợ. Error paths theo validator submission backend; cấu hình multipart limit và exception handler cùng contract.

## Công việc

- Chỉ user ACTIVE có role REQUESTER được tạo Ticket; reporter lấy từ current user trong database.
- Kiểm tra location và asset eligibility ở thời điểm submit, áp dụng validator validator submission backend.
- Lấy priority mặc định từ category, required skill và SLA policy từ published form; snapshot `slaDueAt = createdAt + resolutionMinutes`.
- Sinh mã bằng generator generator Ticket Number; năm theo Asia/Bangkok, sequence toàn cục không reset.
- Trong transaction database: lưu Ticket SUBMITTED, dynamic values, attachment metadata và initial status history.
- Dùng storage FileStorageService và cleanup cho file và cleanup khi thất bại. Filesystem không cùng transaction với SQL Server: phải kiểm thử bù trừ cleanup khi validation/lưu file/lưu DB thất bại.
- Kiểm soát form/policy/reference thay đổi đồng thời để không lưu Ticket theo dữ liệu chưa được xác thực; xác định lock/check phù hợp với SQL Server trong implementation.
- Cập nhật OpenAPI, API docs và kiểm thử SQL Server sạch/concurrency.
- Chốt title/description required/length theo schema hiện có và validate location active/references hợp lệ.
- Field-linked IMAGE lưu REPORT_EVIDENCE với field_definition_id; không invent attachment type IMAGE.
- Giới hạn tổng tối đa 5 file/request; file JPEG/PNG tối đa 5 MiB, bytes/MIME hợp lệ; request limit đủ cho 5 file + JSON.
- Idempotency chưa có: UI ngăn double-click, API không hứa exactly-once. Retry sau timeout phải xem lại list trước khi người dùng gửi lại.

## Business rule

- Chỉ current user ACTIVE có REQUESTER tạo Ticket; reporter lấy từ database user hiện tại.
- Ticket dùng category active và current PUBLISHED form; asset đúng location/status và đúng policy.
- Sequence toàn cục không reset theo năm; năm theo Asia/Bangkok; không dùng MAX(id)+1.
- Priority lấy mặc định category; skill/SLA từ published form; snapshot SLA theo calendar minutes.
- Database writes atomic; filesystem lỗi/rollback cần cleanup bù trừ. API chưa có idempotency key không hứa exactly-once.

## Đạt yêu cầu khi

POST tạo đúng Ticket/values/evidence/history/SLA; List/Detail đọc được; lỗi không tạo bản ghi dở dang, file của request thất bại được cleanup; OpenAPI và SQL Server tests pass.

## Cách test

- POST tạo Ticket đủ values/ảnh, hoặc không asset/không ảnh khi policy cho phép; List/Detail đọc lại đúng.
- 401/403; stale form/reference/asset sai; dynamic/file validation và MIME/size limits.
- SQL Server transaction rollback/file cleanup, sequence concurrency và unique ticket number.
- Kiểm tra OpenAPI request/response/status codes khớp frontend/mock.

## Ngoài phạm vi

Workflow sau SUBMITTED, upload WORK_RESULT, notification, duplicate detection và idempotency key.

---

# Xây dựng API đọc attachment có phân quyền

**Owner:** Member 5  
**Module:** backend `ticket`, `common/storage`  
**Loại issue:** Backend/API  
**Bắt đầu:** Chưa chốt — theo lịch backend/Milestone 3 nếu nhóm chọn  
**Hoàn thành:** Chưa chốt — theo lịch backend/Milestone 3  
**Milestone đề xuất:** Milestone 3; chưa gán/chuyển trên GitHub  
**Branch gợi ý:** `feature/51-ticket-attachment-content-api`  
**Phụ thuộc:** #28, #34, #35, #40, #49.

## Mục tiêu

Người có quyền xem Ticket được đọc ảnh minh chứng an toàn; cung cấp binary content cho UI UI Detail/actions/ảnh/comments/feedback.

## API — đầu vào/đầu ra và nơi thực hiện

**API đọc binary attachment chưa có.** Sẽ triển khai trong issue hiện tại nếu nhóm chọn Milestone 3.

Attachment metadata đọc được qua `GET /api/v1/tickets/{ticketId}` của issue trước **“Xây dựng API đọc danh sách và chi tiết Ticket”**; file storage tái sử dụng từ issue trước **“Xây dựng nền tảng lưu trữ File đính kèm”**. Có metadata không đồng nghĩa đã có endpoint download.

### `GET /api/v1/tickets/{ticketId}/attachments/{attachmentId}/content` — API mới

**Đầu vào:** path ticketId/attachmentId là positive IDs; Bearer JWT; không request body, filesystem path hoặc storageKey.

**Đầu ra 200:** binary JPEG/PNG, không JSON/base64. Headers:
```http
Content-Type: image/png
Content-Length: <bytes>
Content-Disposition: inline; filename="<safe-name>"; filename*=UTF-8''<encoded-name>
X-Content-Type-Options: nosniff
Cache-Control: private, no-store
```

Tên file header phải được encode/sanitize đúng, không header injection. UI muốn tải dùng blob/download; không gửi token trong URL.

**Lỗi:** 400 invalid IDs; 401 invalid session; 404 Ticket/attachment không tồn tại, attachment không thuộc Ticket hoặc không có visibility; 404 file backing đã mất theo convention rõ. Lỗi mở storage bất ngờ trả ProblemDetail phù hợp, không lộ path.

## Công việc

- Kiểm tra current user ACTIVE và Ticket visibility theo API danh sách/chi tiết Ticket.
- Query attachment thuộc chính ticketId, không đọc attachmentId toàn cục rồi bỏ qua parent.
- Mở file từ opaque storageKey server-side bằng FileStorageService và cleanup; stream response và đóng resource đúng.
- Xử lý headers/mime/filename an toàn; file không nằm ở public static web root.
- Không trả storage path/key trong content response; update OpenAPI và tests.
- Phần UI xem/tải ảnh nằm ở UI Detail/actions/ảnh/comments/feedback, hạn 15/10; issue này chỉ backend, hạn sẽ do nhóm chốt.

## Business rule

- Visibility theo Ticket read: REQUESTER own; TECHNICIAN current/historical assignment; MANAGER/ADMIN all.
- Attachment phải thuộc ticketId trên path; server mở opaque storageKey, client không gửi filesystem path.
- Không public static file route, không JWT trong URL; response headers/filename/MIME an toàn.

## Đạt yêu cầu khi

Người có quyền nhận đúng binary/headers; sai parent/visibility bị từ chối an toàn; file mất/error contract rõ và tests pass.

## Cách test

- Đủ bốn role và current/historical visibility; sai parent Ticket, invalid IDs và file mất.
- Unicode filename/header handling, MIME/length/stream; response không lộ storage path.
- Ảnh của Ticket tạo thật khi API tạo sẵn sàng; có thể triển khai trước bằng fixture.

## Ngoài phạm vi

Upload WORK_RESULT, thumbnail, cloud storage, public sharing và phần UI ảnh.

---

# Xây dựng API danh sách Technician phục vụ UI Manager/Admin

**Owner:** Member 1  
**Module:** backend `user`, `auth`  
**Loại issue:** Backend/API  
**Bắt đầu:** Chưa chốt — theo lịch backend/Milestone 3 nếu nhóm chọn  
**Hoàn thành:** Chưa chốt — theo lịch backend/Milestone 3  
**Milestone đề xuất:** Milestone 3; chưa gán/chuyển trên GitHub  
**Branch gợi ý:** `feature/57-technician-list-api`  
**Phụ thuộc:** #29.

## Mục tiêu

Cung cấp list Technician có phân trang cho Manager/Admin mà không cấp quyền đọc admin user directory; bổ sung query cần để UI UI cấu hình Technician hoạt động thật.

## API — đầu vào/đầu ra và nơi thực hiện

**API danh sách Technician chưa có trong code hiện tại.** Sẽ triển khai trong issue hiện tại nếu nhóm chọn Milestone 3.

Profile/skills/service-areas đã được giao trong issue trước **“Xây dựng chức năng quản lý User, Role và Technician”**. Nếu issue đó bổ sung list tương đương thì tái sử dụng và hoàn thiện contract/tests, không tạo controller trùng.

### `GET /api/v1/technicians?page=0&size=20&query=an&available=true` — mới nếu API quản lý User, Role và Technician chưa có

**Đầu vào:** Bearer JWT; page ≥ 0, size 1–100, query trim để tìm theo fullName/email, available boolean tùy chọn. Sort chưa hỗ trợ client; unknown filter/sort trả 400. Server sort fullName asc rồi technicianId để ổn định page.

**Đầu ra 200:**
```json
{
  "content": [
    {
      "technicianId": 20,
      "fullName": "Nguyễn Văn An",
      "email": "technician@nexora.local",
      "status": "ACTIVE",
      "available": true,
      "maxActiveTickets": 5,
      "version": 0
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

Chỉ profile có user TECHNICIAN hiện tại; có thể hiển thị LOCKED/DISABLED để Manager biết profile mất hiệu lực, không coi là candidate assign. GET detail/PUT profile/skills/areas vẫn thuộc API quản lý User, Role và Technician. 401 unauthenticated, 403 không MANAGER/ADMIN, 400 bad query.

## Công việc

- Bổ sung controller/service/query DTO và paginated DB query nếu chưa có endpoint tương đương.
- Current user ACTIVE + MANAGER/ADMIN; Technician own detail không cho phép đọc list workforce này.
- Apply filters/permission ở query/service thích hợp, không load tất cả rồi paginate Java.
- DTO không password/hash, không serialize entity graph; allowlist query và stable ordering.
- Không lấy user admin endpoint làm list cho Manager; chốt OpenAPI, client/mock shape UI cấu hình Technician.
- Nếu endpoint đã nằm trong PR API quản lý User, Role và Technician, bổ sung ở PR đó rồi liên kết, không tạo controller trùng.

## Business rule

- Caller phải ACTIVE và MANAGER/ADMIN; TECHNICIAN own-profile không cho quyền đọc toàn bộ workforce list.
- Row có user TECHNICIAN và profile; target LOCKED/DISABLED vẫn hiển thị trạng thái rõ cho quản trị, không coi là eligible assign.
- List phục vụ cấu hình, không triển khai capacity/eligibility engine hoặc user directory tùy ý.

## Đạt yêu cầu khi

Manager/Admin query/page được; role khác bị từ chối; DTO và filter allowlist/stable ordering đúng, tests/OpenAPI hoàn chỉnh.

## Cách test

- Manager/Admin thành công; Requester/Technician/no-token/disabled caller bị từ chối.
- Query/available/page, filter/sort không hỗ trợ, stable ordering và role/profile validity.
- Response không có password hash hoặc entity graph; UI/mock contract khớp response thật.

## Ngoài phạm vi

Eligible technicians query, auto assignment, arbitrary user directory và backend profile implementation trùng.

---

# Nghiệm thu tích hợp API thật và cập nhật tài liệu

**Owner:** Member 5  
**Module:** backend/frontend integration, `docs`, Project Knowledge  
**Loại issue:** Integration/QA  
**Bắt đầu:** Chưa chốt — theo lịch backend/Milestone 3 nếu nhóm chọn  
**Hoàn thành:** Chưa chốt — theo lịch backend/Milestone 3  
**Milestone đề xuất:** Milestone 3; chưa gán/chuyển trên GitHub  
**Branch gợi ý:** `feature/58-real-api-integration`  
**Phụ thuộc:** #27, #28, #29, #30, #35, #36, #37, #38, #39, #40, #42, #43, #44, #45, #46, #47, #48, #49, #50, #51, #54, #57.

## Mục tiêu

Sau khi API mới hoàn thành, nối và kiểm chứng các hành trình thật; cập nhật evidence mà không đổi hạn hoàn thành UI ngày 15.

## API — đầu vào/đầu ra và nơi thực hiện

Không thêm endpoint mới. Các API đã có được mô tả trong issue Auth, Category/Form, Location/Equipment/Asset và Ticket đọc trước đó. API tạo Ticket, nội dung attachment, danh sách Technician và validator submission sẽ được triển khai ở các issue backend sau đã nêu bằng tên trong kế hoạch.

Công việc này kiểm chứng đầu vào/đầu ra API thật với UI đã nghiệm thu ngày 15/10; chỉ bắt đầu nghiệm thu hành trình khi các API cần thiết sẵn sàng.

## Công việc

- Nối client/hook đã chuẩn bị ở UI vào API thật, bỏ mock khỏi hành trình nghiệm thu thật; giữ mock future workflows ở preview/test.
- Demo Admin tạo Requester → login → chọn category/form/location/asset → submit ảnh/dynamic values → List/Detail → preview/download.
- Verify administration/location/equipment/asset/profile mutations và readback đúng roles.
- Kiểm tra 401/403/404/409, form thay version, file errors, optimistic lock và rollback cleanup.
- Backend verify, SQL Server integration checks liên quan, frontend lint/test/build; build production không có fake mutations/controllers.
- Cập nhật docs/progress/API/UI cũ bằng commit/test/demo evidence; workflow/comments/feedback/Dashboard thật còn pending ghi rõ.
- Nếu thấy integration defect phải sửa và ghi nhận; không tự ghi rằng UI đã nối thật từ ngày 15.

## Business rule

- UI completion và API integration là hai bằng chứng nghiệm thu riêng.
- Workflow/comments/feedback/Dashboard query chưa được chọn không thuộc backend Done chỉ vì mock UI chạy được.

## Đạt yêu cầu khi

Khi nghiệm thu, hành trình provisioning/Create/List/Detail/ảnh và các màn quản trị chạy thật đúng contract/rights, checks xanh và docs phản ánh chính xác phần thật/mock/pending.

## Cách test

- API contract regression và deny cases theo role/visibility/version/input.
- SQL Server create/readback/sequence/rollback/cleanup; hành trình provisioning → login → create → detail → ảnh thật.
- Backend/frontend checks phù hợp và production không bật mock.

## Ngoài phạm vi

Thiết kế UI mới ngoài phần đã nghiệm thu ngày 15/10; API workflow/comments/feedback/Dashboard thật chưa được chọn và các tính năng P1.

---

## 4. Phụ thuộc triển khai backend

- Backend profile User/Technician trước API danh sách Technician.
- Validator submission, Eligible Assets, Ticket Number, persistence và storage trước nghiệm thu API tạo Ticket.
- Ticket visibility/read, attachment metadata và storage trước API đọc nội dung ảnh; có thể dùng fixture để triển khai trước khi API tạo xong.
- Nghiệm thu tích hợp thật sau các API cần thiết và UI tương ứng.

**Phụ thuộc hiện có:** #27, #28, #29, #30, #31, #33, #34, #35, #36, #37, #38, #39, #40.  
**Phụ thuộc bổ sung dự kiến:** #42, #43, #44, #45, #46, #47, #48, #49, #50, #51, #54, #57.

## 5. Nghiệm thu khi nhóm triển khai backend

1. Request/response/errors thật khớp API client/mock đã chuẩn bị.
2. Tạo Ticket lưu đúng values/number/SLA/history/ảnh; rollback cleanup không để Ticket dở dang.
3. Attachment binary đúng parent/visibility; Technician list đúng permission/filter/page, không lộ password hash/user directory.
4. Hành trình thật Admin tạo Requester → login → Create Ticket → List/Detail → xem/tải ảnh được kiểm chứng khi đủ dependencies.
5. Backend/frontend checks, review và docs phản ánh đúng API thật, UI mock và backend pending.

## 6. Các API workflow khác cần lập issue sau

Các tên dưới đây là đề xuất để UI biết nhiệm vụ backend sẽ thực hiện, **chưa có số issue xác nhận trong ảnh**:

- API review, reject và cancel Ticket.
- API phân công, đổi và gỡ phân công Technician, gồm query eligibility.
- API công việc hiện tại của Technician.
- API bắt đầu xử lý và Work Log.
- API ảnh kết quả và resolve Ticket.
- API close và reopen Ticket.
- API comments Ticket.
- API feedback Ticket.
- API Dashboard summary theo role.

Đầu vào/đầu ra dự kiến đã được mô tả ở phần API của UI tương ứng. Nhóm chọn scope và tạo issue backend trước khi triển khai; không coi các API này đã hoàn thành vì UI mock hoạt động.
