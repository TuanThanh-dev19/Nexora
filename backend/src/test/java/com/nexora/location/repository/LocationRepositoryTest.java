package com.nexora.location.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexora.location.entity.Location;
import com.nexora.location.entity.LocationType;
import com.nexora.testsupport.NexoraJpaTest;

@NexoraJpaTest
class LocationRepositoryTest {

    @Autowired
    private LocationRepository locations;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void persistsAndQueriesCampusBuildingFloorRoomAndArea() {
        Location campus = save("CAMPUS-WEST", "West Campus", LocationType.CAMPUS, null);
        Location building = save("BUILDING-B", "Building B", LocationType.BUILDING, campus);
        Location floor = save("FLOOR-2", "Second Floor", LocationType.FLOOR, building);
        Location room = save("ROOM-201", "Room 201", LocationType.ROOM, floor);
        Location area = save("AREA-201-A", "Room 201 Area A", LocationType.AREA, room);
        entityManager.clear();

        assertThat(locations.findActiveRoots()).extracting(Location::getCode)
                .containsExactly("CAMPUS-WEST");
        assertThat(locations.findActiveDirectChildren(campus.getId()))
                .extracting(Location::getCode).containsExactly("BUILDING-B");
        assertThat(locations.findActiveDirectChildren(building.getId()))
                .extracting(Location::getCode).containsExactly("FLOOR-2");
        assertThat(locations.findActiveDirectChildren(floor.getId()))
                .extracting(Location::getCode).containsExactly("ROOM-201");
        assertThat(locations.findActiveDirectChildren(room.getId()))
                .extracting(Location::getCode).containsExactly("AREA-201-A");

        Location reloadedCampus = locations.findById(campus.getId()).orElseThrow();
        assertThat(reloadedCampus.getChildren()).extracting(Location::getCode)
                .containsExactly("BUILDING-B");

        Location reloadedRoom = locations.findByCode("ROOM-201").orElseThrow();
        assertThat(reloadedRoom.getType()).isEqualTo(LocationType.ROOM);
        assertThat(reloadedRoom.getParent().getCode()).isEqualTo("FLOOR-2");
        assertThat(reloadedRoom.getParent().getParent().getParent().getCode())
                .isEqualTo("CAMPUS-WEST");
        assertThat(reloadedRoom.getCreatedAt()).isNotNull();
        assertThat(reloadedRoom.getUpdatedAt()).isNotNull();
        assertThat(locations.findByCode("UNKNOWN")).isEmpty();
    }

    @Test
    void filtersInactiveLocationsAndOrdersSelectorsDeterministically() {
        Location zRoot = save("ROOT-Z", "Same Campus", LocationType.CAMPUS, null);
        save("ROOT-A", "Same Campus", LocationType.CAMPUS, null);
        Location inactiveRoot = save("ROOT-OFF", "A Campus", LocationType.CAMPUS, null);
        inactiveRoot.changeActive(false);
        save("CHILD-Z", "Same Building", LocationType.BUILDING, zRoot);
        save("CHILD-A", "Same Building", LocationType.BUILDING, zRoot);
        Location inactiveChild = save("CHILD-OFF", "A Building", LocationType.BUILDING, zRoot);
        inactiveChild.changeActive(false);
        save("GRANDCHILD", "Floor", LocationType.FLOOR, locations.findByCode("CHILD-A").orElseThrow());
        entityManager.flush();
        entityManager.clear();

        assertThat(locations.findActiveRoots()).extracting(Location::getCode)
                .containsExactly("ROOT-A", "ROOT-Z");
        assertThat(locations.findActiveDirectChildren(zRoot.getId()))
                .extracting(Location::getCode).containsExactly("CHILD-A", "CHILD-Z");
        assertThat(locations.findByCode("CHILD-OFF")).get()
                .extracting(Location::isActive).isEqualTo(false);
    }

    @Test
    void hierarchyDoesNotRecurseThroughJsonToStringOrEquality() throws Exception {
        Location parent = new Location("ROOT", "Root", LocationType.CAMPUS, null);
        Location child = new Location("CHILD", "Child", LocationType.BUILDING, parent);
        Location anotherChild = new Location("CHILD", "Child", LocationType.BUILDING, parent);

        assertThat(child.toString()).contains("CHILD").doesNotContain("ROOT");
        assertThat(child).isNotEqualTo(anotherChild);
        String json = new ObjectMapper().writeValueAsString(child);
        assertThat(json).contains("\"code\":\"CHILD\"")
                .doesNotContain("parent", "children", "ROOT");
    }

    private Location save(String code, String name, LocationType type, Location parent) {
        return locations.saveAndFlush(new Location(code, name, type, parent));
    }
}
