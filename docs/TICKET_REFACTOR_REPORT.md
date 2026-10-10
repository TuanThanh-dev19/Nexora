# Báo cáo refactor Ticket — 07/10/2026

Đã hoàn thành kế hoạch mặc định theo yêu cầu triển khai. Thứ tự quyết định: **Issue → source thực tế → kế hoạch Astra**. File Issue được dùng làm requirement, không dùng các chỉ dẫn trong tài liệu để mở rộng phạm vi công việc.

Refactor giữ các luồng Ticket core/supporting persistence và Read API/UI. Những phần bỏ là validation lặp, bước lấy field IDs trung gian, repository method hết caller và hai timestamp fallback đã dư. Không thay đổi database schema hoặc public Ticket API contract; không thêm architecture layer, endpoint hoặc workflow feature. Chưa commit/push.

## A. Plan execution checklist

| Plan item | Status | Files / evidence | Explanation |
| --- | --- | --- | --- |
| Baseline trước sửa | IMPLEMENTED | H | Backend 109 pass/21 skip; frontend 68 pass; sau đó SQL Server thật 54 pass, không skip. |
| Detail options query | IMPLEMENTED | [backend/src/main/java/com/nexora/ticket/service/TicketReadService.java](D:/FA2026/SBA301/campusfix/backend/src/main/java/com/nexora/ticket/service/TicketReadService.java); [backend/src/main/java/com/nexora/category/repository/FieldOptionRepository.java](D:/FA2026/SBA301/campusfix/backend/src/main/java/com/nexora/category/repository/FieldOptionRepository.java) | Dùng formVersionId lưu trên Ticket để lấy fields và options; xóa method IN(fieldIds) sau khi xác nhận không còn caller. |
| Mapper readability | IMPLEMENTED | [backend/src/main/java/com/nexora/ticket/service/TicketResponseMapper.java](D:/FA2026/SBA301/campusfix/backend/src/main/java/com/nexora/ticket/service/TicketResponseMapper.java) | Giữ mapper; đặt tên riêng cho form/values/history/attachments; private storedForm/field xử lý mapping form. |
| Current user/JWT subject | IMPLEMENTED | [backend/src/main/java/com/nexora/auth/service/AuthService.java](D:/FA2026/SBA301/campusfix/backend/src/main/java/com/nexora/auth/service/AuthService.java); [backend/src/main/java/com/nexora/ticket/service/TicketReadService.java](D:/FA2026/SBA301/campusfix/backend/src/main/java/com/nexora/ticket/service/TicketReadService.java) | AuthService validate và parse subject một lần trước đọc user. Ticket chỉ chuyển lỗi user mất hiệu lực sang convention 401 của Ticket. |
| TicketSpecifications | ALREADY OK | [backend/src/main/java/com/nexora/ticket/repository/TicketSpecifications.java](D:/FA2026/SBA301/campusfix/backend/src/main/java/com/nexora/ticket/repository/TicketSpecifications.java) | SQL visibility đúng bốn role, union nhiều role, historical assignment bằng EXISTS; filters AND. Không cần sửa. |
| TicketListQuery | ALREADY OK | [backend/src/main/java/com/nexora/ticket/dto/request/TicketListQuery.java](D:/FA2026/SBA301/campusfix/backend/src/main/java/com/nexora/ticket/dto/request/TicketListQuery.java) | Giữ whitelist, duplicate/empty validation, page/size/offset limits, sort ổn định và lỗi 400. Không cần sửa. |
| Timestamp callbacks | IMPLEMENTED | [backend/src/main/java/com/nexora/ticket/entity/TicketAttachment.java](D:/FA2026/SBA301/campusfix/backend/src/main/java/com/nexora/ticket/entity/TicketAttachment.java); [backend/src/main/java/com/nexora/ticket/entity/TicketStatusHistory.java](D:/FA2026/SBA301/campusfix/backend/src/main/java/com/nexora/ticket/entity/TicketStatusHistory.java) | Constructor luôn gán timestamp hoặc từ chối null; xóa hai fallback @PrePersist dư. Giữ callback còn cần ở TicketFieldValue và Ticket. |
| Frontend list validation | IMPLEMENTED | [frontend/src/features/tickets/hooks/useTicketSearchParams.js](D:/FA2026/SBA301/campusfix/frontend/src/features/tickets/hooks/useTicketSearchParams.js); [frontend/src/features/tickets/pages/TicketListPage.jsx](D:/FA2026/SBA301/campusfix/frontend/src/features/tickets/pages/TicketListPage.jsx) | Bỏ bộ validator thứ hai và lỗi Axios giả của list. UI nhận 400 từ backend; vẫn giữ guard ID detail nhỏ phục vụ UX. |
| Query serialization | IMPLEMENTED | [frontend/src/features/tickets/api/ticketApi.js](D:/FA2026/SBA301/campusfix/frontend/src/features/tickets/api/ticketApi.js); [frontend/src/features/tickets/hooks/useTickets.js](D:/FA2026/SBA301/campusfix/frontend/src/features/tickets/hooks/useTickets.js) | URLSearchParams bảo toàn các entries; ID ở dạng string; query key dùng raw query string ổn định. |
| Search params hook | IMPLEMENTED | [frontend/src/features/tickets/hooks/useTicketSearchParams.js](D:/FA2026/SBA301/campusfix/frontend/src/features/tickets/hooks/useTicketSearchParams.js) | URL → controls và raw request → update/reset URL. Defaults chỉ phục vụ controls; backend áp dụng defaults request. |
| List rendering | IMPLEMENTED | [frontend/src/features/tickets/pages/TicketListPage.jsx](D:/FA2026/SBA301/campusfix/frontend/src/features/tickets/pages/TicketListPage.jsx) | Component cục bộ TicketResults dùng early return; pagination lấy page từ response. Giữ filters/table/empty/pagination. |
| Error rendering | IMPLEMENTED | [frontend/src/features/tickets/components/TicketErrorState.jsx](D:/FA2026/SBA301/campusfix/frontend/src/features/tickets/components/TicketErrorState.jsx); [frontend/src/features/tickets/pages/TicketDetailPage.jsx](D:/FA2026/SBA301/campusfix/frontend/src/features/tickets/pages/TicketDetailPage.jsx) | Switch chọn message, giữ text/nút hiện tại. Detail guard truyền status=400 trực tiếp thay vì dựng lỗi Axios giả. |
| Auth tests | IMPLEMENTED | [backend/src/test/java/com/nexora/auth/AuthServiceCurrentUserTest.java](D:/FA2026/SBA301/campusfix/backend/src/test/java/com/nexora/auth/AuthServiceCurrentUserTest.java) | Malformed/null/blank/zero/negative/overflow subject không query user; Long.MAX_VALUE không tràn số. |
| TicketReadService tests | IMPLEMENTED | [backend/src/test/java/com/nexora/ticket/service/TicketReadServiceTest.java](D:/FA2026/SBA301/campusfix/backend/src/test/java/com/nexora/ticket/service/TicketReadServiceTest.java) | Đổi assertion sang AuthService sở hữu authentication. Giữ chặn child reads và kiểm tra cả list/detail dừng trước Ticket query khi auth/role sai. |
| Mapper tests | IMPLEMENTED | [backend/src/test/java/com/nexora/ticket/service/TicketResponseMapperTest.java](D:/FA2026/SBA301/campusfix/backend/src/test/java/com/nexora/ticket/service/TicketResponseMapperTest.java) | Test DTO quan sát được: fields/options/raw JSON/history/attachments, asset có hoặc null, empty collections và không lộ storage key. |
| Frontend tests | IMPLEMENTED | [frontend/src/features/tickets/api/ticketApi.test.js](D:/FA2026/SBA301/campusfix/frontend/src/features/tickets/api/ticketApi.test.js); [frontend/src/features/tickets/hooks/useTicketSearchParams.test.jsx](D:/FA2026/SBA301/campusfix/frontend/src/features/tickets/hooks/useTicketSearchParams.test.jsx); [frontend/src/features/tickets/pages/TicketListPage.test.jsx](D:/FA2026/SBA301/campusfix/frontend/src/features/tickets/pages/TicketListPage.test.jsx) | Kiểm tra request thật qua Axios/MSW giữ query entries; backend 400/reset/URL, invalid URL gặp lỗi mạng và cache theo viewer. Giữ suite detail. |
| SQL stale-update test tùy chọn | IMPLEMENTED | [backend/src/test/java/com/nexora/TicketPersistenceSqlServerIntegrationTest.java](D:/FA2026/SBA301/campusfix/backend/src/test/java/com/nexora/TicketPersistenceSqlServerIntegrationTest.java) | Tái sử dụng fixture/container hiện có; update SQL tăng version rồi merge bản stale phải conflict. Không thêm hạ tầng. |
| Verification | IMPLEMENTED | H | Full backend verify; full frontend lint/test/build; ba Ticket SQL suites không skip; git diff --check. |
| Đối chiếu toàn bộ Issue | IMPLEMENTED | D, E, F | Rà lại core/supporting/read/UI với source và các contract test, không bỏ business requirement. |
| Tài liệu test và báo cáo | IMPLEMENTED | [docs/TESTING.md](D:/FA2026/SBA301/campusfix/docs/TESTING.md); [docs/TICKET_REFACTOR_REPORT.md](D:/FA2026/SBA301/campusfix/docs/TICKET_REFACTOR_REPORT.md) | Ghi trách nhiệm validation mới, coverage, commands, kết quả và flow để trình bày. |
| Bảo toàn milestone khác/Potential Deviations | ALREADY OK | Workflow policy, comment Mongo, migration/backfill, schema | Không xóa/redesign các phần user yêu cầu giữ; git diff xác nhận không thay đổi chúng. |

Không có mục mặc định nào bị bỏ âm thầm. Hai mục query/specification được giữ vì source đã đúng requirement; các Potential Deviations bị loại khỏi phạm vi theo yêu cầu user. Test stale-update SQL Server tùy chọn được thực hiện vì có thể dùng fixture/container hiện có với một test nhỏ.

## B. Files changed

Các đường dẫn dưới đây trỏ tới checkout đã sửa.

| File | Thay đổi | Lý do |
| --- | --- | --- |
| [backend/src/main/java/com/nexora/auth/service/AuthService.java](D:/FA2026/SBA301/campusfix/backend/src/main/java/com/nexora/auth/service/AuthService.java) | Validate subject tại đầu getCurrentUser; parse một lần. | Một nơi xác định current user; malformed subject không chạm repository. |
| [backend/src/main/java/com/nexora/category/repository/FieldOptionRepository.java](D:/FA2026/SBA301/campusfix/backend/src/main/java/com/nexora/category/repository/FieldOptionRepository.java) | Xóa findByFieldDefinitionIdInOrderByDisplayOrderAscIdAsc; chú thích query theo form. | Query cũ không còn caller; dùng query sẵn có và giữ inactive historical options. |
| [backend/src/main/java/com/nexora/ticket/service/TicketReadService.java](D:/FA2026/SBA301/campusfix/backend/src/main/java/com/nexora/ticket/service/TicketReadService.java) | Dùng stored form ID cho options; đặt tên child data; bỏ subject parsing. | Detail dễ trace; giữ scoped lookup trước mọi child read và convention lỗi. |
| [backend/src/main/java/com/nexora/ticket/service/TicketResponseMapper.java](D:/FA2026/SBA301/campusfix/backend/src/main/java/com/nexora/ticket/service/TicketResponseMapper.java) | Named locals và hai private method storedForm/field. | Tách mapping form khỏi constructor response; DTO công khai không đổi. |
| [backend/src/main/java/com/nexora/ticket/entity/TicketAttachment.java](D:/FA2026/SBA301/campusfix/backend/src/main/java/com/nexora/ticket/entity/TicketAttachment.java) | Bỏ initializeCreatedAt và import PrePersist. | Public constructor đã bảo đảm createdAt. |
| [backend/src/main/java/com/nexora/ticket/entity/TicketStatusHistory.java](D:/FA2026/SBA301/campusfix/backend/src/main/java/com/nexora/ticket/entity/TicketStatusHistory.java) | Bỏ initializeChangedAt và import PrePersist. | Public constructor đã bảo đảm changedAt. |
| [backend/src/test/java/com/nexora/auth/AuthServiceCurrentUserTest.java](D:/FA2026/SBA301/campusfix/backend/src/test/java/com/nexora/auth/AuthServiceCurrentUserTest.java) | Bổ sung malformed subject, null JWT và Long.MAX_VALUE. | Chứng minh validation trước user query và không tràn số. |
| [backend/src/test/java/com/nexora/ticket/service/TicketReadServiceTest.java](D:/FA2026/SBA301/campusfix/backend/src/test/java/com/nexora/ticket/service/TicketReadServiceTest.java) | Tách invalid identity/inactive user/no roles; kiểm tra list và detail. | Coverage theo responsibility mới, vẫn không đọc Ticket/children khi bị từ chối. |
| [backend/src/test/java/com/nexora/ticket/service/TicketResponseMapperTest.java](D:/FA2026/SBA301/campusfix/backend/src/test/java/com/nexora/ticket/service/TicketResponseMapperTest.java) | Thêm mapping đầy đủ bằng entity fixture và kiểm tra serialized DTO. | Bảo vệ dữ liệu công khai, raw JSON, historical option/order và metadata. |
| [backend/src/test/java/com/nexora/TicketPersistenceSqlServerIntegrationTest.java](D:/FA2026/SBA301/campusfix/backend/src/test/java/com/nexora/TicketPersistenceSqlServerIntegrationTest.java) | Thêm stale detached update. | Xác nhận optimistic locking trên SQL Server thực tế, dùng hạ tầng có sẵn. |
| [frontend/src/features/tickets/hooks/useTicketSearchParams.js](D:/FA2026/SBA301/campusfix/frontend/src/features/tickets/hooks/useTicketSearchParams.js) | Thay parseTicketSearchParams bằng readTicketSearchParams cho controls; trả requestSearch riêng. | Bỏ backend validator bản sao; giữ URL là nguồn trạng thái. |
| [frontend/src/features/tickets/hooks/useTickets.js](D:/FA2026/SBA301/campusfix/frontend/src/features/tickets/hooks/useTickets.js) | Nhận requestSearch string trong queryKey/queryFn. | Không làm mất query data hoặc collision do dùng URLSearchParams object làm key. |
| [frontend/src/features/tickets/api/ticketApi.js](D:/FA2026/SBA301/campusfix/frontend/src/features/tickets/api/ticketApi.js) | Truyền URLSearchParams cho Axios. | Giữ duplicate/unknown/empty và ID lớn mà không ép Number. |
| [frontend/src/features/tickets/pages/TicketListPage.jsx](D:/FA2026/SBA301/campusfix/frontend/src/features/tickets/pages/TicketListPage.jsx) | TicketResults cùng file, early return, pagination theo response. | Giảm nesting; không dựng framework mới. |
| [frontend/src/features/tickets/components/TicketErrorState.jsx](D:/FA2026/SBA301/campusfix/frontend/src/features/tickets/components/TicketErrorState.jsx) | Switch chọn message, thêm status trực tiếp cho lỗi UX. | Dễ đọc, giữ text và retry/reset behavior. |
| [frontend/src/features/tickets/pages/TicketDetailPage.jsx](D:/FA2026/SBA301/campusfix/frontend/src/features/tickets/pages/TicketDetailPage.jsx) | Guard ID truyền status=400. | Bỏ shape lỗi Axios giả; giữ validation detail và nội dung detail. |
| [frontend/src/features/tickets/hooks/useTicketSearchParams.test.jsx](D:/FA2026/SBA301/campusfix/frontend/src/features/tickets/hooks/useTicketSearchParams.test.jsx) | Test control defaults, raw query, update/reset. | Không còn yêu cầu frontend tự thực thi backend query contract. |
| [frontend/src/features/tickets/api/ticketApi.test.js](D:/FA2026/SBA301/campusfix/frontend/src/features/tickets/api/ticketApi.test.js) | Test URLSearchParams, enums, signal, large ID và all entries. | Bảo vệ serialization theo responsibility mới. |
| [frontend/src/features/tickets/pages/TicketListPage.test.jsx](D:/FA2026/SBA301/campusfix/frontend/src/features/tickets/pages/TicketListPage.test.jsx) | Thay zero-call test bằng các case backend 400; thêm invalid-query/network; cập nhật cache key. | Chứng minh query không bị sửa/mất và reset thực sự gọi lại API. |
| [docs/TESTING.md](D:/FA2026/SBA301/campusfix/docs/TESTING.md) | Mô tả auth guard, mapper, SQL stale update và frontend raw query tests. | Hướng dẫn chạy và hiểu test đúng source mới. |
| [docs/TICKET_REFACTOR_REPORT.md](D:/FA2026/SBA301/campusfix/docs/TICKET_REFACTOR_REPORT.md) | Báo cáo A–I. | Checklist tất cả mục, behavior, flow, so sánh, kết quả và giới hạn. |

## C. Simplifications performed

1. **Subject parsing tập trung:** TicketReadService.viewer không còn regex/parse Long hoặc bắt NumberFormatException. AuthService kiểm tra JWT/subject, digits, Long range và ID dương trước khi tìm user + roles. Viewer chỉ giữ chuyển InvalidCredentialsException sang BadCredentialsException để Ticket tiếp tục trả 401 UNAUTHENTICATED.
2. **Options theo stored form:** dùng method có sẵn `findByFieldDefinitionFormVersionIdOrderByDisplayOrderAscIdAsc`. Bỏ nhánh fields rỗng, stream lấy field IDs và method `findByFieldDefinitionIdInOrderByDisplayOrderAscIdAsc`. Không thêm điều kiện active hay lookup form mới nhất.
3. **Mapper rõ nhóm dữ liệu:** detail tạo storedForm, dynamicValues, statusHistory, attachmentMetadata rồi ghép response. Hai private method cùng mapper đọc form/field, không tạo mapper framework hay class mới.
4. **Timestamp có một đường khởi tạo:** bỏ `TicketAttachment.initializeCreatedAt()` và `TicketStatusHistory.initializeChangedAt()`. Constructor mặc định gọi constructor đầy đủ với UTC now; constructor đầy đủ requireNonNull timestamp. Protected no-arg dành cho JPA hydrate row có cột NOT NULL, không phải đường tạo mới trong application. Không có setter đưa timestamp về null. Callback TicketFieldValue/Ticket còn có nhiệm vụ thực tế nên được giữ.
5. **Frontend không validate query contract lần hai:** bỏ whitelist params, duplicate checks, enum checks, integer/range/offset rules khỏi search hook. Bỏ fake Axios error. Backend TicketListQuery tiếp tục là nguồn validation; invalid request không bị chuyển thành valid request.
6. **Serialization đơn giản:** raw query string → URLSearchParams → Axios. Controls chỉ đọc entry đầu tiên để hiển thị; outbound request giữ toàn bộ entries. Không dùng Object.fromEntries trên request và không ép locationId sang Number. URLSearchParams có thể chuẩn hóa cách percent-encode nhưng giữ tên, giá trị, empty entry và duplicate theo thứ tự.
7. **Render list dễ theo dõi:** page dựng heading/filters; TicketResults cùng file chọn error/loading rồi empty/table và pagination. Error được ưu tiên hơn cached rows để không hiển thị lại dữ liệu khi request trả lỗi quyền. Pagination lấy `data.page` từ backend thay vì tự parse URL page.
8. **Error message:** nested ternary được thay bằng switch; 400/401/403/404/network/5xx giữ message cũ. Detail guard giữ UX nhanh nhưng truyền status trực tiếp.

Giữ TicketSpecifications vì nó tập trung visibility/filter và thực hiện chúng ở DB. Giữ TicketListQuery vì validation bảo vệ API thật. Giữ TicketResponseMapper để entity không bị serialize trực tiếp và trường bí mật không lọt vào response.

## D. Behavior retained — đối chiếu Issue

| Nhóm requirement | Behavior sau refactor | Bằng chứng |
| --- | --- | --- |
| Core persistence | Schema Flyway giữ nguyên; reporter/category/formVersion/location bắt buộc; asset tùy chọn; default SUBMITTED; stored form cố định; ticket number unique; find ID/number; @Version; SLA/resolved/closed mapping; quan hệ một chiều | TicketRepositoryTest, TicketPersistenceSqlServerIntegrationTest, source Ticket không đổi |
| Optimistic locking | Stale update bị từ chối, không ghi đè bản mới | H2 test có sẵn và SQL Server stale-update test mới đều pass |
| Dynamic values | valueJson trả nguyên string JSON, giữ Unicode/escaping/scalar/array/date/datetime và số lớn; IMAGE có field-linked attachment mà không cần scalar row | Supporting repository + SQL suites; mapper test; Read API contract |
| History | Null source/reason, same-status row, changedAt tăng dần, ID tie-break; Ticket update + history cùng SQL transaction | Supporting H2/SQL suites, commit/rollback test có sẵn |
| Attachment | Metadata và opaque key trong persistence; filename chỉ để hiển thị; không binary; public API chỉ REPORT_EVIDENCE/WORK_RESULT và không storage key/file URL | Constructor/entity tests, supporting SQL constraints, mapper + API confidentiality tests |
| Ordering/FK | Fields/options displayOrder rồi ID; values theo field; history/attachments thời gian rồi ID; FK/NO ACTION không xóa nhầm reference data | Supporting H2/SQL tests, Read contract |
| List API | GET /api/v1/tickets; shared page envelope, ID/number/title/status/priority/category/location/asset/reporter/SLA/created/updated | Controller/OpenAPI/MVC và H2/SQL Read API contract |
| Detail API | GET /api/v1/tickets/{ticketId}; description, version, form lưu trên Ticket, fields/options kể cả inactive, values/history/metadata; null optional và empty lists | Mapper tests, Read API contract trên H2 và SQL Server |
| REQUESTER | Chỉ Ticket do chính mình báo | SQL specification + repository/HTTP contract tests |
| TECHNICIAN | Ticket đang hoặc từng được phân công; EXISTS tránh nhân đôi Ticket khi có nhiều assignment | Visibility tests, historical/repeated assignment fixture |
| MANAGER / ADMIN | Xem toàn bộ; nhiều role giữ union behavior | Visibility tests; count/page vẫn trong query có scope |
| Current user | Đọc status/roles hiện tại từ DB; token claims cũ/giả không mở rộng quyền; missing/LOCKED/DISABLED bị từ chối; no roles trả 403 | Auth unit, Ticket service unit, H2/SQL HTTP tests |
| Errors | Unsupported/duplicate/blank/invalid query trả 400; identity lỗi trả 401; missing và invisible cùng safe 404; không query children trước access | TicketListQueryTest, MVC, service và HTTP contract |
| Frontend | API thật, read-only, nhãn Việt/enum Anh, filters/page/size/sort từ URL, Back/Forward và mở lại URL; loading/empty/success/errors; detail dynamic/history/metadata | Frontend Testing Library + Axios/MSW suites và build |
| Milestone khác | Workflow contract/transition/scope, TicketCommentService, Mongo document/repository, migration/backfill giữ nguyên; không thêm create/upload/comment/workflow endpoints | Rà git diff; full backend regression bình thường |

## E. Final Ticket List flow

Để trình bày khi bảo vệ:

1. **UI giữ trạng thái trong URL.** useTicketSearchParams đọc size/sort/status/priority/locationId cho controls và trả requestSearch nguyên dữ liệu query. Thay filter/sort/size reset page về 0; đổi trang chỉ đổi page; reset xóa query. React Router quản lý lịch sử nên Back/Forward và mở lại URL khôi phục controls.
2. **Gọi API thật.** useTickets dùng user ID + roles + raw search string làm cache key; chỉ chạy khi auth đã xác định. ticketApi.search tạo URLSearchParams, gửi Axios GET /tickets với AbortSignal. Không bỏ duplicate/unknown/empty hay chuyển ID lớn sang Number. Nếu URL không có params, backend áp dụng defaults.
3. **Security và Controller nhận request.** Spring Security xác thực bearer JWT. TicketController nhận MultiValueMap, vì vậy duplicate vẫn đến TicketListQuery.
4. **TicketListQuery kiểm tra API contract.** Chỉ sáu params, một giá trị mỗi param, page mặc định 0, size 20 và giới hạn 1–100, sort whitelist với default createdAt,desc; id ASC phá hòa. Status/priority là enum Anh; locationId dương và khớp chính xác. Sai contract trả safe 400.
5. **TicketReadService xác định viewer.** AuthService kiểm tra subject và đọc user + roles từ DB. User phải ACTIVE; roles rỗng/không phù hợp bị từ chối trước query Ticket. Claims roles trong token không quyết định scope Ticket.
6. **Xây SQL scope.** TicketSpecifications.visibleTo và filters kết hợp: visibility của requester/technician là OR nếu có nhiều role; manager/admin được tất cả; filters là AND.
7. **Repository truy vấn page và count theo scope.** Không load mọi Ticket rồi lọc Java. EntityGraph chỉ lấy các to-one references cần cho DTO, không tải aggregate collections.
8. **Mapper và response.** mapper.summary ánh xạ dữ liệu công khai; PageResponse.from giữ shared pagination envelope. Không serialize entity.
9. **UI render kết quả.** TicketResults trả error hoặc loading sớm; sau đó empty/table và pagination theo page từ response. Nhãn Việt hiển thị trên enum Anh. Link detail lưu listSearch để quay lại danh sách với filter cũ.

Câu trình bày ngắn: **URL giữ bộ lọc; backend kiểm tra query và user hiện tại; DB lọc quyền trước phân trang; mapper chỉ trả dữ liệu công khai; UI hiển thị đúng response.**

## F. Final Ticket Detail flow

1. UI đọc ticketId từ route. Guard ID nhỏ giữ string và BigInt range; sai ID hiển thị UX 400 trực tiếp, không dựng lỗi HTTP giả.
2. useTicket gọi ticketApi.getById với AbortSignal và cache theo viewer; link quay lại dùng listSearch.
3. Spring Security xác thực JWT; TicketController kiểm tra path ID dương và gọi TicketReadService.detail.
4. AuthService xác định user/roles hiện tại từ DB. Ticket service không tự parse JWT.
5. Repository tìm **ID AND visibility** trong một scoped lookup. Không tìm ID riêng rồi báo khác nhau cho invisible và missing. Không tìm thấy trả cùng safe 404; các bảng con chưa được đọc.
6. Lấy formVersionId đã lưu trên Ticket. Fields và options query trực tiếp theo ID đó, theo displayOrder/ID. Archived form và inactive option lịch sử vẫn được lấy; không thay bằng published form mới nhất.
7. Lấy dynamic values theo field order, history theo changedAt/ID, attachments theo createdAt/ID với type REPORT_EVIDENCE/WORK_RESULT. IMAGE nhận metadata field-linked attachment.
8. TicketResponseMapper tạo summary public references, stored form, raw dynamicValues, history và attachmentMetadata; ghép TicketDetailResponse thêm description/version. Không trả storageKey, email, passwordHash hoặc entity.
9. UI hiển thị dynamic values theo field type, history và metadata; chỉ đọc, không có workflow button hoạt động giả. Read transaction và contract tests xác nhận không đổi Ticket version/updatedAt.

Câu trình bày ngắn: **Kiểm tra user và quyền trước; lấy Ticket cùng form đã lưu; đọc ba nhóm dữ liệu con theo thứ tự; mapper tạo DTO công khai; UI chỉ hiển thị.**

## G. Before vs After

| Điểm | Trước | Sau |
| --- | --- | --- |
| JWT subject | TicketReadService parse/validate rồi AuthService parse lại | AuthService parse/validate một lần; Ticket giữ translation lỗi cần thiết |
| Detail options | Fields → nhánh rỗng → field IDs → IN query | Stored form ID → fields và options query cùng version |
| Mapper | Constructor detail chứa nhiều stream/map lồng nhau | Named groups; form/field nằm trong hai private method cùng class |
| List validation | Frontend lặp backend rules và tạo lỗi 400 giả | Frontend giữ raw query; backend validation; UI xử lý response |
| Serialization | Params object sau parse/normalize chỉ giữ query đã được frontend chấp nhận | URLSearchParams giữ mọi entry, ID string; không tự sửa query sai |
| Search hook | URL parser + backend validator + error builder + URL updates | Controls + raw request + update/reset URL |
| List JSX | Nhánh local error bao nhánh query error/loading/data/empty/table | Page + một local result component dùng early return |
| Error text | Nested ternary 400/401/403/404/network/other | Switch message và các nút giữ nguyên |
| Timestamp | Constructor gán timestamp rồi callback fallback lặp | Constructor sở hữu timestamp, NOT NULL giữ nguyên |
| Abstraction/layers | Controller/service/specification/repository/mapper/DTO | Cùng các layer; không thêm provider/resolver/assembler/framework |
| Tests | Frontend validator tests yêu cầu invalid URL có zero API calls | URL preservation/backend 400/reset/network được test; backend validator coverage giữ nguyên |

Không dùng metric cognitive complexity tự đặt. Số class/layer xử lý backend không giảm; bước trung gian field IDs và validation hai lần đã bỏ. Mapper có thêm hai private method để đọc rõ nhóm dữ liệu, còn list có một component cục bộ; đây là phân chia nhỏ trong cùng file, không phải kiến trúc mới.

Detail có form rỗng vẫn query options một lần thay vì skip như trước. Đây là chi phí một query rỗng để bỏ nhánh và dùng một đường đọc form nhất quán; response vẫn empty và contract test pass. Không tuyên bố cải thiện performance vì không benchmark.

## H. Tests

Log nằm ngoài repository tại `C:\Users\FPT\AppData\Local\Temp\nexora-ticket-refactor-20261007`. Các dòng pass dưới đây là test chạy thành công, không tính skipped. Fail/errors không tồn tại ở baseline.

| Giai đoạn | Command ID | Kết quả | Log |
| --- | --- | --- | --- |
| Baseline backend trước sửa | B1 | PASS: 109 pass, 0 fail/error, 21 skip (130 reported) | baseline-backend.log |
| Baseline frontend trước sửa | F1 | PASS: 8 files, 68 tests, 0 fail/skip | baseline-frontend.log |
| Baseline SQL Server thật trước sửa | B7 | PASS: 54 tests, 0 fail/error/skip | baseline-sqlserver.log |
| Group 1: options | B2 | PASS: 11 tests, 0 fail/error/skip | group1-options.log |
| Group 2: mapper, lần đầu | B3 | FAIL tại testCompile: fixture dùng enum UPDATE_PRIORITY không tồn tại; test chưa chạy | group2-mapper.log |
| Group 2: sửa fixture về REASSIGN, chạy lại | B3 | PASS: 11 tests, 0 fail/error/skip | group2-mapper-rerun.log |
| Group 3: auth | B4 | PASS: 50 tests, 0 fail/error/skip | group3-auth.log |
| Group 4: timestamp + SQL stale update | B5 | PASS: 79 tests, 0 fail/error/skip | group4-timestamps-sql.log |
| Group 5: frontend query | F1 | PASS: 8 files, 62 tests, 0 fail/skip | group5-frontend-query.log |
| Group 6: frontend render/error | F1 | PASS: 8 files, 62 tests, 0 fail/skip | group6-frontend-render.log |
| Final toàn bộ backend | B6 | PASS: 452 pass, 0 fail/error, 47 skip (499 reported), JAR build thành công | final-backend-verify.log |
| Final SQL Server Ticket | B7 | PASS: 55 tests (5 core + 9 read + 41 supporting), 0 fail/error/skip | final-sqlserver.log |
| Final frontend lint | F2 | PASS, exit 0 | final-frontend-lint.log |
| Final toàn bộ frontend test | F3 | PASS: 23 files, 121 tests, 0 fail/skip | final-frontend-test.log |
| Final frontend build | F4 | PASS, exit 0; Vite có cảnh báo chunk >500 kB | final-frontend-build.log |
| Format changed frontend files | Q1 | PASS, exit 0 | Console |
| Diff whitespace | Q2 | PASS, exit 0 | Console |

Lỗi testCompile ở nhóm mapper do fixture tôi bổ sung, không phải lỗi có sẵn từ baseline hay lỗi chạy application. Đã sửa tên enum theo source hiện có và cả nhóm lẫn regression cuối đều pass.

Số frontend focused tests từ 68 xuống 62 vì 17 test parser/validator được thay bằng 3 test controls/URL; đồng thời thêm 6 case HTTP 400 ngoài case cũ, 1 case network với invalid URL và 1 serialization test. Không xóa requirement validation backend. Các test success/empty/error/pagination/detail/dynamic/history/attachment và auth-cache vẫn chạy.

### Commands chính xác

Chạy B1–B7 từ `D:\FA2026\SBA301\campusfix\backend`.

B1 — baseline focused (RUN_SQLSERVER_IT chưa bật):

```powershell
.\mvnw.cmd '-Dtest=TicketRepositoryTest,TicketSupportingPersistenceRepositoryTest,TicketSupportingEntitiesTest,TicketListQueryTest,TicketReadServiceTest,TicketResponseMapperTest,TicketControllerMvcTest,TicketVisibilityRepositoryTest,TicketReadApiIntegrationTest,AuthServiceCurrentUserTest,AuthControllerMeMvcTest,SharedApiContractMvcTest,SecurityProblemHandlerTest,TicketPersistenceSqlServerIntegrationTest,TicketSupportingPersistenceSqlServerIntegrationTest,TicketReadSqlServerIntegrationTest' test
```

B2 — options:

```powershell
.\mvnw.cmd '-Dtest=TicketReadServiceTest,TicketReadApiIntegrationTest' test
```

B3 — mapper (cùng lệnh cho lần fail compile và lần pass):

```powershell
.\mvnw.cmd '-Dtest=TicketResponseMapperTest,TicketReadApiIntegrationTest' test
```

B4 — current user:

```powershell
.\mvnw.cmd '-Dtest=AuthServiceCurrentUserTest,AuthControllerMeMvcTest,TicketReadServiceTest,TicketReadApiIntegrationTest,TicketControllerMvcTest' test
```

B5 — timestamp/persistence, có Docker:

```powershell
$env:RUN_SQLSERVER_IT='true'
.\mvnw.cmd '-Dtest=TicketSupportingEntitiesTest,TicketSupportingPersistenceRepositoryTest,TicketSupportingPersistenceSqlServerIntegrationTest,TicketPersistenceSqlServerIntegrationTest' test
```

B6 — regression/build bình thường, cả hai container flags chưa bật:

```powershell
.\mvnw.cmd verify
```

B7 — baseline và final Ticket SQL Server, có Docker:

```powershell
$env:RUN_SQLSERVER_IT='true'
.\mvnw.cmd '-Dtest=TicketPersistenceSqlServerIntegrationTest,TicketSupportingPersistenceSqlServerIntegrationTest,TicketReadSqlServerIntegrationTest' test
```

Cờ env ở B5/B7 được đặt trong các process PowerShell riêng của từng command; không lưu thay đổi environment của user. Docker Server 29.5.2; dùng SQL Server 2022 Testcontainer hiện có, Flyway + Hibernate schema validation. Không reset Compose database của user.

Chạy F1–F4 và Q1 từ `D:\FA2026\SBA301\campusfix\frontend`.

```powershell
# F1: baseline, group 5 và group 6
npm.cmd run test -- src/features/tickets src/features/auth/context/AuthProvider.test.jsx

# F2, F3, F4
npm.cmd run lint
npm.cmd run test
npm.cmd run build

# Q1: chỉ format các file frontend đã sửa
.\node_modules\.bin\prettier.cmd --write src/features/tickets/api/ticketApi.js src/features/tickets/api/ticketApi.test.js src/features/tickets/components/TicketErrorState.jsx src/features/tickets/hooks/useTicketSearchParams.js src/features/tickets/hooks/useTicketSearchParams.test.jsx src/features/tickets/hooks/useTickets.js src/features/tickets/pages/TicketDetailPage.jsx src/features/tickets/pages/TicketListPage.jsx src/features/tickets/pages/TicketListPage.test.jsx
```

Q2 chạy từ repository root:

```powershell
git diff --check
```

### Container skips trong lệnh bình thường

Baseline B1 skip 21: TicketPersistenceSqlServerIntegrationTest 4, TicketReadSqlServerIntegrationTest 8, TicketSupportingPersistenceSqlServerIntegrationTest 9. Khi flag được bật, parameterized methods tạo nhiều test cases hơn số method disabled nên số chạy thực tế là 54/55, không phải 21/22.

Final B6 skip 47 do opt-in flags chưa bật:

| Suite | Skipped |
| --- | ---: |
| AdminBootstrapSqlServerIntegrationTest | 4 |
| AssetPersistenceSqlServerIntegrationTest | 7 |
| CategoryFormSqlServerIntegrationTest | 5 |
| LegacyDocumentBackfillIntegrationTest | 1 |
| LocationEquipmentSqlServerIntegrationTest | 1 |
| MongoPersistenceIntegrationTest | 2 |
| SqlServerMigrationIntegrationTest | 1 |
| TechnicianPersistenceSqlServerIntegrationTest | 1 |
| TicketPersistenceSqlServerIntegrationTest | 5 |
| TicketReadSqlServerIntegrationTest | 8 |
| TicketSupportingPersistenceSqlServerIntegrationTest | 9 |
| UserPersistenceSqlServerIntegrationTest | 3 |

B7 đã chạy lại toàn bộ ba Ticket SQL suites với 0 skip. Các SQL suites khác và Mongo/backfill container suites không được bật trong regression bình thường; không tuyên bố đã chạy chúng. Không chạy full regression với toàn bộ container flags.

Phạm vi bằng chứng: backend HTTP bằng MockMvc trên H2 và SQL Server thực; frontend HTTP/UI bằng Axios/MSW và Testing Library. Chưa kiểm tra thủ công qua browser kết nối backend thật hoặc toàn bộ tính năng ngoài Ticket. Build pass không có nghĩa chứng minh hệ thống tuyệt đối không thể có bug.

## I. Remaining complexity

| Phần giữ lại | Vì sao cần |
| --- | --- |
| TicketListQuery và shared pagination/parameter validation | Contract thật: whitelist, enum, duplicate/blank, integer/range/offset và stable sort; xóa sẽ làm query sai bị bỏ qua hoặc lỗi runtime |
| TicketSpecifications/EXISTS | Security và count/pagination đúng scope, gồm assignment lịch sử và multi-role; không thay bằng Java filtering |
| Current DB user/status/roles + viewer exception translation | Quyền có thể bị thu hồi sau phát token; malformed/inactive/no-role phải fail an toàn và giữ convention Ticket |
| Separate supporting queries và ordered repositories | Quan hệ một chiều, form lịch sử, thứ tự ổn định; tránh fetch nhiều collections cùng page/aggregate |
| Explicit DTO mapper | Không expose entity/storage keys/password/email; giữ response contract và raw JSON |
| Constructors, composite FKs, uniqueness, @Version, còn lại timestamp callbacks | Integrity và persistence requirements thực, không phải defensive logic tùy tiện |
| URL request khác display controls; viewer query keys, AbortSignal, cache clearing, retry rules | Giữ raw invalid query cho backend, tránh precision/cache/privacy vấn đề; phân biệt 4xx với network/5xx |
| Dynamic field rendering và history/metadata UI | Requirement nhiều field types; IMAGE khác scalar; option lịch sử cần hiển thị đúng |
| SQL Server Testcontainers và opt-in flags | H2 không chứng minh T-SQL, JSON constraints, offsets, FK/NO ACTION hoặc migration schema thực |
| Workflow policy và Mongo comment/backfill đã tồn tại | User yêu cầu bảo toàn deliverable milestone khác; không tham gia redesign lần này |

Không tiếp tục rút gọn những phần này chỉ để giảm số dòng. Các hành vi được chứng minh trong test vẫn đúng Issue; không có thay đổi nghiệp vụ cần user chấp thuận thêm.
