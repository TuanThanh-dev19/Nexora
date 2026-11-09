# Phan Tuấn Thành — 28 câu hỏi bảo vệ và cách mở code

Mốc source **develop / 5cd03ec**, ngày 09/11/2026. Mỗi đáp án là khung nói, cần mở function và tự chỉ điều kiện cụ thể. Xem tài liệu 01 cho ownership/Git và tài liệu 02 cho flow/diff chi tiết.

## Lộ trình mở code trong 5–7 phút

1. **30–45 giây:** nói phần trực tiếp: Ticket persistence/read/workflow, UI Manager/Technician, close/upload/reassign; nêu backend start/log/resolve và storage/content là phần phối hợp.
2. **2 phút:** mở `TicketAssignmentService.reassign`, trace input→lock/version→eligibility→end/flush/insert→touch/history→DTO; mở diff `13e9c00`.
3. **1–2 phút:** mở `TicketAttachmentUploadService.upload`, chỉ permission/state và rollback cleanup; mở WorkImages khi hỏi UI state.
4. **1 phút:** mở `TicketReadService.detail`/`TicketSpecifications.visibleTo` để giải thích quyền và stored form.
5. **1 phút:** mở một test concurrency/rollback, rồi nêu một change impact. Không cần đọc hết các class.

## Câu hỏi và đáp án

### 01. Em trực tiếp làm những gì? Có bằng chứng nào rõ nhất?

“Em làm Ticket core/supporting persistence, list/detail, contract workflow, UI điều phối và Technician; sau đó làm/gia cố review-assign, reporter close, upload ảnh và reassign. Em mở diff `429218b`, `3129613`, `18a0219`, `13e9c00` để chứng minh. Backend start/log/resolve do Oanh, eligibility ban đầu do Huy, content ảnh do Thái.”

**Mở:** {{src|backend/src/main/java/com/nexora/ticket/service/TicketAssignmentService.java|public TicketActionResultResponse reassign}}; Git `git show --name-status 13e9c00`.

### 02. Vì sao không lấy số commit hoặc người merge làm tỷ lệ đóng góp?

“Một merge commit có thể đưa code người khác vào develop. Một commit tên docs như `ee11d14` lại thêm supporting entities và bootstrap. Em đối chiếu patch và tác giả theo từng chức năng, không quy số commit/số dòng thành tỷ lệ.”

**Mở:** `git show --stat ee11d14`; `git show --no-patch --format=fuller e308875`, rồi commit create `4fb7f65` của Thoại Thanh.

### 03. `TicketWorkflowContract.requireTransition` có tự chuyển trạng thái hay kiểm hết quyền không?

“Không. Nó kiểm action có hợp lệ từ source status rồi trả definition bất biến. Service vẫn phải kiểm current user, scope, reason, eligibility và tự ghi Ticket/history trong transaction. Có definition không đồng nghĩa đã có endpoint.”

**Mở:** {{src|backend/src/main/java/com/nexora/ticket/service/TicketWorkflowContract.java|public static TicketTransitionDefinition requireTransition}}; tiếp `TicketAssignmentService.recordTransition`.

### 04. Trace một lần Manager bấm ‘Phân công’ từ UI tới DB?

“ActionPanel giữ ticket/version lúc mở; AssignmentDialog lấy candidate và gửi version/technicianId. Hook gọi ticketApi.assign; controller đọc request strict; service kiểm quyền, khóa Ticket rồi profile, kiểm lại eligibility, lưu assignment/status/history, flush lấy version mới. Thành công invalidate tickets, technicians, dashboard để UI đọc lại.”

**Mở:** {{src|frontend/src/features/tickets/components/TicketActionPanel.jsx|  const open = (action)}} → {{src|frontend/src/features/tickets/components/TicketAssignmentDialog.jsx|  const submit = (event)}} → {{src|backend/src/main/java/com/nexora/ticket/service/TicketAssignmentService.java|public TicketActionResultResponse assign}}.

### 05. UI đã cho chọn ứng viên đủ điều kiện, sao backend kiểm lại?

“Danh sách là snapshot. Trong lúc chọn, người khác có thể giao việc hoặc đổi availability/skill/coverage. Em kiểm lại dưới transaction và profile lock bằng evaluateCandidateForAssignment. Dữ liệu thay đổi có thể trả 422, UI tải lại candidates và bỏ lựa chọn cũ.”

**Mở:** {{src|backend/src/main/java/com/nexora/ticket/service/TechnicianEligibilityService.java|evaluateCandidateForAssignment(}}; phần này em bổ sung `429218b`, evaluator nền tảng là Huy.

### 06. Chỉ khóa Ticket có chống vượt capacity Technician không?

“Không đủ. Hai phiếu khác nhau vẫn có thể cùng nhắm slot cuối của một Technician. Khóa destination profile nối tiếp việc đếm active assignments và phân công. Ticket lock bảo vệ một phiếu; profile lock bảo vệ tài nguyên dùng chung giữa nhiều phiếu.”

**Mở:** {{src|backend/src/main/java/com/nexora/ticket/service/TicketAssignmentService.java|// Every assignment/configuration writer}}; test `differentTicketsCannotConsumeTheSameLastCapacitySlot`.

### 07. Đã có pessimistic lock thì vì sao còn version?

“Lock xử lý cạnh tranh trong lúc transaction chạy. Version cho biết người dùng đang xác nhận dữ liệu đã cũ từ trước khi gửi. Sau khi chờ lock, request vẫn phải so expected version với DB; khác thì 409 để người dùng tải lại và quyết định trên dữ liệu mới.”

**Mở:** {{src|backend/src/main/java/com/nexora/ticket/service/TicketAssignmentService.java|private Ticket lockedTicket}}; {{src|backend/src/main/java/com/nexora/ticket/entity/Ticket.java|    @Version}}.

### 08. Vì sao `flush()` trước tạo DTO? Flush có phải commit không?

“JPA tăng version khi UPDATE được thực thi. Nếu map trước flush, response có thể còn version cũ. Flush đẩy SQL trong transaction để đọc version đúng; lỗi sau đó vẫn rollback. Commit mới là chốt transaction.”

**Mở:** `TicketAssignmentService.assign` ở đoạn `recordTransition` → `ticketRepository.flush()` → response; diff `429218b`.

### 09. `reassign` khác `assign` ở đâu?

“Assign tạo phân công khi đang UNDER_REVIEW hoặc REOPENED. Reassign chỉ ASSIGNED, phải có người hiện tại, chọn người khác và reason; kết thúc row cũ, tạo row mới, giữ status ASSIGNED, tăng version và ghi REASSIGN history.”

**Mở:** {{src|backend/src/main/java/com/nexora/ticket/service/TicketAssignmentService.java|public TicketActionResultResponse reassign}}; `ReassignTicketRequest`.

### 10. Bỏ `assignmentRepository.flush()` giữa end và insert thì sao?

“Hibernate có thể INSERT người mới trước khi UPDATE endedAt của row cũ. Filtered unique index chỉ cho một row active mỗi Ticket, nên insert có thể lỗi. Flush buộc old end chạy trước, nhưng vẫn cùng transaction; nếu insert/history lỗi thì old end rollback.”

**Mở:** {{src|backend/src/main/java/com/nexora/ticket/service/TicketAssignmentService.java|        current.end(now, request.reason());}}; {{src|backend/src/main/resources/db/migration/V4__create_ticket_workflow_tables.sql|CREATE UNIQUE INDEX uq_ticket_assignments_one_active}}.

### 11. Reassign không đổi status, vì sao `ticket.touch(now)`?

“Nếu chỉ đổi assignment/history, row Ticket có thể không dirty nên @Version không tăng. Touch cập nhật Ticket để request start/reassign cũ bị phát hiện. History vẫn phải có vì thay người là thay đổi nghiệp vụ dù status giống nhau.”

**Mở:** {{src|backend/src/main/java/com/nexora/ticket/service/TicketAssignmentService.java|        ticket.touch(now);}} trong method `reassign`.

### 12. Reassign và start đồng thời, ai thắng?

“Cả hai dùng Ticket lock. Nếu reassign commit trước, request start cũ nhận version conflict; đọc lại thì chỉ Technician mới được start. Nếu start trước, Ticket thành IN_PROGRESS; reassign dùng version cũ nhận 409, còn version mới vẫn bị state rule chặn.”

**Mở:** test {{src|backend/src/test/java/com/nexora/ticket/testsupport/TicketActionApiContract.java|void reassignAndStartSerializeInBothOrders}}; service `start` là dependency do Oanh viết.

### 13. Tại sao người cũ vẫn xem được phiếu nhưng không xuất hiện ở My Work?

“visibleTo kiểm từng có assignment để giữ quyền đọc lịch sử. My Work kiểm active assignment endedAt null, và write kiểm đúng current assignee. Quyền đọc lịch sử và quyền xử lý hiện tại là hai tiêu chí khác nhau.”

**Mở:** {{src|backend/src/main/java/com/nexora/ticket/repository/TicketSpecifications.java|visibleTo(}} và `currentTechnicianWork`; `TicketAssignmentService.start`.

### 14. Giảng viên yêu cầu cho reassign khi đang IN_PROGRESS, sửa một dòng được không?

“Chưa đủ. Phải thống nhất quyền với work đang chạy, acceptedAt, work logs, capacity, upload/resolve cạnh tranh. Em sửa contract, service, UI status rule và test hai thứ tự cạnh tranh. Không chỉ thêm IN_PROGRESS vào danh sách status.”

**Mở:** `TicketWorkflowContract.buildTransitions` đoạn REASSIGN; `TicketAssignmentService.reassign`; `ticketWorkflow.js`; test ở câu 12.

### 15. Reporter có thêm role Manager có được close không? Manager khác reporter thì sao?

“Có thêm Manager vẫn được close nếu có REQUESTER và đúng reporter. Manager khác reporter không được CLOSE. Em đưa nhánh close→owner lên trước manager trong UI và kiểm tương tự ở backend. FORCE_CLOSE là chức năng khác đang tắt.”

**Mở:** {{src|frontend/src/features/tickets/constants/ticketWorkflow.js|export function availableTicketActions}} và `TicketAssignmentService.close`; diff `3129613`.

### 16. Close có xóa kết quả xử lý hoặc tự kết thúc assignment không?

“Không. Close giữ outcome/summary/resolvedAt, thêm closedAt/status CLOSED và history. Nếu RESOLVED vẫn có active assignment thì báo 422 vì dữ liệu bất nhất. Nó không âm thầm sửa assignment để vượt điều kiện.”

**Mở:** {{src|backend/src/main/java/com/nexora/ticket/entity/Ticket.java|public void close(}} và `TicketAssignmentService.close`.

### 17. Trace một batch upload ảnh kết quả?

“UI giữ File và preview URL; gửi metadata WORK_RESULT cùng files. Reader kiểm multipart strict; service khóa Ticket, kiểm visibility/current Technician/IN_PROGRESS, validate toàn batch, lưu file và metadata, đăng ký cleanup rollback. Trả metadata rồi refetch; status và version Ticket giữ nguyên.”

**Mở:** {{src|backend/src/main/java/com/nexora/ticket/service/TicketAttachmentUploadService.java|public List<AttachmentMetadata> upload}}; {{src|backend/src/main/java/com/nexora/ticket/controller/TicketMultipartReader.java|public List<MultipartFile> readWorkResult}}.

### 18. Người dùng đổi đuôi `.exe` thành `.png` có qua không?

“Không chỉ kiểm tên. Backend kiểm MIME và prefix signature đúng JPEG/PNG, kích thước và count. File giả chỉ đổi đuôi thường bị chặn. Nhưng signature check không là giải mã/kiểm tra toàn bộ ảnh; em không khẳng định mọi nội dung có header đúng đều an toàn.”

**Mở:** {{src|backend/src/main/java/com/nexora/common/storage/FileStorageValidator.java|public void validateSignature(MultipartFile file)}}; validator là nền tảng dùng lại, không phải toàn bộ do em viết.

### 19. Upload file thứ ba lỗi sau khi hai file đầu đã ghi thì sao?

“Transaction rollback metadata. Mỗi file đã store có callback deleteOnRollback nên hai file trước được cố gắng xóa. Cleanup lỗi thì log; crash process có thể để file mồ côi. Đây là rollback compensation, chưa phải atomic transaction giữa DB và filesystem.”

**Mở:** loop store/cleanup trong upload; {{src|backend/src/main/java/com/nexora/common/storage/FileStorageTransactionCleanup.java|public void deleteOnRollback}}; test `cleansOnlyThisBatchOnStorageOrPersistenceFailure`.

### 20. Upload không có expected version, sao vẫn khóa Ticket?

“Upload không đổi version theo contract, nhưng cần giữ điều kiện IN_PROGRESS và current assignment trong suốt ghi file/metadata. Resolve dùng cùng Ticket lock: hoặc đợi upload commit, hoặc upload đợi rồi đọc state mới và bị từ chối. Nếu bỏ lock có thể lưu ảnh sau khi điều kiện quyền/state đã đổi.”

**Mở:** `TicketAttachmentUploadService.upload`; test `uploadHoldsTicketLockUntilFilesAndMetadataCommit`.

### 21. Lỗi upload có làm mất ảnh đã chọn không? Vì sao không retry mutation tự động?

“WorkImages chỉ xóa previews khi upload trả true. Lỗi giữ ảnh; lỗi quyền/state yêu cầu reload trước khi thao tác lại. Mutation retry false vì mất response không đồng nghĩa server chưa lưu, retry tự động có thể tạo ảnh hoặc action lặp.”

**Mở:** {{src|frontend/src/features/tickets/components/TechnicianWorkPanel.jsx|function WorkImages}} và {{src|frontend/src/features/tickets/hooks/useTechnicianWork.js|export function useTechnicianMutation}}.

### 22. 409 và 422 khác nhau thế nào trong dialog phân công?

“409 là version dữ liệu đã cũ, phải reload Ticket rồi xác nhận lại. 422 là điều kiện nghiệp vụ không còn phù hợp, ví dụ candidate hết capacity; hook tải lại candidates và selection bị bỏ để chọn lại. Không gom cả hai thành ‘lỗi mạng’ rồi gửi lại payload cũ.”

**Mở:** {{src|frontend/src/features/tickets/components/TicketAssignmentDialog.jsx|  const selectedTechnicianId}}; submit guard 409; `useTicketWorkflow.onError`.

### 23. Vì sao dialog giữ snapshot ticket, còn query key có user/roles?

“Snapshot giữ đúng version/ngữ cảnh người dùng đã xem; background refresh không tự thay nội dung đang xác nhận. Query key theo ID/roles tách server data theo viewer. Invalidate tickets/technicians/dashboard làm mới các view liên quan sau action, không tự sửa từng cache bằng phỏng đoán.”

**Mở:** `TicketActionPanel.open`; {{src|frontend/src/features/tickets/hooks/ticketQueryOptions.js|export function ticketViewerKey}}; `useTicketWorkflow.refresh`.

### 24. List/detail kiểm quyền ở đâu? Bỏ visibility và chỉ ẩn nút thì sao?

“Service ghép visibility với id/filter trong DB query. Missing và invisible trả safe 404; chưa đọc child data trước khi tìm được scoped Ticket. Chỉ ẩn UI thì người dùng vẫn gọi API trực tiếp được, nên không thể bỏ scope backend.”

**Mở:** {{src|backend/src/main/java/com/nexora/ticket/service/TicketReadService.java|public TicketDetailResponse detail}}; `TicketSpecifications.visibleTo`.

### 25. Tại sao Ticket giữ formVersion cũ, không lấy form published mới nhất?

“Câu trả lời đã được nhập theo field/options của version cũ. Form mới có thể đổi nghĩa hoặc loại bỏ option; render bằng latest sẽ làm sai lịch sử. Entity cố định formVersion/category; detail đọc fields/options theo version đã lưu, không bỏ option inactive của lịch sử.”

**Mở:** {{src|backend/src/main/java/com/nexora/ticket/entity/Ticket.java|    @JoinColumn(name = "form_version_id"}}; `TicketReadService.detail`; `TicketFieldValue` constructor.

### 26. Em đã refactor gì, và chứng minh không chỉ đổi cách viết?

“Trong cf0aa38 em bỏ parse JWT subject lặp ở Ticket service, đưa validation vào AuthService; query options trực tiếp bằng stored formVersion và tách nhóm mapper. Em mở diff và tests invalid subject/stored values để chứng minh behavior. Commit này chỉ sửa backend, không nhận phần frontend được mô tả trong report cũ là patch của commit đó.”

**Mở:** `git diff 'cf0aa38^' cf0aa38 -- backend/src/main/java/com/nexora/ticket/service/TicketReadService.java`; {{src|backend/src/main/java/com/nexora/auth/service/AuthService.java|public CurrentUserResponse getCurrentUser}}.

### 27. Tại sao audit ghi Mongo sau SQL commit? Có đảm bảo không mất audit không?

“Ghi trước khi SQL commit có thể để audit của transaction rollback. AFTER_COMMIT tránh trường hợp đó, nhưng Mongo lỗi thì hiện chỉ log, SQL đã chốt không rollback lại. Em chưa có durable outbox; muốn không mất audit phải thêm outbox/retry/idempotency. Cũng không nói mọi Ticket action hiện đã publish audit event.”

**Mở:** {{src|backend/src/main/java/com/nexora/audit/event/AfterCommitAuditEventListener.java|public void persistAfterSqlCommit}}; commit nền tảng `c76bb07`.

### 28. Phần khó nhất của em là gì? Em kiểm chứng bằng gì và còn thiếu gì?

“Phần reassign khó vì cùng status nhưng đổi assignee, giữ một assignment active, tăng version và cạnh tranh với start/capacity. Em dùng end→flush→insert trong một transaction, touch và history; test rollback sau flush và hai thứ tự tranh nhau. Upload còn bài toán dọn file khi SQL rollback. Em không nhận E2E #91 đã có suite chỉ vì issue đóng; hiện chưa thấy Playwright trong source này.”

**Mở:** {{src|backend/src/test/java/com/nexora/ticket/testsupport/TicketActionApiContract.java|void reassignFailureRollsBackEvenAfterTheOldAssignmentHasFlushed}}; {{src|backend/src/test/java/com/nexora/ticket/testsupport/TicketAttachmentUploadApiContract.java|void cleansOnlyThisBatchOnStorageOrPersistenceFailure}}.

## Thực hành trước buổi bảo vệ

- Chọn ba entry point: reassign, upload, read detail. Mỗi phần tự nói trong 60–90 giây theo **mục đích → input → điều kiện/processing → state/data → output/lỗi → vì sao → đổi yêu cầu**.
- Tại mỗi điều kiện, tự thử nói: “Bỏ dòng này thì invariant nào hỏng?” Phân biệt kết luận chắc từ code với nguy cơ cần test tái hiện.
- Khi mở test, đọc setup → request/race → assertion. Không chỉ chỉ tên test hoặc nói “có nhiều test”. Các suite H2 và SQL Server không tương đương; DB-specific index/locking cần SQL Server suite.
- Câu chưa có bằng chứng: trả lời “Trong source hiện tại em chưa xác minh phần đó; em mở commit/test để kiểm tra”, không tự gán issue hoặc người thực hiện.
- Không nói comments/feedback/reopen/unassign đã chạy thật. Không gọi `FileStorageTransactionCleanup` hay backend content/start/log/resolve là toàn bộ code riêng.

Các lệnh chỉ đọc, chạy tại root repo:

```powershell
Set-Location 'D:\FA2026\SBA301\campusfix'
git show --stat 13e9c00
git diff '13e9c00^' 13e9c00 -- backend/src/main/java/com/nexora/ticket/service/TicketAssignmentService.java
git diff '429218b^' 429218b -- backend/src/main/java/com/nexora/ticket/service/TicketAssignmentService.java
git diff '3129613^' 3129613 -- frontend/src/features/tickets/constants/ticketWorkflow.js
git diff '18a0219^' 18a0219 -- backend/src/main/java/com/nexora/ticket/service/TicketAttachmentUploadService.java
rg -n 'reassignAndStartSerialize|RollsBackEvenAfter|lastCapacity|LastCapacity' backend/src/test/java/com/nexora/ticket/testsupport/TicketActionApiContract.java
rg -n 'uploadHoldsTicketLock|cleansOnlyThisBatch' backend/src/test/java/com/nexora/ticket/testsupport/TicketAttachmentUploadApiContract.java
```

Tài liệu được kiểm tra đường dẫn/đoạn trích/diff khi soạn; **không chạy lại application tests hoặc demo trong lần soạn này**. Các kết quả pass lịch sử trong PR #86/#87/#89/#97 cần nêu đúng là kết quả được PR ghi nhận, không phải lần chạy hiện tại.
