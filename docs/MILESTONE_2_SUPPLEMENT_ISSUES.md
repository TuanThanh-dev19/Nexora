# Chỉ mục issue GitHub và kế hoạch Milestone 2 bổ sung

> **Kế hoạch local để duyệt; chưa chỉnh sửa GitHub.**
> Số/tên/trạng thái issue hiện có được đối chiếu từ hai ảnh GitHub người dùng cung cấp. Đây là trạng thái trong ảnh, không phải xác nhận trạng thái live hoặc CI/merge.

## 1. Chỉ mục issue hiện có trên GitHub

| Số issue | Tiêu đề theo ảnh | Trạng thái theo ảnh |
|---|---|---|
| #27 | [Backend] Xây dựng API đăng nhập và phát hành JWT | Đã đóng |
| #28 | Xây dựng API Current User và kết nối phiên đăng nhập Frontend | Đã đóng |
| #29 | [Backend] Xây dựng chức năng quản lý User, Role và Technician | Đang mở |
| #30 | [Backend] Xây dựng API đọc Category, Published Form và SLA | Đã đóng |
| #31 | [Backend] Xây dựng API quản trị Category và Dynamic Form | Đã đóng |
| #32 | [Frontend] Xây dựng giao diện quản trị Category và Dynamic Form | Đã đóng |
| #33 | [Backend] Xây dựng Persistence nền tảng cho Ticket | Đang mở |
| #34 | [Backend] Persistence dữ liệu động, lịch sử và Attachment của Ticket | Đang mở |
| #35 | [Backend] Xây dựng API đọc danh sách và chi tiết Ticket | Đang mở |
| #36 | [Backend] Xây dựng CRUD Location và Equipment Type | Đang mở |
| #37 | [Backend] Xây dựng CRUD Asset và lịch sử trạng thái | Đang mở |
| #38 | [Backend] Xây dựng API lấy Asset hợp lệ theo Location | Đang mở |
| #39 | Tạo SQL Server Sequence cho Ticket Number | Đang mở |
| #40 | Xây dựng nền tảng lưu trữ File đính kèm | Đang mở |
| #41 | Xây dựng Contract và giao diện khung Dashboard | Đang mở |

Dùng số GitHub này trong mục Phụ thuộc; không dùng mã planning. Những issue đang mở có thể đã có một phần code; trạng thái API trong mô tả cũng đối chiếu branch hiện tại.

## 2. Milestone 2 bổ sung — UI từ 09/10 đến 15/10/2026

Xem [mô tả chi tiết UI](MILESTONE_2_SUPPLEMENT_UI_ISSUES.md).

- **Bắt đầu:** 09/10/2026.
- **Hoàn thành:** 15/10/2026.
- **Phạm vi:** 12 issue UI; API hiện có dùng thật, API thiếu có mock/contract và tên issue backend sẽ thực hiện sau.
- Giao diện Category/Form đã có: chỉ hoàn thiện các phần còn thiếu, không tạo implementation trùng.
- Giữ layout/màu/components hiện tại; API được ghi đầy đủ endpoint và đầu vào/đầu ra.

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

Các số trên là dự kiến cho issue mới, không xác nhận đã có trên GitHub. Số thực tế do GitHub cấp khi tạo.

## 3. API/tích hợp để cân nhắc Milestone 3

Xem [kế hoạch backend riêng](MILESTONE_3_CANDIDATE_API_ISSUES.md). Ngày bắt đầu/hoàn thành chưa chốt, nhóm có thể chọn một số hoặc toàn bộ nhiệm vụ.

| Số issue dự kiến | Owner | Nội dung |
|---|---|---|
| #48 | Member 2 | Xây dựng validation dynamic values/evidence và đồng bộ contract form |
| #49 | Member 3 | Xây dựng API tạo Ticket với sequence, SLA và ảnh minh chứng |
| #51 | Member 5 | Xây dựng API đọc attachment có phân quyền |
| #57 | Member 1 | Xây dựng API danh sách Technician phục vụ UI Manager/Admin |
| #58 | Member 5 | Nghiệm thu tích hợp API thật và cập nhật tài liệu |

Các API workflow/comments/feedback/Dashboard chưa có issue xác nhận chỉ được ghi tên nhiệm vụ đề xuất ở file backend, không tự gán số.

## 4. Quy tắc mô tả và phụ thuộc

- Chỉ mục dùng số để nhận diện issue; phần Phụ thuộc dùng số issue GitHub.
- Nội dung API/UI dùng tên chức năng hoặc tên issue, không rải số issue hay mã planning trong mô tả.
- API đã có: ghi thuộc issue trước nào theo tên, tái sử dụng đúng contract.
- API đã giao nhưng chưa có code: ghi tên issue backend cần tiếp tục hoàn tất.
- API mới chưa có: mô tả endpoint/inputs/outputs, ghi tên issue backend sau sẽ thực hiện.
- UI hoàn thành ngày 15/10 được nghiệm thu độc lập với backend tiếp theo, luôn báo rõ thật/mock/chưa tích hợp.
- File chi tiết giữ đầy đủ template, dependencies và yêu cầu UI đồng nhất; mô tả mỗi issue chỉ nằm ở file tương ứng.
