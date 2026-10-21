package com.nexora.ticket.testsupport;

import com.nexora.asset.entity.*;
import com.nexora.category.entity.*;
import com.nexora.location.entity.*;
import com.nexora.sla.entity.SlaPolicy;
import com.nexora.testsupport.fixture.UserFixtures;
import com.nexora.ticket.entity.*;
import com.nexora.user.entity.*;

import jakarta.persistence.EntityManager;

import java.time.OffsetDateTime;
import java.util.*;

/** Tracks owned records; never resets shared seed data or the global sequence. */
public final class TicketCreateFixtures {
    private final EntityManager em;
    private final List<Object> owned = new ArrayList<>();
    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    public AppUser requester, manager;
    public IncidentCategory category;
    public CategoryFormVersion form;
    public Location location, otherLocation;
    public Asset asset, wrongAsset;
    public Skill skill;
    public SlaPolicy sla;
    public final Map<String, FieldDefinition> fields = new LinkedHashMap<>();

    public TicketCreateFixtures(EntityManager em) {
        this.em = em;
    }

    public TicketCreateFixtures create() {
        requester = user("requester", RoleCode.REQUESTER);
        manager = user("manager", RoleCode.MANAGER);
        location = persist(new Location("CREATE-" + suffix, "Room", LocationType.ROOM, null));
        otherLocation = persist(new Location("OTHER-" + suffix, "Other", LocationType.ROOM, null));
        var equipment = persist(new EquipmentType("CREATE-" + suffix, "Projector"));
        asset = persist(new Asset("CREATE-" + suffix, equipment, location));
        wrongAsset = persist(new Asset("OTHER-" + suffix, equipment, otherLocation));
        skill = persist(new Skill("CREATE-" + suffix, "Repair"));
        sla = persist(new SlaPolicy("CREATE-" + suffix, IssuePriority.HIGH, 90));
        category = persist(new IncidentCategory("CREATE-" + suffix, "Maintenance"));
        category.changeDefaultPriority(IssuePriority.HIGH);
        category.changeLocationRequired(false);
        category.changeAssetPolicy(AssetPolicy.OPTIONAL);
        form = new CategoryFormVersion(category, 1);
        form.changeDefaultSkillId(skill.getId());
        form.changeDefaultSlaPolicyId(sla.getId());
        form.changeFormStatus(FormStatus.PUBLISHED);
        form.changePublishingMetadata(manager.getId(), OffsetDateTime.now());
        persist(form);
        int i = 0;
        for (DynamicFieldType type : DynamicFieldType.values()) {
            var field =
                    persist(
                            new FieldDefinition(
                                    form,
                                    type.name().toLowerCase(Locale.ROOT),
                                    type.name(),
                                    type,
                                    i++));
            field.changeValidationRules("{}");
            fields.put(field.getFieldKey(), field);
            if (type == DynamicFieldType.SELECT || type == DynamicFieldType.MULTI_SELECT) {
                persist(new FieldOption(field, "a", "Alpha", 0));
                var inactive = persist(new FieldOption(field, "old", "Inactive", 1));
                inactive.changeActive(false);
            }
        }
        em.flush();
        return this;
    }

    private AppUser user(String name, RoleCode code) {
        AppUser user =
                persist(
                        UserFixtures.builder()
                                .email(name + ".create." + suffix + "@example.test")
                                .build());
        var roles =
                em.createQuery("select r from Role r where r.code=:code", Role.class)
                        .setParameter("code", code)
                        .getResultList();
        Role role;
        if (roles.isEmpty()) {
            role = new Role(code, code.name());
            em.persist(role);
            em.flush();
        } else role = roles.getFirst();
        persist(new UserRole(user, role, null));
        return user;
    }

    public <T> T persist(T value) {
        em.persist(value);
        em.flush();
        owned.add(value);
        return value;
    }

    public void cleanup() {
        for (String type : List.of("TicketAttachment", "TicketFieldValue", "TicketStatusHistory"))
            em.createQuery("delete from " + type + " x where x.ticket.category.id=:id")
                    .setParameter("id", category.getId())
                    .executeUpdate();
        em.createQuery("delete from Ticket t where t.category.id=:id")
                .setParameter("id", category.getId())
                .executeUpdate();
        var reverse = new ArrayList<>(owned);
        Collections.reverse(reverse);
        for (Object value : reverse) {
            Object id = em.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(value);
            Object managed = em.find(value.getClass(), id);
            if (managed != null) {
                em.remove(managed);
                em.flush();
            }
        }
    }
}
