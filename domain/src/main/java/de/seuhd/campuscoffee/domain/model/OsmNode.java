package de.seuhd.campuscoffee.domain.model;

import lombok.Builder;
import org.jspecify.annotations.NonNull;

import java.util.Map;

/**
 * Represents an OpenStreetMap node with relevant Point of Sale information.
 * This is the domain model for OSM data before it is converted to a POS object.
 *
 * @param nodeId The OpenStreetMap node ID.
 * @param tags   The key-value tags associated with the node (e.g., name, addr:street, amenity, etc.).
 */
@Builder
public record OsmNode(@NonNull Long nodeId, @NonNull Map<String, String> tags) {
}
