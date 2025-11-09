package de.seuhd.campuscoffee.data.impl;

import de.seuhd.campuscoffee.domain.exceptions.OsmNodeMissingFieldsException;
import de.seuhd.campuscoffee.domain.exceptions.OsmNodeNotFoundException;
import de.seuhd.campuscoffee.domain.model.OsmNode;
import de.seuhd.campuscoffee.domain.ports.OsmDataService;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

/**
 * Real OSM import service using HTTP calls to the OpenStreetMap API.
 * This adapter is activated only when the Spring profile 'osm-http' is enabled.
 * Default builds and tests continue to use the stub implementation.
 */
@Profile("osm-http")
@Service
@Slf4j
class OsmDataHttpServiceImpl implements OsmDataService {

    private static final String USER_AGENT = "CampusCoffee/0.0.1 (+https://github.com/se-ubt/ise25-26_campus-coffee)";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    @Override
    public @NonNull OsmNode fetchNode(@NonNull Long nodeId) throws OsmNodeNotFoundException {
        String url = "https://www.openstreetmap.org/api/0.6/node/" + nodeId;
        log.info("Fetching OSM node {} via HTTP from {}", nodeId, url);

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("HTTP request to OSM failed for node {}", nodeId, e);
            // Treat I/O errors as not found from the domain perspective (external dependency unreachable)
            throw new OsmNodeNotFoundException(nodeId);
        }

        int status = response.statusCode();
        if (status == 404 || status == 410) {
            throw new OsmNodeNotFoundException(nodeId);
        }
        if (status < 200 || status >= 300) {
            log.error("Unexpected HTTP status {} from OSM for node {}: {}", status, nodeId, response.body());
            throw new OsmNodeNotFoundException(nodeId);
        }

        String body = response.body();
        try {
            Map<String, String> tags = parseTagsFromOsmXml(body);
            return OsmNode.builder()
                    .nodeId(nodeId)
                    .tags(tags)
                    .build();
        } catch (Exception e) {
            log.warn("Failed to parse OSM XML for node {}", nodeId, e);
            throw new OsmNodeMissingFieldsException(nodeId);
        }
    }

    private static Map<String, String> parseTagsFromOsmXml(String xml) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(false);
        var db = dbf.newDocumentBuilder();
        try (var in = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
            Document doc = db.parse(in);
            NodeList tagNodes = doc.getElementsByTagName("tag");
            Map<String, String> tags = new HashMap<>();
            for (int i = 0; i < tagNodes.getLength(); i++) {
                var node = tagNodes.item(i);
                var attrs = node.getAttributes();
                var k = attrs.getNamedItem("k");
                var v = attrs.getNamedItem("v");
                if (k != null && v != null) {
                    tags.put(k.getNodeValue(), v.getNodeValue());
                }
            }
            return tags;
        }
    }
}
