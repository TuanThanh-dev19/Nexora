# Báo cáo nhập Product Backlog Nexora

Ngày kiểm tra: 12/10/2026 (Asia/Bangkok). Kết quả: **COMPLETED** cho việc tạo và kiểm tra workbook.

Workbook có 51 công việc, 3 sheet (`Product Backlog`, `Config`, `Import Audit`) và đúng 9 cột trên sheet chính. Có 14 Candidate Milestone 3: 5 nhiệm vụ có số dự kiến và 9 nhiệm vụ API chưa có số được nêu độc lập ở mục 6 của tài liệu Candidate. Candidate chưa được coi là scope đã cam kết.

## 1. Nguồn và số Issue đọc được

Đã đọc đầy đủ cả 5 file trong `D:\FA2026\SBA301\campusfix\docs`. Không thiếu tài liệu.

| Tài liệu | Issue / nhiệm vụ được mô tả | Dòng mới vào backlog | Xuất hiện được hợp nhất |
|---|---:|---:|---:|
| MILESTONE_1_ISSUES.md | 10 Issue chính | 10 | 0 |
| MILESTONE_2_ISSUES.md | 14 Issue chính; thêm 6 tham chiếu UI cũ được đối chiếu | 14 | 0 |
| MILESTONE_2_SUPPLEMENT_ISSUES.md | 32: 15 Issue hiện có, 12 UI dự kiến, 5 Candidate dự kiến | 18 | 14 |
| MILESTONE_2_SUPPLEMENT_UI_ISSUES.md | 12 Issue UI chi tiết | 0 | 12 |
| MILESTONE_3_CANDIDATE_API_ISSUES.md | 14: 5 Candidate chi tiết và 9 đề xuất API chưa có số | 9 | 5 |
| Tổng | 82 xuất hiện chính; 6 tham chiếu kế hoạch cũ | **51** | **31** |

Trong cùng một file, một Issue xuất hiện trong cả chỉ mục và mô tả chi tiết được tính một lần. Các dòng phụ thuộc, Acceptance Criteria, endpoint và phần nhắc lại công việc không được tách thành nhiệm vụ mới.

`Import Audit` có 88 bản ghi: 82 xuất hiện chính và 6 tham chiếu UI cũ. Mỗi bản ghi lưu file, ID nguồn, Index backlog, kết quả nhập, trường thiếu, cảnh báo, dòng nguồn, tiêu đề và các metadata gốc. Cột Index liên kết về dòng backlog; tên file liên kết về tài liệu local.

## 2. Xử lý trùng và phần bổ sung

31 Issue xuất hiện ở hai tài liệu đã được hợp nhất thông tin, không tạo thêm dòng:

- 14 Issue chính M2 được đối chiếu với chỉ mục GitHub: #27–#31 và #33–#41.
- 12 Issue UI có số dự kiến: #42, #43, #44, #45, #46, #47, #50, #52, #53, #54, #55, #56.
- 5 Candidate có số dự kiến: #48, #49, #51, #57, #58.

Các tham chiếu cũ được ghi riêng trong audit:

| Tham chiếu | Dòng backlog tương ứng | Cách xử lý |
|---|---|---|
| M3-FE02 | #42 và #43 | Kế hoạch bổ sung mô tả hai Issue UI độc lập: quản trị tài khoản và cấu hình Technician. |
| M3-FE03 / nội dung M2-06 cũ | #32; phần hoàn thiện sau là #44 | #32 là UI gốc có trong chỉ mục. #44 là phần bổ sung riêng, giữ hai Issue. Lịch/milestone cũ cần đối chiếu. |
| M3-FE04 | #35 | Tài liệu xác định rõ Ticket List/Detail UI thuộc scope #35, chỉ có một dòng. |
| M3-FE05 | #45 | Tham chiếu tới UI Location/Equipment trong kế hoạch bổ sung. |
| M3-FE06 | #46 | Tham chiếu tới UI Asset/History trong kế hoạch bổ sung. |
| M3-FE07 | #47 | Tham chiếu tới selectors trong kế hoạch bổ sung. |

#32 được nhập từ chỉ mục dù không có mô tả Issue chi tiết riêng. Không lấy Owner/deadline/branch của #44 để điền cho #32. #41 và #55 cũng được giữ riêng vì một Issue tạo contract/shell, Issue sau hoàn thiện shell theo role.

## 3. Số công việc theo Usecase

Không tìm thấy mã Usecase chính thức trong nguồn. Dropdown dùng nguyên tên nhóm feature trong `AI_PROJECT_KNOWLEDGE/03_Features.md`. Issue liên quan nhiều feature được xếp theo phạm vi chính; đây là phân loại quản lý từ nội dung, không phải mã Usecase mới.

| Usecase | Số công việc |
|---|---:|
| Authentication and User Management | 6 |
| Technician Workforce Management | 3 |
| Location Management | 3 |
| Equipment and Asset Management | 5 |
| Incident Category and Dynamic Form Management | 5 |
| Ticket Reporting | 7 |
| Ticket Review and Workflow | 4 |
| Ticket Assignment and Work Logs | 5 |
| Ticket Collaboration and Attachments | 4 |
| Feedback | 1 |
| Dashboard and Reporting | 3 |
| Chưa phân loại | 5 |
| Tổng | **51** |

5 công việc `Chưa phân loại` là M1-03 (vocabulary), M1-09 (API contracts), M1-10 (fixtures/bootstrap), #56 (UI/navigation/QA) và #58 (tích hợp API/QA). Các nhiệm vụ chung này chưa có Usecase nghiệp vụ riêng trong nguồn.

## 4. Số công việc theo trạng thái

| Trạng thái | Số công việc |
|---|---:|
| Backlog | 46 |
| To Do | 0 |
| In Progress | 0 |
| In Review | 0 |
| Done | 5 |
| Blocked | 0 |
| Cancelled | 0 |
| Tổng | **51** |

5 dòng Done là #27, #28, #30, #31 và #32, ánh xạ từ `Đã đóng` trong chỉ mục đối chiếu theo ảnh. Ghi chú ghi rõ chưa xác minh trạng thái GitHub live, CI hoặc merge; nguồn không nêu lý do đóng. Những Issue `Đang mở` được để Backlog vì không có bằng chứng phân biệt To Do/In Progress/In Review.

M1 không được đánh dấu Done chỉ vì tài liệu M2 giả định nền tảng M1 đã hoàn thành. Các ngày `Hoàn thành` trong kế hoạch được dùng làm deadline của từng Issue, không làm bằng chứng hoàn thành thực tế.

## 5. Các trường còn thiếu

| Trường | Có giá trị | Để trống | Phạm vi còn thiếu |
|---|---:|---:|---|
| Chủ sở hữu | 14 | **37** | 10 M1, #32, 12 UI bổ sung và 14 Candidate |
| Ngày đến hạn | 26 | **25** | 10 M1, #32 và 14 Candidate |
| Sprint Number | 0 | **51** | Tất cả công việc; không tài liệu nào nêu Sprint rõ ràng |
| Branch name | 41 | **10** | #32 và 9 đề xuất API chưa có số |

14 Owner được điền là `Member 1`–`Member 5` theo trường Owner và bảng phân công của 14 Issue chính M2. Đây là định danh thành viên từ tài liệu, không phải tên cá nhân đã được xác minh.

27 công việc có Owner đề xuất hoặc theo kế hoạch chưa chốt: 10 M1 ghi `Suggested owner`, 12 UI được mục 1 xác định là Owner đề xuất, và 5 Candidate còn chờ chọn scope. Các giá trị này được giữ trong Ghi chú và Import Audit; cột Chủ sở hữu để trống để tránh coi đề xuất là phân công đã xác nhận. 10 công việc còn lại (#32 và 9 đề xuất API) không có Owner riêng.

26 deadline gồm 14 ngày hoàn thành được nêu riêng từng Issue M2 và 12 hạn UI 15/10/2026. Không dùng lịch chung 26–30/09 của M1, không dùng lịch UI cho Candidate, không khôi phục lịch backend cũ 14–18/10 đã bị nguồn loại bỏ.

41 branch đều là branch đề xuất và đều có ghi `Branch đề xuất` trong Ghi chú. 24 branch M1/M2 còn placeholder `<issue-number>`; giữ nguyên theo nguồn. Không tự thay placeholder bằng số GitHub, không tạo branch mới.

## 6. Dữ liệu chưa xác định hoặc cần đối chiếu

| Nội dung | Phạm vi | Xử lý trong workbook |
|---|---|---|
| Số GitHub thực tế của M1 | M1-01–M1-10 | Giữ Planning ID; không tạo số GitHub. |
| Số Issue mới chưa xác nhận | 12 UI và 5 Candidate có số dự kiến | Ghi `#… dự kiến`; không tạo URL Issue giả. |
| Chưa có số Issue | 9 API ở mục 6 tài liệu Candidate | Truy vết bằng tên file, mục và dòng nguồn. |
| Phân công/tên cá nhân | 37 dòng chưa có Owner xác nhận; Member 1–5 chưa có tên thật | Owner trống khi chỉ là đề xuất; giữ evidence trong Ghi chú/Audit. |
| Deadline/Sprint/branch chưa có | Theo bảng trường thiếu | Để trống; không suy diễn từ milestone hoặc module. |
| Trạng thái GitHub hiện tại và lý do đóng | #27–#41 | Lưu trạng thái theo ảnh; chưa có xác nhận live hoặc lý do đóng. |
| Scope #35 | Tiêu đề/metadata cũ Backend; scope cập nhật có List/Detail UI | Giữ tên Issue gốc; ghi scope cập nhật, gộp M3-FE04 theo tuyên bố rõ của nguồn. |
| Lịch/milestone của UI gốc #32 | M2-06 đã bỏ khỏi M2, M3-FE03 là kế hoạch cũ; chỉ mục nói #32 đã đóng | Không tự gán milestone/deadline/Owner; ghi cần đối chiếu. #44 giữ riêng. |
| Phân định API cancel | Candidate mục 6 nêu review/reject/cancel; UI #54 đặt cancel cùng close/reopen | Giữ nhiệm vụ review/reject/cancel và close/reopen riêng; ghi cảnh báo phân định scope trước khi lập Issue. |
| API Technician list có thể nằm trong #29 | Candidate #57 | Giữ Candidate riêng, ghi tái sử dụng #29 nếu endpoint đã được bổ sung; chưa có bằng chứng để gộp. |

Không phát hiện xung đột Owner, ngày hoặc branch giữa chỉ mục và mô tả chi tiết của cùng Issue cần tự chọn một giá trị. Những khác biệt scope/kế hoạch cũ được giữ như cảnh báo ở trên; không bỏ dữ liệu nguồn.

## 7. Cấu hình và kết quả kiểm thử workbook

Đã thực hiện đủ 4 phase:

1. Tạo workbook rỗng: đúng 9 header, độ rộng cột, Wrap Text, header cố định, Excel Table và kiểu hiển thị từng cột; lưu và đọc lại trước khi nạp backlog.
2. Tạo Config và kiểm tra trước khi nhập: 3 Named Range động cho Usecase/Status/Owner, 6 Data Validation, 7 Conditional Formatting cho trạng thái.
3. Nhập 51 công việc và 88 bản ghi audit; cập nhật Table/filter phủ toàn bộ dữ liệu.
4. Lưu và mở lại `.xlsx` bằng openpyxl, đối chiếu độc lập với nguồn, kiểm tra gói XML và render để kiểm tra hiển thị.

**41 kiểm tra độc lập đạt PASS**, bao gồm:

- Đúng 3 sheet; sheet chính chính xác 9 cột theo đúng thứ tự.
- Index nguyên, liên tục 1–51; không có dòng trống trong Table hoặc dòng công việc bị bỏ ngoài filter.
- Đủ 10 M1, 15 Issue GitHub hiện có, 12 UI và 14 Candidate; không trùng ID hoặc title không chủ đích.
- Metadata M2 và branch M1/UI/Candidate khớp nguồn; không thay placeholder.
- 26 ngày là datetime trong openpyxl và numeric Excel serial trong XML, hiển thị `dd/mm/yyyy`.
- Sprint để trống; định dạng và validation chỉ nhận số nguyên dương khi được nhập sau.
- Usecase/Status/Owner dropdown trỏ đúng Named Range; kiểm tra thêm một giá trị trong bản sao bộ nhớ làm vùng danh sách mở rộng thêm một hàng.
- 7 Conditional Formatting có công thức theo trạng thái, 7 màu riêng và phủ `D2:D10001`.
- Excel Table và AutoFilter đều phủ `A1:I52`; freeze panes, Wrap Text và định dạng có trong file đã lưu.
- 31 bản ghi hợp nhất và 6 tham chiếu cũ; mọi dòng backlog có nguồn audit, dòng nguồn tồn tại, hyperlink Index trỏ đúng hàng.
- Gói XLSX/ZIP không lỗi, mọi XML parse được, không có ô lỗi công thức.

Artifact Tool cũng import file thành công, recalculate và quét lỗi công thức không thấy lỗi. Đã render 7 vùng đại diện của cả 3 sheet và kiểm tra trực quan header, dropdown, màu trạng thái, ngày, nội dung dài, branch, Config và Audit; không thấy chữ/ngày bị cắt hoặc `####`.

Kiểm tra thực hiện bằng thư viện Excel, XML và công cụ render; chưa thao tác dropdown trong Microsoft Excel desktop. File dùng các tính năng Excel/OOXML chuẩn, đọc lại thành công; không có lỗi cấu trúc được phát hiện.

## 8. Cập nhật sau khi bàn giao

Thêm Usecase ở Config cột A, trạng thái ở C, thành viên ở E, từ hàng 2 đến 1001, không để ô trống xen giữa danh sách. Named Range sẽ mở rộng theo số giá trị. Thêm công việc ngay dưới Excel Table và nhập Index tiếp theo; validation và conditional formatting đã được đặt đến hàng 10001.

Khi xác nhận phân công, chọn Owner từ dropdown. Khi GitHub cấp số Issue, cập nhật Ghi chú/Audit theo evidence; việc đổi branch cần quyết định riêng vì workbook hiện giữ đúng branch đã khai báo. Chỉ điền Sprint hoặc deadline khi có dữ liệu rõ ràng.

Không chỉnh sửa source code dự án hoặc tài liệu Milestone nguồn.
