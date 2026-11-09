# Nexora – Project Report và Final Presentation

Áp dụng tiêu chí trong hai ảnh cho dự án Nexora, Group 2, SE1910, giảng viên PhucPT. Công nghệ thực tế là React SPA và Spring Boot. Phần kiến trúc trình bày theo stack này thay cho ví dụ Flutter trong tiêu chí.

## I. Project Report – Outline tài liệu

**16 trang nội dung chính**, không tính bìa, mục lục và 6 trang phụ lục. File Word có tổng cộng 24 trang. Trang trong bảng dưới là số trang của phần nội dung, bắt đầu từ Project Overview.

| Phần | Trang | Nội dung cần trình bày | Minh chứng chính |
|---|---:|---|---|
| 1. Project Overview | 1 | Tên dự án, nhóm, vấn đề, mục tiêu, người dùng, phạm vi và tiêu chí thành công | Case study, 4 vai trò và luồng Ticket |
| 2. Requirements & Project Planning | 2–4 | Danh mục chức năng, UC ID, actor, mô tả ngắn, acceptance criteria, iteration, phân công và kế hoạch hoàn tất | UC01–UC46, tiêu chí AC01–AC06, 4 iteration theo giao phẩm |
| 3. System Design | 5–8 | Kiến trúc React/Spring Boot, navigation flow, UI, state management, data model, API và quyền | Sơ đồ kiến trúc, route matrix, quan hệ dữ liệu, endpoint và HTTP errors |
| 4. Implementation & Individual Contribution | 9–11 | Module đã triển khai, tích hợp UI/API, công việc 5 thành viên, khó khăn và giải pháp | Commit cá nhân, assignment locks/version, form version, private attachments và notification |
| 5. Development Process & Git Evidence | 12–13 | Repository, branches, commit history, tích hợp và truy vết UC → Task → Student → Commit | T01–T10, SHA kiểm được trong local Git, merge PR #97/#98, CI definition |
| 6. Testing & Results | 14–15 | Chiến lược kiểm thử, test cases, kết quả đã ghi nhận, lỗi và giải pháp, phạm vi có/chưa có implementation | Backend/frontend tests, static review, kết quả lịch sử có baseline |
| 7. Conclusion & Future Improvements | 16 | Kết quả, hạn chế, bài học, ưu tiên cải tiến và tiêu chí hoàn tất | Scope còn thiếu, CRUD/modal/Delete, coverage, backup và outbox |

### Phụ lục

- **A. Backlog đầy đủ:** UC01–UC46, actor và trạng thái phạm vi.
- **B. Ownership matrix:** phạm vi đóng góp và link commit của từng thành viên.
- **C. Test cases:** 20 tình huống trọng tâm, liên kết bảng static review chi tiết trong `TESTING_MATRIX.md`.
- **D. Giao diện:** ảnh Ticket detail đã lưu và kịch bản demo xuyên vai trò.
- **E. AI Assistance Log:** phần AI hỗ trợ, cách kiểm tra/chỉnh sửa và các lệnh tái kiểm tra.

## II. Final Presentation – Đề xuất 10 slide

Một bộ slide phục vụ buổi Final và bảo vệ cá nhân. Nội dung ưu tiên thông điệp, sơ đồ và minh chứng, không trình bày lại chi tiết từng checkpoint.

| Slide | Tiêu đề | Nội dung chính | Người trình bày đề xuất |
|---:|---|---|---|
| 1 | Project Introduction | Nexora, Group 2, lớp, giảng viên, 5 thành viên và giới thiệu ngắn | Phan Tuấn Thành |
| 2 | Problem & Proposed Solution | Vấn đề phân tán thông tin, 4 nhóm người dùng và luồng xử lý tập trung bằng Ticket | Phan Tuấn Thành |
| 3 | Key Features & Use Cases | Các nhóm UC chính, luồng submit–close, phạm vi implementation và phần chưa bật | Lâm Thoại Thanh |
| 4 | Project Iterations & Contributions | 4 iteration theo giao phẩm, owner và đóng góp cá nhân có minh chứng Git | Trần Phan Nhật Huy |
| 5 | System Architecture | React SPA, Spring Boot, SQL Server, MongoDB, private filesystem và state management | Trần Quốc Thái |
| 6 | UI & Navigation Design | Sơ đồ điều hướng theo role, Ticket detail nối các vai trò và trạng thái UI | Tô Phi Oanh |
| 7 | Implementation Highlights | Assignment locks/version, immutable published forms, ảnh private và notification sau commit | Phan Tuấn Thành |
| 8 | Git, Teamwork & Testing | Chuỗi truy vết UC–owner–commit, nhánh/tích hợp, chiến lược và kết quả test lịch sử | Trần Phan Nhật Huy |
| 9 | Final Results & Limitations | Kết quả phạm vi demo, giới hạn, cải tiến và kịch bản demo một phiếu xuyên vai trò | Tô Phi Oanh |
| 10 | Conclusion & Q&A | Tổng kết và chuyển sang hỏi đáp cá nhân theo ownership | Trần Quốc Thái; cả nhóm Q&A |

### Kịch bản demo gắn slide 9

1. Requester submit Ticket và refresh để kiểm dữ liệu.
2. Manager review, kiểm eligibility rồi assign.
3. Current technician start, lưu work log, upload ảnh tùy chọn rồi resolve.
4. Reporter đọc kết quả, xem ảnh, close và refresh.
5. Minh họa report/CSV, notification và một lỗi quyền hoặc stale version.

### Các thông tin cần hoàn tất trước khi nộp

- Điền MSSV và xác nhận người trình bày.
- Chạy regression theo SHA nộp, lưu command, môi trường và số passed/failed/skipped. Số liệu trong bộ tài liệu là mốc **30/10/2026, baseline a26f98c**, không phải lần chạy mới.
- UC27 Unassign chưa có API thực thi. UC41 hiện đọc SLA deadline. Reject/cancel/reopen/force-close, comments và feedback đang tắt trong demo.
- I1–I4 là cách tổng hợp theo giao phẩm. T01–T10 là mã truy vết của báo cáo, không được coi là mã issue hoặc lịch checkpoint chính thức.

## Tệp bàn giao

- `Nexora_Project_Report_Final.docx`: báo cáo 7 phần và phụ lục.
- `presentation/Nexora_Final_Presentation_10_Slides.pptx`: 10 slide chỉnh sửa được, kèm speaker notes và phân công người trình bày.

Nguồn đối chiếu: báo cáo và đặc tả Group 2 hiện có, README, mã nguồn frontend/backend, demoScope, route configuration, CI và local Git history. Các file gốc được giữ nguyên.
