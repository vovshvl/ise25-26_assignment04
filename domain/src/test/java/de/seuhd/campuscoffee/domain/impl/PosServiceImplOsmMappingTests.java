package de.seuhd.campuscoffee.domain.impl;

import de.seuhd.campuscoffee.domain.exceptions.OsmNodeMissingFieldsException;
import de.seuhd.campuscoffee.domain.model.CampusType;
import de.seuhd.campuscoffee.domain.model.OsmNode;
import de.seuhd.campuscoffee.domain.model.Pos;
import de.seuhd.campuscoffee.domain.model.PosType;
import de.seuhd.campuscoffee.domain.ports.OsmDataService;
import de.seuhd.campuscoffee.domain.ports.PosDataService;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for OSM → POS mapping logic in {@link PosServiceImpl} via the public import method.
 * These tests use minimal fake adapters to avoid Spring and persistence.
 */
public class PosServiceImplOsmMappingTests {

    // --- Fakes ---
    static class FakePosDataService implements PosDataService {
        @Override
        public void clear() {}
        @Override
        public List<Pos> getAll() { return List.of(); }
        @Override
        public Pos getById(Long id) { return null; }
        @Override
        public Pos upsert(Pos pos) { return pos; }
    }

    static class FakeOsmDataService implements OsmDataService {
        private final OsmNode node;
        FakeOsmDataService(OsmNode node) { this.node = node; }
        @Override
        public OsmNode fetchNode(Long nodeId) { return node; }
    }

    private static PosServiceImpl buildServiceWithTags(Map<String, String> tags) {
        OsmNode node = OsmNode.builder().nodeId(1L).tags(tags).build();
        return new PosServiceImpl(new FakePosDataService(), new FakeOsmDataService(node));
    }

    private static Map<String, String> baseTags() {
        Map<String, String> tags = new HashMap<>();
        tags.put("name", "Test Café");
        tags.put("addr:street", "Hauptstraße");
        tags.put("addr:housenumber", "10");
        tags.put("addr:postcode", "69117");
        tags.put("addr:city", "Heidelberg");
        tags.put("amenity", "cafe");
        return tags;
    }

    @Test
    void missingRequiredTag_throws() {
        // each required tag when missing should throw
        for (String required : List.of("name", "addr:street", "addr:housenumber", "addr:postcode", "addr:city")) {
            Map<String, String> tags = baseTags();
            tags.remove(required);
            PosServiceImpl service = buildServiceWithTags(tags);
            assertThrows(OsmNodeMissingFieldsException.class, () -> service.importFromOsmNode(1L));
        }
    }

    @Test
    void invalidPostcode_throws() {
        Map<String, String> tags = baseTags();
        tags.put("addr:postcode", "invalid");
        PosServiceImpl service = buildServiceWithTags(tags);
        assertThrows(OsmNodeMissingFieldsException.class, () -> service.importFromOsmNode(1L));
    }

    @Test
    void amenityCafe_resultsInCafeType() {
        Map<String, String> tags = baseTags();
        tags.put("amenity", "cafe");
        PosServiceImpl service = buildServiceWithTags(tags);
        Pos pos = service.importFromOsmNode(1L);
        assertThat(pos.type()).isEqualTo(PosType.CAFE);
    }

    @Test
    void vendingMachine_resultsInVendingType() {
        Map<String, String> tags = baseTags();
        tags.put("amenity", "vending_machine");
        PosServiceImpl service = buildServiceWithTags(tags);
        Pos pos = service.importFromOsmNode(1L);
        assertThat(pos.type()).isEqualTo(PosType.VENDING_MACHINE);
    }

    @Test
    void shopBakery_resultsInBakeryType() {
        Map<String, String> tags = baseTags();
        tags.remove("amenity");
        tags.put("shop", "bakery");
        PosServiceImpl service = buildServiceWithTags(tags);
        Pos pos = service.importFromOsmNode(1L);
        assertThat(pos.type()).isEqualTo(PosType.BAKERY);
    }

    @Test
    void campusInference() {
        // ALTSTADT default for Heidelberg
        PosServiceImpl s1 = buildServiceWithTags(baseTags());
        assertThat(s1.importFromOsmNode(1L).campus()).isEqualTo(CampusType.ALTSTADT);
        // INF for Neuenheimer Feld
        Map<String, String> tags2 = baseTags();
        tags2.put("addr:street", "Im Neuenheimer Feld 100");
        PosServiceImpl s2 = buildServiceWithTags(tags2);
        assertThat(s2.importFromOsmNode(1L).campus()).isEqualTo(CampusType.INF);
        // BERGHEIM for other city
        Map<String, String> tags3 = baseTags();
        tags3.put("addr:city", "Other City");
        PosServiceImpl s3 = buildServiceWithTags(tags3);
        assertThat(s3.importFromOsmNode(1L).campus()).isEqualTo(CampusType.BERGHEIM);
    }
}
