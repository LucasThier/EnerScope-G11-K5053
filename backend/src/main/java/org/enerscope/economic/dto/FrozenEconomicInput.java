package org.enerscope.economic.dto;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.enerscope.economic.model.configuration.EconomicConfiguration;
import org.enerscope.economic.model.results.SimulationMetadata;

public record FrozenEconomicInput(EconomicConfiguration configuration,
                                  String name,
                                  List<PhysicalNode> nodes,
                                  JsonNode connections,
                                  SimulationMetadata simulation) {
    public record PhysicalNode(String type, JsonNode data) {}
}
