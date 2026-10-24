# Kiểm thử và live demo

## 1. Phạm vi bằng chứng

Kiểm thử gồm unit/service policy, repository/API integration trên H2, database thật bằng Testcontainers và UI qua Vitest/Testing Library. MSW chỉ mô phỏng transport; không chứng minh quyền backend, persistence hoặc transaction.

H2 compatibility mode giúp chạy nhanh; SQL Server là nơi kiểm T-SQL, filtered index, khóa và concurrency. Test có Docker được bật riêng bằng RUN_SQLSERVER_IT hoặc RUN_MONGODB_IT.

## 2. Lệnh chạy

Backend, từ backend/:

```powershell
.\mvnw.cmd verify
```

SQL Server thật, Docker đang chạy:

```powershell
$env:RUN_SQLSERVER_IT = 'true'
try { .\mvnw.cmd verify }
finally { Remove-Item Env:RUN_SQLSERVER_IT }
```

Có thể chọn suite liên quan để chạy nhanh hơn:

```powershell
$env:RUN_SQLSERVER_IT = 'true'
try {
    .\mvnw.cmd '-Dtest=TicketAttachmentUploadSqlServerIntegrationTest,TicketCreateSqlServerIntegrationTest,TechnicianWorkSqlServerIntegrationTest' test
}
finally { Remove-Item Env:RUN_SQLSERVER_IT }
```

MongoDB thật:

```powershell
$env:RUN_MONGODB_IT = 'true'
try { .\mvnw.cmd '-Dtest=MongoPersistenceIntegrationTest' test }
finally { Remove-Item Env:RUN_MONGODB_IT }
```

Frontend, từ frontend/:

```powershell
npm ci
npm run lint
npm run test -- --maxWorkers=2
npm run build
```

Hai worker giúp giảm tải khi Docker chạy cùng lúc. Không thay timeout/assertion chỉ để che lỗi môi trường. Test data phải được tạo và dọn trong phạm vi fixture sở hữu; không drop schema hoặc xóa dữ liệu module khác.

## 3. Danh sách test cases

Đây là danh mục theo hành vi, gom nhiều test tự động cùng mục đích. Mỗi dòng chỉ ra nơi tìm bằng chứng; không phải khẳng định từng thao tác đã được chạy lại trong lần chỉnh tài liệu.

Các test Java dưới backend/src/test/java/com/nexora; test UI dưới frontend/src/features.

| ID    | Tình huống / kết quả mong đợi                                                           | Nguồn kiểm thử tiêu biểu                                                                |
| ----- | --------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------- |
| TC-01 | Login hợp lệ; thiếu/sai token hoặc user không ACTIVE bị từ chối                         | auth tests; TicketAttachmentUploadApiContract                                           |
| TC-02 | Admin bootstrap chỉ chạy khi opt-in local/demo; không đổi Admin cũ                      | AdminBootstrap service/runner tests                                                     |
| TC-03 | Quyền user/role, validation và stale update                                             | user controller/service tests                                                           |
| TC-04 | Category draft save/publish/archive; immutable published; revision conflict             | CategoryControllerMvcTest, CategoryServiceJpaTest, CategoryFormSqlServerIntegrationTest |
| TC-05 | Dynamic value đúng kiểu; false/zero không bị coi là thiếu; decimal/date/regex đúng      | TicketSubmissionValidatorTest; shared ticket-submission-values.json                     |
| TC-06 | Tạo Ticket giữ snapshot form/priority/skill/SLA; refresh đọc được                       | TicketCreateApiContract                                                                 |
| TC-07 | Optional answer được bỏ; IMAGE có mapping; field lạ/inactive/duplicate bị từ chối       | TicketCreateApiContract, TicketSubmissionValidatorTest                                  |
| TC-08 | Thiếu reference, stale form, incompatible legacy rules trả lỗi đúng                     | TicketCreateApiContract                                                                 |
| TC-09 | Visibility own/current/history/all và role union không đếm đôi                          | TicketReadApiContract, TicketVisibilityRepositoryTest                                   |
| TC-10 | List query invalid/sort/filter/pagination bị từ chối                                    | TicketListQueryTest, TicketReadApiContract                                              |
| TC-11 | Review/assign đúng quyền; skill/coverage/capacity thay đổi phải kiểm lại                | TicketActionApiContract                                                                 |
| TC-12 | Hai assignment cùng tranh capacity; rollback không để state/history một phần            | TicketActionSqlServerIntegrationTest                                                    |
| TC-13 | Start chỉ current TECHNICIAN trên ASSIGNED                                              | TechnicianWorkApiContract                                                               |
| TC-14 | Work log chỉ current TECHNICIAN trên IN_PROGRESS; timestamp/minutes hợp lệ              | TechnicianWorkApiContract                                                               |
| TC-15 | Resolve cần work log/outcome/summary; kết thúc assignment và ghi history                | TechnicianWorkApiContract                                                               |
| TC-16 | Close chỉ reporter có REQUESTER, RESOLVED và không active assignment; stale version 409 | TechnicianWorkApiContract                                                               |
| TC-17 | Upload 1/5 JPEG/PNG, metadata server-owned, null fieldDefinition                        | TicketAttachmentUploadApiContract                                                       |
| TC-18 | Upload thiếu quyền/former assignee/invisible/state sai trả 401/403/404/422 phù hợp      | TicketAttachmentUploadApiContract                                                       |
| TC-19 | Metadata/path/parts/JSON key/type ngoài contract bị từ chối                             | TicketAttachmentUploadApiContract                                                       |
| TC-20 | Empty/MIME/signature/count/size sai; đúng 5 MiB được chấp nhận                          | TicketAttachmentUploadApiContract, LocalFileStorageServiceTest                          |
| TC-21 | Giới hạn 5 MiB/file và 30 MiB/request qua HTTP container thật                           | TicketAttachmentUploadApiIntegrationTest                                                |
| TC-22 | Store ảnh thứ hai hoặc persistence thất bại: rollback toàn batch, giữ ảnh đã commit     | TicketAttachmentUploadApiContract                                                       |
| TC-23 | Upload không đổi status/version/resolution/history/work logs                            | TicketAttachmentUploadApiContract                                                       |
| TC-24 | Upload tranh với resolve/assignment change: cả hai thứ tự phải an toàn                  | TicketAttachmentUploadSqlServerIntegrationTest                                          |
| TC-25 | Image read đúng Ticket/visibility, tải bytes và filename an toàn                        | TicketAttachmentContent service/controller tests; upload contract                       |
| TC-26 | UI select/preview/remove, validate trước gửi, khóa pending, success refresh             | TechnicianWorkPage.test.jsx                                                             |
| TC-27 | UI failure giữ draft, không tự retry; quyền/state đổi yêu cầu reload                    | TechnicianWorkPage.test.jsx                                                             |
| TC-28 | Mocks on/off không bật tính năng hoãn; production không chứa preview bootstrap          | TicketDemoScope.test.jsx; production build inspection                                   |
| TC-29 | Dashboard role scope, SLA hai giờ, ISO week Bangkok; close không đổi resolved metric    | dashboard tests                                                                         |
| TC-30 | Asset/location/technician CRUD, trạng thái và quyền quản lý                             | Test suites theo feature                                                                |
| TC-31 | MongoDB mappings/indexes, SQL ID references và backfill                                 | MongoPersistenceIntegrationTest và migration tests                                      |
| TC-32 | End-to-end start/log/optional upload/resolve/reporter read/close                        | Upload contract; live demo ở phần 5                                                     |

## 4. Kết quả đã ghi nhận

Mốc **24/10/2026**, sau phần upload ảnh kết quả và trước lần chuẩn hóa tài liệu:

| Lệnh / kiểm tra                             | Kết quả                                                             | Giới hạn                                                             |
| ------------------------------------------- | ------------------------------------------------------------------- | -------------------------------------------------------------------- |
| Backend verify mặc định                     | 1.027 tổng; 885 executed thành công; 142 skipped; 0 failures/errors | Không phải toàn bộ suite Docker đã được chạy                         |
| H2 upload + LocalFileStorageServiceTest     | 54 thành công, gồm 42 case upload                                   | Không thay thế concurrency SQL Server                                |
| SQL Server upload với RUN_SQLSERVER_IT=true | 41 thành công / 42 case; 1 skipped; 0 failures/errors               | Embedded HTTP case chỉ chạy trên H2                                  |
| Frontend full test                          | 597 thành công / 75 files                                           | UI tests chủ yếu dùng transport có kiểm soát                         |
| Frontend targeted sau format                | 39 thành công / 2 files                                             | Bổ sung xác nhận các file vừa thay đổi                               |
| Frontend lint và production build           | Thành công                                                          | Có advisory bundle >500 kB                                           |
| Production UI + API / SQL Server cô lập     | Luồng chính thành công; ảnh tải về trùng bytes upload               | Work log nhập qua API do browser automation không điền được datetime |

Các log gốc ở .data/qa/backend-upload-verify.log, backend-upload-sqlserver.log và frontend-upload-tests.log trong môi trường local; thư mục này bị Git ignore và có thể không tồn tại ở máy clone mới. Khi nộp, đính kèm bản log đã loại secrets, thời gian, môi trường và commit được kiểm.

Chưa có số liệu statement/branch/line coverage được xuất. Không ghi phần trăm coverage khi chưa đo; số test passed không phải code coverage.

## 5. Kịch bản demo trực tiếp

Chuẩn bị trước theo [SETUP](SETUP.md): database/backend đang chạy, frontend production build với mock flags false, tài khoản ACTIVE theo vai trò, kỹ thuật viên eligible và form published hợp lệ. Dùng dữ liệu demo riêng; tránh thay đổi dữ liệu người dùng thật.

| Bước | Người thực hiện    | Thao tác và bằng chứng cần thấy                                              |
| ---- | ------------------ | ---------------------------------------------------------------------------- |
| 1    | REQUESTER          | Tạo Ticket với category/location/form thật; refresh vẫn thấy SUBMITTED       |
| 2    | MANAGER/ADMIN      | Review; xem ứng viên; assign đúng người, trạng thái ASSIGNED                 |
| 3    | TECHNICIAN         | My Work thấy Ticket; start; lưu work log hợp lệ                              |
| 4    | TECHNICIAN         | Chọn JPEG/PNG, preview/remove; upload; notice thành công; refresh vẫn có ảnh |
| 5    | TECHNICIAN         | Resolve với outcome/summary; assignment kết thúc                             |
| 6    | Reporter           | Mở Ticket; xem/tải ảnh; đối chiếu bytes hoặc nội dung; close                 |
| 7    | Reporter / quản lý | Refresh thấy CLOSED, giữ ảnh và resolution; Dashboard đúng scope             |

Demo thêm một trường hợp âm ngắn: mở Ticket ở user khác hoặc gửi stale version, cho thấy backend từ chối và UI yêu cầu tải lại. Không demo các action đang tắt như thể đã hoàn thiện.

Bằng chứng upload đã ghi nhận: version 2 → 2, IN_PROGRESS giữ nguyên, history hiện có không tăng; resolve/close sau đó tới CLOSED/version 4. [Ảnh hỗ trợ](evidence/ticket-upload-attachment.png) ghi nhận attachment sau close.

**Live system là bằng chứng chính.** Screenshot, log và test report hỗ trợ phần trình bày; không thay live demo bằng video quay sẵn.

## 6. Chuẩn bị kết quả nộp

Ghi commit/hash, ngày giờ, OS/runtime, lệnh, số passed/failed/skipped và lý do skip. Lưu Surefire reports ở backend/target/surefire-reports và output Vitest cần thiết. Không đưa token/mật khẩu/log có dữ liệu nhạy cảm vào repository.

Lần thay đổi chỉ tài liệu không yêu cầu chạy lại toàn bộ business tests; cần kiểm links, sơ đồ, test-suite references, số slide và đối chiếu thông tin với mã nguồn. Nếu có sửa code/config ảnh hưởng runtime, chạy lại kiểm tra tương ứng.
