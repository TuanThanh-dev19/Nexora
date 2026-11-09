# Phan Tuấn Thành — Giải thích code và thay đổi thực tế

Mốc source: **develop / 5cd03ec**, ngày 09/11/2026. Đọc cùng tài liệu 01 để phân biệt code trực tiếp và phần nhóm. Các đoạn trích bên dưới được lấy nguyên văn từ source; số dòng được chốt theo snapshot này. Chỉ mô tả hành vi có trong code, không giả định mọi action trong contract đều có endpoint.

## 1. Bản đồ luồng và state cần nhớ

```mermaid
sequenceDiagram
    actor U as Người dùng
    participant UI as Panel / Dialog React
    participant Q as Hook React Query
    participant API as ticketApi
    participant C as Controller + RequestReader
    participant S as Service
    participant DB as SQL Server
    U->>UI: Mở action, xác nhận dữ liệu đã thấy
    UI->>UI: Giữ snapshot ticket/version trong dialog
    UI->>Q: mutateAsync(action, payload)
    Q->>API: Gửi request khi feature/quyền UI cho phép
    API->>C: Bearer JWT + JSON hoặc multipart
    C->>S: Payload hợp lệ
    S->>DB: Current user; lock Ticket; kiểm tra preconditions
    S->>DB: Ghi dữ liệu + history trong transaction
    DB-->>S: Flush / commit hoặc rollback
    S-->>Q: DTO hoặc ProblemDetail
    Q->>Q: Thành công: invalidate tickets/technicians/dashboard
    Q-->>UI: Refetch; cập nhật trạng thái hoặc hiện lỗi
```

| State | Nơi giữ | Điều phải giải thích |
| --- | --- | --- |
| Filters/page/sort | URL trong `useTicketSearchParams` | Back/Forward giữ điều kiện; đổi filter reset page 0; request giữ query entries |
| Server data | React Query theo viewer + ticket/search | Không dùng cache của user/role khác; invalidate sau action |
| Dialog đang xác nhận | `TicketActionPanel.dialog = { action, ticket }` | Snapshot version không tự thay khi background refetch; 409 yêu cầu xác nhận lại |
| Candidate selection/reason | `TicketAssignmentDialog` | Đổi page/filter/search xóa lựa chọn; 422 bỏ candidate cũ; 409 khóa submit |
| Ảnh chưa upload | `WorkImages.previews` và `urls` ref | File + object URL; lỗi giữ lựa chọn; thành công/bỏ ảnh/unmount dọn URL |
| Ticket/assignment/history | SQL Server | Source of truth cho workflow; transaction giữ nhất quán |
| File bytes | Local storage | Metadata ở SQL; rollback có callback dọn file, không có transaction phân tán |

## 2. Đọc danh sách và chi tiết Ticket — UC19/20/21

**Ownership:** `1d4d54d`, `cf0aa38`; currentAssignment thêm ở `429218b`, assignmentHistory ở `13e9c00`. Keyword là phần Thoại Thanh `e572384`.

**Purpose:** người dùng chỉ thấy phiếu trong phạm vi quyền; mở lại dữ liệu theo đúng form từng dùng, kể cả form/option đã ngừng dùng.

**Input/Trigger:** mở `/tickets` hoặc `/tickets/:ticketId`, thay URL filter, phân trang; GET `/api/v1/tickets` và GET `/api/v1/tickets/{ticketId}`.

**Processing:** `useTicketSearchParams` giữ query string; `ticketApi.search` tạo URLSearchParams thay vì ép locationId sang Number. Controller gọi `TicketListQuery.from(MultiValueMap)`: chặn tham số lạ/lặp, pagination/range/sort sai; sort thêm `id ASC` phá hòa. `AuthService.getCurrentUser` đọc user ACTIVE và roles hiện tại từ DB. `TicketReadService.list` ghép **visibility AND filters** trước phân trang/count.

{{code|backend/src/main/java/com/nexora/ticket/service/TicketReadService.java|public PageResponse<TicketSummaryResponse> list|6}}

{{code|backend/src/main/java/com/nexora/ticket/repository/TicketSpecifications.java|public static Specification<Ticket> visibleTo|19}}

Giải thích `visibleTo`: Manager/Admin không bị giới hạn scope; Requester theo reporter; Technician theo **từng có assignment**, không chỉ active. User nhiều role nhận hợp các quyền qua OR. EXISTS không nhân đôi ticket khi một người có nhiều assignment. Bộ lọc sau đó dùng AND, không được OR với visibility vì có thể mở rộng quyền.

**Data flow/state:** URL → `useTickets`/`useTicket` (query key chứa user ID và roles đã sort) → API → service → specification query DB → mapper/DTO → table/detail. Không lọc quyền sau khi tải toàn bộ rows. `findAll`/`findOne` có EntityGraph các quan hệ to-one; không khẳng định mọi đường đọc đều hết N+1.

`detail` tìm **id AND scope** trước khi đọc con. Sau đó lấy `ticket.getFormVersion().getId()`, query fields/options cùng stored version; đọc raw values, status history, REPORT_EVIDENCE/WORK_RESULT metadata và assignment trail. Mapper không trả JPA entity, password hash hay storage key. `valueJson` được trả nguyên chuỗi; IMAGE thể hiện bằng attachment gắn field.

{{code|backend/src/main/java/com/nexora/ticket/service/TicketReadService.java|public TicketDetailResponse detail|13}}

**Output/Error:** PageResponse hoặc TicketDetailResponse; invalid query 400, user không hợp lệ 401, thiếu role phù hợp 403, ticket mất/ngoài scope cùng thông báo 404. UI ưu tiên error trước cached data; retry query chỉ tối đa một lần bổ sung cho network/5xx (`retryTicketQuery`), không retry 4xx.

**Design decision:** scope ở DB giữ count/page đúng quyền; stored form giữ ý nghĩa lịch sử; raw query tránh âm thầm biến request sai thành request hợp lệ. Không nhận là cải thiện hiệu năng định lượng vì chưa benchmark.

**Change impact:** thêm filter category phải sửa `TicketListQuery` allowlist/parser, `TicketSpecifications.filters`, `TicketFilters`/search hook/UI và contract tests. Đổi Technician chỉ được xem assignment hiện tại phải sửa `visibleTo`, nhưng sẽ mất quyền xem lịch sử của người bị đổi; phải tách yêu cầu đọc lịch sử và quyền ghi. Đổi form rendering không được chuyển sang “latest published”.

**Mở test:** `TicketVisibilityRepositoryTest`, `TicketListQueryTest`, `TicketReadApiContract`, `TicketReadServiceTest`, `TicketResponseMapperTest`; phía UI `TicketListPage.test.jsx`, `useTicketSearchParams.test.jsx`.

## 3. Core/supporting persistence và workforce

**Ownership:** `9368bfe` (identity), `0c0d2ef` (workforce), `45aee8d` (core), `ee11d14` (supporting). Đây là nền tảng backend, không có UI/API riêng trong từng commit nền tảng.

**Purpose/Input:** service tạo/sửa entity, repository persist/load; bảo vệ quan hệ identity, technician và Ticket. Các object liên quan phải tồn tại trước; không cascade persist các reference tùy tiện.

{{code|backend/src/main/java/com/nexora/user/entity/TechnicianProfile.java|    @MapsId|8}}

`TechnicianProfile` dùng ID của AppUser làm PK/FK nhờ MapsId: một profile gắn đúng một user, không sinh identity người khác. Constructor `requirePersistedUser` đòi user có ID; `requirePositiveCapacity` chặn <=0; `@Version` phát hiện stale profile update. TechnicianSkill/TechnicianServiceArea dùng composite IDs; proficiency 1–5. **Direct coverage repository** trong persistence không tự duyệt descendant; coverage theo ancestor trong eligibility là bước nghiệp vụ sau.

`UserRole` là entity liên kết có `assignedAt` và `assignedBy`, thay vì quan hệ many-to-many chỉ giữ tập role. `AppUser.normalizedEmail` là giá trị DB generated/read-only; không nhận email hoa/thường thành hai danh tính. Enum status/code gắn JDBC VARCHAR để khớp schema.

`Ticket` cố định category và formVersion bằng `updatable=false`, constructor `requireMatchingFormVersion` chặn form thuộc category khác; default SUBMITTED; `@Version` là expected version của workflow. LAZY/no cascade tránh ghi/xóa nhầm các catalog/users.

{{code|backend/src/main/java/com/nexora/ticket/entity/Ticket.java|    @Version|3}}

`TicketFieldValue` lưu `value_json` là String trong NVARCHAR(MAX), kiểm field thuộc form đã lưu. Nó không thay thế validator kiểu/required/range của create service. `TicketStatusHistory` giữ action/from/to/actor/time, kể cả cùng trạng thái như REASSIGN. `TicketAttachment` chỉ giữ metadata và opaque storage key, không lưu bytes vào row.

**Data flow/state/output:** API nghiệp vụ của nhóm → service → entity constructor → repository → SQL constraints → DTO. Ví dụ form mismatch bị chặn ngay constructor; stale update bị ORM phát hiện; DB giữ FK/unique/check như lớp bảo vệ sau cùng. Không phải mọi IllegalArgumentException từ constructor đều có public API error mapping riêng: service/controller vẫn phải validate đầu vào.

**Design/Change impact:** thêm trường profile sửa entity/schema bằng migration mới, DTO/service/UI/test tương ứng; đổi capacity rule phải cập nhật eligibility và đường config có khóa. Đổi field JSON thành object parse/reserialize có thể đổi biểu diễn số lớn/escaping; phải bảo vệ contract. Đổi category/form đã lưu ảnh hưởng foreign keys và cách đọc lịch sử, không chỉ đổi một setter.

## 4. Workflow contract — metadata và giới hạn thực thi

**Ownership:** `d69667f`, package được di chuyển trong `1d4d54d`, REASSIGN giới hạn lại trong `13e9c00`.

**Purpose/Input:** service đưa status hiện tại + TicketAction để lấy transition được phép. Không cho client tự chọn arbitrary target status. SUBMIT dùng NEW_REQUEST metadata, không thêm pseudo-status vào DB.

{{code|backend/src/main/java/com/nexora/ticket/service/TicketWorkflowContract.java|public static TicketTransitionDefinition requireTransition|10}}

**Processing/state/output:** lookup action; kiểm source type và source statuses; trả immutable TicketTransitionDefinition hoặc InvalidStateTransitionException. Definition giữ roles/scopes, reason required, requirements/effects; `verifyCompleteness` kiểm đủ action, duy nhất SUBMIT là creation, terminal không có outgoing action. **Hàm này không tự kiểm user/reason/eligibility, không update Ticket, không ghi history.** Service phải thực hiện các bước đó.

**Design/Change impact:** contract tập trung state rule, service quản giao dịch; demoScope điều khiển phần đã hỗ trợ. Muốn thêm action phải sửa enum, definitions, service/DTO/controller, schema check nếu có, UI/flag và tests. Đừng chỉ bật flag hoặc thêm một status. Hiện reject/cancel/unassign/reopen/force-close là thiết kế/controls đã hoãn, chưa phải flow thật.

## 5. Review và assign — UC22/23

**Ownership:** Huy tạo assign/eligibility ban đầu `1b247a2`; bạn thêm review, lock/validation/DTO và gia cố assign trong `429218b`. Notification event hiện tại được nhóm thêm sau, không thuộc patch review/assign của bạn.

**Purpose/Trigger:** Manager/Admin tiếp nhận SUBMITTED rồi chọn Technician phù hợp. `TicketActionPanel.open` giữ `{action,ticket}`; dialog gửi `{version}` hoặc `{version,technicianId}` qua POST `/tickets/{id}/review` hoặc `/assign`.

**Processing:** request reader strict kiểm kiểu/thuộc tính; `getViewer` kiểm current user; Manager/Admin; `lockedTicket` đọc Ticket bằng PESSIMISTIC_WRITE, so version **trước** state transition. Review đổi UNDER_REVIEW và ghi BEGIN_REVIEW. Assign chặn active assignment có sẵn, khóa destination profile, đánh giá lại eligibility trong transaction, tạo assignment, đổi ASSIGNED và ghi ASSIGN history.

{{code|backend/src/main/java/com/nexora/ticket/service/TicketAssignmentService.java|private Ticket lockedTicket|10}}

{{code|backend/src/main/java/com/nexora/ticket/service/TicketAssignmentService.java|// Every assignment/configuration writer|12}}

`evaluateCandidateForAssignment` dùng Propagation.MANDATORY và refresh user/required skill/location ancestors bằng PESSIMISTIC_READ khi ghi. Đây là phần bạn bổ sung lên eligibility có sẵn. Candidate list chỉ là gợi ý tại lúc đọc; không thể tin trạng thái “đủ điều kiện” lúc mở modal.

Điều kiện nghiệp vụ đang dùng: user ACTIVE + TECHNICIAN + profile tồn tại + available + active count < max + matching active required skill (nếu yêu cầu) + active service area thuộc location hoặc ancestor. Hiện không override. **Ticket lock** chặn hai request cùng phiếu; **profile lock** nối tiếp các phân công cho cùng Technician ở các phiếu khác nhau để bảo vệ slot capacity cuối. Không tuyên bố khóa này loại bỏ mọi deadlock; tác dụng phụ thuộc các writer dùng quy ước tương thích.

**Data flow/state:** UI action → mutation → API/controller → service/locks → ticket_assignments + tickets + ticket_status_history cùng transaction → `flush()` → DTO có version mới → invalidate tickets/technicians/dashboard → refetch detail/list/candidates. Flush thực thi SQL để đọc version đã tăng, **không phải commit**.

**Output/Error:** role sai 403; không có Ticket 404; stale 409; state/eligibility/active assignment sai 422; body sai 400. 422 assign/reassign invalidate candidates và bỏ selection, cho chọn lại. 409 giữ ngữ cảnh cũ, khóa xác nhận đến khi reload.

**Design/Change impact:** không dùng “max(active)+1” hay chỉ khóa Ticket để giữ capacity toàn cục. Muốn assign trực tiếp SUBMITTED phải sửa contract và tests/UI, đồng thời quyết định có bỏ bước review/history không. Muốn override phải thêm policy, quyền, reason, strict request reader và cả validation/giải thích UI; không chỉ xóa check `isEligible`.

**Test minh chứng:** `TicketActionApiContract.changedEligibilityIsCheckedAtWriteTime`, `differentTicketsCannotConsumeTheSameLastCapacitySlot`, `concurrentAssignmentOfOneTicketReturnsOneSuccessAndOneStaleWrite`, `persistenceFailureRollsBackEveryWrite`.

## 6. Reassign atomic — UC26, phần nên bảo vệ đầu tiên

**Ownership:** `13e9c00`, [issue #92](https://github.com/TuanThanh-dev19/Nexora/issues/92), [PR #97](https://github.com/TuanThanh-dev19/Nexora/pull/97).

**Purpose/Trigger:** thay Technician trước khi bắt đầu việc, giữ dấu vết người cũ. Dialog chọn người khác, nhập reason; POST `/api/v1/tickets/{id}/reassign` với `version`, `technicianId`, `reason` trim, bắt buộc 1–1000 ký tự theo validation.

**Processing:** current Manager/Admin → Ticket lock → version → contract chỉ ASSIGNED → active assignment tồn tại → người mới khác người cũ → profile lock → kiểm eligibility tại thời điểm ghi → end assignment cũ → flush → insert replacement → touch Ticket → same-status REASSIGN history → flush/version mới.

{{code|backend/src/main/java/com/nexora/ticket/service/TicketAssignmentService.java|        current.end(now, request.reason());|16}}

Tại sao flush giữa end và insert? SQL có filtered unique index `uq_ticket_assignments_one_active ON ticket_id WHERE ended_at IS NULL`. Chỉ đổi object trong memory chưa chắc UPDATE được Hibernate chạy trước INSERT. Flush giải phóng entry active cũ trong **cùng transaction**, để insert mới hợp lệ. Nếu bước sau thất bại, cả UPDATE đã flush vẫn rollback.

Tại sao touch? Status vẫn ASSIGNED; nếu chỉ đổi bảng assignment/history thì row Ticket có thể không dirty, `@Version` không tăng. `ticket.touch(now)` đổi updatedAt để version của aggregate phản ánh lần đổi người; request start/reassign cũ bị từ chối.

**Data/state/output:** assignment cũ có endedAt/endReason; assignment mới active, acceptedAt null; Ticket vẫn ASSIGNED, version +1; thêm một REASSIGN từ ASSIGNED sang ASSIGNED. `TicketReadService.detail` trả assignmentHistory mới nhất trước và currentAssignment là row endedAt null. My Work của A mất phiếu, B có phiếu sau refetch; A vẫn đọc lịch sử vì `visibleTo` xét từng có assignment, nhưng `start` yêu cầu current assignee nên A không được bắt đầu.

**Error:** 400 malformed/missing reason; 403 sai vai trò; 404 thiếu Ticket/user đích; 409 version cũ; 422 sai trạng thái, cùng người, thiếu assignment hoặc không đủ eligibility. Không kết thúc assignment cũ trước khi kiểm xong eligibility. Nếu reassign và start tranh nhau: request giữ lock trước có thể thành công; request sau dùng version cũ nhận 409. Với version mới nhưng IN_PROGRESS, reassign vẫn bị state rule chặn.

**Design/Change impact:** chỉ hỗ trợ ASSIGNED; chưa có unassign/IN_PROGRESS transfer/override/notification cho reassign. V12 mở `end_reason` từ 50 lên NVARCHAR(1000), đồng bộ DTO/parser/entity/UI, không sửa migration cũ. Nếu cho đổi khi IN_PROGRESS, phải quyết định công việc đang nhận, log/outcome/capacity, acceptedAt và trách nhiệm giữa người cũ/mới; sửa contract, service, UI, tests hai thứ tự cạnh tranh.

**Test nên mở:** `reassignFailureRollsBackEvenAfterTheOldAssignmentHasFlushed`, `reassignAndStartSerializeInBothOrders`, `reassignmentAndAssignmentCannotConsumeTheSameLastCapacitySlot` trong {{src|backend/src/test/java/com/nexora/ticket/testsupport/TicketActionApiContract.java|void reassignFailureRollsBackEvenAfterTheOldAssignmentHasFlushed}}.

## 7. Reporter close và scope demo — UC25

**Ownership:** `3129613`, issue #77/PR #87. Backend start/log/resolve dùng phía trước là code Oanh, không nhận là tự viết.

**Purpose/Input:** reporter đối chiếu kết quả rồi xác nhận kết thúc RESOLVED; POST `/tickets/{id}/close` với version và reason tùy chọn tối đa 500 ký tự sau trim. Người có thêm role vẫn đóng được nếu có REQUESTER và đúng reporter.

**Processing:** lock Ticket → visibility → requester AND owner → version → contract RESOLVED/CLOSE → không có active assignment → `ticket.close(now)` → CLOSE history → flush. Không cho Manager đóng phiếu người khác bằng CLOSE; FORCE_CLOSE đang hoãn.

{{code|backend/src/main/java/com/nexora/ticket/service/TicketAssignmentService.java|        if (!roles.contains(RoleCode.REQUESTER) || !ticket.getReporter().getId().equals(viewer.id()))|15}}

**State/output:** status CLOSED, closedAt có giá trị, version tăng, thêm một history; resolutionOutcome/summary/resolvedAt được giữ nguyên. Không kết thúc assignment bất thường thay người dùng: nếu RESOLVED còn active assignment thì từ chối để lộ bất nhất cần xử lý.

**Data flow:** action owner UI → `useTicketWorkflow` → `ticketApi.close` → controller/service → SQL → invalidate Ticket + Technician + Dashboard. `demoScope` tách feature availability khỏi mock flag. `useTicketCollaboration` chặn query/mutation comments/feedback khi tắt; ẩn UI một mình là chưa đủ.

**Error/Why:** missing/invisible đều 404, visible nhưng không phải owner REQUESTER 403, version cũ 409, state/assignment sai 422. Kiểm ownership trước version/state hạn chế lộ tình trạng nghiệp vụ cho actor không được thao tác. Close không cần WORK_RESULT vì ảnh hiện optional; work log được kiểm ở resolve, không được nhận là close đã kiểm lại mọi rule resolve.

**Change impact:** muốn Manager force-close phải thêm endpoint/DTO reason bắt buộc và quyền riêng; không nới điều kiện owner trong CLOSE. Muốn ảnh bắt buộc phải thống nhất kiểm ở resolve hay close, cập nhật UI/contract/test với phiếu cũ. Bật comments cần backend/API authorization và persistence trước khi đổi flag.

**Test:** `closeTicket_reporterAndMixedRoles_preserveResolutionAndHistory`, `closeTicket_visibilityAndPermissionPrecedeVersionAndState`, `closeTicket_historyFailureRollsBackAllChanges`, `closeTicket_concurrentRequestsProduceExactlyOneSuccessAndOneStaleWrite` trong {{src|backend/src/test/java/com/nexora/ticket/testsupport/TechnicianWorkApiContract.java|void closeTicket_reporterAndMixedRoles_preserveResolutionAndHistory}}.

## 8. Upload ảnh kết quả — UC24

**Ownership:** `18a0219`, issue #88/PR #89. Tái sử dụng FileStorageValidator, FileStorageService và FileStorageTransactionCleanup có trước; bạn thêm upload service/controller/reader và nối UI.

**Purpose/Trigger:** current assigned Technician đưa ảnh kết quả để người có visibility đối chiếu. `WorkImages.send` validate UI rồi `ticketApi.uploadWorkResult` gửi FormData: đúng một metadata JSON string `{"attachmentType":"WORK_RESULT"}` và 1–5 parts `files`. Không gửi version trong API upload, không gửi storage path từ client.

**Processing:** `TicketMultipartReader.readWorkResult` loại unknown/duplicate metadata/extra parts/type khác; service kiểm current user ACTIVE qua AuthService, lock Ticket, visibility, role TECHNICIAN/current active assignment, IN_PROGRESS. Validate **toàn batch trước ghi file**: count, không rỗng, mỗi file <=5 MiB, MIME JPEG/PNG, signature tương ứng, filename 1–255.

{{code|backend/src/main/java/com/nexora/ticket/service/TicketAttachmentUploadService.java|        validator.validateCount(files);|8}}

{{code|backend/src/main/java/com/nexora/ticket/service/TicketAttachmentUploadService.java|        for (var file : files) {|8|2}}

Kiểm signature đọc prefix bytes theo MIME; không đủ để chứng minh file là ảnh giải mã được hoàn chỉnh, cũng không có quét malware/re-encode trong code này. Giới hạn request 30 MiB được mô tả/config multipart; không nhầm giới hạn batch upload với tổng số ảnh suốt đời Ticket.

**Data flow/state:** File UI → HTTP multipart → service/validator → local private storage + SQL ticket_attachments → AttachmentMetadata (không key/path/public URL) → invalidate/refetch Ticket → preview gọi content endpoint có kiểm quyền. Upload giữ nguyên status/version/history/work logs/resolution; **khóa Ticket vẫn cần** để serialize với resolve/reassignment dù không tăng version.

Nếu upload giữ lock trước, resolve đợi metadata/file hoàn tất; nếu resolve commit trước, upload đọc lại status và thất bại 422. Không dùng optimistic version cho thao tác chỉ bổ sung ảnh theo contract hiện tại.

**Rollback:** mỗi file store xong đăng ký `cleanup.deleteOnRollback(storageKey)`; lỗi storage/SQL sau đó rollback metadata và callback cố gắng xóa các file batch. Cleanup catch/log lỗi xóa. Không phải giao dịch phân tán hoàn toàn: crash process hoặc lỗi xóa có thể để file mồ côi; không tuyên bố bảo đảm tuyệt đối.

**UI state/Error:** pending khóa gửi/bỏ ảnh; thành công xóa previews, revoke URL và báo lưu. Lỗi giữ files để không bắt chọn lại. 403/404/409/422 khóa thao tác và hiện reload; mutation upload không tự refresh lúc lỗi để giữ lựa chọn. Lỗi 400/413/415/500 có thông báo, không báo success giả. Client dùng fetch, tự phát `nexora:unauthorized` khi 401; nếu response không phải JSON, `response.json()` có thể ném lỗi parse và UI rơi về thông báo chung — không khẳng định đã parse được mọi error body.

**Design/Change impact:** cho PDF/video cần sửa cả UI validator, backend allowed MIME/signatures, storage/content contract và preview; không chỉ sửa input accept. Đổi quota 5 lên 10 phải cập nhật validator/UI/OpenAPI/request-size/tests. Chuyển S3 cần implementation FileStorageService và chiến lược cleanup/read, không đưa key thành public URL tùy tiện. Upload hiện giữ DB lock qua I/O file, đổi lại transaction dễ kiểm chứng nhưng tăng thời gian giữ khóa.

**Test:** `validatesEntireBatchBeforeStorageAndAcceptsExactSizeLimit`, `cleansOnlyThisBatchOnStorageOrPersistenceFailure`, `uploadWaitsAndRechecksAfterResolveOrAssignmentChange`, `uploadHoldsTicketLockUntilFilesAndMetadataCommit` trong {{src|backend/src/test/java/com/nexora/ticket/testsupport/TicketAttachmentUploadApiContract.java|void validatesEntireBatchBeforeStorageAndAcceptsExactSizeLimit}}.

## 9. UI Technician, preview ảnh và Mongo foundation

### 9.1 My Work và xử lý Ticket — phần UI của bạn

`5d831a5` tạo `MyWorkPage`, `useAssignedTickets`, `TechnicianWorkPanel` và validation. Trigger mở `/my-work` → GET `/technicians/me/assigned-tickets`; backend của Oanh dùng current active assignment, mặc định ASSIGNED/IN_PROGRESS. Work panel dùng role TECHNICIAN + đúng current assignee + status để bật Start/log/upload/resolve. Payload action có expected version, mutation retry false, success refetch; error không tự nhận đã xử lý.

Khi giảng viên yêu cầu trace backend: `TicketAssignmentService.start` ghi acceptedAt + IN_PROGRESS; `addWorkLog` lưu log và touch/version; `resolve` cần ít nhất một saved log, outcome/summary, end assignment + RESOLVED/history. Đây là **hiểu dependency**, không nhận tác giả. Thay validation thời gian phải đồng bộ `technicianWorkValidation.js`, AddWorkLogRequest/request reader và backend rule/test.

### 9.2 Preview/download — phần client của bạn

`f615480` thêm `ticketApi.attachmentContent`: GET arraybuffer với bearer qua httpClient, kiểm MIME/nonempty, tạo Blob. Cách này xử lý vấn đề XHR/MSW giải mã blob thành text đã được ghi ngay trong comment code. Error JSON ở response nhị phân được TextDecoder/JSON.parse để UI hiểu detail/status.

{{code|frontend/src/features/tickets/components/TicketAttachmentPreview.jsx|    const next = URL.createObjectURL(query.data)|6}}

`TicketAttachmentPreview` query key gồm viewer/ticket/attachment; `retry:false`, `gcTime:0`. Effect tạo object URL và revoke khi dữ liệu đổi/unmount; khi ảnh decode lỗi, nút tải không xuất hiện. Backend content đọc file riêng, kiểm quyền là `cf08221` của Thái. Đổi auth/storage có thể ảnh hưởng cả download/preview; không dùng đường dẫn storage nội bộ làm `img src`.

### 9.3 Mongo/backfill/audit — nền tảng của bạn

`c76bb07`: SQL giữ relational data; Mongo giữ documents theo SQL IDs dạng Long, không DBRef/cross-store FK. `SqlReferenceValidator` kiểm user/ticket trước tạo document. `LegacyDocumentBackfillRunner.run` đọc các bảng cũ khi Flyway target 7, upsert theo legacySqlId, verify counts, ghi SQL verification marker; V8 chỉ drop khi rỗng hoặc marker/count hợp lệ. Runner chạy sau startup nên không thể để V8 drop trước rồi mới mong backfill chạy.

{{code|backend/src/main/java/com/nexora/audit/event/AfterCommitAuditEventListener.java|    @TransactionalEventListener|10}}

`persistAfterSqlCommit` chỉ ghi Mongo sau SQL commit; catch/log lỗi vì không thể rollback SQL đã commit. Đây là best effort, chưa có durable outbox cho audit. Metadata listener tồn tại không chứng minh mọi Ticket action đã publish AuditEventRequested. Notification nghiệp vụ hiện tại có đóng góp sau của Oanh; phân biệt với audit foundation này.

Đổi yêu cầu “audit không được mất” cần outbox/retry/idempotency và test crash/recovery; không chỉ bỏ catch hoặc đổi AFTER_COMMIT sang BEFORE_COMMIT. Bản backfill kiểm count không là checksum so sánh toàn bộ nội dung từng record.

## 10. Bug/refactor/change review theo diff trước và sau

Các mục “nguy cơ” bên dưới là suy luận trực tiếp từ patch, không bịa rằng đã xảy ra incident production. Feature thiếu endpoint được gọi là bổ sung chức năng, không tự gắn nhãn bug đã được người dùng báo.

### B1. Enum JDBC không khớp VARCHAR SQL Server

1. **Vấn đề:** mapping enum status/code chưa chỉ định JDBC VARCHAR dù schema dùng VARCHAR.
2. **Nguyên nhân/trước sửa:** chỉ có `@Enumerated(EnumType.STRING)`; tên enum dạng chuỗi không tự cam kết JDBC type khớp cột.
3. **Thay đổi thật:** thêm `@JdbcTypeCode(SqlTypes.VARCHAR)` cho AppUser.status và Role.code, kèm imports.
4. **Vì sao:** sửa mapping Java để khớp schema, không sửa migration đã áp dụng.
5. **Kết quả/ảnh hưởng:** rõ JDBC type cho schema validation/binding. PR #20 mô tả fix tương thích SQL Server; không có log lỗi gốc trong patch để trích nguyên lỗi.
6. **Evidence:** `6fbcce6` branch riêng; patch đã tích hợp trong `d69667f`. Hai file ở package user/entity.

{{diff|d69667f|backend/src/main/java/com/nexora/user/entity/AppUser.java}}

### B2. Lặp parse JWT subject và đọc options qua bước trung gian

1. **Trước:** `TicketReadService.viewer` regex/parseLong subject rồi AuthService lại parse; detail lấy fields → field IDs → options IN query, có nhánh form rỗng.
2. **Nguyên nhân:** trách nhiệm xác định identity rải ở hai nơi; đường đọc options đi vòng qua list ID.
3. **Sửa:** `cf0aa38` chuyển validation subject đầy đủ vào `AuthService.getCurrentUser`, Ticket chỉ translate InvalidCredentialsException; options query trực tiếp stored formVersion ID; mapper tách locals/form helpers.
4. **Vì sao:** một nơi quyết định identity/range; đọc cùng version rõ ràng hơn.
5. **Ảnh hưởng:** giữ safe 401/404 và lịch sử form; form rỗng có thể vẫn query options rỗng. Không tuyên bố nhanh hơn vì không benchmark.
6. **Evidence:** diff `cf0aa38` ở AuthService, TicketReadService, FieldOptionRepository, TicketResponseMapper và unit/SQL tests. **Commit này không sửa frontend** dù tài liệu refactor lịch sử có mô tả cả frontend; frontend URL handling đã có trong `1d4d54d`.

{{diff|cf0aa38|backend/src/main/java/com/nexora/ticket/service/TicketReadService.java}}

### B3. Assign có thể phản hồi version trước flush và kiểm dữ liệu chưa ổn định

1. **Trước:** assign dùng `findOne(scope AND id)`, rồi `save(ticket)` và map response ngay; evaluateCandidate thông thường. Đây là nền tảng Huy đã viết.
2. **Nguyên nhân/nguy cơ:** version JPA có thể chưa tăng trước serialize; Ticket chưa bị khóa ngay khi đọc; candidate/catalog có thể đổi giữa kiểm tra và ghi.
3. **Sửa:** `429218b` thêm `lockedTicket`, check version trước transition; lock profile; `evaluateCandidateForAssignment` refresh/locks; `recordTransition`; flush rồi mapper.
4. **Vì sao:** serialize thay đổi cùng Ticket và capacity của Technician; response phải chứa version đã thực thi update.
5. **Ảnh hưởng:** tăng bảo vệ concurrency nhưng giữ lock lâu hơn; stale trả 409, eligibility thay đổi trả 422; transaction rollback ticket/assignment/history nếu lỗi.
6. **Evidence:** TicketAssignmentService, TechnicianEligibilityService, TicketRepository, TechnicianService/AdminUserService và TicketActionApiContract trong commit trên.

### B4. UI close từng cho Manager đi qua trước owner

1. **Trước:** trong `availableTicketActions`, `if (manager) return true` đứng trước xử lý close; helper còn suy ra FORCE_CLOSE cho người không phải owner.
2. **Nguyên nhân:** UI policy dùng nhánh role rộng, chưa tách reporter close khỏi force-close ngoài scope.
3. **Sửa:** `3129613` thêm `if (action === 'close') return owner` trước manager, bỏ suy diễn FORCE_CLOSE; bổ sung API close strict và demoScope.
4. **Vì sao:** CLOSE là quyền reporter có REQUESTER, kể cả nhiều role; Manager khác người báo không được hưởng quyền đó.
5. **Ảnh hưởng:** UI và backend thống nhất; không bật các action chỉ có mock; refresh dashboard sau success.
6. **Evidence:** ticketWorkflow.js, TicketAssignmentService.close, CloseTicketRequest, hooks, tests nhiều role.

{{diff|3129613|frontend/src/features/tickets/constants/ticketWorkflow.js}}

### B5. Upload client có trước, backend chưa có endpoint thật

1. **Trước:** `ticketApi.uploadWorkResult`/UI contract tồn tại, nhưng chưa có controller/service upload thật; flow bị giới hạn scope.
2. **Nguyên nhân:** UI phát triển trước backend; có storage foundation không đồng nghĩa có public upload API.
3. **Sửa:** `18a0219` thêm controller/service/readWorkResult, authorization/state check, validate batch, save/cleanup, bật riêng flag; sửa `LocalFileStorageService.copyBounded` ném reason TOO_LARGE.
4. **Vì sao:** lỗi oversize khi stream phải giữ đúng loại lỗi để handler trả 413, không bị gom thành lỗi file chung. Cleanup dùng lại foundation, không tự nhận toàn bộ storage do mình viết.
5. **Ảnh hưởng:** upload thật, metadata không lộ key, Ticket version không đổi, lỗi giữ ảnh; rollback dọn batch. Giới hạn crash/cleanup vẫn còn.
6. **Evidence:** `18a0219`, TicketAttachmentUpload* + TicketMultipartReader + TechnicianWorkPanel/useTechnicianWork + storage diff.

{{diff|18a0219|backend/src/main/java/com/nexora/common/storage/LocalFileStorageService.java}}

### B6. Reassign cần giữ unique active assignment và version dù status không đổi

1. **Trước:** frontend có client/control mock, chưa có API reassign thật; workflow contract từng cho REASSIGN từ IN_PROGRESS, không đúng scope thực thi mới.
2. **Nguyên nhân/nguy cơ khi thêm:** INSERT replacement trước UPDATE end sẽ vướng unique active; chỉ đổi bảng con không đảm bảo Ticket version tăng.
3. **Sửa:** `13e9c00` thêm service/endpoint/request strict, giới hạn ASSIGNED; flush old end trước replacement, touch Ticket, same-status history, assignmentHistory DTO/UI; V12 nới end_reason 50→1000.
4. **Vì sao:** giữ một active assignment và expected version của aggregate; reason dài đồng bộ các lớp.
5. **Ảnh hưởng:** rollback cả old end dù đã flush; start/reassign cạnh tranh có test; người cũ còn read nhưng không write; không hỗ trợ đổi người IN_PROGRESS.
6. **Evidence:** `13e9c00`, TicketAssignmentService.reassign, TicketWorkflowContract, TicketAssignment, V12, request/parser và dialog/tests.

### B7. Blob response và vòng đời object URL ở ảnh

1. **Trước:** chưa có flow client đọc ảnh authorized trong ticketApi/preview này; không có before-fix code để khẳng định một bug production cụ thể.
2. **Vấn đề kỹ thuật được code ghi rõ:** XHR interceptor MSW có thể decode blob thành text; JSON ProblemDetail nằm trong response nhị phân.
3. **Sửa/bổ sung:** `f615480` dùng arraybuffer→Blob, TextDecoder khi JSON error; effect tạo/revoke object URL, gcTime 0.
4. **Vì sao:** giữ bytes để render/download, đọc lỗi đúng và giải phóng tài nguyên khi đóng modal.
5. **Ảnh hưởng:** preview/retry/download theo quyền; lỗi ảnh không làm vỡ toàn detail. Backend content vẫn là dependency của Thái.
6. **Evidence:** `f615480`, ticketApi.attachmentContent, TicketAttachmentPreview, TicketCollaboration.test.jsx. Mở `git diff 'f615480^' f615480 -- frontend/src/features/tickets/api/ticketApi.js` để thấy đây là phần thêm mới.

## 11. Checklist đổi yêu cầu và mở code

| Giảng viên yêu cầu | Chỗ sửa chính | Hệ quả phải nói trước khi sửa |
| --- | --- | --- |
| Reassign IN_PROGRESS | TicketWorkflowContract + reassign + dialog/constants | acceptedAt/log ownership; old/new permissions; concurrency với resolve/upload |
| Reason reassign tối đa 2000 | ReassignTicketRequest, TicketActionRequestReader.Reassign, entity + migration mới, UI max/validation/OpenAPI/tests | Cả history reason column và assignment end_reason phải chứa đủ; kiểm schema, không chỉ đổi textarea |
| Manager đóng thay reporter | Action mới force-close và service/controller/DTO/UI/flag | Quyền khác CLOSE, reason bắt buộc, history FORCE_CLOSE |
| Mỗi Ticket tối đa 5 ảnh tổng cộng | upload service + query count + lock + UI/error/tests | Hiện 1–5 mỗi request; cần kiểm tổng dưới Ticket lock |
| Ảnh bắt buộc trước resolve | Resolve path + UI resolution + contract + tests | Ảnh optional hiện tại; cần chính sách cho phiếu cũ |
| Technician chỉ đọc việc hiện tại | visibleTo và API contract tests | Khác My Work; mất lịch sử sau đổi người nếu không có route/scope khác |
| Thêm filter category | TicketListQuery, filters, UI/search/tests | Giữ AND với scope, query key, URL và pagination |
| Audit không được mất khi Mongo down | Outbox/retry/idempotency, listener và tests | AFTER_COMMIT catch/log hiện chưa đáp ứng durable delivery |

Khi thực hành, sửa giả định trên nhánh riêng theo yêu cầu giảng viên; tài liệu này **không sửa source dự án**. Có thể dùng test sẵn để giải thích invariant; không lấy nội dung PR ghi “passed” làm kết quả kiểm thử hiện tại.
