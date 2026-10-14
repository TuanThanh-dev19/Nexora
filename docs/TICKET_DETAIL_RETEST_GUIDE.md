# Hướng dẫn test lại Ticket Detail (#60)

## Chuẩn bị

Chạy database và backend/auth thật theo [hướng dẫn tài khoản local](LOCAL_ACCOUNTS_GUIDE.md).
Cần tài khoản ACTIVE chỉ có REQUESTER để test conflict; và tài khoản ACTIVE có
REQUESTER + TECHNICIAN (không có MANAGER/ADMIN) để test mất phân công.
Mục 6 của hướng dẫn tài khoản local mô tả cách chuẩn bị tài khoản và role.

Mở PowerShell rồi chạy:

```powershell
Set-Location D:\FA2026\SBA301\campusfix\frontend
$env:VITE_TICKET_WORKFLOW_MOCK='true'
npm run dev
```

Mở URL Vite in ra (mặc định `http://localhost:5173`), đăng nhập và kiểm tra trang
có nhãn **Mô phỏng workflow**. Nếu `window.ticketWorkflowMock` không tồn tại trong
DevTools Console, hãy kiểm tra biến môi trường rồi khởi động lại Vite.

Ticket, bình luận, feedback và ảnh trong chế độ này là dữ liệu trong bộ nhớ;
đăng nhập vẫn dùng backend thật. Requester đầu tiên được gắn làm người báo của
ticket 10–17. Technician đầu tiên được gắn vào phân công demo 21. Với tài khoản
hai role, mở `/tickets/12` và kiểm tra **Người báo** và **Phân công hiện tại** đều
là tài khoản đang đăng nhập. Dùng F5 trước mỗi case độc lập để reset fixtures.
Không dùng F5 giữa các bước cần giữ bản nháp hoặc dữ liệu conflict. Mỗi tab có
store mock riêng, nên thao tác ở tab khác không giả lập được thay đổi đồng thời.

## Case 1: ảnh hỏng, retry một lần phải phục hồi

1. Đăng nhập Requester và mở `/tickets/10`.
2. Khi modal ảnh đang đóng, mở DevTools Console và chạy:

   ```javascript
   window.ticketWorkflowMock.failNextSubresource(
     "attachments",
     "invalid-image",
   );
   ```

3. Bấm **Xem ảnh** của `minh-chứng.png`. Phải hiện **Không đọc được nội dung ảnh.**
   và không có nút **Tải xuống**. Nội dung chi tiết phiếu vẫn đọc được.
4. Bấm **Thử lại ảnh** đúng một lần. Trong lúc tải phải có **Đang tải ảnh...**;
   ảnh hỏng cũ không xuất hiện lại. Khi xong, lỗi biến mất, ảnh hiện và có
   **Tải xuống**. PNG demo chỉ có kích thước 2×2 nên nhìn rất nhỏ.
5. Tải xuống: tên tệp là `minh-chứng.png`, dung lượng 73 byte, mở được như PNG.
   Trong Elements, `img.src` và liên kết tải xuống dùng `blob:`, không chứa token.
6. Đóng modal rồi mở lại: ảnh vẫn hiện, không mắc kẹt ở trạng thái lỗi.

## Case 2: mất phân công khi bản nháp đang chọn Nội bộ

1. Đăng nhập tài khoản chỉ có REQUESTER + TECHNICIAN, F5 rồi mở `/tickets/12`.
   Kiểm tra tài khoản là người báo và kỹ thuật viên đang được phân công; nút
   **Bắt đầu xử lý** phải khả dụng.
2. Ở **Bình luận**, chọn **Nội bộ**, nhập `Bản nháp sau khi mất phân công`, chưa gửi.
3. Trong Console chạy:

   ```javascript
   window.ticketWorkflowMock.failNextAction(403);
   ```

4. Bấm **Bắt đầu xử lý**, sau đó **Xác nhận bắt đầu xử lý**. Kịch bản 403 của
   thao tác kỹ thuật viên mô phỏng mất phân công. Khi có lỗi, bấm **Tải lại phiếu**
   trong modal để đóng modal và lấy dữ liệu mới; không dùng F5.
5. Phải hiện **Không còn phân công hiện tại**; lựa chọn **Nội bộ** biến mất,
   **Công khai** được chọn và nội dung bản nháp còn nguyên.
6. Bấm **Gửi bình luận**. Bình luận xuất hiện với nhãn **Công khai**, form được xóa
   và có thông báo lưu trong bản mô phỏng. Trong Network, POST
   `/api/v1/tickets/12/comments` phải có `visibility: "PUBLIC"`, trả 201.

## Case 3–4: Requester hủy phiếu gặp conflict 409

Lặp lại các bước dưới đây riêng cho `/tickets/10` và `/tickets/11`, dùng tài khoản
chỉ có REQUESTER. F5 trước từng lần để có trạng thái ban đầu.

1. Mở trang, bấm **Hủy phiếu**, nhập lý do `Không cần hỗ trợ nữa`.
2. Trước khi xác nhận, chạy trong Console:

   ```javascript
   window.ticketWorkflowMock.failNextAction(409);
   ```

3. Bấm **Xác nhận hủy phiếu**. Request cancel trả 409, thông báo yêu cầu tải lại;
   không được báo hủy thành công hoặc tự gửi lại request.
4. Bấm **Tải lại phiếu** trong modal, xem **Lịch sử trạng thái** và response GET
   detail trong Network:

   | Ticket | Trạng thái sau tải lại | History mới nhất | Người thực hiện |
   | ------ | ---------------------- | ---------------- | --------------- |
   | 10     | UNDER_REVIEW           | BEGIN_REVIEW     | Quản lý demo    |
   | 11     | ASSIGNED               | ASSIGN           | Quản lý demo    |

   `version` tăng 1; history không gán thao tác quản lý cho Requester. Với ticket
   11, `currentAssignment.assignedBy` phải trùng người thực hiện ASSIGN. Khi đã
   ASSIGNED, Requester không còn nút hủy phiếu.

## Smoke test sau khi sửa

- Requester: ticket 10 chỉ có bình luận PUBLIC; gửi và sửa bình luận của mình được,
  không có xóa. Bình luận mới không tăng version của ticket.
- Manager: ticket 10 đọc được INTERNAL và gửi Nội bộ được.
- Requester: mở ticket 15, **Đóng phiếu**, dùng Tab/phím mũi tên chọn sao rồi gửi
  feedback. Đánh giá đã gửi chỉ đọc; ticket 17 có feedback sẵn và không có form gửi.
- Gây lỗi `failNextSubresource('attachments', 404)` trước khi mở ảnh: chỉ phần ảnh
  báo lỗi, chi tiết/bình luận vẫn dùng được. Retry phải phục hồi.
- Kiểm tra độ rộng 390px và desktop: modal, textarea và nút không tràn ngang.
- Tắt mock và khởi động lại Vite, dùng ID phiếu thật: detail vẫn đọc được,
  các chức năng chưa có backend hiển thị chưa khả dụng. Không dùng ID demo để
  kiểm tra dữ liệu thật.

Sau khi dừng Vite, xóa biến môi trường trong terminal demo:

```powershell
Remove-Item Env:VITE_TICKET_WORKFLOW_MOCK
```

## Kết quả chạy tự động ngày 14/10/2026

- `npm run lint`: pass.
- `npm run test`: **405/405 test**, **55/55 file** pass.
- Bộ tái hiện lỗi độc lập ngoài repository: **4/4** pass.
- `npm run build`: pass; còn cảnh báo chunk khoảng 798 kB.
- Trình duyệt với fixture identity: retry ảnh hỏng phục hồi sau một lần;
  PNG giải mã được và download 73 byte; sau mất phân công, gửi PUBLIC thành công.

Các kiểm tra này xác nhận frontend và mock contract. API collaboration thật chưa
được triển khai trong phạm vi issue này; các kết quả mock không xác nhận lưu trữ
hoặc phân quyền của backend tương lai.
