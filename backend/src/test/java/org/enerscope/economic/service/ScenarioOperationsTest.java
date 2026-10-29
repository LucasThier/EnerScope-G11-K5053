package org.enerscope.economic.service;
import org.enerscope.scenario.service.ScenarioConfigurationService;
import org.enerscope.scenario.service.ScenarioOperations;
import org.junit.jupiter.api.Test;
import org.enerscope.probabilistic.*;
import org.enerscope.node.model.*;
import org.enerscope.node.model.enums.*;
import org.enerscope.node.model.extraction.Well;
import org.enerscope.money.MoneyAmount;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ScenarioOperationsTest {
 private final ScenarioOperations service=new ScenarioOperations(EconomicExample.MAPPER);

 @Test
 void consumptionFieldsRoundTripAndCloneWithoutChangingOriginal() {
  var internal = new org.enerscope.node.model.export.InternalConsumption();
  internal.setConsumptionSummer(10); internal.setConsumptionAutumn(20);
  internal.setConsumptionWinter(30); internal.setConsumptionSpring(40);
  var industrial = new org.enerscope.node.model.export.IndustrialConsumption();
  industrial.setConsumption(50);
  for (BaseNode node : List.of(internal, industrial)) {
   node.setName("Consumer"); node.setState(NodeStateEnum.RUNNING); node.setIdentityId(UUID.randomUUID());
   node.setType(new NodeTypeData(VerticalEnum.INTERNAL_CONSUMPTION, StructuralRoleEnum.SINK,
     node == internal ? NodeTypeEnum.INTERNAL_CONSUMPTION : NodeTypeEnum.INDUSTRIAL_CONSUMPTION));
   var before = service.describe(node).values();
   var values = new TreeMap<>(before);
   String field = node == internal ? "consumptionWinter" : "consumption";
   values.put(field, EconomicExample.MAPPER.valueToTree("12.5"));
   var copy = service.prepare(node, values);
   assertEquals(12.5, service.describe(copy).values().get(field).asDouble());
   assertEquals(before, service.describe(node).values());
   assertEquals(node.getIdentityId(), copy.getIdentityId());
   assertNotSame(node.getType(), copy.getType());
   values.put(field, EconomicExample.MAPPER.valueToTree(-1));
   assertThrows(IllegalArgumentException.class, () -> service.prepare(node, values));
  }
 }

 private Well well() {
  Well node=EconomicExample.well(UUID.randomUUID(),100,"2031-01-01T00:00:00Z");
  node.setType(new NodeTypeData(VerticalEnum.EXTRACTION,StructuralRoleEnum.GENERATOR,NodeTypeEnum.WELL));
  node.setIdentityId(UUID.randomUUID());node.setDeclineCurve(new ConstantValue(0));
  node.setUpkeepCosts(MoneyAmount.of(123));node.setOperatingCosts(MoneyAmount.of(456));
  node.setDTMCost(MoneyAmount.of(77));node.setInvestmentCost(new InvestmentCost(new ArrayList<>()));
  node.setGraphData(new NodeGraphData(new GraphPosition(4d,5d),null));return node;
 }

 @Test
 void physicalEditPreservesCostsGeometryAndOriginalNode() {
  var source=well();source.deactivate();var values=new TreeMap<>(service.describe(source).values());values.put("maxCollectionCapacity",EconomicExample.MAPPER.valueToTree("250"));
  var changed=(Well)service.prepare(source,values);
  assertEquals(250,changed.getMaxCollectionCapacity());assertEquals(100,source.getMaxCollectionCapacity());
  assertEquals(source.getUpkeepCosts(),changed.getUpkeepCosts());assertEquals(source.getDTMCost(),changed.getDTMCost());
  assertNotSame(source.getGraphData(),changed.getGraphData());assertEquals(4d,changed.getGraphData().getGraphPosition().getX());
  assertNull(changed.getId());assertFalse(changed.isActive());
 }

 @Test
 void distributionsRoundTripWithoutSamplingOrSharedMutation() {
  for(var distribution:List.of(new ConstantValue(4),new NormalDistributionCase(4,2),new UniformDistributionCase(1,7))) {
   var source=well();source.setDeclineCurve(distribution);
   var changed=(Well)service.prepare(source,service.describe(source).values());
   assertEquals(service.describe(source).values(),service.describe(changed).values());
   assertNotSame(distribution,changed.getDeclineCurve());
   assertEquals(distribution.sampler(42).getAsDouble(),changed.getDeclineCurve().sampler(42).getAsDouble());
  }
 }

 @Test
 void rejectsNonfiniteNegativeAndFractionalIntegerValues() {
  for(String value:List.of("NaN","Infinity","-1","1.5","2147483648","")) {
   var node=well();var values=new TreeMap<>(service.describe(node).values());
   values.put("lifespanInMonths",EconomicExample.MAPPER.valueToTree(value));
   assertThrows(IllegalArgumentException.class,()->service.prepare(node,values));
  }
 }

 @Test
 void rejectsUnknownFieldsAndInvalidDistributionBounds() {
  var node=well();var values=new TreeMap<>(service.describe(node).values());values.put("operatingCosts",EconomicExample.MAPPER.valueToTree(0));
  assertThrows(IllegalArgumentException.class,()->service.prepare(node,values));
  values.remove("operatingCosts");values.put("declineCurve",EconomicExample.MAPPER.valueToTree(Map.of("type","UNIFORM","a",10,"b",2)));
  assertThrows(IllegalArgumentException.class,()->service.prepare(node,values));
 }

 @Test
 void remappingChangesOnlyNodeReferences() {
  var tree=EconomicExample.MAPPER.valueToTree(Map.of("nodeId","old","deliveryNodeId","old","concept","old","nested",List.of(Map.of("nodeId","old"))));
  ScenarioConfigurationService.remapReferences(tree,Map.of("old","new"));
  assertEquals("new",tree.path("nodeId").asText());assertEquals("new",tree.path("deliveryNodeId").asText());
  assertEquals("old",tree.path("concept").asText());assertEquals("new",tree.path("nested").get(0).path("nodeId").asText());
 }
}
