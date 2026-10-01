package org.enerscope.economic.service;

import java.time.Instant;
import java.util.*;
import org.enerscope.common.*;
import org.enerscope.economic.model.*;
import org.enerscope.economic.repository.*;
import org.enerscope.logging.AppLogger;
import org.enerscope.organization.model.Organization;
import org.enerscope.project.model.*;
import org.enerscope.project.model.enums.*;
import org.enerscope.project.repository.ProjectRepository;
import org.enerscope.session.model.Session;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.enerscope.economic.service.EconomicExample.*;

class EconomicServiceTest {
    final ProjectRepository projects=mock(ProjectRepository.class);
    final EconomicConfigurationRepository configs=mock(EconomicConfigurationRepository.class);
    final EconomicEvaluationRepository evaluations=mock(EconomicEvaluationRepository.class);
    final EconomicDraftRepository drafts=mock(EconomicDraftRepository.class);
    final EconomicSimulationAdapter simulator=spy(new EconomicSimulationAdapter());
    final EconomicService service=new EconomicService(projects,configs,evaluations,drafts,new EconomicValidator(),new EconomicEngine(new EconomicValidator(), new EconomicIndicatorsCalculator()),
            new EconomicIndicatorsCalculator(), simulator,MAPPER,mock(AppLogger.class));
    final UUID projectId=UUID.randomUUID(); final varHolder holder=new varHolder();
    static class varHolder { final org.enerscope.version.model.Version version=version(); }
    Project project; User user;

    @BeforeEach
    void setup() {
        user=User.fromJwtClaims(UUID.randomUUID(),"reader@example.com","Reader","User");
        project=new Project("Example","Example",new Organization("Example"));project.addVersion(holder.version);
        when(projects.findById(projectId)).thenReturn(Optional.of(project));
        authenticate(user);
    }

    private void authenticate(User caller) {
        var token=new UsernamePasswordAuthenticationToken(caller.getId(),null,List.of());
        token.setDetails(new Session("test",caller,Instant.now().plusSeconds(3600)));
        SecurityContextHolder.getContext().setAuthentication(token);
    }

    private void grant(ProjectMemberPermission... permissions) {
        ProjectMember member=new ProjectMember(user,project);
        member.addRole(new ProjectMemberRole("Custom",ProjectMemberType.EDITOR,Set.of(permissions)));project.addMember(member);
    }

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void outsiderCannotReadOrWriteConfiguration() {
        assertThrows(ForbiddenException.class,()->service.get(projectId,holder.version.getId()));
        assertThrows(ForbiddenException.class,()->service.save(projectId,holder.version.getId(),configuration()));
        verifyNoInteractions(configs);
    }

    @Test
    void viewerCanReadButCannotEvaluate() throws Exception {
        grant(ProjectMemberPermission.VIEW_PROJECT);
        when(configs.findByVersionId(holder.version.getId())).thenReturn(Optional.of(new EconomicConfigurationEntity(holder.version,MAPPER.writeValueAsString(configuration()))));
        assertEquals(configuration(),service.get(projectId,holder.version.getId()));
        assertThrows(ForbiddenException.class,()->service.evaluate(projectId,holder.version.getId()));
    }

    @Test
    void editorCanSaveValidatedConfiguration() {
        grant(ProjectMemberPermission.EDIT_PROJECT);
        assertEquals(configuration(),service.save(projectId,holder.version.getId(),configuration()));
        verify(configs).save(any(EconomicConfigurationEntity.class));
    }

    @Test
    void adminStillCannotAddressVersionInAnotherProject() {
        user.updatePlatformRole(PlatformRole.ADMIN);
        assertThrows(EntityNotFoundException.class,()->service.get(projectId,UUID.randomUUID()));
        verifyNoInteractions(configs);
    }

    @Test
    void foreignNodeIsRejectedBeforePersistence() {
        grant(ProjectMemberPermission.EDIT_PROJECT);
        var c=mutate(t->((com.fasterxml.jackson.databind.node.ObjectNode)t.withArray("nodeProfiles").get(0)).put("nodeId",UUID.randomUUID().toString()));
        assertThrows(IllegalArgumentException.class,()->service.save(projectId,holder.version.getId(),c));
        verify(configs,never()).save(any());
    }

    @Test
    void evaluationIsScopedToVersion() {
        grant(ProjectMemberPermission.VIEW_PROJECT);UUID evaluation=UUID.randomUUID();
        assertThrows(EntityNotFoundException.class,()->service.getEvaluation(projectId,holder.version.getId(),evaluation));
        verify(evaluations).findByIdAndVersionId(evaluation,holder.version.getId());
    }

    @Test void newEvaluationStoresVersionTwoAndSimulatesOnce() throws Exception {
        grant(ProjectMemberPermission.EDIT_PROJECT);
        when(configs.findByVersionId(holder.version.getId())).thenReturn(Optional.of(
                new EconomicConfigurationEntity(holder.version, MAPPER.writeValueAsString(configuration()))));
        when(evaluations.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        var evaluation = service.evaluate(projectId, holder.version.getId());
        assertEquals(2, evaluation.snapshot().schemaVersion());
        assertEquals(new java.math.BigDecimal("0.1483447840"), evaluation.snapshot().result().indicators().irr().rate());
        verify(simulator, times(1)).simulate(holder.version, configuration());
        var captor = org.mockito.ArgumentCaptor.forClass(EconomicEvaluation.class);
        verify(evaluations).saveAndFlush(captor.capture());
        var stored = MAPPER.readTree(captor.getValue().getSnapshotJson());
        assertEquals("STORED", stored.at("/result/indicators/origin").asText());
        assertEquals(2, stored.path("schemaVersion").asInt());
    }

    private EconomicEvaluation historical(java.util.function.Consumer<com.fasterxml.jackson.databind.node.ObjectNode> change) {
        var result = new EconomicEngine(new EconomicValidator(), new EconomicIndicatorsCalculator()).calculate(configuration(), metrics());
        var snapshot = new org.enerscope.economic.dto.EvaluationDTO.Snapshot(1, configuration(), null, null, List.of(), result);
        com.fasterxml.jackson.databind.node.ObjectNode tree = MAPPER.valueToTree(snapshot);
        ((com.fasterxml.jackson.databind.node.ObjectNode) tree.get("result")).remove("indicators");
        change.accept(tree);
        var entity = new EconomicEvaluation(holder.version, tree.toString());
        when(evaluations.findByIdAndVersionId(entity.getId(), holder.version.getId())).thenReturn(Optional.of(entity));
        when(evaluations.findByVersionIdOrderByCreatedAtDesc(holder.version.getId())).thenReturn(List.of(entity));
        return entity;
    }

    @Test void historyDerivesFromSnapshotWithoutSimulationConfigurationLookupOrWrites() {
        grant(ProjectMemberPermission.VIEW_PROJECT);
        var entity = historical(tree -> {});
        String original = entity.getSnapshotJson();
        var single = service.getEvaluation(projectId, holder.version.getId(), entity.getId());
        var listed = service.list(projectId, holder.version.getId()).getFirst();
        assertEquals(single.snapshot(), listed.snapshot());
        assertEquals(1, single.snapshot().schemaVersion());
        var indicators = single.snapshot().result().indicators();
        assertEquals(org.enerscope.economic.model.enums.IndicatorOrigin.DERIVED_FROM_SNAPSHOT, indicators.origin());
        assertEquals(new java.math.BigDecimal("0.1483447840"), indicators.irr().rate());
        assertEquals(5, indicators.discountedPayback().period());
        assertEquals(original, entity.getSnapshotJson());
        verifyNoInteractions(configs, simulator);
        verify(evaluations).findByIdAndVersionId(entity.getId(), holder.version.getId());
        verify(evaluations).findByVersionIdOrderByCreatedAtDesc(holder.version.getId());
        verifyNoMoreInteractions(evaluations);
    }

    @Test void truncatedHistoryReturnsInsufficientDataWithoutFillingMissingYears() {
        grant(ProjectMemberPermission.VIEW_PROJECT);
        var entity = historical(tree -> ((com.fasterxml.jackson.databind.node.ArrayNode)tree.at("/result/periods")).remove(5));
        var result = service.getEvaluation(projectId, holder.version.getId(), entity.getId()).snapshot().result();
        assertEquals(org.enerscope.economic.model.enums.IrrStatus.INSUFFICIENT_DATA, result.indicators().irr().status());
        assertEquals(5, result.periods().size());
        assertEquals(0, new java.math.BigDecimal("169.20").compareTo(result.npv()));
        verifyNoInteractions(configs, simulator);
    }

    @Test void missingHistoricalResultOrConfigurationReturnsInsufficientData() {
        grant(ProjectMemberPermission.VIEW_PROJECT);
        for (String field : List.of("result", "configuration")) {
            var entity = historical(tree -> tree.remove(field));
            var result = service.getEvaluation(projectId, holder.version.getId(), entity.getId()).snapshot().result();
            assertEquals(org.enerscope.economic.model.enums.IrrStatus.INSUFFICIENT_DATA, result.indicators().irr().status());
        }
        verifyNoInteractions(configs, simulator);
    }

    @Test void storedIndicatorsAreReturnedWithoutRecalculation() {
        grant(ProjectMemberPermission.VIEW_PROJECT);
        var entity = historical(tree -> {
            var original = new EconomicIndicatorsCalculator().calculate(EconomicIndicatorsCalculatorTest.periods("-100", "200"), java.math.BigDecimal.ZERO);
            ((com.fasterxml.jackson.databind.node.ObjectNode)tree.get("result")).set("indicators", MAPPER.valueToTree(original));
        });
        var result = service.getEvaluation(projectId, holder.version.getId(), entity.getId()).snapshot().result();
        assertEquals(0, new java.math.BigDecimal("1.0000000000").compareTo(result.indicators().irr().rate()));
        assertEquals(org.enerscope.economic.model.enums.IndicatorOrigin.STORED, result.indicators().origin());
        verifyNoInteractions(configs, simulator);
    }

    @Test void absentHistoricalPeriodFieldsDoNotBecomeDefaultZeroes() {
        grant(ProjectMemberPermission.VIEW_PROJECT);
        for (String field : List.of("period", "year", "cashFlow")) {
            var entity = historical(tree -> ((com.fasterxml.jackson.databind.node.ObjectNode)tree.at("/result/periods/0")).remove(field));
            var result = service.getEvaluation(projectId, holder.version.getId(), entity.getId()).snapshot().result();
            assertEquals(org.enerscope.economic.model.enums.IrrStatus.INSUFFICIENT_DATA, result.indicators().irr().status());
        }
    }

    @Test void missingHistoricalWaccDoesNotUseCurrentConfiguration() {
        grant(ProjectMemberPermission.VIEW_PROJECT);
        var entity = historical(tree -> ((com.fasterxml.jackson.databind.node.ObjectNode)tree.path("configuration")).remove("wacc"));
        var indicators = service.getEvaluation(projectId, holder.version.getId(), entity.getId()).snapshot().result().indicators();
        assertEquals(org.enerscope.economic.model.enums.IrrStatus.CALCULATED, indicators.irr().status());
        assertEquals(org.enerscope.economic.model.enums.PaybackStatus.INSUFFICIENT_DATA, indicators.discountedPayback().status());
        assertEquals(4, indicators.simplePayback().period());
        verifyNoInteractions(configs, simulator);
    }

    @Test void absentHistoricalPeriodsAreInsufficientInListAndDetail() {
        grant(ProjectMemberPermission.VIEW_PROJECT);
        var entity = historical(tree -> ((com.fasterxml.jackson.databind.node.ObjectNode)tree.path("result")).remove("periods"));
        var single = service.getEvaluation(projectId, holder.version.getId(), entity.getId());
        assertEquals(org.enerscope.economic.model.enums.IrrStatus.INSUFFICIENT_DATA, single.snapshot().result().indicators().irr().status());
        assertEquals(single.snapshot(), service.list(projectId, holder.version.getId()).getFirst().snapshot());
        verifyNoInteractions(configs, simulator);
    }

    //TODO: revisar tests
    private org.enerscope.economic.model.configuration.EconomicConfiguration exportConfiguration() throws Exception {
        return MAPPER.readValue(MAPPER.writeValueAsString(configuration()).replace("OUTPUT_VOLUME", "EXPORTED_VOLUME"),
                org.enerscope.economic.model.configuration.EconomicConfiguration.class);
    }

    @Test void exportOnWellIsRejectedBeforeSaving() throws Exception {
        grant(ProjectMemberPermission.EDIT_PROJECT);
        var configuration = exportConfiguration();
        var error = assertThrows(IllegalArgumentException.class, () -> service.save(projectId, holder.version.getId(), configuration));
        assertEquals("El volumen exportado requiere un buque del escenario.", error.getMessage());
        verifyNoInteractions(configs, simulator, evaluations);
    }

    @Test void savedInvalidExportCannotRunSimulation() throws Exception {
        grant(ProjectMemberPermission.EDIT_PROJECT);
        when(configs.findByVersionId(holder.version.getId())).thenReturn(Optional.of(
                new EconomicConfigurationEntity(holder.version, MAPPER.writeValueAsString(exportConfiguration()))));
        assertThrows(IllegalArgumentException.class, () -> service.evaluate(projectId, holder.version.getId()));
        verifyNoInteractions(simulator, evaluations);
    }

    @Test void exportOnCarrierCanBeSaved() throws Exception {
        grant(ProjectMemberPermission.EDIT_PROJECT);
        var originalNodes = List.copyOf(holder.version.getNodeSnapshot());
        ReflectionTestUtils.setField(holder.version, "nodeSnapshot", new ArrayList<org.enerscope.node.model.BaseNode>());
        for (var node : originalNodes) {
            var carrier = new org.enerscope.node.model.export.LNGCarrier();
            ReflectionTestUtils.setField(carrier, "id", node.getId());
            holder.version.getNodeSnapshot().add(carrier);
        }
        var configuration = exportConfiguration();
        assertEquals(configuration, service.save(projectId, holder.version.getId(), configuration));
        verify(configs).save(any(EconomicConfigurationEntity.class));
    }

    private org.enerscope.economic.dto.EconomicDraftDTO incompleteDraft() {
        var json = MAPPER.createObjectNode();
        for (String field : List.of("startYear", "years", "currency", "wacc", "entityId", "entityName", "taxRate")) json.put(field, "");
        json.putArray("assets"); json.putArray("rules");
        return new org.enerscope.economic.dto.EconomicDraftDTO(1, json);
    }

    @Test void editorSavesIncompleteDraftWithoutChangingConfigurationOrSimulating() {
        grant(ProjectMemberPermission.EDIT_PROJECT);
        var draft = incompleteDraft();
        assertEquals(draft, service.saveDraft(projectId, holder.version.getId(), draft));
        var captor = org.mockito.ArgumentCaptor.forClass(EconomicDraftEntity.class);
        verify(drafts).save(captor.capture());
        assertTrue(captor.getValue().getDraftJson().contains("schemaVersion"));
        verifyNoInteractions(configs, simulator, evaluations);
    }

    @Test void viewerReadsDraftButCannotSaveIt() throws Exception {
        grant(ProjectMemberPermission.VIEW_PROJECT);
        var draft = incompleteDraft();
        when(drafts.findByVersionId(holder.version.getId())).thenReturn(Optional.of(new EconomicDraftEntity(holder.version, MAPPER.writeValueAsString(draft))));
        assertEquals(draft, service.getDraft(projectId, holder.version.getId()));
        assertThrows(ForbiddenException.class, () -> service.saveDraft(projectId, holder.version.getId(), draft));
        verify(drafts, never()).save(any());
    }

    @Test void draftCannotLeakAcrossProjects() {
        user.updatePlatformRole(PlatformRole.ADMIN);
        assertThrows(EntityNotFoundException.class, () -> service.getDraft(projectId, UUID.randomUUID()));
        verifyNoInteractions(drafts);
    }

    @Test void invalidDraftStructureIsRejectedBeforePersistence() {
        grant(ProjectMemberPermission.EDIT_PROJECT);
        assertThrows(IllegalArgumentException.class, () -> service.saveDraft(projectId, holder.version.getId(),
                new org.enerscope.economic.dto.EconomicDraftDTO(1, MAPPER.createObjectNode())));
        verifyNoInteractions(drafts);
    }

    @Test void validatedConfigurationSaveClearsDraft() {
        grant(ProjectMemberPermission.EDIT_PROJECT);
        service.save(projectId, holder.version.getId(), configuration());
        verify(drafts).deleteByVersionId(holder.version.getId());
    }
}
