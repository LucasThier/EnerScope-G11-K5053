package org.enerscope.scenario.service;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.enerscope.common.EntityNotFoundException;
import org.enerscope.economic.service.EconomicInputCodec;
import org.enerscope.economic.service.EconomicService;
import org.enerscope.scenario.dto.EconomicDraftDTO;
import org.enerscope.scenario.dto.ScenarioConfigurationDTO;
import org.enerscope.scenario.dto.ScenarioConfigurationDTO.*;
import org.enerscope.scenario.model.ScenarioDraft;
import org.enerscope.economic.model.configuration.EconomicConfiguration;
import org.enerscope.economic.repository.*;
import org.enerscope.node.model.*;
import org.enerscope.node.model.enums.ChangeTypeEnum;
import org.enerscope.node.service.NodeCloner;
import org.enerscope.project.model.enums.ProjectMemberPermission;
import org.enerscope.project.repository.ProjectRepository;
import org.enerscope.scenario.repository.EconomicDraftRepository;
import org.enerscope.scenario.repository.ScenarioDraftRepository;
import org.enerscope.version.model.Version;
import org.enerscope.logging.AppLogger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** One transaction publishes economic and physical edits; invalid input is stored only as a draft. */
@Service @RequiredArgsConstructor @Transactional
public class ScenarioConfigurationService {
 private final EconomicService economics;
 private final EconomicConfigurationRepository configurations;
 private final EconomicDraftRepository legacyDrafts;
 private final ScenarioDraftRepository drafts;
 private final ScenarioOperations operations;
 private final EconomicInputCodec codec;
 private final ObjectMapper mapper;
 private final ProjectRepository projects;
 private final AppLogger logger;
 @PersistenceContext
 private EntityManager em;

 public ScenarioConfigurationDTO get(UUID project,UUID id) {
  Version version = economics.authorize(project,id,ProjectMemberPermission.VIEW_PROJECT);
  return view(project,version,List.of());
 }

 private ScenarioConfigurationDTO view(UUID project,Version version,List<String> errors) {
  var configuration=configurations.findByVersionId(version.getId()).map(e->read(e.getConfigurationJson(),EconomicConfiguration.class)).orElse(null);
  var draft=drafts.findByVersionId(version.getId()).map(e->read(e.getDraftJson(),SaveRequest.class)).orElse(null);
  String source=source(version,configuration);
  String token=hash(source+"|"+drafts.findByVersionId(version.getId()).map(e->e.getRevision()+":"+e.getDraftJson()).orElse("")+"|"+
      legacyDrafts.findByVersionId(version.getId()).map(e->e.getRevision()+":"+e.getDraftJson()).orElse(""));
  return new ScenarioConfigurationDTO(token,source,configuration,
      CurrentScenarioNodes.nodes(version).stream().sorted(Comparator.comparing(n->n.getId().toString())).map(operations::describe).toList(),
      draft,economics.getDraft(project,version.getId()),errors);
 }

 public ScenarioConfigurationDTO save(UUID project,UUID id,SaveRequest request) {
  var version=economics.authorize(project,id,ProjectMemberPermission.EDIT_PROJECT);
  economics.lock(version);
  var before=view(project,version,List.of());
  if(request==null || !Objects.equals(request.token(),before.token()) || !Objects.equals(request.sourceToken(),before.sourceToken()))
   throw new ScenarioConflictException();
  if(request.editor()==null || !request.editor().isObject() ||
     !Set.of("simple","advanced").contains(request.editor().path("mode").asText()) ||
     !request.editor().path("value").isObject() || mapper.valueToTree(request).toString().length()>2000000)
   throw new IllegalArgumentException("Invalid scenario draft");
  if(request.editor().path("mode").asText().equals("simple"))
   new EconomicDraftDTO(1,request.editor().path("value")).validateStructure();
  else for(String field:List.of("taxEntities","boundary","nodeProfiles","adjustments","contracts","assets","taxTreatments","conversions"))
   if(!request.editor().path("value").path(field).isArray()) throw new IllegalArgumentException("Invalid advanced editor collection: "+field);
  var errors=new ArrayList<String>();
  var prepared=new LinkedHashMap<UUID,BaseNode>();
  var supplied=request.operations();
  if(supplied==null || !supplied.keySet().equals(economics.nodeIds(version))) errors.add("La lista de nodos no coincide con el escenario.");
  else for(var node:CurrentScenarioNodes.nodes(version)) {
   try { prepared.put(node.getId(),operations.prepare(node,supplied.get(node.getId()))); }
   catch(IllegalArgumentException e) { errors.add(e.getMessage()); }
  }
  EconomicConfiguration configuration=null;
  try {
   if(request.configuration()==null || request.configuration().isNull()) throw new IllegalArgumentException("Completá las secciones económicas pendientes.");
   configuration=mapper.treeToValue(request.configuration(),EconomicConfiguration.class);
   economics.validateConfiguration(configuration,version);
   if(errors.isEmpty()) codec.validatePrepared(version,configuration,prepared);
  } catch(com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException e) { errors.add(e.getMessage()); }
  if(!errors.isEmpty()) {
   var entity=drafts.findByVersionId(id).orElseGet(()->new ScenarioDraft(version,""));
   entity.replace(json(request));drafts.saveAndFlush(entity);
   logger.info("Scenario draft saved for version {}",id);
   return view(project,version,errors);
  }
  var remap=new HashMap<String,String>();
  for(int i=0;i<version.getNodeSnapshot().size();i++) {
   var original=version.getNodeSnapshot().get(i);
   if(!CurrentScenarioNodes.includes(original)) continue;
   var candidate=prepared.get(original.getId());
   if(operations.describe(original).values().equals(operations.describe(candidate).values())) continue;
   // Always copy on a joint save: even a node originally created here may have been inherited by a child.
   em.persist(candidate);
   version.getNodeSnapshot().set(i,candidate);
   version.getNodeChanges().add(new NodeChange(ChangeTypeEnum.EDIT,original,candidate));
   remap.put(original.getId().toString(),candidate.getId().toString());
  }
  for(int i=0;i<version.getConnectionSnapshot().size();i++) {
   var old=version.getConnectionSnapshot().get(i);
   if(!remap.containsKey(old.getFromNodeId().toString()) && !remap.containsKey(old.getToNodeId().toString())) continue;
   var next=NodeCloner.cloneConnection(old);
   next.setFromNodeId(UUID.fromString(remap.getOrDefault(old.getFromNodeId().toString(),old.getFromNodeId().toString())));
   next.setToNodeId(UUID.fromString(remap.getOrDefault(old.getToNodeId().toString(),old.getToNodeId().toString())));
   em.persist(next);version.getConnectionSnapshot().set(i,next);
   version.getConnectionChanges().add(new ConnectionChange(ChangeTypeEnum.EDIT,old,next));
  }
  JsonNode tree=mapper.valueToTree(configuration);
  remapReferences(tree,remap);
  configuration=mapper.convertValue(tree,EconomicConfiguration.class);
  drafts.deleteByVersionId(id);
  economics.save(project,id,configuration);
  codec.freeze(version,configuration,1,0);
  em.flush();
  logger.info("Scenario configuration published for version {}",id);
  return view(project,version,List.of());
 }

 public static void remapReferences(JsonNode tree, Map<String, String> ids) {
  if(tree.isObject()) {
   var object=(ObjectNode)tree;
   var keys=new ArrayList<String>();object.fieldNames().forEachRemaining(keys::add);
   for(String key:keys) {
    JsonNode value=object.get(key);
    if(Set.of("nodeId","deliveryNodeId").contains(key) && value.isTextual() && ids.containsKey(value.asText())) object.put(key,ids.get(value.asText()));
    else remapReferences(value,ids);
   }
  } else if(tree.isArray()) tree.forEach(child->remapReferences(child,ids));
 }

 public ScenarioConfigurationDTO discard(UUID project,UUID id,String token) {
  var version = economics.authorize(project,id,ProjectMemberPermission.EDIT_PROJECT);economics.lock(version);
  if(!Objects.equals(token,view(project,version,List.of()).token())) throw new ScenarioConflictException();
  drafts.deleteByVersionId(id);legacyDrafts.deleteByVersionId(id);em.flush();
  return view(project,version,List.of());
 }

 public List<Readiness> readiness(UUID project) {
  var p=projects.findById(project).orElseThrow(()->new EntityNotFoundException("Project not found"));
  // Validate project access even when the project has no versions.
  economics.authorizeProject(project,ProjectMemberPermission.VIEW_PROJECT);
  return p.getVersions().stream().filter(Version::isActive).map(v->{
   var errors=new ArrayList<String>();
   if(drafts.existsByVersionId(v.getId()) || legacyDrafts.findByVersionId(v.getId()).isPresent()) errors.add("Hay un borrador pendiente en Configuración del Escenario.");
   try {
    var config = economics.configuration(v.getId());economics.validateConfiguration(config,v);codec.freeze(v,config,1,0);
   } catch(EntityNotFoundException | IllegalArgumentException e) { errors.add(e.getMessage()); }
   return new Readiness(v.getId(),errors.isEmpty(),errors);
  }).toList();
 }

 private String source(Version version,EconomicConfiguration configuration) {
  var root=mapper.createObjectNode();
  root.set("configuration",mapper.valueToTree(configuration));
  root.set("nodes",codec.physicalNodes(version));
  root.set("connections",mapper.valueToTree(version.getConnectionSnapshot()));
  return hash(canonical(root).toString());
 }

 private JsonNode canonical(JsonNode node) {
  if(node.isObject()) {
   ObjectNode result=mapper.createObjectNode();var keys=new TreeSet<String>();node.fieldNames().forEachRemaining(keys::add);
   for(String key:keys) {
    // Presentation and persistence timestamps do not affect scenario configuration.
    if(!Set.of("graphData","createdAt","lastModified").contains(key)) result.set(key,canonical(node.get(key)));
   }
   return result;
  }
  if(node.isArray()) { var values=new ArrayList<JsonNode>();node.forEach(n->values.add(canonical(n)));
   values.sort(Comparator.comparing(JsonNode::toString));var array=mapper.createArrayNode();values.forEach(array::add);return array; }
  return node;
 }
 private String hash(String value) {
  try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
  catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
 }
 private String json(Object value) { try {return mapper.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);} }
 private <T>T read(String value,Class<T> type){try{return mapper.readValue(value,type);}catch(Exception e){throw new IllegalStateException(e);}}
}
