package org.enerscope.scenario.dto;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.enerscope.economic.model.configuration.EconomicConfiguration;
public record ScenarioConfigurationDTO(String token, String sourceToken,
                                       EconomicConfiguration configuration, List<OperationalNode> nodes, SaveRequest draft,
                                       EconomicDraftDTO legacyDraft, List<String> errors) {
 public record Field(String name,String type) {}
 public record OperationalNode(UUID id,UUID identity,String name,String type,List<Field> fields,Map<String,JsonNode> values) {}
 public record SaveRequest(String token,String sourceToken,JsonNode editor,JsonNode configuration,
                           Map<UUID,Map<String,JsonNode>> operations) {}
 public record Readiness(UUID versionId,boolean ready,List<String> errors) {}
}
