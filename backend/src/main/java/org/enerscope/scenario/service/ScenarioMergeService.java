package org.enerscope.scenario.service;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.enerscope.common.EntityNotFoundException;
import org.enerscope.economic.service.EconomicInputCodec;
import org.enerscope.scenario.model.*;
import org.enerscope.scenario.repository.*;
import org.enerscope.version.model.Version;
import org.enerscope.version.repository.VersionRepository;
import org.enerscope.version.service.VersionAccessGuard;
import org.enerscope.project.repository.ProjectRepository;
import org.enerscope.logging.AppLogger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional
public class ScenarioMergeService {
 private final ScenarioMergeRepository merges;
 private final ScenarioRevisionRepository revisions;
 private final ScenarioRevisionService revisionService;
 private final ScenarioDraftRepository drafts;
 private final EconomicDraftRepository economicDrafts;
 private final VersionRepository versions;
 private final ProjectRepository projects;
 private final VersionAccessGuard access;
 private final ObjectMapper mapper;
 private final AppLogger logger;
 @PersistenceContext private EntityManager em;
 public record Conflict(String kind,UUID identity,JsonNode base,JsonNode source,JsonNode target) {}
 public record View(UUID id,UUID targetId,UUID sourceRevisionId,UUID targetRevisionId,UUID baseRevisionId,
                    String state,String token,List<Conflict> conflicts) {}

 public boolean supports(UUID id) {return projects.findIdByVersionId(id).isPresent();}
 public Version prepareLegacy(UUID sourceId) {
  UUID project=projects.findIdByVersionId(sourceId).orElseThrow(()->new EntityNotFoundException("Scenario project not found"));
  var prepared=prepare(project,sourceId);return versions.findById(prepared.targetId()).orElseThrow();
 }
 public View prepare(UUID project,UUID sourceId) {
  var source=version(project,sourceId); access.assertCanManageVersion(sourceId);
  if(source.getParentVersion()==null)throw new IllegalArgumentException("Scenario has no parent");
  var target=version(project,source.getParentVersion().getId());access.assertCanManageVersion(target.getId());
  em.lock(target,LockModeType.PESSIMISTIC_WRITE);
  var existing=merges.findByTargetIdAndState(target.getId(),"PREPARED");
  if(existing.isPresent())throw new IllegalArgumentException("Target already has a prepared merge");
  if(drafts.existsByVersionId(target.getId()) || economicDrafts.findByVersionId(target.getId()).isPresent()
      || revisionService.hasWorkspaceChanges(target))throw new IllegalArgumentException("Publish or discard the target draft before merging");
  var incoming=revisionService.current(source);var current=revisionService.current(target);
  var sourceScenario=revisionService.ensure(source);
  UUID base=Objects.equals(sourceScenario.getBaseScenarioId(),target.getId())?sourceScenario.getBranchRevisionId():null;
  for(var previous:merges.findByTargetIdOrderByCreatedAtDesc(target.getId())) {
   if(previous.getState().equals("PUBLISHED") && revisions.findById(previous.getSourceRevisionId()).orElseThrow().getScenarioId().equals(sourceId)) {
    base=previous.getSourceRevisionId();break;
   }
  }
  var merge=new ScenarioMerge(target.getId(),incoming.getId(),current.getId(),base);
  rebuild(merge,target,Map.of());merges.saveAndFlush(merge);
  logger.info("Merge {} prepared from scenario {} into {}",merge.getId(),sourceId,target.getId());
  return view(merge);
 }
 public View get(UUID project,UUID targetId) {
  version(project,targetId);access.assertCanViewVersion(targetId);
  return merges.findByTargetIdAndState(targetId,"PREPARED").map(this::view).orElse(null);
 }
 public View resolve(UUID project,UUID targetId,String token,Map<String,OperationalThreeWayMerge.Choice> choices) {
  var target=version(project,targetId);access.assertCanManageVersion(targetId);em.lock(target,LockModeType.PESSIMISTIC_WRITE);
  var merge=merges.findByTargetIdAndState(targetId,"PREPARED").orElseThrow(()->new EntityNotFoundException("Prepared merge not found"));
  if(!token(merge).equals(token) || !revisionService.current(target).getId().equals(merge.getTargetRevisionId()))throw new ScenarioConflictException();
  if(merge.getWorkspaceHash()!=null && !merge.getWorkspaceHash().equals(revisionService.workspaceHash(target)))throw new ScenarioConflictException();
  rebuild(merge,target,Objects.requireNonNull(choices));merges.saveAndFlush(merge);return view(merge);
 }
 private void rebuild(ScenarioMerge merge,Version target,Map<String,OperationalThreeWayMerge.Choice> choices) {
  JsonNode incoming=normalized(merge.getSourceRevisionId()),current=normalized(merge.getTargetRevisionId());
  JsonNode base=merge.getBaseRevisionId()==null?null:normalized(merge.getBaseRevisionId());
  ObjectNode candidate=((ObjectNode)current).deepCopy();var conflicts=new ArrayList<Conflict>();var consumed=new HashSet<String>();
  for(String kind:List.of("nodes","connections")) {
   var resolutions=new HashMap<UUID,OperationalThreeWayMerge.Choice>();
   choices.forEach((key,value)->{if(key.startsWith(kind+":")){resolutions.put(UUID.fromString(key.substring(kind.length()+1)),value);consumed.add(key);}});
   var result=OperationalThreeWayMerge.merge(base==null?null:index(base.path(kind),kind),index(incoming.path(kind),kind),index(current.path(kind),kind),resolutions);
   result.conflicts().forEach(c->conflicts.add(new Conflict(kind,c.identity(),c.base(),c.source(),c.target())));
   var array=mapper.createArrayNode();result.values().forEach((identity,value)->{
    ObjectNode item=((ObjectNode)value).deepCopy();ObjectNode data=kind.equals("nodes")?(ObjectNode)item.path("data"):item;
    data.put("id",identity.toString());array.add(item);
   });candidate.set(kind,array);
  }
  if(consumed.size()!=choices.size())throw new IllegalArgumentException("Unknown merge resolution kind");
  if(conflicts.isEmpty()) revisionService.applyContent(target,mapper.convertValue(candidate,EconomicInputCodec.RevisionContent.class));
  merge.prepare(json(choices),json(conflicts),revisionService.workspaceHash(target));
 }
 private Map<UUID,JsonNode> index(JsonNode array,String kind) {
  var result=new LinkedHashMap<UUID,JsonNode>();
  for(JsonNode value:array) {
   JsonNode identity=kind.equals("nodes")?value.path("data").path("identityId"):value.path("identityId");
   if(!identity.isTextual() || result.put(UUID.fromString(identity.asText()),value)!=null)throw new IllegalArgumentException("Invalid logical identity in revision");
  }return result;
 }
 private JsonNode normalized(UUID id) {
  try{return RevisionContentHash.normalize(mapper.readTree(revisions.findById(id).orElseThrow().getSnapshotJson()));}
  catch(com.fasterxml.jackson.core.JsonProcessingException error){throw new IllegalStateException("Invalid revision",error);}
 }
 private Version version(UUID project,UUID id) {
  if(projects.findIdByVersionId(id).filter(project::equals).isEmpty())throw new EntityNotFoundException("Scenario not found in project");
  return versions.findById(id).orElseThrow();
 }
 private String token(ScenarioMerge merge){return merge.getId()+":"+merge.getGeneration();}
 private View view(ScenarioMerge merge) {
  try{return new View(merge.getId(),merge.getTargetId(),merge.getSourceRevisionId(),merge.getTargetRevisionId(),merge.getBaseRevisionId(),
   merge.getState(),token(merge),mapper.readValue(merge.getConflictsJson(),new TypeReference<List<Conflict>>(){}));}
  catch(com.fasterxml.jackson.core.JsonProcessingException error){throw new IllegalStateException("Invalid merge conflicts",error);}
 }
 private String json(Object value){try{return mapper.writeValueAsString(value);}catch(com.fasterxml.jackson.core.JsonProcessingException error){throw new IllegalStateException(error);}}
}
