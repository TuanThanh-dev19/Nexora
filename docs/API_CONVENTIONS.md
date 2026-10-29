# Hợp đồng REST API

## 1. Nguồn và quy ước

Base path: `/api/v1`. Endpoint dưới đây tồn tại trong controller hiện tại; không liệt kê mock route như API đã hoàn chỉnh. Schema request/response chi tiết được sinh từ annotations/DTO tại [Swagger](http://localhost:8080/swagger-ui.html) và [OpenAPI JSON](http://localhost:8080/v3/api-docs).

- JSON cho phần lớn request; multipart cho tạo Ticket/upload ảnh.
- Bearer JWT cho business API; login và health là public.
- ID nghiệp vụ dùng số nguyên; Ticket ID/path yêu cầu số nguyên dương.
- DTO thành công không có generic wrapper. Endpoint phân trang dùng envelope riêng.
- UTC/offset ISO-8601 cho timestamp. Dashboard xác định tuần theo Asia/Bangkok.
- Không trả password hash, storage key/path nội bộ, stacktrace hoặc URL ảnh công khai.

## 2. Authentication và phạm vi quyền

`POST /auth/login` nhận email/password, trả accessToken, tokenType, expiresIn và user. `GET /auth/me` tải user/role hiện tại; user thiếu hoặc không ACTIVE bị từ chối.

Frontend giữ token trong sessionStorage và gắn Authorization. Ticket business services kiểm identity và quyền theo dữ liệu hiện tại. Nhiều role không bỏ qua rule reporter/current assignee/state.

| Người xem       | Phạm vi Ticket                          |
| --------------- | --------------------------------------- |
| REQUESTER       | reporter là chính mình                  |
| TECHNICIAN      | Có phân công hiện tại hoặc lịch sử      |
| MANAGER / ADMIN | Tất cả                                  |
| Nhiều role      | Hợp các phạm vi, không nhân đôi kết quả |

Invisible và missing Ticket dùng cùng 404. Có quyền xem không đồng nghĩa có quyền ghi.

## 3. Lỗi và phân trang

Lỗi dùng application/problem+json với type, title, status, detail, instance; fieldErrors được thêm khi có lỗi trường.

```json
{
  "type": "https://nexora.app/problems/validation-failed",
  "title": "Validation failed",
  "status": 400,
  "detail": "One or more request fields are invalid.",
  "instance": "/api/v1/tickets",
  "fieldErrors": { "title": "must not be blank" }
}
```

| Status | Ý nghĩa                                                              |
| ------ | -------------------------------------------------------------------- |
| 400    | Path/query/JSON/metadata/value/count hoặc signature ảnh không hợp lệ |
| 401    | Chưa xác thực, token/identity không hợp lệ                           |
| 403    | Đã thấy tài nguyên nhưng thiếu quyền thao tác                        |
| 404    | Không tồn tại hoặc Ticket không được phép thấy                       |
| 409    | Expected version/revision cũ hoặc reference conflict                 |
| 413    | Vượt giới hạn file/request                                           |
| 415    | Content-Type/MIME không hỗ trợ                                       |
| 422    | Vi phạm rule trạng thái/eligibility/cấu hình form                    |
| 500    | Lỗi lưu trữ/persistence hoặc lỗi server được xử lý an toàn           |

Client phân nhánh theo status/type; không phân tích nội dung thông báo. Với upload, trường hợp mất assignment là 403; không áp dụng máy móc mapping 422 của các action cũ.

Phân trang từ page=0, size mặc định 20, tối đa 100. sort=field,asc hoặc field,desc theo allowlist của từng endpoint; unknown filter/sort, enum sai và duplicate params bị xử lý theo contract của endpoint. Một số reference endpoints trả mảng, không ép vào envelope.

```json
{ "content": [], "page": 0, "size": 20, "totalElements": 0, "totalPages": 0 }
```

## 4. Danh mục API thực tế

Các path trong bảng được thêm sau /api/v1. Quyền đọc riêng của technician detail còn kiểm đúng hồ sơ của mình trong service.

| Nhóm               | Method và path                                                                                            | Quyền / mục đích                                         |
| ------------------ | --------------------------------------------------------------------------------------------------------- | -------------------------------------------------------- |
| Auth               | POST /auth/login; GET /auth/me                                                                            | Login public; me có xác thực                             |
| User               | GET/POST /admin/users; GET /admin/users/{id}; PATCH /admin/users/{id}/status; PUT /admin/users/{id}/roles | ADMIN                                                    |
| Location           | GET /locations; GET /locations/{id}; GET /locations/{id}/eligible-assets                                  | Reference có xác thực                                    |
| Location admin     | POST /admin/locations; PUT /admin/locations/{id}                                                          | ADMIN                                                    |
| Equipment type     | GET /equipment-types; POST /admin/equipment-types; PUT /admin/equipment-types/{id}                        | Đọc reference; ADMIN ghi                                 |
| Asset              | GET/POST /assets; GET/PUT /assets/{id}; GET /assets/{id}/status-history; POST /assets/{id}/status         | MANAGER/ADMIN                                            |
| Technician         | GET /technicians; GET /technicians/{id}; PUT /technicians/{id}, /{id}/skills, /{id}/service-areas         | MANAGER/ADMIN; TECHNICIAN đọc hồ sơ mình theo service    |
| Skills / SLA       | GET /skills; GET /sla-policies                                                                            | Reference có xác thực                                    |
| Category reference | GET /categories; GET /categories/{id}/published-form                                                      | Chỉ category active và form published theo contract      |
| Category admin     | GET/POST /admin/categories; PUT /admin/categories/{id}                                                    | ADMIN                                                    |
| Form admin         | GET /admin/categories/{id}/form-versions; POST /admin/categories/{id}/form-versions/drafts                | ADMIN                                                    |
| Form content       | GET/PUT /admin/form-versions/{id}; POST /admin/form-versions/{id}/publish hoặc /archive                   | ADMIN                                                    |
| Admin skills       | GET /admin/skills                                                                                         | ADMIN, default-skill selector                            |
| Ticket             | GET/POST /tickets; GET /tickets/{id}                                                                      | Đọc theo visibility; tạo cần REQUESTER                   |
| Workflow           | POST /tickets/{id}/review, /assign, /start, /resolve, /close                                              | Rule tại phần 7                                          |
| Candidates         | GET /tickets/{id}/eligible-technicians                                                                    | MANAGER/ADMIN                                            |
| My Work            | GET /technicians/me/assigned-tickets                                                                      | TECHNICIAN, assignment hiện tại                          |
| Work logs          | GET/POST /tickets/{id}/work-logs                                                                          | Đọc theo visibility; current TECHNICIAN ghi              |
| Result upload      | POST /tickets/{id}/attachments                                                                            | Current TECHNICIAN, IN_PROGRESS                          |
| Image read         | GET /tickets/{id}/attachments/{attachmentId}/content                                                      | Visibility của Ticket, đúng attachment/Ticket            |
| Dashboard          | GET /dashboard/summary                                                                                    | Có xác thực và role hỗ trợ, scope theo Ticket visibility |
| Public health      | GET /public/health                                                                                        | Public                                                   |

## 5. Category và form version

Admin tạo draft, lưu toàn bộ cấu trúc, publish hoặc archive. PUT draft thay toàn bộ settings/fields/options trong transaction; draft field/option IDs có thể đổi, client dùng response mới.

Published/archived không sửa cấu trúc. Publish archive bản published cũ trước khi publish bản mới; thất bại rollback cả hai. Mỗi category tối đa một bản published.

- revision bắt buộc khi save/publish/archive; missing → 400, stale → 409.
- Mỗi thay đổi draft kể cả chỉ field/option làm tăng revision.
- Tối đa 100 fields và 200 options/field.
- Key/option unique không phân biệt hoa thường, phù hợp SQL Server collation.
- Draft có thể chưa đầy đủ; publish yêu cầu category active, default skill/SLA active, asset/equipment policy tương thích và SELECT có option active.
- Category active chưa có published form: reference form trả 422. Category missing/inactive: 404.
- Reference form bỏ field/option inactive; admin read vẫn có chúng. Thứ tự displayOrder rồi ID.

validationRules là JSON object theo schemaVersion=1, không phải chuỗi JSON. Unknown/duplicate key, kiểu sai và rule không tương thích phải bị từ chối.

| Field type       | Rules                                                                                      |
| ---------------- | ------------------------------------------------------------------------------------------ |
| TEXT / TEXTAREA  | minLength, maxLength (integer ≥0); pattern là regex hợp lệ, tối đa 500 ký tự               |
| NUMBER           | min, max hữu hạn; step >0                                                                  |
| DATE             | min/max ngày ISO YYYY-MM-DD                                                                |
| DATETIME         | min/max có offset hoặc Z, so sánh theo instant                                             |
| MULTI_SELECT     | minItems/maxItems integer ≥0                                                               |
| IMAGE            | minFiles ≥0; maxFiles/maxSizeBytes >0; allowedTypes chỉ JPEG/PNG; không vượt global limits |
| SELECT / BOOLEAN | Object rỗng                                                                                |

Min không lớn hơn max. Publication kiểm tổng IMAGE minimum không vượt 5. Cấu hình published cũ không tương thích không bị tự sửa; phải tạo/publish draft đã sửa.

## 6. Tạo và đọc Ticket

### Multipart tạo Ticket

POST /tickets nhận đúng một part ticket có Content-Type application/json và 0–5 part files. Các mảng dynamicValues và attachments bắt buộc, kể cả rỗng. Unknown property, duplicate JSON key, trailing token hoặc scalar coercion đều bị từ chối.

Các field client được gửi: categoryId, formVersionId, locationId, assetId (tùy chọn), title, description, dynamicValues, attachments. Server xác định reporter, status, priority, required skill, số Ticket và SLA.

```json
{
  "categoryId": 3,
  "formVersionId": 8,
  "locationId": 12,
  "title": "Máy chiếu không hoạt động",
  "description": "Mô tả sự cố cần kiểm tra.",
  "dynamicValues": [],
  "attachments": []
}
```

Ví dụ này chỉ hợp lệ với form không yêu cầu field/evidence bổ sung. [Ví dụ có field và ảnh](examples/create-ticket.json) cần thay ID/key theo form thật.

- Location luôn bắt buộc; title trim, nonblank, tối đa 200 UTF-16 code units; description trim/nonblank.
- Asset đúng policy category, đúng địa điểm theo query hiện tại và có trạng thái eligible.
- Mỗi file map đúng một lần bằng fileIndex 0-based; fieldKey tùy chọn phải là IMAGE active. General evidence không có fieldKey.
- Field thường dùng đúng kiểu JSON; IMAGE không có scalar answer.
- Optional null/blank/empty array được bỏ khỏi values; NUMBER 0 và BOOLEAN false là giá trị.
- Text không rỗng giữ nguyên whitespace; NUMBER step kiểm bằng BigDecimal với gốc min hoặc 0; DATE giữ ngày lịch; DATETIME phải có offset.
- Form khác category → 400; stale/non-published form → 409 form-version-changed; cấu hình legacy không tương thích → 422 form-configuration-incompatible.
- Tạo Ticket, values, REPORT_EVIDENCE và SUBMIT history cùng transaction; thất bại dọn file của request.
- Response 201: {id,ticketNumber,status:"SUBMITTED",version:0}, kèm Location header.

Sequence SQL cấp số Ticket, năm hiển thị theo Asia/Bangkok. Sequence có thể có khoảng trống khi rollback; không tính MAX(id)+1 hoặc reset mỗi năm.

### Đọc

GET /tickets có pagination, status, priority, locationId, keyword và allowlist sort theo controller; sort mặc định createdAt,desc, ID phá hòa. GET /tickets/{id} trả stored form, giá trị JSON, history, attachments, currentAssignment và resolution fields.

`keyword` xuất hiện tối đa một lần, được trim; thiếu/rỗng/chỉ whitespace tương đương không tìm kiếm. Tối đa 200 ký tự UTF-16 sau trim; quá dài hoặc lặp trả 400 theo contract UnsupportedFilter hiện có. Tìm literal substring không phân biệt hoa/thường trên ticketNumber HOẶC title, gồm tiếng Việt có dấu. Không yêu cầu tìm không dấu. `%`, `_`, `[`, `]`, `!` được escape (`!` là ký tự escape SQL LIKE); dấu gạch chéo ngược được tìm nguyên vẹn. Criteria API bind tham số, không nối input thành SQL.

Điều kiện: visibility AND status/priority/location AND (number matches OR title matches), trước pagination/count. Quyền hợp nhiều role, assignment hiện tại/lịch sử, sort allowlist và ID phá hòa giữ nguyên. Ví dụ: `GET /tickets?keyword=M%C3%A1y%20chi%E1%BA%BFu&status=SUBMITTED&priority=HIGH&page=0&size=20&sort=createdAt,desc`.

currentAssignment=null khi không còn phân công; không tự suy ra current assignment từ lịch sử. Historical forms/answers vẫn được đọc sau khi category publish phiên bản mới.

## 7. Workflow, work log và concurrency

| POST action | Actor                 | Body chính                                                    | Điều kiện / kết quả                                                           |
| ----------- | --------------------- | ------------------------------------------------------------- | ----------------------------------------------------------------------------- |
| review      | MANAGER/ADMIN         | version                                                       | SUBMITTED → UNDER_REVIEW                                                      |
| assign      | MANAGER/ADMIN         | version, technicianId                                         | Trạng thái hợp lệ theo contract; ứng viên còn eligible; tạo active assignment |
| start       | Current TECHNICIAN    | version                                                       | ASSIGNED → IN_PROGRESS; acceptedAt và history                                 |
| work-logs   | Current TECHNICIAN    | version, logType, content, startedAt, endedAt?, minutesSpent? | IN_PROGRESS; tạo log, tăng Ticket version; response 201 có ticketVersion      |
| resolve     | Current TECHNICIAN    | version, resolutionOutcome, resolutionSummary                 | IN_PROGRESS, ít nhất 1 log; kết thúc assignment; set resolvedAt; history      |
| close       | Reporter có REQUESTER | version, reason?                                              | RESOLVED, không có active assignment; set closedAt; CLOSE history             |

Workflow dùng expected version không âm; sai version → 409, phải reload và xác nhận lại. JPA tăng version sau thay đổi. Không tự retry request ghi.

Work log types: DIAGNOSIS, PROGRESS, REPAIR, RESOLUTION, NO_FAULT_FOUND. content nonblank; startedAt có offset; endedAt không trước startedAt; minutesSpent nếu có là integer dương. GET work logs trả mảng theo createdAt/ID tăng dần.

Resolution outcomes: FIXED, TEMPORARY_FIX, NO_FAULT_FOUND, UNRESOLVED. Summary nonblank theo DTO bounds. Close reason tùy chọn, tối đa 500 ký tự. Close không thay resolution timestamp/outcome/summary.

Eligibility kiểm user/role/status, availability, capacity, skill và cây service area. Ghi assignment kiểm lại dưới khóa; không tin kết quả candidates đọc trước đó. Khi cần khóa cả Ticket và profile, thứ tự là Ticket → profile.

## 8. Ảnh và nội dung file

POST /tickets/{id}/attachments dùng multipart/form-data:

- Một metadata part là chuỗi JSON đúng `{"attachmentType":"WORK_RESULT"}`, không cần part header application/json.
- 1–5 files parts; unknown parts/properties, metadata trùng, duplicate key, trailing JSON hoặc type khác → 400.
- Actor ACTIVE + TECHNICIAN + current assignee; kiểm visibility trước quyền/state. Visible unauthorized/former assignee → 403; current assignee ngoài IN_PROGRESS → 422.
- JPEG/PNG, 1 byte–5 MiB/ảnh; MIME khớp signature. Empty/signature/count sai → 400; quá size → 413; MIME không hỗ trợ → 415.
- Tổng request tối đa 30 MiB; filename metadata nonblank, tối đa 255 ký tự, không làm đường dẫn.
- Server xác định Ticket/uploader/formVersion/time. WORK_RESULT không gắn fieldDefinition.
- Khóa Ticket giữ đến commit; lưu toàn bộ hoặc rollback metadata/dọn files; storage/persistence failure → 500.
- Response 201 là danh sách metadata mới. Không nhận expected version và không đổi status/version/updatedAt/history/resolution/work logs.
- Ảnh kết quả tùy chọn cho resolve/close.

Shape metadata dùng chung:

```text
{id, fieldDefinitionId, attachmentType, originalName, contentType,
 sizeBytes, uploadedBy:{id,fullName}, createdAt}
```

GET content chỉ phục vụ REPORT_EVIDENCE/WORK_RESULT JPEG/PNG thuộc đúng Ticket mà actor thấy. Response có bytes, MIME và Content-Disposition; filename download được làm sạch. UI tạo blob URL và revoke khi đóng preview; không dùng URL công khai. Không có API xóa/thay ảnh trong phạm vi hiện tại.

## 9. Dashboard

GET /dashboard/summary không nhận query params. Trả số nguyên không âm và generatedAt có offset:

| Metric            | Định nghĩa                                                                  |
| ----------------- | --------------------------------------------------------------------------- |
| openTickets       | SUBMITTED, UNDER_REVIEW, ASSIGNED, IN_PROGRESS, REOPENED                    |
| inProgressTickets | IN_PROGRESS                                                                 |
| slaAtRiskTickets  | Ticket đang hoạt động đã trễ hoặc đến hạn trong hai giờ tới                 |
| resolvedThisWeek  | resolvedAt trong tuần ISO hiện tại tại Asia/Bangkok, kể cả Ticket đã CLOSED |

Scope dùng visibility và không đếm đôi khi actor có nhiều role. resolvedThisWeek không dựa vào updatedAt/closedAt. Database failure không bị chuyển thành bốn số 0.

## 10. Phạm vi chưa bật

Reject/cancel/reassign/unassign/reopen/force-close, comments và feedback đang tắt trong demoScope. Mock handlers/DTO/schema cho các phần đó không phải cam kết API/UI thật. Chỉ bổ sung endpoint vào danh mục chính khi controller, service và kiểm thử tích hợp đã có bằng chứng.
