package de.seuhd.campuscoffee.data.impl;

import de.seuhd.campuscoffee.domain.exceptions.OsmNodeNotFoundException;
import de.seuhd.campuscoffee.domain.model.OsmNode;
import de.seuhd.campuscoffee.domain.ports.OsmDataService;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * OSM import service (stub).
 */
@Profile("!osm-http")
@Service
@Slf4j
class OsmDataServiceImpl implements OsmDataService {

    @Override
    public @NonNull OsmNode fetchNode(@NonNull Long nodeId) throws OsmNodeNotFoundException {
        log.warn("Using stub OSM import service - returning hardcoded data for node {}", nodeId);

        // Stub implementation used by default and in tests to keep builds deterministic.
        // A real HTTP-based implementation is available behind the 'osm-http' Spring profile.
        if (nodeId.equals(5589879349L)) {
            return OsmNode.builder()
                    .nodeId(nodeId)
                    .tags(Map.of(
                            "name", "Rada Coffee & Rösterei",
                            "description", "Caffé und Rösterei",
                            "amenity", "cafe",
                            "addr:street", "Untere Straße",
                            "addr:housenumber", "21",
                            "addr:postcode", "69117",
                            "addr:city", "Heidelberg"
                    ))
                    .build();
        } else {
            // For any other node ID, throw not found exception
            throw new OsmNodeNotFoundException(nodeId);
        }
    }
}
