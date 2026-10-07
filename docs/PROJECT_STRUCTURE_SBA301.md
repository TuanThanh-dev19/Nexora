# Cấu trúc Nexora phù hợp SBA301

## Kết luận kiểm tra

Nguồn FPT công khai xác nhận SBA301 là môn **Integrate Single Page Application with Spring Boot**. Không có nguồn công khai nào quy định một cây thư mục duy nhất bắt buộc cho mọi lớp. Vì vậy, cấu trúc của nhóm cần làm rõ được các nội dung chính: React SPA, component, route, hooks/context, gọi REST API, Spring Controller–Service–Repository, validation, authentication/authorization và kiểm thử.

Nexora sử dụng **feature-first modular monolith** và ReactJS bằng **JavaScript/JSX**. Mỗi thành viên có thể phụ trách một nghiệp vụ xuyên suốt từ database, REST API đến React UI mà không phải cùng sửa một số file trung tâm quá lớn.

## Frontend

```text
frontend/src/
├── app/
│   ├── router.jsx
│   ├── providers.jsx
│   ├── queryClient.js
│   └── routes/
├── features/
│   ├── auth/
│   │   ├── components/
│   │   ├── context/
│   │   ├── pages/
│   │   └── schemas/
│   ├── tickets/
│   │   ├── api/
│   │   ├── components/
│   │   ├── constants/
│   │   ├── hooks/
│   │   ├── pages/
│   ├── assets/
│   ├── categories/
│   ├── dashboard/
│   └── home/
├── shared/
│   ├── api/
│   ├── components/
│   ├── hooks/
│   ├── layouts/
│   ├── styles/
│   ├── constants/
│   └── utils/
├── test/
├── App.jsx
└── main.jsx
```

Quy ước:

- `pages`: màn hình hoàn chỉnh được gắn với URL.
- `components`: các phần giao diện nhỏ được page sử dụng lại.
- `api`: hàm Axios của riêng feature; component không gọi Axios trực tiếp.
- `hooks`: logic lấy/cập nhật dữ liệu và quản lý server state.
- `context`: chỉ dùng cho trạng thái global thực sự như phiên đăng nhập.
- `schemas`: validation form phía frontend.
- `shared`: chỉ chứa mã được ít nhất hai feature sử dụng.
- File có JSX dùng đuôi `.jsx`; module JavaScript không render JSX dùng `.js`. Project frontend không dùng `.ts`, `.tsx` hoặc bước compile TypeScript.

Không tạo hàng loạt thư mục rỗng. Khi feature cần file thuộc loại nào mới tạo thư mục loại đó.

## Backend

```text
backend/src/main/java/com/nexora/
├── NexoraBackendApplication.java
├── config/
├── common/
│   ├── exception/
│   └── health/
├── auth/
│   ├── controller/
│   ├── dto/request/
│   ├── dto/response/
│   ├── security/
│   └── service/
├── user/
├── location/
├── asset/
├── category/
├── ticket/
│   ├── controller/
│   ├── dto/request/
│   ├── dto/response/
│   ├── document/
│   ├── entity/
│   ├── repository/
│   └── service/
└── dashboard/
```

Quy ước:

- Controller chỉ xử lý HTTP, `@Valid` và gọi service.
- Service chứa business rule, transaction và status transition.
- Repository chỉ truy cập dữ liệu.
- Trong `ticket`, comment dùng chung các package `document`, `repository`, `service`; lớp JPA specification đặt trong `repository`. `TicketStatus` và `TicketAction` đặt trong `entity`, các lớp quy tắc workflow đặt trực tiếp trong `service` theo cấu trúc phẳng.
- Không trả trực tiếp JPA entity qua REST API; dùng DTO request/response.
- Chỉ tạo package mà feature thực sự cần. Ví dụ dashboard có thể không cần entity.
- Không dùng `BaseService`, `BaseRepository`, microservices hoặc Spring StateMachine cho MVP.

## Chia việc cho 5 thành viên

1. Authentication, user và role.
2. Tạo ticket và dynamic category fields.
3. Assignment, status transition và work log.
4. Asset, location và category administration.
5. Dashboard, search/filter/sort và kiểm thử tích hợp.

Mỗi thành viên chịu trách nhiệm một vertical slice gồm backend, frontend và test. Không nên chia một người chỉ viết entity, một người chỉ viết controller vì cách đó làm giảm khả năng tích hợp và tăng thời gian chờ nhau.

## Checklist trước khi demo

- README có hướng dẫn chạy frontend, backend và database.
- `.env.example` không chứa secret thật.
- Swagger/OpenAPI mô tả API.
- Có dữ liệu hoặc tài khoản demo.
- Có role/permission matrix và sơ đồ ticket transition.
- Có validation ở cả React và Spring Boot.
- Có test cho workflow, authorization và dữ liệu không hợp lệ.
- Demo một luồng end-to-end: tạo ticket → review → assign → xử lý → resolve → feedback.
