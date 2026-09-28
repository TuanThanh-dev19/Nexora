# Nexora backend

This is the Spring Boot 3.5 REST API. It uses a package-by-feature modular
monolith with SQL Server for relational data and MongoDB for audit events,
ticket comments, and notifications.

Create each feature's `controller`, `dto`, `entity`, `repository`, and `service` packages only when needed. Business workflow rules belong in services or dedicated policy classes, never in controllers.

Common commands:

```bash
./mvnw spring-boot:run
./mvnw verify
```

Swagger UI is available at http://localhost:8080/swagger-ui.html while the application is running.

## Location and equipment references

`com.nexora.location.entity.Location` is the shared JPA mapping for `dbo.locations`.
Asset and technician modules should reference this entity rather than define another
location mapping. `LocationType` contains the five schema values. `parent` and
`children` are lazy persistence associations; selectors should use
`LocationRepository.findActiveRoots()` and `findActiveDirectChildren(parentId)`,
which order by name, code, then ID. `EquipmentTypeRepository` provides the
equivalent ordered active equipment reference query. API responses must map these
entities to DTOs. The location associations are ignored during JSON serialization,
and entity equality uses Java object identity to avoid traversing the hierarchy.

## Technician workforce references

`com.nexora.user.entity.Skill` and `com.nexora.user.repository.SkillRepository`
are the authoritative JPA mapping and repository for `dbo.skills`. Technician,
dynamic-form, and ticket code must reuse them rather than introduce feature-local
`Skill` mappings. `TechnicianProfile` similarly extends the existing `AppUser`
mapping through a shared primary key, while service areas reference the existing
`com.nexora.location.entity.Location` mapping.
