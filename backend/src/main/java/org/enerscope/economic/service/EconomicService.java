package org.enerscope.economic.service;
import org.enerscope.scenario.service.CurrentScenarioNodes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.enerscope.common.*;
import org.enerscope.economic.dto.EvaluationDTO;
import org.enerscope.economic.dto.EvaluationDTO.Snapshot;
import org.enerscope.economic.model.*;
import org.enerscope.economic.model.configuration.EconomicConfiguration;
import org.enerscope.economic.model.results.EconomicResult;
import org.enerscope.economic.repository.*;
import org.enerscope.logging.AppLogger;
import org.enerscope.project.model.enums.ProjectMemberPermission;
import org.enerscope.project.repository.ProjectRepository;
import org.enerscope.project.service.ProjectAccessGuard;
import org.enerscope.scenario.dto.EconomicDraftDTO;
import org.enerscope.scenario.model.EconomicDraftEntity;
import org.enerscope.scenario.repository.EconomicDraftRepository;
import org.enerscope.scenario.repository.ScenarioDraftRepository;
import org.enerscope.scenario.model.Scenario;
import org.enerscope.scenario.service.ScenarioChangeScope;
import org.enerscope.scenario.service.ScenarioDefaults;
import org.enerscope.user.model.enums.PlatformRole;
import org.enerscope.util.AuthUtil;
import org.enerscope.version.model.Version;
import org.enerscope.version.service.VersionAccessGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class EconomicService {
    private final ProjectRepository projects;
    private final EconomicConfigurationRepository configurations;
    private final EconomicEvaluationRepository evaluations;
    private final EconomicDraftRepository drafts;
    private final EconomicValidator validator;
    private final EconomicEngine engine;
    private final EconomicIndicatorsCalculator indicators;
    private final EconomicSimulationAdapter simulator;
    private final ObjectMapper mapper;
    private final AppLogger logger;
    private final VersionAccessGuard versionAccessGuard;
    private final ScenarioChangeScope changeScope;
    private final ScenarioDefaults scenarioDefaults;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;
    @org.springframework.beans.factory.annotation.Autowired
    private ScenarioDraftRepository scenarioDrafts;

    //TODO: revisar parametros de entrada
    public void lock(Version version) {
        if (entityManager != null && entityManager.getLockMode(version) != jakarta.persistence.LockModeType.PESSIMISTIC_WRITE) {
            entityManager.flush();
            entityManager.refresh(version, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        }
    }

    void assertNoDraft(Version version) {
        lock(version);
        var scenarioId = scenarioDefaults.existing(version.getId()).map(BaseEntity::getId);
        if (scenarioId.isPresent() && ((scenarioDrafts != null && scenarioDrafts.existsByScenarioId(scenarioId.get())) || drafts.findByScenarioId(scenarioId.get()).isPresent()))
            throw new IllegalArgumentException("Hay un borrador pendiente. Completá o descartá los cambios en Configuración del Escenario.");
    }
    /** Refuses to simulate a scenario that still has an unpublished draft. */
    public void assertNoDraft(Version version, Scenario scenario) {
        lock(version);
        if ((scenarioDrafts != null && scenarioDrafts.existsByScenarioId(scenario.getId())) || drafts.findByScenarioId(scenario.getId()).isPresent())
            throw new IllegalArgumentException("Hay un borrador pendiente. Completá o descartá los cambios en Configuración del Escenario.");
    }

    public EconomicConfiguration save(UUID projectId, UUID versionId, EconomicConfiguration configuration) {
        Version version = authorizeConfigurationEdit(projectId, versionId);
        lock(version);
        return saveIn(version, scenarioDefaults.forVersion(version), configuration);
    }

    /** Publishes {@code configuration} as the economic configuration of {@code scenario}; the caller has authorized and locked. */
    public EconomicConfiguration saveIn(Version version, Scenario scenario, EconomicConfiguration configuration) {
        var stored = configurations.findByScenarioId(scenario.getId());
        changeScope.assertCanSave(version, null,
                stored.map(e -> read(e.getConfigurationJson(), EconomicConfiguration.class)).orElse(null), configuration);
        validateConfiguration(configuration, version);
        var entity = stored.orElseGet(() -> new EconomicConfigurationEntity(version, ""));
        entity.replace(json(configuration)); entity.assignScenario(scenario); configurations.save(entity);
        drafts.deleteByScenarioId(scenario.getId());
        logger.info("Economic configuration saved for scenario {} of version {}", scenario.getId(), version.getId());
        return configuration;
    }

    public EconomicConfiguration saveImportedConfiguration(
            UUID versionId,
            EconomicConfiguration configuration) {
        UUID projectId = projects.findIdByVersionId(versionId)
                .orElseThrow(() -> new EntityNotFoundException("Project version not found"));
        return save(projectId, versionId, configuration);
    }

    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public void validateConfiguration(EconomicConfiguration configuration, Version version) {
        validator.validate(configuration, nodeIds(version));
        validateExportNodes(configuration, version);
        // Validate fiscal coverage/classification before accepting a configuration, with zero observed quantities.
        List<OperationalMetric> validationMetrics = new ArrayList<>();
        for (var m : configuration.conversions()) for (int t = 0; t <= configuration.years(); t++)
            validationMetrics.add(new OperationalMetric(m.nodeId(), configuration.startYear() + t, m.metric(), m.unit(), java.math.BigDecimal.ZERO));
        engine.calculate(configuration, validationMetrics);
    }

    public EconomicDraftDTO saveDraft(UUID projectId, UUID versionId,
                                      EconomicDraftDTO draft) {
        Version version = authorizeConfigurationEdit(projectId, versionId);
        lock(version);
        EconomicValidator.require(draft != null, "Draft is required");
        var scenario = scenarioDefaults.forVersion(version);
        changeScope.assertCanSaveDraft(version, drafts.findByScenarioId(scenario.getId())
                .map(e -> read(e.getDraftJson(), EconomicDraftDTO.class).draft()).orElse(null), draft.draft());
        draft.validateStructure();
        var entity = drafts.findByScenarioId(scenario.getId()).orElseGet(() -> new EconomicDraftEntity(version, ""));
        entity.replace(json(draft)); entity.assignScenario(scenario); drafts.save(entity);
        logger.info("Economic draft saved for version {}", versionId);
        return draft;
    }

    @Transactional(readOnly = true)
    public EconomicDraftDTO getDraft(UUID projectId, UUID versionId) {
        authorize(projectId, versionId, ProjectMemberPermission.VIEW_PROJECT);
        return scenarioDefaults.existing(versionId).map(this::draftOf).orElse(null);
    }

    /** The legacy economic draft of a scenario, or null. */
    @Transactional(readOnly = true)
    public EconomicDraftDTO draftOf(Scenario scenario) {
        return drafts.findByScenarioId(scenario.getId()).map(e -> read(e.getDraftJson(), EconomicDraftDTO.class)).orElse(null);
    }

    @Transactional(readOnly = true)
    public EconomicConfiguration get(UUID projectId, UUID versionId) {
        authorize(projectId, versionId, ProjectMemberPermission.VIEW_PROJECT);
        return configuration(versionId);
    }

    public EvaluationDTO evaluate(UUID projectId, UUID versionId) {
        Version version = authorizeSimulation(projectId, versionId);
        assertNoDraft(version);
        EconomicConfiguration c = configuration(versionId); validator.validate(c, nodeIds(version));
        validateExportNodes(c, version);
        var codec = new EconomicInputCodec(mapper);
        long seed = new java.security.SecureRandom().nextLong();
        var input = codec.freeze(version, c, 1, seed);
        var operational = simulator.simulate(codec.thaw(input), c, org.enerscope.simulator.Simulator.seedForRound(seed, 0));
        EconomicResult result = engine.calculate(c, operational.metrics());
        if (result.indicators().irr().status() == org.enerscope.economic.model.enums.IrrStatus.NUMERICAL_FAILURE
                || result.indicators().discountedPayback().status() == org.enerscope.economic.model.enums.PaybackStatus.NUMERICAL_FAILURE)
            logger.warn("Economic indicator numerical failure for version {}; evaluation remains available", versionId);
        // Node entities have no back-reference to versions; Jackson captures their concrete physical parameters.
        Snapshot snapshot = new Snapshot(2, c, codec.displayNodes(input),
                input.connections(), operational.rawMetrics(), result, input.simulation(), null);
        var evaluation = new EconomicEvaluation(version, json(snapshot));
        evaluation.assignScenario(scenarioDefaults.forVersion(version));
        EconomicEvaluation stored = evaluations.saveAndFlush(evaluation);
        logger.info("Economic evaluation {} completed for version {}", stored.getId(), versionId);
        return new EvaluationDTO(stored.getId(), stored.getCreatedAt(), snapshot);
    }

    @Transactional(readOnly = true)
    public List<EvaluationDTO> list(UUID projectId, UUID versionId) {
        authorize(projectId, versionId, ProjectMemberPermission.VIEW_PROJECT);
        return scenarioDefaults.existing(versionId).stream().flatMap(s -> evaluationsOf(s).stream()).toList();
    }

    /** The evaluations of a scenario, newest first. */
    @Transactional(readOnly = true)
    public List<EvaluationDTO> evaluationsOf(Scenario scenario) {
        return evaluations.findByScenarioIdOrderByCreatedAtDesc(scenario.getId()).stream().map(this::dto).toList();
    }

    @Transactional(readOnly = true)
    public EvaluationDTO getEvaluation(UUID projectId, UUID versionId, UUID evaluationId) {
        authorize(projectId, versionId, ProjectMemberPermission.VIEW_PROJECT);
        return dto(scenarioDefaults.existing(versionId).flatMap(s -> evaluations.findByIdAndScenarioId(evaluationId, s.getId()))
                .orElseThrow(() -> new EntityNotFoundException("Economic evaluation not found")));
    }

    /**
     * A saved evaluation by its own id, with no requirement on its scenario or version being active: saved comparisons read
     * the simulations they were made with even after the scenario or the version was deleted. The caller authorizes.
     */
    @Transactional(readOnly = true)
    public EvaluationDTO evaluationById(UUID evaluationId) {
        return dto(evaluations.findById(evaluationId).orElseThrow(() -> new EntityNotFoundException("Economic evaluation not found")));
    }

    /** One evaluation of a scenario; another scenario's evaluation is a 404. */
    @Transactional(readOnly = true)
    public EvaluationDTO evaluationOf(Scenario scenario, UUID evaluationId) {
        return dto(evaluations.findByIdAndScenarioId(evaluationId, scenario.getId())
                .orElseThrow(() -> new EntityNotFoundException("Economic evaluation not found")));
    }

    //TODO: revisar parametros de entrada
    @Transactional(noRollbackFor = EntityNotFoundException.class)
    public EconomicConfiguration configuration(UUID versionId) {
        return read(scenarioDefaults.existing(versionId).flatMap(s -> configurations.findByScenarioId(s.getId()))
                .orElseThrow(() -> new EntityNotFoundException("Economic configuration not found")).getConfigurationJson(), EconomicConfiguration.class);
    }

    /** The published economic configuration of a scenario. */
    @Transactional(noRollbackFor = EntityNotFoundException.class)
    public EconomicConfiguration configuration(Scenario scenario) {
        return read(configurations.findByScenarioId(scenario.getId())
                .orElseThrow(() -> new EntityNotFoundException("Economic configuration not found")).getConfigurationJson(), EconomicConfiguration.class);
    }

    private EvaluationDTO dto(EconomicEvaluation e) {
        Snapshot stored = read(e.getSnapshotJson(), Snapshot.class);
        if (stored.schemaVersion() < 3 && (stored.result() == null || stored.result().indicators() == null)) {
            var result = stored.result();
            var config = stored.configuration();
            var periods = result == null ? null : result.periods();
            boolean completeHorizon = hasPeriodData(read(e.getSnapshotJson(), com.fasterxml.jackson.databind.JsonNode.class))
                    && config != null && periods != null && config.years() >= 1
                    && config.years() <= 100 && periods.size() == config.years() + 1
                    && periods.getFirst() != null && periods.getFirst().year() == config.startYear();
            var derived = (completeHorizon ? indicators.calculate(periods, config.wacc()) : indicators.insufficientData())
                    .withOrigin(org.enerscope.economic.model.enums.IndicatorOrigin.DERIVED_FROM_SNAPSHOT);
            if (result == null) result = new EconomicResult(null, null, null, null, null, null, derived);
            else result = result.withIndicators(derived);
            stored = new Snapshot(stored.schemaVersion(), config, stored.physicalNodes(),
                    stored.physicalConnections(), stored.rawMetrics(), result, stored.simulation(), stored.summary());
        }
        return new EvaluationDTO(e.getId(), e.getCreatedAt(), stored);
    }

    private boolean hasPeriodData(com.fasterxml.jackson.databind.JsonNode snapshot) {
        var configuration = snapshot.path("configuration");
        if (!configuration.path("startYear").isIntegralNumber() || !configuration.path("years").isIntegralNumber()) return false;
        var periods = snapshot.path("result").path("periods");
        if (!periods.isArray()) return false;
        for (var period : periods) {
            if (!period.path("period").isIntegralNumber() || !period.path("year").isIntegralNumber()
                    || !period.path("cashFlow").isNumber()) return false;
        }
        return true;
    }

    //TODO: revisar alcance
    public org.enerscope.project.model.Project authorizeProject(UUID projectId, ProjectMemberPermission permission) {
        var session = AuthUtil.currentSession();
        if (session == null || session.getUser() == null) throw new UnauthorizedException("Authentication required");
        boolean platformAdmin = session.getUser().getPlatformRole() == PlatformRole.ADMIN;
        var project = projects.findById(projectId).orElseThrow(() -> platformAdmin
                ? new EntityNotFoundException("Project not found")
                : new ForbiddenException("Project permission required: " + permission));
        if (!platformAdmin) {
            boolean allowed = project.getMembers().stream().filter(m -> m.getUser().getId().equals(session.getUser().getId()))
                    .anyMatch(m -> ProjectAccessGuard.grants(m, permission));
            if (!allowed) throw new ForbiddenException("Project permission required: " + permission);
        }
        return project;
    }

    //TODO: revisar alcance
    public Version authorize(UUID projectId, UUID versionId, ProjectMemberPermission permission) {
        var project=authorizeProject(projectId,permission);
        return project.getVersions().stream().filter(v -> v.getId().equals(versionId) && v.isActive()).findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Version does not belong to project"));
    }

    public Version authorizeSimulation(UUID projectId, UUID versionId) {
        Version version = authorize(projectId, versionId, ProjectMemberPermission.EDIT_PROJECT);
        versionAccessGuard.assertCanSimulateVersion(versionId);
        return version;
    }

    public Version authorizeConfigurationEdit(UUID projectId, UUID versionId) {
        Version version = authorize(projectId, versionId, ProjectMemberPermission.EDIT_PROJECT);
        versionAccessGuard.assertCanEditVersion(versionId);
        return version;
    }

    // Structural validation already requires a conversion for every variable rule,
    // including contract revenue. Check actual node classes, not editable type labels.
    void validateExportNodes(EconomicConfiguration configuration, Version version) {
        Set<UUID> carriers = new HashSet<>();
        if (version.getNodeSnapshot() != null) CurrentScenarioNodes.nodes(version).stream()
                .filter(n -> n instanceof org.enerscope.node.model.export.LNGCarrier)
                .forEach(n -> carriers.add(n.getId()));
        for (var conversion : configuration.conversions()) {
            EconomicValidator.require(conversion.metric() != org.enerscope.economic.model.enums.Driver.EXPORTED_VOLUME
                    || carriers.contains(conversion.nodeId()),
                    "El volumen exportado requiere un buque del escenario.");
        }
    }

    public Set<UUID> nodeIds(Version version) {
        Set<UUID> ids = new HashSet<>();
        if (version.getNodeSnapshot() != null) CurrentScenarioNodes.nodes(version).forEach(n -> ids.add(n.getId()));
        return ids;
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot serialize economic snapshot", e); }
    }

    private <T> T read(String value, Class<T> type) {
        try { return mapper.readValue(value, type); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot read economic snapshot", e); }
    }
}
