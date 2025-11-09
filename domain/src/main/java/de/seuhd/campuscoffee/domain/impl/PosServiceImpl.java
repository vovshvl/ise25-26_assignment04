package de.seuhd.campuscoffee.domain.impl;

import de.seuhd.campuscoffee.domain.exceptions.DuplicatePosNameException;
import de.seuhd.campuscoffee.domain.exceptions.OsmNodeMissingFieldsException;
import de.seuhd.campuscoffee.domain.exceptions.OsmNodeNotFoundException;
import de.seuhd.campuscoffee.domain.model.CampusType;
import de.seuhd.campuscoffee.domain.model.OsmNode;
import de.seuhd.campuscoffee.domain.model.Pos;
import de.seuhd.campuscoffee.domain.exceptions.PosNotFoundException;
import de.seuhd.campuscoffee.domain.model.PosType;
import de.seuhd.campuscoffee.domain.ports.OsmDataService;
import de.seuhd.campuscoffee.domain.ports.PosDataService;
import de.seuhd.campuscoffee.domain.ports.PosService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * Implementation of the POS service that handles business logic related to POS entities.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PosServiceImpl implements PosService {
    private final PosDataService posDataService;
    private final OsmDataService osmDataService;

    @Override
    public void clear() {
        log.warn("Clearing all POS data");
        posDataService.clear();
    }

    @Override
    public @NonNull List<Pos> getAll() {
        log.debug("Retrieving all POS");
        return posDataService.getAll();
    }

    @Override
    public @NonNull Pos getById(@NonNull Long id) throws PosNotFoundException {
        log.debug("Retrieving POS with ID: {}", id);
        return posDataService.getById(id);
    }

    @Override
    public @NonNull Pos upsert(@NonNull Pos pos) throws PosNotFoundException {
        if (pos.id() == null) {
            // Create new POS
            log.info("Creating new POS: {}", pos.name());
            return performUpsert(pos);
        } else {
            // Update existing POS
            log.info("Updating POS with ID: {}", pos.id());
            // POS ID must be set
            Objects.requireNonNull(pos.id());
            // POS must exist in the database before the update
            posDataService.getById(pos.id());
            return performUpsert(pos);
        }
    }

    @Override
    public @NonNull Pos importFromOsmNode(@NonNull Long nodeId) throws OsmNodeNotFoundException {
        log.info("Importing POS from OpenStreetMap node {}...", nodeId);

        // Fetch the OSM node data using the port
        OsmNode osmNode = osmDataService.fetchNode(nodeId);

        // Convert OSM node to POS domain object and upsert it
        Pos savedPos = upsert(convertOsmNodeToPos(osmNode));
        log.info("Successfully imported POS '{}' from OSM node {}", savedPos.name(), nodeId);

        return savedPos;
    }

    /**
     * Converts an OSM node to a POS domain object.
     * Note: This is a stub implementation and should be replaced with real mapping logic.
     */
    private @NonNull Pos convertOsmNodeToPos(@NonNull OsmNode osmNode) {
        var tags = osmNode.tags();

        String name = tags.get("name");
        String street = tags.get("addr:street");
        String houseNumber = tags.get("addr:housenumber");
        String postcode = tags.get("addr:postcode");
        String city = tags.get("addr:city");

        if (name == null || street == null || houseNumber == null || postcode == null || city == null) {
            throw new OsmNodeMissingFieldsException(osmNode.nodeId());
        }

        // Description is optional in OSM. Fall back to empty string.
        String description = tags.getOrDefault("description", "");

        // Infer POS type from amenity/shop tags
        PosType type = inferPosType(tags);

        // Infer campus from address heuristics (simple rules for the exercise)
        CampusType campus = inferCampus(street, city);

        return Pos.builder()
                .name(name)
                .description(description)
                .type(type)
                .campus(campus)
                .street(street)
                .houseNumber(houseNumber)
                .postalCode(parsePostalCode(postcode, osmNode.nodeId()))
                .city(city)
                .build();
    }

    private static int parsePostalCode(String postcode, Long nodeId) {
        try {
            return Integer.parseInt(postcode);
        } catch (NumberFormatException e) {
            throw new OsmNodeMissingFieldsException(nodeId);
        }
    }

    private static PosType inferPosType(java.util.Map<String, String> tags) {
        String amenity = tags.get("amenity");
        String shop = tags.get("shop");

        if (amenity != null) {
            switch (amenity) {
                case "cafe" -> { return PosType.CAFE; }
                case "vending_machine" -> { return PosType.VENDING_MACHINE; }
                case "cafeteria", "canteen" -> { return PosType.CAFETERIA; }
                case "bakery" -> { return PosType.BAKERY; }
            }
        }
        if (shop != null) {
            if (shop.equals("bakery")) {
                return PosType.BAKERY;
            }
        }
        // Default to cafe if unknown, to allow import for typical coffee places
        return PosType.CAFE;
    }

    private static CampusType inferCampus(String street, String city) {
        if (city != null && !city.equalsIgnoreCase("Heidelberg")) {
            // Unknown city: pick a generic campus for now
            return CampusType.BERGHEIM;
        }
        if (street != null) {
            String s = street.toLowerCase();
            if (s.contains("im neuenheimer feld") || s.contains("berliner str")) {
                return CampusType.INF;
            }
        }
        return CampusType.ALTSTADT;
    }

    /**
     * Performs the actual upsert operation with consistent error handling and logging.
     * Database constraint enforces name uniqueness - data layer will throw DuplicatePosNameException if violated.
     * JPA lifecycle callbacks (@PrePersist/@PreUpdate) set timestamps automatically.
     *
     * @param pos the POS to upsert
     * @return the persisted POS with updated ID and timestamps
     * @throws DuplicatePosNameException if a POS with the same name already exists
     */
    private @NonNull Pos performUpsert(@NonNull Pos pos) throws DuplicatePosNameException {
        try {
            Pos upsertedPos = posDataService.upsert(pos);
            log.info("Successfully upserted POS with ID: {}", upsertedPos.id());
            return upsertedPos;
        } catch (DuplicatePosNameException e) {
            log.error("Error upserting POS '{}': {}", pos.name(), e.getMessage());
            throw e;
        }
    }
}
