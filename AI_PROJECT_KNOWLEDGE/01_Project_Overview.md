# Project Overview

> **Decision update (2026-09-25):** `00_Approved_Decisions.md` is the authoritative source for approved MVP scope, roles, workflow, SLA, authentication, team baseline and roadmap. It supersedes older `TBD`, `Inferred`, `Likely` or `Suggested` statements in this document where the subjects overlap.

## Knowledge Status Convention

- **Implemented**: đã tồn tại trong source code, configuration hoặc database migration hiện tại.
- **Designed**: đã được xác định trong tài liệu hoặc database nhưng chưa có business implementation end-to-end.
- **Planned**: đề xuất hoặc ý định phát triển trong tài liệu, chưa phải chức năng hiện có.
- **TBD**: repository chưa cung cấp đủ thông tin để kết luận.

## Project Identity

| Item | Current knowledge | Status | Evidence |
|---|---|---|---|
| Project name | **Nexora** | Implemented | `README.md`, Maven artifact `nexora-backend`, frontend package `nexora-frontend` |
| Repository workspace name | `campusfix` | Implemented | Tên thư mục làm việc; không được tài liệu xác nhận là product name |
| Project type | Feature-first modular monolith gồm Spring Boot REST API và React SPA | Implemented foundation | `README.md`, `docs/ARCHITECTURE.md` |
| Project purpose | Nền tảng báo cáo sự cố trong campus, điều phối bảo trì và theo dõi thiết bị | Designed | `README.md`, OpenAPI description, database domain |
| Delivery context | Project SBA301, MVP dự kiến trong 8 tuần | Designed | `docs/PROJECT_STRUCTURE_SBA301.md`, `docs/ARCHITECTURE.md` |
| Current phase | Khởi tạo project, thiết kế database và dựng technical/UI scaffold | Implemented | Source tree, Flyway V1-V7, UI starter và absence of business services/controllers |

## Problem Being Solved

Repository mô tả Nexora nhằm tập trung các hoạt động sau trong một hệ thống:

- ghi nhận sự cố tại campus;
- liên kết sự cố với địa điểm và, khi phù hợp, thiết bị;
- review, phân công kỹ thuật viên, theo dõi xử lý và kết quả;
- quản lý category cùng biểu mẫu động có version;
- theo dõi thiết bị, lịch sử trạng thái, SLA và phản hồi sau xử lý.

**Status: Designed.** Database đã mô hình hóa các khái niệm này, nhưng chưa có business flow backend/frontend hoàn chỉnh.

## Target Users and Main Actors

Các actor sau được xác nhận bởi seed data của bảng `roles`:

| Actor | Evidence-backed interpretation | Status |
|---|---|---|
| `REQUESTER` | Người báo cáo sự cố | Designed in database |
| `TECHNICIAN` | Kỹ thuật viên thực hiện công việc | Designed in database |
| `MANAGER` | Quản lý vận hành/review và phân công | Designed in database/documentation |
| `ADMIN` | Quản trị viên | Designed in database/documentation |

Chi tiết permission matrix cho từng actor là **TBD - requires project requirement information**. Frontend starter hiện dùng `USER` thay cho `REQUESTER`; đây chưa được coi là quyết định nghiệp vụ hợp lệ.

## Main Domain Concepts

- User, role, technician profile, skill và service area.
- Location phân cấp: campus, building, floor, room, area.
- Equipment type, asset và asset status history.
- Incident category, form version, dynamic field và field option.
- Ticket, dynamic field value, assignment, status history, work log, comment và attachment.
- Feedback, accuracy review và notification.
- SLA policy và audit log.
- Violation case và appeal: có trong schema nhưng được tài liệu xếp vào P1/optional, chưa phải MVP implementation.

**Status:** Domain model đã được thiết kế và implemented trong Flyway migrations; business behavior chưa implemented.

## Technology Stack

| Layer | Technology | Status |
|---|---|---|
| Backend | Java 21, Spring Boot 4.1.1, Spring MVC, Spring Data JPA, Bean Validation, Spring Security, OAuth2 Resource Server | Implemented setup |
| API documentation | Springdoc OpenAPI/Swagger UI | Implemented setup |
| Database | Microsoft SQL Server 2022 Developer | Implemented setup |
| Migration | Flyway, migrations V1-V7 | Implemented |
| Frontend | React 19, JavaScript/JSX, Vite 8, React Router 8 | Implemented setup |
| Frontend data/forms | Axios, TanStack Query, React Hook Form, Zod | Implemented setup/starter usage |
| UI | React Bootstrap, Bootstrap, React Icons, Recharts | Implemented dependencies; feature usage is partial |
| Tests | JUnit/Spring Test, H2 test profile, SQL Server Testcontainers, Vitest, React Testing Library, MSW | Implemented setup; business coverage not present |
| Local infrastructure | Docker Compose, SQL Server persistent volume | Implemented |
| CI | GitHub Actions: backend verify; frontend lint/test/build | Implemented |

`spring-boot-starter-data-mongodb` có trong Maven dependencies nhưng repository không có MongoDB configuration hoặc usage. Vai trò của dependency này là **TBD**.

## Current Project Phase

**Project initialization / database design / application scaffolding.**

Đã có:

- repository structure và convention;
- database schema, constraints, indexes và reference seed data;
- backend configuration, health endpoint, error foundation, JWT resource-server validation;
- frontend shell, routing, demo authentication state và placeholder/starter views;
- test/CI foundation.

Chưa có:

- JPA entities và repositories cho business domain;
- business services, workflow policy hoặc real authentication service;
- business REST controllers/DTOs;
- frontend-to-backend business integration;
- end-to-end business feature hoàn chỉnh.

## Sources of Truth Used

Theo thứ tự ưu tiên khi tài liệu này được tạo:

1. `AI_PROJECT_KNOWLEDGE/00_Approved_Decisions.md` cho approved product/technical planning decisions từ ngày 2026-09-25.
2. Flyway migrations trong `backend/src/main/resources/db/migration/` cho physical database model.
3. `docs/DATABASE_SCHEMA.md` cho giải thích mapping và scope database.
4. `README.md`, `docs/ARCHITECTURE.md` và `docs/PROJECT_STRUCTURE_SBA301.md` cho architecture và intended MVP.
5. Source/config thực tế để xác định implementation status.
