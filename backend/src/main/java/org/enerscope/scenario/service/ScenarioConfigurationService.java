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
import org.enerscope.scenario.model.Scenario;
import org.enerscope.scenario.model.ScenarioDraft;
import org.enerscope.economic.model.configuration.EconomicConfiguration;
import org.enerscope.economic.repository.*;
import org.enerscope.node.model.*;
import org.enerscope.project.model.enums.ProjectMemberPermission;
import org.enerscope.project.repository.ProjectRepository;
import org.enerscope.scenario.repository.EconomicDraftRepository;
import org.enerscope.scenario.repository.ScenarioDraftRepository;
import org.enerscope.version.model.Version;
import org.enerscope.logging.AppLogger;
import org.enerscope.simulator.InvalidSimulationGraphException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes the economic configuration of a scenario in one transaction; incomplete input is stored only as its draft.
 * Node values belong to the version and are no longer saved here.
 */
@Service @RequiredArgsConstructor @Transactional
public class ScenarioConfigurationService {
 private final EconomicService economics;
 private final EconomicConfigurationRepository configurations;
 private final EconomicDraftRepository legacyDrafts;
 private final ScenarioDraftRepository drafts;
 private final ScenarioOperations operations;
 private final ScenarioChangeScope changeScope;
 private final EconomicInputCodec codec;
 private final ObjectMapper mapper;
 private final ProjectRepository projects;
 private final ScenarioDefaults scenarioDefaults;
 private final org.enerscope.scenario.repository.ScenarioRepository scenarioRepository;
 private final AppLogger logger;
 @PersistenceContext
 private EntityManager em;

 public ScenarioConfigurationDTO get(UUID project,UUID id) {
  Version version = economics.authorize(project,id,ProjectMemberPermission.VIEW_PROJECT);
  return view(version,scenarioDefaults.existing(id).orElse(null),List.of());
 }

 /** The configuration screen's state for one scenario; the caller has authorized the read. */
 @Transactional(readOnly=true)
 public ScenarioConfigurationDTO viewOf(Version version,Scenario scenario) {
  return view(version,scenario,List.of());
 }

 /** {@code scenario} is null while the version has no scenario yet: nothing is stored, so nothing is shown. */
 private ScenarioConfigurationDTO view(Version version,Scenario scenario,List<String> errors) {
  var scenarioId=Optional.ofNullable(scenario).map(s->s.getId());
  var configuration=scenarioId.flatMap(configurations::findByScenarioId).map(e->read(e.getConfigurationJson(),EconomicConfiguration.class)).orElse(null);
  var storedDraft=scenarioId.flatMap(drafts::findByScenarioId);
  var draft=storedDraft.map(e->read(e.getDraftJson(),SaveRequest.class)).orElse(null);
  String source=source(version,configuration);
  String token=hash(source+"|"+storedDraft.map(e->e.getRevision()+":"+e.getDraftJson()).orElse("")+"|"+
      scenarioId.flatMap(legacyDrafts::findByScenarioId).map(e->e.getRevision()+":"+e.getDraftJson()).orElse(""));
  // A draft saved before "Operación" moved to the version may carry node values that are no longer applied from here.
  boolean ignored=draft!=null && draft.operations()!=null && !draft.operations().isEmpty()
      && !changeScope.changedNodes(version,draft.operations(),null,null).isEmpty();
  return new ScenarioConfigurationDTO(token,source,configuration,
      CurrentScenarioNodes.nodes(version).stream().sorted(Comparator.comparing(n->n.getId().toString())).map(operations::describe).toList(),
      draft,scenario==null ? null : economics.draftOf(scenario),errors,ignored);
 }

 public ScenarioConfigurationDTO save(UUID project,UUID id,SaveRequest request) {
  var version=economics.authorizeConfigurationEdit(project,id);
  economics.lock(version);
  return saveIn(version,scenarioDefaults.forVersion(version),request);
 }

 /**
  * Publishes the economic configuration of {@code scenario}, or stores an incomplete one as its draft. Only the
  * economic and simulation part is saved here: node values ("Operación") belong to the version and are edited on the
  * node; a request that would change them is refused. Order: vertical scope (403), shape (400), stale token (409).
  */
 public ScenarioConfigurationDTO saveIn(Version version,Scenario scenario,SaveRequest request) {
  economics.lock(version);
  if(request==null) throw new IllegalArgumentException("Invalid scenario draft");
  var before=view(version,scenario,List.of());
  changeScope.assertCanSave(version,null,before.configuration(),parsed(request.configuration()));
  if(request.editor()==null || !request.editor().isObject() ||
     !Set.of("simple","advanced").contains(request.editor().path("mode").asText()) ||
     !request.editor().path("value").isObject() || mapper.valueToTree(request).toString().length()>2000000)
   throw new IllegalArgumentException("Invalid scenario draft");
  if(request.editor().path("mode").asText().equals("simple"))
   new EconomicDraftDTO(1,request.editor().path("value")).validateStructure();
  else for(String field:List.of("taxEntities","boundary","nodeProfiles","adjustments","contracts","assets","taxTreatments","conversions"))
   if(!request.editor().path("value").path(field).isArray()) throw new IllegalArgumentException("Invalid advanced editor collection: "+field);
  // A stale token is a conflict first: node values that differ from the current ones may only be the screen's old copy.
  if(!Objects.equals(request.token(),before.token()) || !Objects.equals(request.sourceToken(),before.sourceToken()))
   throw new ScenarioConflictException();
  rejectNodeChanges(version,request.operations());
  var errors=new ArrayList<String>();
  EconomicConfiguration configuration=null;
  try {
   if(request.configuration()==null || request.configuration().isNull()) throw new IllegalArgumentException("Completá las secciones económicas pendientes.");
   configuration=mapper.treeToValue(request.configuration(),EconomicConfiguration.class);
   economics.validateConfiguration(configuration,version);
   codec.freeze(version,configuration,1,0);
  } catch(com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException e) { errors.add(e.getMessage()); }
  if(!errors.isEmpty()) {
   var entity=drafts.findByScenarioId(scenario.getId()).orElseGet(()->new ScenarioDraft(version,""));
   entity.replace(json(request));entity.assignScenario(scenario);drafts.saveAndFlush(entity);
   logger.info("Scenario draft saved for scenario {} of version {}",scenario.getId(),version.getId());
   return view(version,scenario,errors);
  }
  drafts.deleteByScenarioId(scenario.getId());
  economics.saveIn(version,scenario,configuration);
  em.flush();
  logger.info("Scenario configuration published for scenario {} of version {}",scenario.getId(),version.getId());
  return view(version,scenario,List.of());
 }

 /** Node values are edited on the node (version editor); a scenario save that would change them is refused, never silently dropped. */
 private void rejectNodeChanges(Version version,Map<UUID,Map<String,JsonNode>> supplied) {
  if(supplied==null || supplied.isEmpty()) return;
  var changed=changeScope.changedNodes(version,supplied,null,null);
  if(!changed.isEmpty())
   throw new IllegalArgumentException("Los valores de operación de los nodos ("+
     changed.stream().map(BaseNode::getName).sorted().collect(java.util.stream.Collectors.joining(", "))+
     ") se editan en cada nodo de la versión, no en el escenario.");
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
  var version = economics.authorizeConfigurationEdit(project,id);economics.lock(version);
  var scenario=scenarioDefaults.existing(id).orElse(null);
  if(scenario==null) {
   if(!Objects.equals(token,view(version,null,List.of()).token())) throw new ScenarioConflictException();
   return view(version,null,List.of());
  }
  return discardIn(version,scenario,token);
 }

 /** Drops the drafts of {@code scenario} if {@code token} is still current (otherwise 409); the caller has authorized and locked. */
 public ScenarioConfigurationDTO discardIn(Version version,Scenario scenario,String token) {
  economics.lock(version);
  if(!Objects.equals(token,view(version,scenario,List.of()).token())) throw new ScenarioConflictException();
  drafts.deleteByScenarioId(scenario.getId());legacyDrafts.deleteByScenarioId(scenario.getId());
  em.flush();
  return view(version,scenario,List.of());
 }

 /** Whether one scenario can be simulated now: no pending draft, and a configuration that validates against its version. */
 public Readiness readinessOf(Version version,Scenario scenario) {
  var errors=new ArrayList<String>();
  if(drafts.existsByScenarioId(scenario.getId()) || legacyDrafts.findByScenarioId(scenario.getId()).isPresent())
   errors.add("Hay un borrador pendiente en Configuración del Escenario.");
  try {
   var config=economics.configuration(scenario);economics.validateConfiguration(config,version);codec.freeze(version,config,1,0);
  } catch(EntityNotFoundException | IllegalArgumentException e) { errors.add(reason(e,version,scenario)); }
  catch(RuntimeException unexpected) { errors.add(notChecked(unexpected,version,scenario)); }
  return new Readiness(version.getId(),errors.isEmpty(),errors);
 }

 /** Whether each active version's default scenario can be simulated; the scenarios, drafts and configurations are read in batches. */
 public List<Readiness> readiness(UUID project) {
  var p=projects.findById(project).orElseThrow(()->new EntityNotFoundException("Project not found"));
  // Validate project access even when the project has no versions.
  economics.authorizeProject(project,ProjectMemberPermission.VIEW_PROJECT);
  var active=p.getVersions().stream().filter(Version::isActive).toList();
  var versionIds=active.stream().map(Version::getId).toList();
  Map<UUID,Scenario> defaults=new HashMap<>();
  if(!versionIds.isEmpty()) scenarioRepository.findByVersionIdInAndDefaultScenarioTrueAndActiveTrue(versionIds).forEach(s->defaults.put(s.getVersion().getId(),s));
  var scenarioIds=defaults.values().stream().map(Scenario::getId).toList();
  Set<UUID> pending=new HashSet<>();
  Map<UUID,String> stored=new HashMap<>();
  if(!scenarioIds.isEmpty()) {
   pending.addAll(drafts.scenarioIdsWithDraft(scenarioIds));pending.addAll(legacyDrafts.scenarioIdsWithDraft(scenarioIds));
   configurations.findByScenarioIdIn(scenarioIds).forEach(c->stored.put(c.getScenario().getId(),c.getConfigurationJson()));
  }
  return active.stream().map(v->{
   var errors=new ArrayList<String>();
   var scenario=defaults.get(v.getId());
   if(scenario!=null && pending.contains(scenario.getId())) errors.add("Hay un borrador pendiente en Configuración del Escenario.");
   try {
    String json=scenario==null ? null : stored.get(scenario.getId());
    if(json==null) throw new EntityNotFoundException("Economic configuration not found");
    var config=read(json,EconomicConfiguration.class);economics.validateConfiguration(config,v);codec.freeze(v,config,1,0);
   } catch(EntityNotFoundException | IllegalArgumentException e) { errors.add(reason(e,v,scenario)); }
   catch(RuntimeException unexpected) { errors.add(notChecked(unexpected,v,scenario)); }
   return new Readiness(v.getId(),errors.isEmpty(),errors);
  }).toList();
 }

 /** The reason a scenario is not ready. A graph the simulator cannot process is also logged: it is bad stored data. */
 private String reason(RuntimeException rejection,Version version,Scenario scenario) {
  if(rejection instanceof InvalidSimulationGraphException)
   logger.warn("Scenario {} of version {} is not ready: {} (cause: {})",scenario==null ? null : scenario.getId(),version.getId(),
       rejection.getMessage(),rejection.getCause()==null ? "none" : rejection.getCause().getClass().getSimpleName());
  return rejection.getMessage();
 }

 /** An unforeseen failure checking ONE scenario: that one is not ready, the others are still answered. */
 private String notChecked(RuntimeException unexpected,Version version,Scenario scenario) {
  ScenarioCheckFailures.rethrowUnlessScenarioSpecific(unexpected);
  logger.error("Readiness of scenario {} of version {} could not be checked",
      scenario==null ? null : scenario.getId(),version.getId(),unexpected);
  return ScenarioCheckFailures.NOT_CHECKED;
 }

 private String source(Version version,EconomicConfiguration configuration) {
  var root=mapper.createObjectNode();
  root.set("configuration",mapper.valueToTree(configuration));
  root.set("nodes",codec.physicalNodes(version));
  root.set("connections",mapper.valueToTree(version.getConnectionSnapshot()));
  return hash(canonical(root).toString());
 }

 static JsonNode canonical(JsonNode node) {
  if(node.isObject()) {
   ObjectNode result=JsonNodeFactory.instance.objectNode();var keys=new TreeSet<String>();node.fieldNames().forEachRemaining(keys::add);
   for(String key:keys) {
    // Presentation and persistence timestamps do not affect scenario configuration.
    if(!Set.of("graphData","createdAt","lastModified").contains(key)) result.set(key,canonical(node.get(key)));
   }
   return result;
  }
  if(node.isArray()) { var values=new ArrayList<JsonNode>();node.forEach(n->values.add(canonical(n)));
   values.sort(Comparator.comparing(JsonNode::toString));var array=JsonNodeFactory.instance.arrayNode();values.forEach(array::add);return array; }
  return node;
 }
 private String hash(String value) {
  try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
  catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
 }
 private EconomicConfiguration parsed(JsonNode value) {
  if(value==null || value.isNull()) return null;
  try { return mapper.treeToValue(value,EconomicConfiguration.class); }
  catch(com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException e) { return null; }
 }
 private String json(Object value) { try {return mapper.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);} }
 private <T>T read(String value,Class<T> type){try{return mapper.readValue(value,type);}catch(Exception e){throw new IllegalStateException(e);}}
}
