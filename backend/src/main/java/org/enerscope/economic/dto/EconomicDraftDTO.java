package org.enerscope.economic.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;
import static org.enerscope.economic.service.EconomicValidator.require;

/** Preserve editor strings, including incomplete numbers and dates, without treating them as engine inputs. */
public record EconomicDraftDTO(int schemaVersion, JsonNode draft) {
    public void validateStructure() {
        require(schemaVersion == 1 && draft != null && draft.isObject(), "Unsupported economic draft format");
        require(draft.toString().length() <= 500000, "Economic draft is too large");
        strings(draft, "startYear", "years", "currency", "wacc", "entityId", "entityName", "taxRate");
        for (String collection : Set.of("assets", "rules")) {
            var rows = draft.path(collection);
            require(rows.isArray() && rows.size() <= 500, "Invalid draft collection");
            var ids = new java.util.HashSet<String>();
            for (var row : rows) {
                require(row.isObject(), "Invalid draft row");
                if (collection.equals("assets")) strings(row, "id", "concept", "nodeId", "cost", "residual", "purchase", "service", "life");
                else {
                    strings(row, "id", "concept", "direction", "driver", "nodeId", "value", "unit", "factor", "from", "to");
                    require(Set.of("INCOME", "EXPENSE").contains(row.path("direction").asText()), "Invalid draft direction");
                    require(Set.of("FIXED", "OUTPUT_VOLUME", "EXPORTED_VOLUME").contains(row.path("driver").asText()), "Invalid draft driver");
                }
                require(!row.path("id").asText().isBlank() && ids.add(row.path("id").asText()), "Duplicate or missing draft row ID");
            }
        }
    }
    private static void strings(JsonNode node, String... fields) {
        for (String field : fields) require(node.path(field).isTextual() && node.path(field).textValue().length() <= 2000,
                "Draft field must be text: " + field);
    }
}
