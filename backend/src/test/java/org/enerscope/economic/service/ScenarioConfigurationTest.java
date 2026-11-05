package org.enerscope.economic.service;
import com.fasterxml.jackson.databind.*;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.*;
import org.enerscope.scenario.dto.ScenarioConfigurationDTO;
import org.enerscope.scenario.dto.ScenarioConfigurationDTO.SaveRequest;
import org.enerscope.economic.dto.EconomicJobRequest;
import org.enerscope.economic.model.configuration.EconomicConfiguration;
import org.enerscope.node.model.*;
import org.enerscope.node.model.enums.*;
import org.enerscope.node.model.extraction.Well;
import org.enerscope.money.MoneyAmount;
import org.enerscope.organization.model.Organization;
import org.enerscope.project.model.Project;
import org.enerscope.scenario.service.ScenarioConfigurationService;
import org.enerscope.scenario.service.ScenarioConflictException;
import org.enerscope.version.model.Version;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.enerscope.session.model.Session;
import org.enerscope.probabilistic.ConstantValue;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"economic.worker.enabled=false","spring.datasource.url=jdbc:h2:mem:scenario;MODE=PostgreSQL;NON_KEYWORDS=YEAR;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("test") @Transactional
class ScenarioConfigurationTest {
 @Autowired EntityManager em;
 @Autowired
 ScenarioConfigurationService scenarios;
 @Autowired EconomicService economics;
 @Autowired EconomicJobService jobs;
 @Autowired org.enerscope.version.service.VersionService versions;
 @Autowired ObjectMapper mapper;
 @Autowired org.enerscope.scenario.service.ScenarioRevisionService revisions;
 @Autowired org.enerscope.scenario.service.ScenarioMergeService merges;
 @Autowired org.enerscope.project.service.ProjectService projects;
 @Autowired org.enerscope.scenario.service.ScenarioRevisionImportService importer;
 @Autowired org.enerscope.scenario.service.ScenarioForkService forks;
 @Autowired org.enerscope.economic.repository.EconomicJobRepository jobRepository;

 UUID projectId,versionId;Well well;
 @BeforeEach void setup() throws Exception {
  var user=new User("scenario-"+UUID.randomUUID()+"@example.com","Scenario","Admin","test-hash",PlatformRole.ADMIN);
  em.persist(user);
  var authentication=new UsernamePasswordAuthenticationToken(user.getId(),null,List.of());
  authentication.setDetails(new Session("test",user,Instant.now().plusSeconds(3600)));SecurityContextHolder.getContext().setAuthentication(authentication);
  var source=EconomicExample.version();var nodes=new ArrayList<BaseNode>();var ids=new HashMap<String,String>();
  for(var original:source.getNodeSnapshot()) {
   Well n=(Well)original;String old=n.getId().toString();ReflectionTestUtils.setField(n,"id",null);
   n.setType(new NodeTypeData(VerticalEnum.EXTRACTION,StructuralRoleEnum.GENERATOR,NodeTypeEnum.WELL));
   n.setIdentityId(UUID.randomUUID());n.setGraphData(new NodeGraphData(new GraphPosition(0d,0d),null));n.setInvestmentCost(new InvestmentCost(new ArrayList<>()));
   n.setUpkeepCosts(MoneyAmount.of(2));n.setOperatingCosts(MoneyAmount.of(3));n.setDTMCost(MoneyAmount.of(4));n.setDeclineCurve(new ConstantValue(0));
   em.persist(n);nodes.add(n);ids.put(old,n.getId().toString());
  }
  well=(Well)nodes.getFirst();
  var org=new Organization("Scenario test");em.persist(org);
  var version=new Version("Scenario",null,nodes,new ArrayList<>(),new ArrayList<>(),new ArrayList<>());em.persist(version);
  var project=new Project("Scenario","Test",org);project.addVersion(version);em.persist(project);em.flush();
  projectId=project.getId();versionId=version.getId();
  String json=mapper.writeValueAsString(EconomicExample.configuration());for(var e:ids.entrySet())json=json.replace(e.getKey(),e.getValue());
  economics.save(projectId,versionId,mapper.readValue(json,EconomicConfiguration.class));
  var initial=scenarios.get(projectId,versionId);
  scenarios.save(projectId,versionId,request(initial));
 }

 @Test
 void removedNodesStayInHistoryButLeaveConfigurationAndNewInputs() {
  var version=em.find(Version.class,versionId);
  var removed=org.enerscope.node.service.NodeCloner.cloneNode(well);
  removed.setIdentityId(UUID.randomUUID());removed.setName("Removed consumer");removed.setState(NodeStateEnum.REMOVED);
  em.persist(removed);version.getNodeSnapshot().add(removed);
  var edge=new NodeConnection(UUID.randomUUID(),well.getId(),removed.getId());em.persist(edge);version.getConnectionSnapshot().add(edge);em.flush();
  var state=scenarios.get(projectId,versionId);
  assertFalse(state.nodes().stream().anyMatch(n->n.id().equals(removed.getId())));
  var saved=scenarios.save(projectId,versionId,request(state));
  assertNull(saved.draft());assertTrue(saved.errors().isEmpty());
  assertTrue(version.getNodeSnapshot().contains(removed));
  assertEquals(NodeStateEnum.REMOVED,removed.getState());
  var codec=new EconomicInputCodec(mapper);
  var frozen=codec.freeze(version,saved.configuration(),1,42);
  var current=codec.thaw(frozen);
  assertFalse(current.getNodeSnapshot().stream().anyMatch(n->n.getId().equals(removed.getId())));
  assertTrue(current.getConnectionSnapshot().stream().noneMatch(e->e.getToNodeId().equals(removed.getId())));
  assertFalse(economics.nodeIds(version).contains(removed.getId()));
 }

 @Test
 void removingReferencedNodeRequiresEconomicRulesToBeReviewed() {
  well.setState(NodeStateEnum.REMOVED);em.flush();
  assertThrows(IllegalArgumentException.class,()->economics.validateConfiguration(economics.get(projectId,versionId),em.find(Version.class,versionId)));
 }

 @AfterEach
 void clear(){SecurityContextHolder.clearContext();}
 SaveRequest request(ScenarioConfigurationDTO state) {
  var edits=new HashMap<UUID,Map<String,JsonNode>>();state.nodes().forEach(n->edits.put(n.id(),new TreeMap<>(n.values())));
  return new SaveRequest(state.token(),state.sourceToken(),mapper.valueToTree(Map.of("mode","advanced","value",state.configuration())),mapper.valueToTree(state.configuration()),edits);
 }

 @Test
 void publishesPhysicsAndEconomicsTogetherAndRemapsReferencesWithoutMutatingParent() {
  var parent=em.find(Version.class,versionId);
  var child=new Version("Child",parent,new ArrayList<>(parent.getNodeSnapshot()),new ArrayList<>(),new ArrayList<>(),new ArrayList<>());em.persist(child);
  em.find(Project.class,projectId).addVersion(child);em.flush();
  economics.save(projectId,child.getId(),economics.get(projectId,versionId));
  var state=scenarios.get(projectId,child.getId());var input=request(state);
  input.operations().get(well.getId()).put("maxCollectionCapacity",mapper.valueToTree("250"));
  ((com.fasterxml.jackson.databind.node.ObjectNode)input.configuration()).put("wacc",0.2);
  var saved=scenarios.save(projectId,child.getId(),input);
  assertNull(saved.draft());assertEquals(0,new java.math.BigDecimal("0.2").compareTo(saved.configuration().wacc()));
  var changed=saved.nodes().stream().filter(n->n.identity().equals(well.getIdentityId())).findFirst().orElseThrow();
  assertNotEquals(well.getId(),changed.id());assertEquals(250,changed.values().get("maxCollectionCapacity").asInt());
  assertTrue(saved.configuration().conversions().stream().anyMatch(c->c.nodeId().equals(changed.id())));
  assertEquals(100,well.getMaxCollectionCapacity());
 }

 @Test
 void incompleteSavePreservesPublishedValuesAndBlocksJobUntilDiscarded() {
  var state=scenarios.get(projectId,versionId);var input=request(state);
  input.operations().get(well.getId()).put("maxCollectionCapacity",mapper.valueToTree(""));
  var saved=scenarios.save(projectId,versionId,input);
  assertNotNull(saved.draft());assertFalse(saved.errors().isEmpty());assertEquals(100,well.getMaxCollectionCapacity());
  assertThrows(IllegalArgumentException.class,()->jobs.submit(projectId,versionId,new EconomicJobRequest(1,"42")));
 }

 @Test
 void draftSurvivesReloadAndDiscardEnablesSimulation() {
  var state=scenarios.get(projectId,versionId);var valid=request(state);
  var invalid=new SaveRequest(valid.token(),valid.sourceToken(),valid.editor(),null,valid.operations());
  scenarios.save(projectId,versionId,invalid);em.flush();em.clear();
  var loaded=scenarios.get(projectId,versionId);assertNotNull(loaded.draft());
  assertFalse(scenarios.readiness(projectId).getFirst().ready());
  scenarios.discard(projectId,versionId,loaded.token());
  assertTrue(scenarios.readiness(projectId).getFirst().ready());
  assertNotNull(jobs.submit(projectId,versionId,new EconomicJobRequest(1,"42")).id());
 }
 @Test void mapEditAppearsInConfigurationAndRejectsStaleSave() {
  var before=scenarios.get(projectId,versionId);
  var basics=new org.enerscope.node.dto.NodeBasicsDTO();basics.setName("Changed in map");
  versions.updateNodeBasics(versionId,well.getId(),basics);
  var after=scenarios.get(projectId,versionId);
  assertTrue(after.nodes().stream().anyMatch(n->n.name().equals("Changed in map")));
  assertNotEquals(before.sourceToken(),after.sourceToken());
  assertThrows(ScenarioConflictException.class,()->scenarios.save(projectId,versionId,request(before)));
 }

 @Test
 void persistedDraftKeepsOriginalSourceAfterMapEdit() {
  var before=scenarios.get(projectId,versionId);var valid=request(before);
  scenarios.save(projectId,versionId,new SaveRequest(valid.token(),valid.sourceToken(),valid.editor(),null,valid.operations()));
  well.setMaxCollectionCapacity(321);em.flush();
  var after=scenarios.get(projectId,versionId);
  assertNotEquals(after.sourceToken(),after.draft().sourceToken());
  assertThrows(ScenarioConflictException.class,()->scenarios.save(projectId,versionId,new SaveRequest(after.token(),after.draft().sourceToken(),valid.editor(),valid.configuration(),valid.operations())));
 }

 @Test
 void fullMapEditRemapsEconomicAndConnectionReferences() {
  var version=em.find(Version.class,versionId);
  var connection=new NodeConnection(UUID.randomUUID(),well.getId(),version.getNodeSnapshot().get(1).getId());
  em.persist(connection);version.getConnectionSnapshot().add(connection);em.flush();
  var dto=new org.enerscope.node.dto.WellDTO();
  dto.setName("Map physical edit");dto.setState(well.getState());dto.setStartupDate(well.getStartupDate());
  dto.setLifespanInMonths(well.getLifespanInMonths());dto.setMaintenanceIntervalInDays(well.getMaintenanceIntervalInDays());
  dto.setWastePercentage(well.getWastePercentage());dto.setGasRichness(well.getGasRichness());dto.setMaxCollectionCapacity(250f);
  dto.setUpkeepCosts(2f);dto.setOperatingCosts(3f);dto.setDTMCost(4f);
  var edited=versions.editNodeInVersion(versionId,well.getId(),dto);em.flush();
  assertNotEquals(well.getId(),edited.getId());assertEquals(100,well.getMaxCollectionCapacity());
  assertEquals(edited.getId(),version.getConnectionSnapshot().getFirst().getFromNodeId());
  assertEquals(well.getId(),connection.getFromNodeId());
  var config=scenarios.get(projectId,versionId).configuration();
  assertTrue(config.conversions().stream().anyMatch(c->c.nodeId().equals(edited.getId())));
  assertFalse(config.conversions().stream().anyMatch(c->c.nodeId().equals(well.getId())));
 }

 @Test
 void malformedEditorIsRejectedWithoutPersistingDraft() {
  var state=scenarios.get(projectId,versionId);var input=request(state);
  assertThrows(IllegalArgumentException.class,()->scenarios.save(projectId,versionId,new SaveRequest(input.token(),input.sourceToken(),mapper.createObjectNode(),input.configuration(),input.operations())));
  for(String mode:List.of("simple","advanced")) {
   var editor=mapper.createObjectNode().put("mode",mode);editor.set("value",mapper.createObjectNode());
   assertThrows(IllegalArgumentException.class,()->scenarios.save(projectId,versionId,new SaveRequest(input.token(),input.sourceToken(),editor,input.configuration(),input.operations())));
  }
  assertNull(scenarios.get(projectId,versionId).draft());
 }

 @Test
 void rejectsForeignVersionAndUnauthorizedUser() {
  assertThrows(org.enerscope.common.EntityNotFoundException.class,()->scenarios.get(projectId,UUID.randomUUID()));
  SecurityContextHolder.clearContext();
  assertThrows(org.enerscope.common.UnauthorizedException.class,()->scenarios.get(projectId,versionId));
 }

 @Test void mapDraftBlocksSimulationAndPublishedRevisionRemainsImmutable() {
  var current=revisions.current(em.find(Version.class,versionId));String original=current.getSnapshotJson();
  var basics=new org.enerscope.node.dto.NodeBasicsDTO();basics.setName("Pending name");
  versions.updateNodeBasics(versionId,well.getId(),basics);
  assertThrows(IllegalArgumentException.class,()->jobs.submit(projectId,versionId,new EconomicJobRequest(1,"1")));
  assertEquals(original,current.getSnapshotJson());
  var state=scenarios.get(projectId,versionId);scenarios.save(projectId,versionId,request(state));
  assertEquals(current.getNumber()+1,revisions.current(em.find(Version.class,versionId)).getNumber());
  assertEquals(original,current.getSnapshotJson());
 }
 @Test void restoringOldRevisionCreatesDraftWithoutMovingCurrentPointer() {
  var old=revisions.current(em.find(Version.class,versionId));
  var basics=new org.enerscope.node.dto.NodeBasicsDTO();basics.setName("Published name");
  versions.updateNodeBasics(versionId,well.getId(),basics);
  var state=scenarios.get(projectId,versionId);scenarios.save(projectId,versionId,request(state));
  var status=revisions.status(projectId,versionId);
  var restored=revisions.restore(projectId,versionId,old.getId(),status.token(),false);
  assertEquals(status.revisionId(),restored.revisionId());assertTrue(restored.workspaceChanges());
  assertThrows(IllegalArgumentException.class,()->jobs.submit(projectId,versionId,new EconomicJobRequest(1,"1")));
 }
 @Test void branchUsesPublishedRevisionInsteadOfPendingMapChanges() {
  var before=scenarios.get(projectId,versionId).nodes().stream().filter(n->n.identity().equals(well.getIdentityId())).findFirst().orElseThrow().name();
  var basics=new org.enerscope.node.dto.NodeBasicsDTO();basics.setName("Do not copy draft");
  versions.updateNodeBasics(versionId,well.getId(),basics);
  var branch=projects.saveVersion(projectId,new org.enerscope.version.dto.VersionDTO("Published branch",versionId));
  assertEquals(before,scenarios.get(projectId,branch.getId()).nodes().stream().filter(n->n.identity().equals(well.getIdentityId())).findFirst().orElseThrow().name());
  assertNotNull(revisions.ensure(branch).getBranchRevisionId());
 }
 @Test void operationalMergePreservesTargetEconomicsUntilPublication() {
  var branch=projects.saveVersion(projectId,new org.enerscope.version.dto.VersionDTO("Merge branch",versionId));
  var state=scenarios.get(projectId,branch.getId());var input=request(state);
  input.operations().get(state.nodes().stream().filter(n->n.identity().equals(well.getIdentityId())).findFirst().orElseThrow().id()).put("maxCollectionCapacity",mapper.valueToTree("250"));
  ((com.fasterxml.jackson.databind.node.ObjectNode)input.configuration()).put("wacc",0.5);
  scenarios.save(projectId,branch.getId(),input);
  var current=revisions.current(em.find(Version.class,versionId));
  var prepared=merges.prepare(projectId,branch.getId());assertTrue(prepared.conflicts().isEmpty());
  assertEquals(current.getId(),revisions.current(em.find(Version.class,versionId)).getId());
  var target=scenarios.get(projectId,versionId);
  assertEquals(250,target.nodes().stream().filter(n->n.identity().equals(well.getIdentityId())).findFirst().orElseThrow().values().get("maxCollectionCapacity").asInt());
  assertEquals(0,new java.math.BigDecimal("0.1").compareTo(target.configuration().wacc()));
  scenarios.save(projectId,versionId,request(target));
  assertNotNull(revisions.current(em.find(Version.class,versionId)).getMergeSourceRevisionId());
 }
 @Test void newSimulationReferencesRevisionWithoutDuplicatingPhysicalInputs() throws Exception {
  var job=jobs.submit(projectId,versionId,new EconomicJobRequest(1,"42"));
  var persisted=jobRepository.findById(job.id()).orElseThrow();
  assertNotNull(job.revision());assertFalse(job.revision().outdated());
  assertEquals(revisions.current(em.find(Version.class,versionId)).getId(),persisted.getScenarioRevisionId());
  var input=mapper.readTree(persisted.getInputJson());
  assertFalse(input.has("configuration"));assertFalse(input.has("nodes"));assertEquals("42",input.path("seed").asText());
  setCapacity(versionId,270);
  var historical=jobs.get(projectId,versionId,job.id());
  assertTrue(historical.revision().outdated());assertEquals(job.revision().id(),historical.revision().id());
 }
 @Test void editorSavesSharedOperationDraftWithoutPublishingAndMapReadsIt() {
  var current=revisions.current(em.find(Version.class,versionId));
  var user=new User("editor-"+UUID.randomUUID()+"@example.com","Scenario","Editor","test-hash",PlatformRole.USER);
  em.persist(user);
  var project=em.find(Project.class,projectId);
  var member=new org.enerscope.project.model.ProjectMember(user,project);
  var role=org.enerscope.project.model.enums.ProjectMemberType.EDITOR;
  member.changeRole(role,org.enerscope.project.model.ProjectMember.defaultPermissionsFor(role));
  project.addMember(member);em.persist(member);em.flush();
  var authentication=new UsernamePasswordAuthenticationToken(user.getId(),null,List.of());
  authentication.setDetails(new Session("editor",user,Instant.now().plusSeconds(3600)));
  SecurityContextHolder.getContext().setAuthentication(authentication);
  var state=scenarios.get(projectId,versionId);var input=request(state);
  input.operations().get(well.getId()).put("maxCollectionCapacity",mapper.valueToTree("234"));
  var saved=scenarios.save(projectId,versionId,input);
  assertTrue(saved.errors().isEmpty());
  assertTrue(saved.revision().workspaceChanges());assertFalse(saved.revision().canPublish());
  assertEquals(current.getId(),revisions.current(em.find(Version.class,versionId)).getId());
  var node=saved.nodes().stream().filter(n->n.identity().equals(well.getIdentityId())).findFirst().orElseThrow();
  assertEquals(234.0,versions.getNodeDetail(versionId,node.id()).getAttributes().get("maxCollectionCapacity"));
  assertFalse(scenarios.readiness(projectId).getFirst().ready());
  assertThrows(org.enerscope.common.ForbiddenException.class,()->scenarios.discard(projectId,versionId,saved.token()));
 }
 @Test void historicalImportIsIdempotentAndPreservesSnapshotAndCurrentRevision() throws Exception {
  var version=em.find(Version.class,versionId);
  var current=revisions.current(version);
  var input=new EconomicInputCodec(mapper).freeze(version,economics.get(projectId,versionId),2,42);
  String original=mapper.writeValueAsString(input);
  var job=new org.enerscope.economic.model.Simulation(version,original,2,"42");
  em.persist(job);em.flush();UUID jobId=job.getId();
  int count=revisions.list(projectId,versionId).size();
  importer.initialize();em.clear();
  var imported=jobRepository.findById(jobId).orElseThrow();
  assertNotNull(imported.getScenarioRevisionId());assertEquals(original,imported.getInputJson());
  assertEquals(current.getId(),revisions.current(em.find(Version.class,versionId)).getId());
  assertEquals(count,revisions.list(projectId,versionId).size());
  importer.initialize();em.clear();
  assertEquals(count,revisions.list(projectId,versionId).size());
  assertEquals(original,jobRepository.findById(jobId).orElseThrow().getInputJson());
 }
 @Test void incompleteHistoricalInputRemainsLegacy() {
  var job=new org.enerscope.economic.model.Simulation(em.find(Version.class,versionId),"{}",1,"42");
  em.persist(job);em.flush();UUID jobId=job.getId();
  importer.initialize();em.clear();
  var legacy=jobRepository.findById(jobId).orElseThrow();
  assertNull(legacy.getScenarioRevisionId());assertEquals("{}",legacy.getInputJson());
 }
 @Test void successiveMergesUseLastPublishedSourceAndDiscardDoesNotIntegrate() {
  var branch=projects.saveVersion(projectId,new org.enerscope.version.dto.VersionDTO("Successive branch",versionId));
  setCapacity(branch.getId(),250);
  var firstSource=revisions.current(em.find(Version.class,branch.getId())).getId();
  merges.prepare(projectId,branch.getId());
  var state=scenarios.get(projectId,versionId);scenarios.save(projectId,versionId,request(state));
  setCapacity(branch.getId(),260);
  var second=merges.prepare(projectId,branch.getId());
  assertEquals(firstSource,second.baseRevisionId());assertTrue(second.conflicts().isEmpty());
  state=scenarios.get(projectId,versionId);scenarios.discard(projectId,versionId,state.token());
  var repeated=merges.prepare(projectId,branch.getId());
  assertEquals(firstSource,repeated.baseRevisionId());assertTrue(repeated.conflicts().isEmpty());
  state=scenarios.get(projectId,versionId);scenarios.save(projectId,versionId,request(state));
  var published=revisions.current(em.find(Version.class,versionId));
  assertEquals(repeated.sourceRevisionId(),published.getMergeSourceRevisionId());
  assertEquals(2,merges.history(projectId,versionId).stream().filter(m->m.state().equals("PUBLISHED")).count());
  assertEquals(1,merges.history(projectId,versionId).stream().filter(m->m.state().equals("CANCELLED")).count());
 }
 private void setCapacity(UUID scenario,int capacity) {
  var state=scenarios.get(projectId,scenario);var input=request(state);
  var node=state.nodes().stream().filter(n->n.identity().equals(well.getIdentityId())).findFirst().orElseThrow();
  input.operations().get(node.id()).put("maxCollectionCapacity",mapper.valueToTree(Integer.toString(capacity)));
  assertTrue(scenarios.save(projectId,scenario,input).errors().isEmpty());
 }
 @Test void movingLegacySharedNodePreservesOtherScenarioAndPublishedMap() {
  var original=em.find(Version.class,versionId);
  var other=new Version("Legacy sibling",original,new ArrayList<>(original.getNodeSnapshot()),new ArrayList<>(),new ArrayList<>(),new ArrayList<>());
  em.persist(other);em.find(Project.class,projectId).addVersion(other);em.flush();
  var before=revisions.current(original).getSnapshotJson();
  revisions.ensure(other);
  var position=new org.enerscope.node.dto.NodeGraphDataDTO();
  position.setGraphPosition(new org.enerscope.node.dto.GraphPositionDTO(12d,34d));
  var moved=versions.updateNodePosition(versionId,well.getId(),position);em.flush();
  assertNotEquals(well.getId(),moved.getId());assertEquals(well.getIdentityId(),moved.getIdentityId());
  assertEquals(0d,well.getGraphData().getGraphPosition().getX());
  assertTrue(other.getNodeSnapshot().contains(well));
  assertEquals(before,revisions.current(original).getSnapshotJson());
  assertEquals(12d,moved.getGraphData().getGraphPosition().getX());
  assertTrue(economics.get(projectId,versionId).conversions().stream().anyMatch(c->c.nodeId().equals(moved.getId())));
 }
 @Test void independentCopyUsesPublishedRevisionAndRetainsOriginWithoutResults() {
  var parent=em.find(Version.class,versionId);var published=revisions.current(parent);
  var status=revisions.status(projectId,versionId);
  var copy=forks.fork(projectId,versionId,"Independent copy",status.token(),true);em.flush();
  assertNull(copy.getParentVersion());
  var scenario=revisions.ensure(copy);
  assertEquals(versionId,scenario.getBaseScenarioId());assertEquals(published.getId(),scenario.getBranchRevisionId());
  assertFalse(revisions.hasPendingPublication(copy));assertTrue(jobs.list(projectId,copy.getId()).isEmpty());
  assertTrue(copy.getFinalResults().isEmpty());assertNotNull(economics.get(projectId,copy.getId()));
  assertEquals(published.getId(),revisions.current(parent).getId());
 }
 @Test void staleForkTokenRejectsCreationAfterWorkspaceChange() {
  var status=revisions.status(projectId,versionId);
  well.setMaxCollectionCapacity(333);em.flush();
  assertThrows(ScenarioConflictException.class,()->forks.fork(projectId,versionId,"Stale copy",status.token(),true));
 }
 @Test void archivingScenarioTreePreservesRevisionsAndSimulationsButBlocksNewJobs() {
  var parent=em.find(Version.class,versionId);var before=revisions.current(parent);
  var branch=projects.saveVersion(projectId,new org.enerscope.version.dto.VersionDTO("Archive branch",versionId));
  var job=jobs.submit(projectId,versionId,new EconomicJobRequest(1,"42"));
  versions.deleteVersion(versionId);em.flush();
  assertFalse(parent.isActive());assertFalse(branch.isActive());
  assertNotNull(revisions.get(projectId,versionId,before.getId()).content());
  assertEquals(before.getSnapshotJson(),revisions.current(parent).getSnapshotJson());
  assertEquals(job.id(),jobs.get(projectId,versionId,job.id()).id());
  assertThrows(org.enerscope.common.EntityNotFoundException.class,()->jobs.submit(projectId,versionId,new EconomicJobRequest(1,"42")));
 }
}
