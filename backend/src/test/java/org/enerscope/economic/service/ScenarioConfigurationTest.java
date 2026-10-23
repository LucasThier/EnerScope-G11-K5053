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
 UUID projectId,versionId;Well well;
 @BeforeEach void setup() throws Exception {
  var user=User.fromJwtClaims(UUID.randomUUID(),"scenario@example.com","Scenario","Admin",PlatformRole.ADMIN);
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
 }
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 SaveRequest request(ScenarioConfigurationDTO state) {
  var edits=new HashMap<UUID,Map<String,JsonNode>>();state.nodes().forEach(n->edits.put(n.id(),new TreeMap<>(n.values())));
  return new SaveRequest(state.token(),state.sourceToken(),mapper.valueToTree(Map.of("mode","advanced","value",state.configuration())),mapper.valueToTree(state.configuration()),edits);
 }
 @Test void publishesPhysicsAndEconomicsTogetherAndRemapsReferencesWithoutMutatingParent() {
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
 @Test void incompleteSavePreservesPublishedValuesAndBlocksJobUntilDiscarded() {
  var state=scenarios.get(projectId,versionId);var input=request(state);
  input.operations().get(well.getId()).put("maxCollectionCapacity",mapper.valueToTree(""));
  var saved=scenarios.save(projectId,versionId,input);
  assertNotNull(saved.draft());assertFalse(saved.errors().isEmpty());assertEquals(100,well.getMaxCollectionCapacity());
  assertThrows(IllegalArgumentException.class,()->jobs.submit(projectId,versionId,new EconomicJobRequest(1,"42")));
 }
 @Test void draftSurvivesReloadAndDiscardEnablesSimulation() {
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
 @Test void persistedDraftKeepsOriginalSourceAfterMapEdit() {
  var before=scenarios.get(projectId,versionId);var valid=request(before);
  scenarios.save(projectId,versionId,new SaveRequest(valid.token(),valid.sourceToken(),valid.editor(),null,valid.operations()));
  well.setMaxCollectionCapacity(321);em.flush();
  var after=scenarios.get(projectId,versionId);
  assertNotEquals(after.sourceToken(),after.draft().sourceToken());
  assertThrows(ScenarioConflictException.class,()->scenarios.save(projectId,versionId,new SaveRequest(after.token(),after.draft().sourceToken(),valid.editor(),valid.configuration(),valid.operations())));
 }
 @Test void fullMapEditRemapsEconomicAndConnectionReferences() {
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
 @Test void malformedEditorIsRejectedWithoutPersistingDraft() {
  var state=scenarios.get(projectId,versionId);var input=request(state);
  assertThrows(IllegalArgumentException.class,()->scenarios.save(projectId,versionId,new SaveRequest(input.token(),input.sourceToken(),mapper.createObjectNode(),input.configuration(),input.operations())));
  for(String mode:List.of("simple","advanced")) {
   var editor=mapper.createObjectNode().put("mode",mode);editor.set("value",mapper.createObjectNode());
   assertThrows(IllegalArgumentException.class,()->scenarios.save(projectId,versionId,new SaveRequest(input.token(),input.sourceToken(),editor,input.configuration(),input.operations())));
  }
  assertNull(scenarios.get(projectId,versionId).draft());
 }
 @Test void rejectsForeignVersionAndUnauthorizedUser() {
  assertThrows(org.enerscope.common.EntityNotFoundException.class,()->scenarios.get(projectId,UUID.randomUUID()));
  SecurityContextHolder.clearContext();
  assertThrows(org.enerscope.common.UnauthorizedException.class,()->scenarios.get(projectId,versionId));
 }
}
