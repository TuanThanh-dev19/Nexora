# Nexora persistence schema

Nexora uses two sources of truth:

- SQL Server stores the 26 relational tables, including users and tickets.
- MongoDB stores `audit_events`, `ticket_comments`, and `notifications`.

MongoDB references SQL records only through numeric IDs. There are no `DBRef`
links and no duplicated SQL user or ticket documents.

## Nguồn khởi tạo database

Nexora dùng Flyway làm nguồn duy nhất để tạo và nâng cấp bảng ứng dụng:

```text
backend/src/main/resources/db/migration/
├── V1__create_user_and_role_tables.sql
├── V2__create_location_and_asset_tables.sql
├── V3__create_dynamic_category_tables.sql
├── V4__create_ticket_workflow_tables.sql
├── V5__seed_reference_data.sql
├── V6__complete_erd_support_tables.sql
├── V7__seed_skill_and_sla_reference_data.sql
└── V8__remove_document_data_tables_from_sql.sql
```

Docker service `sqlserver-init` chỉ tạo database rỗng `nexora`. Khi backend
khởi động, Flyway tự chạy lần lượt V1 đến V8. V1–V7 không được sửa vì checksum
đã được Flyway ghi nhận. V8 loại bỏ ba bảng document cũ sau khi chúng rỗng hoặc
kết quả backfill MongoDB đã được xác minh.

## File SQL gộp để chạy thủ công

File `infrastructure/sqlserver/nexora_full_schema.sql` chứa toàn bộ nội dung
V1 đến V8 trong một file duy nhất. File này dành cho việc nộp bài, đọc schema,
hoặc khởi tạo thủ công bằng SSMS/`sqlcmd` trên database mới và rỗng.

Không chạy file gộp trên database đã được Flyway quản lý, và không dùng đồng
thời hai cách khởi tạo trên cùng database:

- Phát triển Spring Boot: dùng Flyway bằng cách khởi động backend.
- Khởi tạo thủ công độc lập: dùng `nexora_full_schema.sql` một lần trên
  database rỗng.

Chạy file gộp trong SQL Server Docker:

```powershell
docker compose exec -T sqlserver /bin/bash -c '/opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "$MSSQL_SA_PASSWORD" -C -b -d master -i /opt/nexora/sql/nexora_full_schema.sql'
```

Trong SSMS, mở file, kết nối SQL Server và chọn **Execute**. Script tự tạo
database `nexora` nếu database chưa tồn tại và dừng với lỗi nếu database đã
có bảng, nhằm tránh ghi đè dữ liệu.

Khi nhóm thêm migration V8 trở lên, file gộp cũng phải được cập nhật nếu vẫn
muốn dùng nó để nộp hoặc khởi tạo thủ công. Flyway migrations mới vẫn là nguồn
chính thức của backend.

## Nhóm bảng

| Nhóm | Bảng vật lý chính |
|---|---|
| User và role | `app_users`, `roles`, `user_roles` |
| Kỹ thuật viên | `technician_profiles`, `skills`, `technician_skills`, `technician_service_areas` |
| Địa điểm và thiết bị | `locations`, `equipment_types`, `assets`, `asset_status_history` |
| Category động | `incident_categories`, `category_form_versions`, `field_definitions`, `field_options` |
| Ticket workflow | `tickets`, `ticket_field_values`, `ticket_assignments`, `ticket_status_history`, `work_logs`, `ticket_attachments` |
| Hậu xử lý | `ticket_feedback` |
| Kiểm tra thông tin | `ticket_accuracy_reviews`, `violation_cases`, `violation_appeals` |
| SLA | `sla_policies` |

Sau V8, SQL Server có đúng 26 bảng ứng dụng. `flyway_schema_history` là bảng
kỹ thuật của Flyway và không tính vào con số này.

## MongoDB collections và document schema

| Collection | Trường chính | Index |
|---|---|---|
| `audit_events` | `_id: ObjectId`, `legacySqlId?: Long`, `actorId?: Long`, `action: String`, `targetType: String`, `targetId: Long`, `before?: Document`, `after?: Document`, `reason?: String`, `createdAt: Date` | `(targetType, targetId, createdAt desc)`, `(actorId, createdAt desc)`, unique sparse `legacySqlId` |
| `ticket_comments` | `_id: ObjectId`, `legacySqlId?: Long`, `ticketId: Long`, `authorId: Long`, `body: String`, `visibility: PUBLIC\|INTERNAL`, `createdAt: Date`, `editedAt?: Date` | `(ticketId, createdAt asc)`, unique sparse `legacySqlId` |
| `notifications` | `_id: ObjectId`, `legacySqlId?: Long`, `recipientId: Long`, `ticketId?: Long`, `notificationType: String`, `title: String`, `message: String`, `read: Boolean`, `readAt?: Date`, `createdAt: Date` | `(recipientId, read, createdAt desc)`, unique sparse `legacySqlId` |

`legacySqlId` chỉ có ở document được chuyển từ SQL Server. Unique sparse index
làm cho backfill có thể chạy lại an toàn. Các document mới dùng `_id` do MongoDB
tạo và không ghi `legacySqlId`.

Service tạo comment xác minh cả `ticketId` và `authorId` trong SQL Server.
Service tạo notification xác minh `recipientId` và `ticketId` (nếu có). Audit
phát sinh từ SQL transaction được ghi ở phase `AFTER_COMMIT`; lỗi MongoDB được
log nhưng không rollback SQL transaction đã commit.

Hai bảng `violation_cases` và `violation_appeals` thuộc scope P1. Chúng có sẵn
trong schema để đúng ERD, nhưng nhóm có thể chưa làm API/UI cho chúng trong MVP.
Kết quả `NO_FAULT_FOUND` không tự động tạo violation; manager phải mở case và
xem xét bằng chứng.

## Quyết định physical model

ERD là logical model nên dùng tên entity số ít và kiểu `UUID`. Physical schema
dùng tên bảng số nhiều và `BIGINT IDENTITY` để JPA/SQL đơn giản hơn cho project
SBA301. Các quan hệ nghiệp vụ chính được bảo vệ bằng PK, FK, unique constraint
và filtered unique index.

Một số khác biệt có chủ đích so với logical ERD:

- `tickets.form_version_id` và `tickets.location_id` là bắt buộc trong physical
  schema để mọi ticket đã lưu luôn có category form và địa điểm rõ ràng.
- `incident_categories.created_by`, `asset_status_history.changed_by` và
  `user_roles.assigned_by` cho phép `NULL` để hỗ trợ seed/migration do hệ thống
  thực hiện, không phải thao tác trực tiếp của user.
- SLA là reference policy dùng chung; form version lưu FK đến policy. Nếu cần
  thay đổi thời hạn mà vẫn giữ snapshot tuyệt đối, hãy tạo policy mới thay vì
  cập nhật policy đã được form published sử dụng.

`TICKET_FIELD_VALUE` trong ERD mô tả các giá trị typed. Physical schema lưu giá
trị động trong `value_json` có `ISJSON` constraint; backend phải kiểm tra type,
required, min/max và option dựa trên `field_definitions.validation_rules`.

Các rule liên bảng như “feedback phải do đúng reporter tạo” hoặc “user được
assign phải có role TECHNICIAN” vẫn phải được kiểm tra ở service layer và bằng
integration test; chúng không phù hợp để biểu diễn hoàn toàn bằng FK đơn giản.

## Khởi tạo local

Tại thư mục gốc repository:

```powershell
Copy-Item .env.example .env
docker compose up -d

cd backend
.\mvnw.cmd spring-boot:run
```

Khi backend khởi động lần đầu, log phải hiển thị Flyway migrate đến version `8`.
Không chạy lại hoặc sửa migration đã áp dụng; thay đổi schema tiếp theo phải tạo
V9, V10, ...

Sau khi migration hoàn tất, dừng backend bằng `Ctrl+C`, quay lại thư mục gốc và
kiểm tra schema bằng SQL Server container:

```powershell
cd ..
docker compose exec -T sqlserver /bin/bash -c '/opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "$MSSQL_SA_PASSWORD" -C -b -d nexora -i /opt/nexora/sql/verify-schema.sql'
```

File được mount read-only vào container bởi `compose.yaml`. Cách kiểm tra độc
lập trên database sạch là chạy integration test SQL Server thật:

```powershell
cd backend
$env:RUN_SQLSERVER_IT='true'
.\mvnw.cmd -Dtest=SqlServerMigrationIntegrationTest test
Remove-Item Env:RUN_SQLSERVER_IT
```

MongoDB integration test dùng container thật:

```powershell
cd backend
$env:RUN_MONGODB_IT='true'
.\mvnw.cmd -Dtest=MongoPersistenceIntegrationTest test
Remove-Item Env:RUN_MONGODB_IT
```

## Backfill database cũ

V8 chỉ drop bảng khi cả ba bảng rỗng, hoặc database có extended property
`NexoraMongoBackfillVerified` chứa đúng số record hiện tại. Với database đã có
dữ liệu, chạy backend một lần với Flyway dừng ở V7:

```powershell
cd backend
$env:MONGO_BACKFILL_ENABLED='true'
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.flyway.target=7"
```

Runner đọc ba bảng SQL, upsert theo `legacySqlId`, kiểm tra số document và ghi
marker xác minh trong SQL Server. Sau log `MongoDB backfill verified`, dừng app,
xóa biến tạm và khởi động lại bình thường để áp dụng V8. Nếu SQL count thay đổi
sau lúc xác minh, V8 chủ động báo lỗi và không drop bảng.
