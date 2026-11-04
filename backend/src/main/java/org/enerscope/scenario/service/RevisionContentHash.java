package org.enerscope.scenario.service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Canonical object properties; array order is preserved because financial rule order can be significant. */
public final class RevisionContentHash {
    private RevisionContentHash() {}
    public static String hash(JsonNode content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical(normalize(content)).toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
    /** Compare physical entities by logical identity, not clone-on-write row IDs. */
    public static JsonNode normalize(JsonNode content) {
        JsonNode copy = content.deepCopy();
        if (!copy.isObject() || !copy.path("nodes").isArray()) return copy;
        var ids = new HashMap<String, String>();
        for (JsonNode node : copy.path("nodes")) {
            JsonNode data = node.path("data");
            if (data.path("id").isTextual() && data.path("identityId").isTextual())
                ids.put(data.path("id").asText(), data.path("identityId").asText());
        }
        for (JsonNode node : copy.path("nodes")) stripPhysicalIds(node.path("data"));
        for (JsonNode connection : copy.path("connections")) {
            if (connection.isObject()) {
                var object = (ObjectNode) connection;
                for (String field : List.of("fromNodeId", "toNodeId"))
                    if (ids.containsKey(object.path(field).asText())) object.put(field, ids.get(object.path(field).asText()));
                stripPhysicalIds(connection);
            }
        }
        ScenarioConfigurationService.remapReferences(copy.path("configuration"), ids);
        ObjectNode root = (ObjectNode) copy;
        for (String collection : List.of("nodes", "connections")) {
            if (!root.path(collection).isArray()) continue;
            var values = new ArrayList<JsonNode>(); root.path(collection).forEach(values::add);
            values.sort(Comparator.comparing(value -> canonical(value).toString()));
            ArrayNode array = JsonNodeFactory.instance.arrayNode(); values.forEach(array::add); root.set(collection, array);
        }
        return copy;
    }
    private static void stripPhysicalIds(JsonNode node) {
        if (node.isObject()) {
            ((ObjectNode) node).remove(List.of("id", "createdAt", "lastModified"));
            node.elements().forEachRemaining(RevisionContentHash::stripPhysicalIds);
        } else if (node.isArray()) node.forEach(RevisionContentHash::stripPhysicalIds);
    }
    private static JsonNode canonical(JsonNode value) {
        if (value.isObject()) {
            ObjectNode result = JsonNodeFactory.instance.objectNode();
            var keys = new TreeSet<String>(); value.fieldNames().forEachRemaining(keys::add);
            for (String key : keys) {
                if (!Set.of("createdAt", "lastModified").contains(key)) result.set(key, canonical(value.get(key)));
            }
            return result;
        }
        if (value.isArray()) {
            ArrayNode result = JsonNodeFactory.instance.arrayNode();
            value.forEach(item -> result.add(canonical(item)));
            return result;
        }
        return value;
    }
}
