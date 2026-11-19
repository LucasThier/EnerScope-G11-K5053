package org.enerscope.scenario.service;

import java.math.BigDecimal;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.enerscope.economic.model.configuration.EconomicConfiguration;
import org.enerscope.logging.AppLogger;
import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.enums.NodeTypeEnum;
import org.enerscope.node.model.enums.VerticalEnum;
import org.enerscope.version.model.Version;
import org.enerscope.version.service.VersionAccessGuard;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ScenarioChangeScope {
 private static final Map<String,String> CONFIGURATION_REFERENCES=Map.of(
   "nodeProfiles","nodeId","assets","nodeId","conversions","nodeId","contracts","deliveryNodeId");
 private static final Map<String,String> DRAFT_REFERENCES=Map.of("assets","nodeId","rules","nodeId");

 private final ScenarioOperations operations;
 private final VersionAccessGuard guard;
 private final ObjectMapper mapper;
 private final AppLogger logger;

 public void assertCanSave(Version version,Map<UUID,Map<String,JsonNode>> supplied,
                           EconomicConfiguration stored,EconomicConfiguration requested) {
  authorize(version,changedNodes(version,supplied,stored,requested));
 }

 public void assertCanSaveDraft(Version version,JsonNode storedDraft,JsonNode requestedDraft) {
  authorize(version,changedDraftNodes(version,storedDraft,requestedDraft));
 }

 public Set<BaseNode> changedNodes(Version version,Map<UUID,Map<String,JsonNode>> supplied,
                                   EconomicConfiguration stored,EconomicConfiguration requested) {
  var nodes=nodesById(version);
  var changed=new LinkedHashSet<BaseNode>();
  if(supplied!=null) for(var node:nodes.values()) {
   var values=supplied.get(node.getId());
   if(values!=null && physicalChange(node,values)) changed.add(node);
  }
  if(requested!=null) for(UUID id:changedReferences(tree(stored),tree(requested),CONFIGURATION_REFERENCES))
   if(nodes.containsKey(id)) changed.add(nodes.get(id));
  return changed;
 }

 public Set<BaseNode> changedDraftNodes(Version version,JsonNode storedDraft,JsonNode requestedDraft) {
  var nodes=nodesById(version);
  var changed=new LinkedHashSet<BaseNode>();
  for(UUID id:changedReferences(storedDraft,requestedDraft,DRAFT_REFERENCES))
   if(nodes.containsKey(id)) changed.add(nodes.get(id));
  return changed;
 }

 private void authorize(Version version,Set<BaseNode> changed) {
  var verticals=EnumSet.noneOf(VerticalEnum.class);
  boolean untyped=false;
  for(var node:changed) {
   NodeTypeEnum type=node.getType()==null ? null : node.getType().getNodeType();
   if(type==null) untyped=true; else verticals.add(type.vertical());
  }
  logger.debug("Scenario save in version {} changes {} node(s) across verticals {}",version.getId(),changed.size(),verticals);
  if(untyped) guard.assertCanEditVersion(version.getId());
  for(var vertical:verticals) guard.assertCanEditNodeVertical(version.getId(),vertical);
 }

 private boolean physicalChange(BaseNode node,Map<String,JsonNode> values) {
  var current=operations.describe(node).values();
  if(current.equals(values)) return false;
  try { return !operations.describe(operations.prepare(node,values)).values().equals(current); }
  catch(IllegalArgumentException e) { return true; }
 }

 private Set<UUID> changedReferences(JsonNode stored,JsonNode requested,Map<String,String> references) {
  var before=bundles(stored,references);
  var after=bundles(requested,references);
  var ids=new HashSet<UUID>(before.keySet());ids.addAll(after.keySet());
  var changed=new HashSet<UUID>();
  for(UUID id:ids) {
   JsonNode a=before.get(id), b=after.get(id);
   if(a==null || b==null || !a.equals(b)) changed.add(id);
  }
  return changed;
 }

 private Map<UUID,JsonNode> bundles(JsonNode root,Map<String,String> references) {
  var result=new HashMap<UUID,ObjectNode>();
  if(root==null || !root.isObject()) return Map.of();
  for(var reference:references.entrySet()) {
   var rows=root.path(reference.getKey());
   if(!rows.isArray()) continue;
   for(var row:rows) {
    UUID id=uuid(row.path(reference.getValue()));
    if(id==null) continue;
    ((ArrayNode)result.computeIfAbsent(id,k->mapper.createObjectNode()).withArray(reference.getKey())).add(row);
   }
  }
  var canonical=new HashMap<UUID,JsonNode>();
  result.forEach((id,bundle)->canonical.put(id,ScenarioConfigurationService.canonical(normalized(bundle))));
  return canonical;
 }

 private JsonNode normalized(JsonNode node) {
  if(node.isNumber()) return mapper.getNodeFactory().numberNode(stripped(node.decimalValue()));
  if(node.isObject()) {
   var copy=mapper.createObjectNode();
   node.fields().forEachRemaining(e->copy.set(e.getKey(),normalized(e.getValue())));
   return copy;
  }
  if(node.isArray()) {
   var copy=mapper.createArrayNode();
   node.forEach(child->copy.add(normalized(child)));
   return copy;
  }
  return node;
 }

 private static BigDecimal stripped(BigDecimal value) {
  return value.signum()==0 ? BigDecimal.ZERO : value.stripTrailingZeros();
 }

 private JsonNode tree(EconomicConfiguration configuration) {
  return configuration==null ? null : mapper.valueToTree(configuration);
 }

 private static UUID uuid(JsonNode value) {
  if(!value.isTextual() || value.asText().isBlank()) return null;
  try { return UUID.fromString(value.asText()); }
  catch(IllegalArgumentException e) { return null; }
 }

 private static Map<UUID,BaseNode> nodesById(Version version) {
  var nodes=new LinkedHashMap<UUID,BaseNode>();
  CurrentScenarioNodes.nodes(version).forEach(node->nodes.put(node.getId(),node));
  return nodes;
 }
}
