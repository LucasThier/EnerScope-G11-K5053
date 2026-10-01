package org.enerscope.economic.service;

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
import org.enerscope.user.model.enums.PlatformRole;
import org.enerscope.util.AuthUtil;
import org.enerscope.version.model.Version;
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

    public EconomicConfiguration save(UUID projectId, UUID versionId, EconomicConfiguration configuration) {
        Version version = authorize(projectId, versionId, ProjectMemberPermission.EDIT_PROJECT);
        validator.validate(configuration, nodeIds(version));
        validateExportNodes(configuration, version);
        // Validate fiscal coverage/classification before accepting a configuration, with zero observed quantities.
        List<OperationalMetric> validationMetrics = new ArrayList<>();
        for (var m : configuration.conversions()) for (int t = 0; t <= configuration.years(); t++)
            validationMetrics.add(new OperationalMetric(m.nodeId(), configuration.startYear() + t, m.metric(), m.unit(), java.math.BigDecimal.ZERO));
        engine.calculate(configuration, validationMetrics);
        var entity = configurations.findByVersionId(versionId).orElseGet(() -> new EconomicConfigurationEntity(version, ""));
        entity.replace(json(configuration)); configurations.save(entity);
        drafts.deleteByVersionId(versionId);
        logger.info("Economic configuration saved for version {}", versionId);
        return configuration;
    }

    public org.enerscope.economic.dto.EconomicDraftDTO saveDraft(UUID projectId, UUID versionId,
            org.enerscope.economic.dto.EconomicDraftDTO draft) {
        Version version = authorize(projectId, versionId, ProjectMemberPermission.EDIT_PROJECT);
        EconomicValidator.require(draft != null, "Draft is required");
        draft.validateStructure();
        var entity = drafts.findByVersionId(versionId).orElseGet(() -> new EconomicDraftEntity(version, ""));
        entity.replace(json(draft)); drafts.save(entity);
        logger.info("Economic draft saved for version {}", versionId);
        return draft;
    }

    @Transactional(readOnly = true)
    public org.enerscope.economic.dto.EconomicDraftDTO getDraft(UUID projectId, UUID versionId) {
        authorize(projectId, versionId, ProjectMemberPermission.VIEW_PROJECT);
        return drafts.findByVersionId(versionId).map(e -> read(e.getDraftJson(),
                org.enerscope.economic.dto.EconomicDraftDTO.class)).orElse(null);
    }

    @Transactional(readOnly = true)
    public EconomicConfiguration get(UUID projectId, UUID versionId) {
        authorize(projectId, versionId, ProjectMemberPermission.VIEW_PROJECT);
        return configuration(versionId);
    }

    public EvaluationDTO evaluate(UUID projectId, UUID versionId) {
        Version version = authorize(projectId, versionId, ProjectMemberPermission.EDIT_PROJECT);
        EconomicConfiguration c = configuration(versionId); validator.validate(c, nodeIds(version));
        validateExportNodes(c, version);
        var operational = simulator.simulate(version, c);
        EconomicResult result = engine.calculate(c, operational.metrics());
        if (result.indicators().irr().status() == org.enerscope.economic.model.enums.IrrStatus.NUMERICAL_FAILURE
                || result.indicators().discountedPayback().status() == org.enerscope.economic.model.enums.PaybackStatus.NUMERICAL_FAILURE)
            logger.warn("Economic indicator numerical failure for version {}; evaluation remains available", versionId);
        // Node entities have no back-reference to versions; Jackson captures their concrete physical parameters.
        Snapshot snapshot = new Snapshot(2, c, mapper.valueToTree(version.getNodeSnapshot()),
                mapper.valueToTree(version.getConnectionSnapshot()), operational.rawMetrics(), result);
        EconomicEvaluation stored = evaluations.saveAndFlush(new EconomicEvaluation(version, json(snapshot)));
        logger.info("Economic evaluation {} completed for version {}", stored.getId(), versionId);
        return new EvaluationDTO(stored.getId(), stored.getCreatedAt(), snapshot);
    }

    @Transactional(readOnly = true)
    public List<EvaluationDTO> list(UUID projectId, UUID versionId) {
        authorize(projectId, versionId, ProjectMemberPermission.VIEW_PROJECT);
        return evaluations.findByVersionIdOrderByCreatedAtDesc(versionId).stream().map(this::dto).toList();
    }

    @Transactional(readOnly = true)
    public EvaluationDTO getEvaluation(UUID projectId, UUID versionId, UUID evaluationId) {
        authorize(projectId, versionId, ProjectMemberPermission.VIEW_PROJECT);
        return dto(evaluations.findByIdAndVersionId(evaluationId, versionId)
                .orElseThrow(() -> new EntityNotFoundException("Economic evaluation not found")));
    }

    private EconomicConfiguration configuration(UUID versionId) {
        return read(configurations.findByVersionId(versionId)
                .orElseThrow(() -> new EntityNotFoundException("Economic configuration not found")).getConfigurationJson(), EconomicConfiguration.class);
    }

    private EvaluationDTO dto(EconomicEvaluation e) {
        Snapshot stored = read(e.getSnapshotJson(), Snapshot.class);
        if (stored.result() == null || stored.result().indicators() == null) {
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
                    stored.physicalConnections(), stored.rawMetrics(), result);
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

    private Version authorize(UUID projectId, UUID versionId, ProjectMemberPermission permission) {
        var session = AuthUtil.currentSession();
        if (session == null || session.getUser() == null) throw new UnauthorizedException("Authentication required");
        var project = projects.findById(projectId).orElseThrow(() -> new EntityNotFoundException("Project not found"));
        if (session.getUser().getPlatformRole() != PlatformRole.ADMIN) {
            boolean allowed = project.getMembers().stream().filter(m -> m.isActive() && m.getUser().getId().equals(session.getUser().getId()))
                    .flatMap(m -> m.getRoles().stream()).filter(BaseEntity::isActive)
                    .anyMatch(r -> r.getPermissions().contains(permission));
            if (!allowed) throw new ForbiddenException("Project permission required: " + permission);
        }
        return project.getVersions().stream().filter(v -> v.getId().equals(versionId) && v.isActive()).findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Version does not belong to project"));
    }

    // Structural validation already requires a conversion for every variable rule,
    // including contract revenue. Check actual node classes, not editable type labels.
    private void validateExportNodes(EconomicConfiguration configuration, Version version) {
        Set<UUID> carriers = new HashSet<>();
        if (version.getNodeSnapshot() != null) version.getNodeSnapshot().stream()
                .filter(n -> n instanceof org.enerscope.node.model.export.LNGCarrier)
                .forEach(n -> carriers.add(n.getId()));
        for (var conversion : configuration.conversions()) {
            EconomicValidator.require(conversion.metric() != org.enerscope.economic.model.enums.Driver.EXPORTED_VOLUME
                    || carriers.contains(conversion.nodeId()),
                    "El volumen exportado requiere un buque del escenario.");
        }
    }

    private Set<UUID> nodeIds(Version version) {
        Set<UUID> ids = new HashSet<>();
        if (version.getNodeSnapshot() != null) version.getNodeSnapshot().forEach(n -> ids.add(n.getId()));
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
