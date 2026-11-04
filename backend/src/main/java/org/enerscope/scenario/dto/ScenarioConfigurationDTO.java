package org.enerscope.scenario.dto;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.enerscope.economic.model.configuration.EconomicConfiguration;
public record ScenarioConfigurationDTO(String token, String sourceToken,
                                       EconomicConfiguration configuration, List<OperationalNode> nodes, SaveRequest draft,
                                       EconomicDraftDTO legacyDraft, List<String> errors,
                                       org.enerscope.scenario.service.ScenarioRevisionService.RevisionStatus revision) {
 public ScenarioConfigurationDTO(String token,String sourceToken,EconomicConfiguration configuration,List<OperationalNode> nodes,
                                 SaveRequest draft,EconomicDraftDTO legacyDraft,List<String> errors) {
  this(token,sourceToken,configuration,nodes,draft,legacyDraft,errors,null);
 }

 public record Field(String name,String type) {}
 public record OperationalNode(UUID id,UUID identity,String name,String type,List<Field> fields,Map<String,JsonNode> values) {}
 public record SaveRequest(String token,String sourceToken,JsonNode editor,JsonNode configuration,
                           Map<UUID,Map<String,JsonNode>> operations) {}
 public record Readiness(UUID versionId,boolean ready,List<String> errors,Integer revisionNumber,Integer years,
                         java.time.Instant lastSimulation,String resultsState) {
  public Readiness(UUID versionId,boolean ready,List<String> errors){this(versionId,ready,errors,null,null,null,"UNKNOWN");}
 }
}
