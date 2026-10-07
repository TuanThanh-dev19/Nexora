package com.nexora.ticket.testsupport;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.nexora.asset.entity.*;
import com.nexora.category.entity.*;
import com.nexora.common.domain.Priority;
import com.nexora.location.entity.*;
import com.nexora.testsupport.fixture.UserFixtures;
import com.nexora.ticket.entity.*;
import com.nexora.user.entity.*;
import jakarta.persistence.EntityManager;

/** Owns only rows created by this fixture. Setup/cleanup run inside explicit transactions. */
public final class TicketReadFixtures {
    public static final OffsetDateTime TIME = OffsetDateTime.parse("2026-10-08T08:30:00+07:00");
    private final EntityManager em;
    private final List<Object> owned = new ArrayList<>();
    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    public AppUser requester, other, technician, manager, admin, multi, noRole;
    public Location location;
    public CategoryFormVersion form;
    public Ticket own, current, historical, hidden;

    public TicketReadFixtures(EntityManager em) { this.em = em; }

    public TicketReadFixtures create() {
        requester = user("requester", RoleCode.REQUESTER);
        other = user("other", RoleCode.REQUESTER);
        technician = user("technician", RoleCode.TECHNICIAN);
        manager = user("manager", RoleCode.MANAGER);
        admin = user("admin", RoleCode.ADMIN);
        multi = user("multi", RoleCode.REQUESTER, RoleCode.TECHNICIAN);
        noRole = user("no-role");
        persist(new TechnicianProfile(technician));
        persist(new TechnicianProfile(multi));
        location = persist(new Location("READ-" + suffix, "Địa điểm cũ", LocationType.CAMPUS, null));
        var category = persist(new IncidentCategory("READ-" + suffix, "Sự cố thiết bị"));
        form = new CategoryFormVersion(category, 1);
        form.changeFormStatus(FormStatus.ARCHIVED);
        form.changePublishingMetadata(manager.getId(), TIME);
        persist(form);
        var latest = new CategoryFormVersion(category, 2);
        latest.changeFormStatus(FormStatus.PUBLISHED);
        latest.changePublishingMetadata(manager.getId(), TIME.plusDays(1));
        persist(latest);
        var equipment = persist(new EquipmentType("READ-" + suffix, "Máy chiếu"));
        var asset = persist(new Asset("READ-" + suffix, equipment, location));
        own = ticket("01", requester, Priority.HIGH, asset);
        current = ticket("02", other, Priority.HIGH, null);
        historical = ticket("03", multi, Priority.NORMAL, null);
        hidden = ticket("04", other, Priority.LOW, null);
        assignment(current, technician, TIME, null);
        assignment(historical, technician, TIME, TIME.plusHours(1));
        assignment(historical, technician, TIME.plusHours(2), TIME.plusHours(3));
        assignment(current, multi, TIME.minusHours(2), TIME.minusHours(1));
        em.createNativeQuery("update dbo.tickets set created_at = :time, updated_at = :time where id in (:ids)")
                .setParameter("time", TIME).setParameter("ids", ticketIds()).executeUpdate();
        String[] json = {"\"Máy chiếu\\nkhông hoạt động\"", "\"Mô tả dài\"", "9007199254740993",
                "\"old\"", "[\"old\",\"new\"]", "\"2026-10-08\"", "\"2026-10-08T08:30:00+07:00\"", "false"};
        DynamicFieldType[] types = DynamicFieldType.values();
        for (int i = 0; i < types.length; i++) {
            var field = persist(new FieldDefinition(form, "field_" + i, "Trường " + types[i], types[i], i));
            if (types[i] == DynamicFieldType.SELECT || types[i] == DynamicFieldType.MULTI_SELECT) {
                var old = new FieldOption(field, "old", "Lựa chọn cũ", 0);
                old.changeActive(false);
                persist(old);
                persist(new FieldOption(field, "new", "Lựa chọn mới", 1));
            }
            if (types[i] == DynamicFieldType.IMAGE) {
                persist(new TicketAttachment(own, field, requester, AttachmentType.REPORT_EVIDENCE,
                        "private/" + suffix, "bằng chứng.png", "image/png", 120, TIME));
            } else persist(new TicketFieldValue(own, field, json[i]));
        }
        persist(new TicketStatusHistory(own, null, TicketStatus.SUBMITTED, TicketAction.SUBMIT, null, requester, TIME));
        persist(new TicketStatusHistory(own, TicketStatus.SUBMITTED, TicketStatus.SUBMITTED,
                TicketAction.REASSIGN, "Dữ liệu fixture", manager, TIME));
        persist(new TicketAttachment(own, null, technician, AttachmentType.WORK_RESULT,
                "result/" + suffix, "kết quả.pdf", "application/pdf", 512, TIME));
        persist(new TicketAttachment(own, null, manager, AttachmentType.COMMENT,
                "comment/" + suffix, "nội bộ.txt", "text/plain", 12, TIME));
        em.flush();
        return this;
    }

    private AppUser user(String name, RoleCode... codes) {
        var user = persist(UserFixtures.builder().email(name + "." + suffix + "@example.test").fullName(name).build());
        for (var code : codes) {
            var existing = em.createQuery("select r from Role r where r.code = :code", Role.class)
                    .setParameter("code", code).getResultList();
            // Canonical roles may be reference seed data; never delete another module's rows.
            Role role;
            if (existing.isEmpty()) {
                role = new Role(code, code.name());
                em.persist(role);
                em.flush();
            } else role = existing.getFirst();
            persist(new UserRole(user, role, null, TIME));
        }
        return user;
    }

    private Ticket ticket(String number, AppUser reporter, Priority priority, Asset asset) {
        return persist(new Ticket("RD-" + suffix + "-" + number, reporter, form.getCategory(), form, location,
                asset, "Sự cố " + number, "Mô tả\nchi tiết", priority, number.equals("01") ? TIME.plusHours(4) : null, null));
    }

    private void assignment(Ticket ticket, AppUser technician, OffsetDateTime assignedAt, OffsetDateTime endedAt) {
        em.createNativeQuery("insert into dbo.ticket_assignments (ticket_id, technician_id, assigned_by, assigned_at, ended_at) "
                + "values (:ticket, :technician, :manager, :time, :end)")
                .setParameter("ticket", ticket.getId()).setParameter("technician", technician.getId())
                .setParameter("manager", manager.getId()).setParameter("time", assignedAt).setParameter("end", endedAt)
                .executeUpdate();
    }

    private <T> T persist(T entity) {
        em.persist(entity);
        em.flush();
        owned.add(entity);
        return entity;
    }

    public List<Long> ticketIds() { return List.of(own.getId(), current.getId(), historical.getId(), hidden.getId()); }

    public void cleanup() {
        em.createNativeQuery("delete from dbo.ticket_assignments where ticket_id in (:ids)")
                .setParameter("ids", ticketIds()).executeUpdate();
        var reverse = new ArrayList<>(owned);
        Collections.reverse(reverse);
        for (Object entity : reverse) {
            Object id = em.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(entity);
            Object managed = em.find(entity.getClass(), id);
            if (managed != null) {
                em.remove(managed);
                em.flush();
            }
        }
    }
}
