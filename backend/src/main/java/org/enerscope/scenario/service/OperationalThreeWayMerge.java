package org.enerscope.scenario.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/** Pure merge over logical identities. Callers normalize physical endpoint IDs first. */
public final class OperationalThreeWayMerge {
    private OperationalThreeWayMerge() {}
    public enum Choice { SOURCE, TARGET }
    public record Conflict(UUID identity, JsonNode base, JsonNode source, JsonNode target) {}
    public record Result(Map<UUID, JsonNode> values, List<Conflict> conflicts) {}

    public static Result merge(Map<UUID, JsonNode> base, Map<UUID, JsonNode> source,
                               Map<UUID, JsonNode> target, Map<UUID, Choice> resolutions) {
        Objects.requireNonNull(source);
        Objects.requireNonNull(target);
        Objects.requireNonNull(resolutions);
        Set<UUID> identities = new TreeSet<>();
        if (base != null) identities.addAll(base.keySet());
        identities.addAll(source.keySet());
        identities.addAll(target.keySet());
        if (!identities.containsAll(resolutions.keySet()) || resolutions.values().stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("Invalid merge resolution");
        var result = new LinkedHashMap<UUID, JsonNode>();
        var conflicts = new ArrayList<Conflict>();
        for (UUID identity : identities) {
            JsonNode original = base == null ? null : base.get(identity);
            JsonNode incoming = source.get(identity);
            JsonNode current = target.get(identity);
            JsonNode selected;
            boolean conflict = false;
            if (Objects.equals(incoming, current)) selected = current;
            else if (base != null && Objects.equals(incoming, original)) selected = current;
            else if (base != null && Objects.equals(current, original)) selected = incoming;
            else {
                conflict = true;
                Choice decision = resolutions.get(identity);
                selected = decision == Choice.SOURCE ? incoming : current;
                if (decision == null) conflicts.add(new Conflict(identity, copy(original), copy(incoming), copy(current)));
            }
            if (!conflict && resolutions.containsKey(identity))
                throw new IllegalArgumentException("Resolution supplied for a non-conflicting identity");
            if (selected != null) result.put(identity, copy(selected));
        }
        return new Result(Collections.unmodifiableMap(result), List.copyOf(conflicts));
    }

    private static JsonNode copy(JsonNode node) { return node == null ? null : node.deepCopy(); }
}
