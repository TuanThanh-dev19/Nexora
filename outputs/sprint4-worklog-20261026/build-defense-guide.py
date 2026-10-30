from pathlib import Path
import json,re

root=Path('D:/FA2026/SBA301/campusfix')
j='backend/src/main/java/com/nexora/'
t='frontend/src/features/tickets/'
bt='backend/src/test/java/com/nexora/'
files=[]
def link(p, needle=None, label=None):
    target=root/p
    assert target.is_file(),f'Missing file: {p}'
    lines=target.read_text(encoding='utf-8-sig').splitlines()
    line=1
    if needle:
        matches=[i+1 for i,s in enumerate(lines) if needle in s]
        assert matches,f'Missing anchor {needle!r}: {p}'
        line=matches[0]
    files.append({'path':p,'line':line,'anchor':needle})
    return f'[{label or p}]({target.as_posix()}:{line})'
def F(p,why,needle=None): return (p,why,needle)

issues=[
dict(id=3,title='Thiết kế database ban đầu',prs=[4],kind='Nền tảng, không có màn hình riêng',
say='Em thiết kế schema SQL Server và các quan hệ, sau đó quản lý phiên bản bằng Flyway để cả nhóm khởi tạo cùng một cấu trúc database.',
flow='Ứng dụng khởi động → Flyway kiểm tra các migration đã chạy → áp dụng migration còn thiếu → Hibernate validate mapping entity với schema.',
demo='Mở schema hoặc migration V4, chỉ ra tickets nối với app_users, category/form và location; mở verify-schema.sql để giải thích kiểm tra FK, index và số bảng.',
point='Migration tạo bảng là nền tảng dữ liệu; API tạo Ticket là phần triển khai ở issue khác. Schema hiện tại còn 26 bảng SQL sau khi #7 chuyển ba nhóm document sang MongoDB.',
main=[F('backend/src/main/resources/db/migration/V1__create_user_and_role_tables.sql','Bảng tài khoản, role và liên kết user-role.'),F('backend/src/main/resources/db/migration/V4__create_ticket_workflow_tables.sql','Bảng Ticket, assignment, work log, history và attachment ban đầu.'),F('backend/src/main/resources/db/migration/V6__complete_erd_support_tables.sql','Hoàn thiện các bảng hỗ trợ trong ERD.'),F('infrastructure/sqlserver/nexora_full_schema.sql','Bản schema tổng hợp hiện tại để tra quan hệ.'),F('infrastructure/sqlserver/verify-schema.sql','Kiểm tra cấu trúc database hiện tại.'),F('docs/DATABASE_SCHEMA.md','ERD và mô tả các bảng để trình bày.')],
tests=[bt+'SqlServerMigrationIntegrationTest.java']),
dict(id=7,title='MongoDB và chuyển dữ liệu document khỏi SQL Server',prs=[8],kind='Nền tảng, không có UI nghiệp vụ riêng',
say='Em giữ dữ liệu quan hệ như Ticket và User trong SQL Server; audit, comment và notification dùng MongoDB. Dữ liệu cũ được backfill và kiểm chứng trước khi loại bỏ bảng SQL tương ứng.',
flow='Đọc ba bảng SQL cũ → upsert document theo legacySqlId → kiểm tra số lượng → lưu dấu xác nhận → migration V8 mới được phép loại bỏ bảng cũ.',
demo='Mở compose.yaml để chỉ hai database; mở LegacyDocumentBackfillRunner và V8 để giải thích chống migrate trùng và điều kiện trước khi drop bảng. Đây là giải thích mã, không cần chạy lại migration khi bảo vệ.',
point='Có document/repository không đồng nghĩa comment hoặc notification đã có API/UI hoàn chỉnh. Audit listener dùng AFTER_COMMIT; lỗi ghi MongoDB được log, không đảo ngược transaction SQL đã commit.',
main=[F('compose.yaml','Service SQL Server/MongoDB và môi trường chạy.'),F('backend/pom.xml','Dependency Spring Boot, JPA và Spring Data MongoDB.'),F(j+'migration/LegacyDocumentBackfillRunner.java','Đọc SQL, upsert và xác minh dữ liệu cũ.','public class LegacyDocumentBackfillRunner'),F('backend/src/main/resources/db/migration/V8__remove_document_data_tables_from_sql.sql','Chặn drop nếu backfill chưa được xác nhận.'),F(j+'audit/document/AuditEvent.java','Document audit_events.'),F(j+'ticket/document/TicketCommentDocument.java','Document ticket_comments; package hiện tại đã được đổi so với PR cũ.'),F(j+'notification/document/NotificationDocument.java','Document notifications.'),F(j+'audit/event/AfterCommitAuditEventListener.java','Ghi audit sau khi SQL commit.','@TransactionalEventListener'),F(j+'common/persistence/SqlReferenceValidator.java','Kiểm tra ID User/Ticket bên SQL trước khi tạo document.')],
tests=[bt+'MongoPersistenceIntegrationTest.java',bt+'LegacyDocumentBackfillIntegrationTest.java']),
dict(id=9,title='Persistence User và Role',prs=[],kind='Nền tảng dữ liệu; không phải issue viết API login',
say='Em mapping User, Role và bảng liên kết UserRole. Một user có thể mang nhiều role; thông tin người gán và thời điểm gán role vẫn được lưu.',
flow='Repository đọc app_users → tải user_roles/roles → tầng xác thực và các nghiệp vụ khác sử dụng identity/role đã có.',
demo='Mở AppUser, UserRole và AppUserRepository. Giải thích @Entity, @OneToMany, @EmbeddedId/@MapsId và @Version.',
point='normalized_email là cột do database sinh, không ghi bằng application. Password hash không được đưa vào DTO trả client. File identity cũng được sửa trong PR #19 của #11; không có PR riêng đóng #9 được xác định trong lần rà này.',
main=[F(j+'user/entity/AppUser.java','Mapping tài khoản, normalizedEmail, version và roleAssignments.','public class AppUser'),F(j+'user/entity/Role.java','Mapping danh mục vai trò.'),F(j+'user/entity/UserRole.java','Mapping bảng liên kết và metadata gán role.','public class UserRole'),F(j+'user/entity/UserRoleId.java','Khóa ghép userId/roleId.'),F(j+'user/repository/AppUserRepository.java','Truy vấn tài khoản và thông tin role.'),F(j+'user/repository/RoleRepository.java','Tra cứu vai trò.')],
tests=[bt+'user/repository/UserPersistenceRepositoryTest.java',bt+'UserPersistenceSqlServerIntegrationTest.java']),
dict(id=11,title='Thống nhất từ vựng domain giữa backend và frontend',prs=[19],kind='Quy ước dùng chung',
say='Em thống nhất mã role, status, priority và các enum để backend, database và frontend không dùng các tên khác nhau cho cùng một khái niệm.',
flow='Backend enum → lưu/đọc đúng giá trị trong SQL → API trả mã chuẩn → frontend dùng constant và nhãn hiển thị tương ứng.',
demo='Mở RoleCode và roles.js: cùng dùng REQUESTER, TECHNICIAN, MANAGER, ADMIN. Mở TicketStatus và ticketStatuses.js để đối chiếu mã trạng thái.',
point='Mã REOPENED/REJECTED/CANCELLED tồn tại trong domain không chứng minh các action tương ứng đã được bật trong demo.',
main=[F(j+'user/entity/RoleCode.java','Bốn role chuẩn.'),F(j+'user/entity/UserStatus.java','ACTIVE, LOCKED, DISABLED.'),F(j+'ticket/entity/TicketStatus.java','Các trạng thái chuẩn của Ticket.'),F(j+'common/domain/Priority.java','Mức độ ưu tiên chuẩn.'),F('frontend/src/shared/constants/roles.js','Role tương ứng bên frontend.'),F(t+'constants/ticketStatuses.js','Mã và nhãn trạng thái trên UI.')],
tests=[bt+'common/domain/CanonicalDomainVocabularyTest.java','frontend/src/test/domainVocabulary.test.js']),
dict(id=13,title='Contract chuyển trạng thái Ticket',prs=[20],kind='Luật workflow, không tự ghi database',
say='Em định nghĩa action nào được đi từ trạng thái nào sang trạng thái nào, role/quan hệ nào được thực hiện, cùng điều kiện và tác động dự kiến.',
flow='Service nhận action → requireTransition kiểm tra trạng thái nguồn → service kiểm tra quyền/điều kiện thực tế → cập nhật Ticket và history trong transaction.',
demo='Mở requireTransition rồi chỉ BEGIN_REVIEW: SUBMITTED → UNDER_REVIEW; ASSIGN: UNDER_REVIEW → ASSIGNED; CLOSE: RESOLVED → CLOSED.',
point='Contract mô tả nhiều nhánh lifecycle. Runtime demo chỉ bật tập action trong demoScope.js; riêng contract không tự kiểm tra database, đổi trạng thái hay lưu history.',
main=[F(j+'ticket/service/TicketWorkflowContract.java','Bảng quy tắc và requireTransition.','public static TicketTransitionDefinition requireTransition'),F(j+'ticket/service/TicketTransitionDefinition.java','Cấu trúc của một transition.'),F(j+'ticket/service/TicketActorScope.java','Quan hệ bắt buộc giữa actor và Ticket.'),F(j+'ticket/entity/TicketAction.java','Các action nghiệp vụ.'),F(j+'ticket/entity/TicketStatus.java','Trạng thái nguồn/đích.'),F('frontend/src/shared/config/demoScope.js','Đối chiếu tập action hiện được bật.')],
tests=[bt+'ticket/service/TicketWorkflowContractTest.java']),
dict(id=14,title='Persistence hồ sơ kỹ thuật viên, kỹ năng và khu vực phục vụ',prs=[24],kind='Nền tảng dữ liệu, chưa phải API eligibility',
say='Em xây dựng dữ liệu năng lực kỹ thuật viên: profile, availability, capacity, skill và service area để luồng phân công sau này có cơ sở kiểm tra.',
flow='AppUser đã tồn tại → tạo TechnicianProfile dùng cùng user ID → lưu liên kết skill và khu vực → service phân công/eligibility đọc dữ liệu này.',
demo='Mở TechnicianProfile: @MapsId dùng chung khóa với AppUser; chỉ available và maxActiveTickets. Sau đó mở TechnicianSkill và TechnicianServiceArea.',
point='Profile là dữ liệu khác với role TECHNICIAN. Issue #73 của Huy triển khai đánh giá eligibility; issue #14 của bạn là nền tảng lưu trữ mà service đó dùng.',
main=[F(j+'user/entity/TechnicianProfile.java','User ID dùng làm profile ID, availability, capacity và version.','public class TechnicianProfile'),F(j+'user/entity/Skill.java','Danh mục kỹ năng.'),F(j+'user/entity/TechnicianSkill.java','Kỹ năng của từng kỹ thuật viên.'),F(j+'user/entity/TechnicianServiceArea.java','Liên kết kỹ thuật viên với location phục vụ.'),F(j+'user/repository/TechnicianProfileRepository.java','Repository profile, hiện có cả truy vấn khóa phục vụ nghiệp vụ phân công.')],
tests=[bt+'user/repository/TechnicianPersistenceRepositoryTest.java',bt+'TechnicianPersistenceSqlServerIntegrationTest.java']),
dict(id=33,title='Persistence cốt lõi của Ticket',prs=[42],kind='Entity/repository, không có API tạo phiếu trong issue này',
say='Em mapping Ticket với người báo, category, form version, location, asset, trạng thái, SLA và version. Đây là bản ghi trung tâm của quy trình xử lý.',
flow='Service nghiệp vụ tạo/cập nhật Ticket → TicketRepository/JPA → bảng dbo.tickets; các quan hệ dùng FK tới entity tương ứng.',
demo='Mở Ticket.java, chỉ reporter, formVersion và @Version; mở TicketRepository. Giải thích vì sao Ticket giữ formVersion đã dùng để tạo phiếu.',
point='Giữ form version giúp đọc Ticket cũ đúng cấu trúc lúc gửi. Controller/service tạo Ticket ở #78 do Lâm Thoại Thanh phụ trách, dựa trên entity này.',
main=[F(j+'ticket/entity/Ticket.java','Mapping bảng tickets, quan hệ, timestamps và @Version.','public class Ticket'),F(j+'ticket/entity/ResolutionOutcome.java','Các mã kết quả xử lý.'),F(j+'ticket/repository/TicketRepository.java','Truy cập Ticket; hiện có findByIdForUpdate cho các bước workflow.')],
tests=[bt+'ticket/repository/TicketRepositoryTest.java',bt+'TicketPersistenceSqlServerIntegrationTest.java']),
dict(id=34,title='Dữ liệu động, lịch sử và metadata attachment của Ticket',prs=[45],kind='Persistence hỗ trợ Ticket',
say='Em tách dữ liệu trả lời biểu mẫu, lịch sử trạng thái và metadata ảnh khỏi bản ghi Ticket chính để mỗi nhóm dữ liệu được lưu và đọc đúng thứ tự.',
flow='Ticket liên kết ticket_field_values, ticket_status_history và ticket_attachments → các repository truy vấn theo thứ tự field/thời gian → API detail ghép thành DTO.',
demo='Mở ba entity: TicketFieldValue, TicketStatusHistory, TicketAttachment. Chỉ valueJson, fromStatus/toStatus/changedBy và storageKey/originalName.',
point='SQL lưu metadata, không lưu bytes ảnh. PR #45 có tiêu đề thiên về tài liệu nhưng head branch thực tế là feature/26-ticket-supporting-persistence và diff có các entity/repository này.',
main=[F(j+'ticket/entity/TicketFieldValue.java','Câu trả lời dynamic form dưới dạng JSON.'),F(j+'ticket/entity/TicketStatusHistory.java','Trạng thái trước/sau, action, actor và thời điểm.'),F(j+'ticket/entity/TicketAttachment.java','Metadata ảnh, kiểu attachment và opaque storage key.'),F(j+'ticket/repository/TicketFieldValueRepository.java','Lấy câu trả lời theo field order.'),F(j+'ticket/repository/TicketStatusHistoryRepository.java','Lịch sử theo thứ tự thời gian.'),F(j+'ticket/repository/TicketAttachmentRepository.java','Lấy metadata ảnh của Ticket.')],
tests=[bt+'ticket/repository/TicketSupportingPersistenceRepositoryTest.java',bt+'TicketSupportingPersistenceSqlServerIntegrationTest.java']),
dict(id=35,title='API và UI đọc danh sách/chi tiết Ticket',prs=[46],kind='GET API thật và màn hình đọc phiếu',
say='Em xây dựng luồng đọc Ticket có phân quyền: Requester xem phiếu mình, Technician xem phiếu được giao hiện tại hoặc lịch sử, Manager/Admin xem toàn bộ; nhiều role lấy hợp các quyền.',
flow='TicketListPage → useTickets → ticketApi.search → GET /api/v1/tickets → TicketReadService.list → TicketSpecifications → repository → DTO. Mở một dòng gọi GET /api/v1/tickets/{id}.',
demo='Mở /tickets, lọc status/priority/location, đổi trang và mở detail; thử tài khoản khác role. Refresh để thấy dữ liệu đọc từ server.',
point='API hiện hỗ trợ page, size, sort, status, priority, locationId; không có tìm kiếm từ khóa tự do. Không tìm thấy Ticket và không có quyền nhìn Ticket đều trả 404 an toàn.',
main=[F(t+'pages/TicketListPage.jsx','Màn danh sách và các trạng thái loading/empty/error.','export function TicketListPage'),F(t+'pages/TicketDetailPage.jsx','Màn chi tiết theo ticketId.','export function TicketDetailPage'),F(j+'ticket/controller/TicketController.java','Điểm vào GET list/detail.','public PageResponse<TicketSummaryResponse> list'),F(j+'ticket/service/TicketReadService.java','Áp dụng quyền và ghép dữ liệu.','public PageResponse<TicketSummaryResponse> list'),F(j+'ticket/repository/TicketSpecifications.java','Điều kiện quyền/filter đặt trong query SQL.','public static Specification<Ticket> visibleTo'),F(j+'ticket/service/TicketResponseMapper.java','Đổi entity sang response DTO.'),F(j+'ticket/dto/request/TicketListQuery.java','Parse query, allowlist và phân trang.'),F(t+'hooks/useTickets.js','Query lấy danh sách.'),F(t+'api/ticketApi.js','HTTP client Ticket.')],
tests=[bt+'ticket/testsupport/TicketReadApiContract.java',t+'pages/TicketListPage.test.jsx']),
dict(id=58,title='UI Manager xem xét và phân công',prs=[63],kind='UI; API ghi thật được bổ sung ở #79',
say='Em làm action panel và dialog phân công trên Ticket Detail. Nút được hiển thị theo role và trạng thái; modal giúp chọn kỹ thuật viên phù hợp và xử lý lỗi khi dữ liệu đã thay đổi.',
flow='Manager mở detail SUBMITTED → Bắt đầu xem xét → UNDER_REVIEW → Phân công → tải candidates → chọn người → xác nhận với version hiện tại → reload detail/history.',
demo='Đăng nhập Manager/Admin; chọn Ticket SUBMITTED; bấm Bắt đầu xem xét rồi Phân công. Chỉ phần tải việc/kỹ năng/khu vực và lý do candidate bị chặn trong dialog.',
point='PR UI ban đầu có contract/mock cho nhiều action. Demo hiện chỉ bật review/assign/close; reject, cancel, reassign, unassign và override đang hoãn. Candidate eligibility API/service gốc thuộc #73 của Huy.',
main=[F(t+'components/TicketActionPanel.jsx','Nhóm nút Xử lý phiếu và mở modal.','export function TicketActionPanel'),F(t+'components/TicketAssignmentDialog.jsx','Tìm/chọn ứng viên, workload, skill, coverage và lỗi.','export function TicketAssignmentDialog'),F(t+'components/TicketActionModal.jsx','Dialog xác nhận action.'),F(t+'hooks/useTicketWorkflow.js','Mutation, refresh cache và tải candidates.','export function useTicketWorkflow'),F(t+'constants/ticketWorkflow.js','Nhãn action và điều kiện hiển thị.','export function availableTicketActions'),F(t+'api/ticketApi.js','Gọi review/assign tới backend.')],
tests=[t+'pages/TicketWorkflow.test.jsx']),
dict(id=59,title='UI My Work và bảng xử lý của Technician',prs=[64],kind='UI Technician; backend #75/#81 thuộc Tô Phi Oanh',
say='Em làm màn My Work và bảng xử lý của kỹ thuật viên: xem việc hiện tại, bắt đầu, nhập nhật ký, chọn ảnh và hoàn tất xử lý. Backend ghi start/work log/resolve do Oanh triển khai.',
flow='GET /technicians/me/assigned-tickets → /my-work → mở detail → UI gửi start hoặc work log/resolve → mutation thành công làm mới My Work, detail và Dashboard.',
demo='Đăng nhập đúng Technician được giao, mở /my-work rồi Ticket Detail. Chỉ các nút Bắt đầu xử lý, ghi nhật ký và Hoàn tất xử lý; mở dialog và giải thích validation.',
point='Được xem phiếu từng nhận không đồng nghĩa được ghi lên phiếu đó. UI chỉ cho current assignee; backend kiểm lại. UI resolve yêu cầu có nhật ký; ảnh kết quả là tùy chọn.',
main=[F(t+'pages/MyWorkPage.jsx','Màn /my-work và danh sách việc hiện tại.','export function MyWorkPage'),F(t+'components/TechnicianWorkPanel.jsx','Nút/dialog start, work log, resolve và ảnh.','export function TechnicianWorkPanel'),F(t+'hooks/useTechnicianWork.js','Query/mutation và refresh cache.','export function useAssignedTickets'),F(t+'schemas/technicianWorkValidation.js','Kiểm tra nhật ký, kết quả và file trên client.'),F(t+'constants/technicianWork.js','Kiểm role, assignee và trạng thái cho UI.'),F('frontend/src/app/router.jsx','Route /my-work chỉ dành cho TECHNICIAN.')],
tests=[t+'pages/TechnicianWorkPage.test.jsx']),
dict(id=60,title='UI Ticket Detail, ảnh và các phần collaboration',prs=[71],kind='UI tổng hợp; comments/feedback hiện tắt',
say='Em hoàn thiện trang chi tiết để người dùng xem nội dung, câu trả lời biểu mẫu, lịch sử, người phụ trách và ảnh. Các action theo role được tích hợp cùng trang.',
flow='useTicket tải DTO → TicketDetailPage chia từng section → action panel/technician panel xử lý thao tác → preview ảnh lấy binary có xác thực rồi tạo Blob URL.',
demo='Mở /tickets/{id}; chỉ form đã snapshot, answers, history, attachment cards; mở một ảnh và tải xuống. Nếu ảnh lỗi, phần detail còn lại vẫn hoạt động.',
point='TicketComments/TicketFeedback có UI và contract lịch sử nhưng demoScope hiện tắt cả render lẫn query tương ứng. API đọc binary ảnh #76 thuộc Trần Quốc Thái; phần bạn là UI preview/tích hợp.',
main=[F(t+'pages/TicketDetailPage.jsx','Tổng hợp các section và feature gate.','export function TicketDetailPage'),F(t+'components/TicketDetailSections.jsx','Hiển thị form/answers/history và attachment metadata.'),F(t+'components/TicketAttachmentPreview.jsx','Modal ảnh, Blob URL, revoke khi đóng và nút tải.','export function TicketAttachmentPreview'),F(t+'components/TicketComments.jsx','UI comments đã xây dựng, đang tắt trong demo.'),F(t+'components/TicketFeedback.jsx','UI đánh giá đã xây dựng, đang tắt trong demo.'),F(t+'hooks/useTicketCollaboration.js','Chặn query/mutation collaboration khi feature chưa bật.'),F('frontend/src/shared/config/demoScope.js','features.comments=false và feedback=false.')],
tests=[t+'pages/TicketCollaboration.test.jsx',t+'pages/TicketDemoScope.test.jsx']),
dict(id=79,title='API review, assign và current assignment',prs=[86],kind='Backend nghiệp vụ nối với UI #58',
say='Em triển khai tiếp nhận và phân công thật. Một thao tác phải lưu đồng thời trạng thái Ticket, assignment và history; version cùng khóa dữ liệu giúp xử lý nhiều người thao tác đồng thời.',
flow='POST review {version} → khóa Ticket → kiểm version/state → UNDER_REVIEW + history → flush. POST assign {version, technicianId} → khóa Ticket rồi profile → đánh giá lại eligibility → tạo assignment + ASSIGNED + history → flush.',
demo='Network: bấm Bắt đầu xem xét/Phân công; chỉ payload version và technicianId. Trên UI xuất hiện assignee và history; F5 vẫn giữ. Dùng hai tab cùng version để giải thích 409 nếu tab thứ hai gửi dữ liệu cũ.',
point='GET candidates chỉ phản ánh lúc đọc. Assign kiểm lại eligibility trong transaction. Khóa profile ngăn hai Ticket cùng chiếm suất capacity cuối. Eligibility gốc do Huy làm; phần bạn là tích hợp kiểm lại và lưu phân công an toàn.',
main=[F(j+'ticket/controller/TicketController.java','Endpoint POST /review, /assign.','public TicketActionResultResponse review'),F(j+'ticket/service/TicketAssignmentService.java','Chỉ các phương thức review(), assign(), lockedTicket(), recordTransition() trong issue này.','public TicketActionResultResponse review'),F(j+'ticket/dto/request/ReviewTicketRequest.java','DTO version bắt buộc.'),F(j+'ticket/dto/request/AssignTicketRequest.java','DTO version và technicianId.'),F(j+'ticket/repository/TicketRepository.java','PESSIMISTIC_WRITE khi đọc Ticket để ghi.','findByIdForUpdate'),F(j+'user/repository/TechnicianProfileRepository.java','Khóa profile trong kiểm tra capacity.','findByIdForUpdate'),F(j+'ticket/entity/TicketAssignment.java','Assignment có thể accept/end theo nghiệp vụ.'),F(j+'ticket/service/TechnicianEligibilityService.java','Dịch vụ dùng chung được gọi lại khi assign; nguồn gốc #73 của Huy.')],
tests=[bt+'ticket/testsupport/TicketActionApiContract.java',bt+'TicketActionSqlServerIntegrationTest.java']),
dict(id=77,title='Đóng Ticket và hoàn thiện phạm vi demo thật',prs=[87],kind='API close và bật/tắt scope UI',
say='Em cho chính người báo xác nhận đóng phiếu đã được kỹ thuật viên xử lý. Server kiểm ownership, trạng thái và version trước khi lưu CLOSED, closedAt và history cùng transaction.',
flow='Reporter có REQUESTER mở phiếu RESOLVED → Đóng phiếu → POST /tickets/{id}/close {version} → kiểm visibility/ownership/version/state và không còn assignment mở → lưu CLOSED + closedAt + history.',
demo='Chuẩn bị một Ticket RESOLVED, đăng nhập reporter, bấm Đóng phiếu và refresh. Giải thích Manager/Admin không được đóng thay chỉ nhờ role quản lý; mixed-role vẫn phải đúng reporter có REQUESTER.',
point='Close khác resolve: Technician báo kết quả xử lý, reporter xác nhận đóng. demoScope chỉ bật những API đã tích hợp; reopen/force-close/comments/feedback vẫn hoãn. Không yêu cầu ảnh kết quả để close.',
main=[F(j+'ticket/controller/TicketController.java','Endpoint POST /{ticketId}/close.','public TicketActionResultResponse close'),F(j+'ticket/service/TicketAssignmentService.java','Phương thức close() và helper dùng chung.','public TicketActionResultResponse close'),F(j+'ticket/dto/request/CloseTicketRequest.java','Version bắt buộc, reason tùy chọn có giới hạn.'),F(t+'components/TicketActionPanel.jsx','Nút đóng và xác nhận trên detail.'),F(t+'hooks/useTicketWorkflow.js','Mutation close và invalidate dữ liệu.'),F('frontend/src/shared/config/demoScope.js','Phạm vi thật hiện được bật; upload được bật thêm ở #88.')],
tests=[bt+'ticket/testsupport/TechnicianWorkApiContract.java',t+'pages/TicketDemoScope.test.jsx']),
dict(id=88,title='Upload ảnh kết quả và tích hợp UI Technician',prs=[89],kind='UI → multipart API → filesystem + SQL metadata',
say='Em hoàn thiện upload ảnh kết quả thật cho kỹ thuật viên đang phụ trách phiếu IN_PROGRESS. File được kiểm tra nội dung và lưu đồng bộ với metadata; nếu transaction thất bại thì dọn file đã tạo.',
flow='Chọn ảnh/preview → Tải ảnh kết quả → FormData metadata WORK_RESULT + files → controller/multipart reader → khóa Ticket → kiểm quyền/state → validate từng ảnh → lưu bytes + metadata → HTTP 201 → reload attachments.',
demo='Dùng Technician đúng phân công trên phiếu IN_PROGRESS; chọn JPEG/PNG, bỏ một ảnh khỏi preview rồi upload; refresh vẫn thấy ảnh. So sánh version trước/sau: upload không tăng version và không đổi status.',
point='1–5 ảnh/request, mỗi ảnh >0 và tối đa 5 MiB; chỉ JPEG/PNG, kiểm MIME và signature. Ảnh là tùy chọn. Cả batch thành công hoặc rollback toàn bộ; upload giữ nguyên status, version, resolution và history.',
main=[F(t+'components/TechnicianWorkPanel.jsx','WorkImages: chọn, preview, bỏ ảnh và nút upload.','function WorkImages'),F(t+'hooks/useTechnicianWork.js','Mutation upload và refresh attachments.','export function useTechnicianMutation'),F(t+'api/ticketApi.js','FormData và HTTP upload.','async uploadWorkResult'),F(j+'ticket/controller/TicketAttachmentUploadController.java','POST multipart, trả danh sách metadata HTTP 201.','public List<AttachmentMetadata> upload'),F(j+'ticket/controller/TicketMultipartReader.java','Đọc metadata WORK_RESULT và từ chối part/field ngoài contract.','readWorkResult'),F(j+'ticket/service/TicketAttachmentUploadService.java','Quyền, khóa Ticket và lưu cả batch trong transaction.','public List<AttachmentMetadata> upload'),F(j+'common/storage/FileStorageValidator.java','Giới hạn số lượng, dung lượng, MIME và chữ ký nội dung.'),F(j+'common/storage/FileStorageTransactionCleanup.java','Tái sử dụng cleanup khi SQL rollback.','public void deleteOnRollback'),F(j+'ticket/entity/TicketAttachment.java','Metadata WORK_RESULT; bytes nằm ở storage.')],
tests=[bt+'ticket/testsupport/TicketAttachmentUploadApiContract.java',bt+'TicketAttachmentUploadSqlServerIntegrationTest.java'])
]

intro='''# Bảo vệ phần việc Phan Tuấn Thành — Nexora

Đối chiếu ngày 26/10/2026 với GitHub `TuanThanh-dev19/Nexora` và checkout `8f33803`. GitHub hiện gán 15 issue cho Phan Tuấn Thành (`TuanThanh-dev19`): 13 issue trong backlog đã rà, cộng #3 và #7 về database/hạ tầng. Tài liệu chỉ dẫn file hiện đang tồn tại; số dòng tương ứng checkout này.

## Đọc nhanh trước khi thầy chấm

**Câu giới thiệu khoảng 30 giây:**

> Em phụ trách nền tảng dữ liệu User/Role, kỹ thuật viên và Ticket; workflow contract; API đọc Ticket; giao diện Manager, Technician và Ticket Detail. Phần nghiệp vụ em hoàn thiện trực tiếp là review/assign, đóng Ticket và upload ảnh kết quả. Em sẽ demo luồng Manager xem xét và phân công vì phần này có đủ UI, API và lưu database.

**Phân biệt vai trò của file:** page là màn hình; component là một khối UI; hook gọi API/quản lý loading, lỗi và refresh; controller nhận HTTP; service kiểm nghiệp vụ và transaction; repository truy vấn database; entity mapping bảng; DTO là dữ liệu gửi/nhận; test chứng minh tình huống đúng/sai.

## Mở 5 file này trước

{quickfiles}

## Demo 3–5 phút

| Bước | Màn hình/thao tác | Câu nói ngắn | Issue của Thành |
|---|---|---|---|
| 1 | `/tickets`, lọc trạng thái rồi mở một phiếu | Em lọc và phân trang ở server, quyền xem dựa trên người đăng nhập. | #35 |
| 2 | Manager mở phiếu SUBMITTED, bấm **Bắt đầu xem xét** | Em chuyển sang UNDER_REVIEW và lưu history trong cùng transaction. | #58 + #79 |
| 3 | **Phân công**, chọn Technician đủ điều kiện và xác nhận | UI lấy candidates; backend kiểm lại eligibility/capacity tại lúc ghi rồi lưu assignment. | #58 + #79 |
| 4 | Technician mở `/my-work` rồi Ticket Detail | Đây là UI em làm; API My Work/start/work log/resolve do Oanh triển khai. | #59 |
| 5 | Trên phiếu IN_PROGRESS, chọn ảnh và **Tải ảnh kết quả** | Em lưu ảnh thật và metadata; refresh vẫn còn ảnh, status/version giữ nguyên. | #88 |
| 6 | Reporter mở phiếu RESOLVED và **Đóng phiếu** | Technician resolve; chính người báo xác nhận đóng. Em kiểm version, ownership và lưu CLOSED. | #77 |

Chuẩn bị ba tài khoản Requester/Manager/Technician và các phiếu ở trạng thái cần demo. Nếu chỉ có 90 giây, làm bước 2–3, refresh, rồi mở `review()`/`assign()` và history. Có thể chuẩn bị sẵn phiếu IN_PROGRESS/RESOLVED cho bước 5–6; không cần bắt thầy xem toàn bộ phần của nhóm.

## Luồng thật hiện tại và ranh giới đóng góp

```mermaid
flowchart LR
    A["Requester tạo phiếu<br/>Lâm Thoại Thanh"] --> B[SUBMITTED]
    B --> C["Review + Assign<br/>Thành: UI và API"]
    C --> D[ASSIGNED]
    D --> E["Start / Work log / Resolve<br/>Thành: UI; Oanh: API"]
    E --> F[RESOLVED]
    F --> G["Reporter Close<br/>Thành: UI và API"]
    G --> H[CLOSED]
    E -. "khi IN_PROGRESS" .-> I["Upload ảnh kết quả<br/>Thành: UI và API #88"]
```

Eligibility #73: Huy; API đọc binary ảnh #76 và Dashboard #72: Thái. Thành tích hợp các phần này, không nhận toàn bộ backend đó là của mình. `TicketAssignmentService.java` là file dùng chung: `review()`/`assign()` thuộc #79; `close()` thuộc #77; API `assignedTickets()`/`start()` thuộc #75 của Oanh; `workLogs()`/`addWorkLog()`/`resolve()` thuộc #81 của Oanh.

Trong UI hiện tại, **reject/cancel/reassign/unassign/reopen/force-close, comments và feedback đang tắt**. Có enum, contract hoặc component không đồng nghĩa chức năng đã chạy hoàn chỉnh bằng API thật. Điểm kiểm tra trực tiếp là `demoScope.js`.

## Tra nhanh 15 issue

| Issue | Phần việc | Cách thể hiện |
|---|---|---|
{index}

'''
quick=[
 (t+'components/TicketActionPanel.jsx','Nhìn UI: nút theo role/state.','export function TicketActionPanel'),
 (t+'components/TicketAssignmentDialog.jsx','Nhìn dialog chọn kỹ thuật viên.','export function TicketAssignmentDialog'),
 (j+'ticket/service/TicketAssignmentService.java','Nhìn nghiệp vụ: review(), assign(), close().','public TicketActionResultResponse review'),
 (j+'ticket/service/TicketAttachmentUploadService.java','Nhìn luồng upload thật, quyền và transaction.','public List<AttachmentMetadata> upload'),
 ('frontend/src/shared/config/demoScope.js','Nhìn đúng chức năng đang bật và đang hoãn.',None)
]
quicktext='\n'.join(f'{i}. {link(p,a)} — {why}' for i,(p,why,a) in enumerate(quick,1))
index='\n'.join(f'| [#{x["id"]}](#issue-{x["id"]}) | {x["title"]} | {x["kind"]} |' for x in issues)
out=[intro.replace('{quickfiles}',quicktext).replace('{index}',index)]
for x in issues:
    out.extend([f'<a id="issue-{x["id"]}"></a>\n',f'## Issue #{x["id"]} — {x["title"]}\n',
        f'Nguồn: [GitHub issue #{x["id"]}](https://github.com/TuanThanh-dev19/Nexora/issues/{x["id"]})'+''.join(f'; [PR #{p}](https://github.com/TuanThanh-dev19/Nexora/pull/{p})' for p in x['prs'])+'.\n',
        '**Em nói:**\n',f'> {x["say"]}\n','**File cần mở:**\n','| File hiện tại | Đọc để giải thích gì |','|---|---|'])
    for p,why,anchor in x['main']:
        out.append(f'| {link(p,anchor)} | {why} |')
    out.extend(['',f'**Luồng cơ bản:** {x["flow"]}\n',f'**Demo hoặc chỉ code:** {x["demo"]}\n',f'**Điểm cần nói đúng:** {x["point"]}\n',
        '**Test có sẵn để mở khi được hỏi:** '+', '.join(link(p,label=Path(p).name) for p in x['tests'])+'.\n'])

out.append('''## Request mẫu để giải thích Network

Trong URL dưới đây, `101` và `21` chỉ là ID minh họa; phải dùng ID thật khi demo. Version lấy từ Ticket Detail ngay trước thao tác.

```http
POST /api/v1/tickets/101/review
Authorization: Bearer <token>
Content-Type: application/json

{"version": 0}
```

```http
POST /api/v1/tickets/101/assign
Authorization: Bearer <token>
Content-Type: application/json

{"version": 1, "technicianId": 21}
```

```http
POST /api/v1/tickets/101/close
Authorization: Bearer <token>
Content-Type: application/json

{"version": 5}
```

Upload dùng `POST /api/v1/tickets/101/attachments`, `multipart/form-data`, gồm `metadata` là chuỗi JSON `{"attachmentType":"WORK_RESULT"}` và các part `files`. Client không gửi uploadedBy hoặc storageKey; server tự xác định. Upload không nhận version để đổi workflow; nó khóa Ticket để kiểm trạng thái/phân công trong cùng transaction.

## Các câu thầy có thể hỏi

| Câu hỏi | Trả lời ngắn, bám code |
|---|---|
| Vì sao cần cả frontend guard và backend kiểm quyền? | UI giúp người dùng không bấm sai; request vẫn có thể được tự gửi nên server luôn kiểm lại role, quan hệ và trạng thái. |
| Hai Manager cùng phân công thì sao? | Client gửi version; service khóa Ticket và kiểm version/state. Request cũ bị 409. Chống hai Ticket giành capacity cuối cần khóa thêm TechnicianProfile. |
| Vì sao khóa Ticket rồi mới profile? | Các writer dùng cùng thứ tự khóa để giảm nguy cơ deadlock, đồng thời đọc trạng thái/capacity có kiểm soát. |
| Vì sao gọi flush trước trả response? | JPA cập nhật @Version khi flush; response phải trả version mới, không tự cộng thêm một lần. |
| Vì sao cần transaction? | Ticket, assignment và status history phải cùng thành công hoặc rollback, tránh phiếu đổi trạng thái nhưng thiếu phân công/lịch sử. |
| Vì sao không chỉ tin candidate đã chọn ở UI? | Availability, capacity, skill hoặc coverage có thể thay đổi sau lúc mở modal; assign phải đánh giá lại lúc ghi. |
| Resolve và close khác nhau thế nào? | Resolve là kỹ thuật viên kết thúc lượt xử lý; close là reporter xác nhận đóng phiếu đã RESOLVED. |
| File ảnh nằm trong database không? | Bytes ở FileStorageService; SQL chỉ giữ metadata và opaque storageKey. API response không lộ key/đường dẫn nội bộ. |
| SQL rollback có tự xóa ảnh không? | Không. Sau khi store, đăng ký FileStorageTransactionCleanup để dọn file khi transaction rollback; lỗi cleanup riêng được log. |
| Vì sao kiểm MIME và signature? | Đổi đuôi/tự khai MIME chưa chứng minh bytes là JPEG/PNG; server kiểm chữ ký nội dung. |
| Vì sao Ticket giữ form version? | Biểu mẫu có thể được sửa/publish bản mới; phiếu cũ phải hiển thị đúng cấu trúc và câu trả lời lúc gửi. |
| Technician từng được giao còn quyền gì? | Có thể còn quyền đọc theo lịch sử assignment; quyền start/log/upload/resolve chỉ cho người đang được phân công. |
| MongoDB không có FK thì kiểm User/Ticket ra sao? | Service dùng SqlReferenceValidator kiểm SQL reference trước khi tạo document; chỉ lưu ID, không join trực tiếp hai database. |
| Vì sao có UI comments/feedback nhưng demo không thấy? | Đây là phần UI/contract đã xây dựng ở #60; phạm vi demo API thật hiện hoãn nên demoScope tắt cả component và request. |
| Em làm toàn bộ TicketAssignmentService à? | Em làm review/assign và close theo PR/issue. My Work/start/work log/resolve là phần backend Oanh bổ sung trong cùng file. |

## Chọn bài test để giải thích

Mở một ca kiểm thử quyền, một ca stale version và một ca rollback/concurrency. Giải thích theo ba bước: **chuẩn bị dữ liệu → gọi API/thực hiện action → assert response và database**. Các file test trong từng issue là bằng chứng cấu trúc kiểm thử có trong repo; lần soạn tài liệu này chỉ đọc mã và kiểm tra liên kết, không chạy lại toàn bộ test hoặc live demo.

## Nếu thầy chỉ yêu cầu xem vài file

1. Mở TicketActionPanel và TicketAssignmentDialog để chỉ đúng giao diện bạn làm.
2. Mở TicketController ở review/assign để chỉ request đi vào đâu.
3. Mở TicketAssignmentService.review()/assign() để nói quyền, version, khóa, eligibility và transaction.
4. Mở TicketStatusHistory hoặc TicketAttachmentUploadService để chứng minh phần persistence/ảnh thật.
5. Quay về UI refresh. Dữ liệu giữ nguyên giúp nối được lời giải thích UI → API → service → database.
''')
doc='\n'.join(out)
target=root/'docs/PHAN_TUAN_THANH_DEFENSE_GUIDE.md'
target.write_text(doc,encoding='utf-8')
inventory=root/'outputs/sprint4-worklog-20261026/defense-guide-file-index.json'
inventory.write_text(json.dumps({'issues':[x['id'] for x in issues],'files':files},ensure_ascii=False,indent=2),encoding='utf-8')
assert set(x['id'] for x in issues)=={3,7,9,11,13,14,33,34,35,58,59,60,77,79,88}
assert len(re.findall(r'^## Issue #',doc,re.M))==15
print(json.dumps({'guide':str(target),'issues':len(issues),'verifiedLinks':len(files),'uniqueFiles':len(set(x['path'] for x in files)),'lines':len(doc.splitlines())},ensure_ascii=False))
