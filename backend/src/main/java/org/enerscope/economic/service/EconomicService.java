package org.enerscope.economic.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.enerscope.common.*;
import org.enerscope.economic.dto.EvaluationDTO;
import org.enerscope.economic.dto.EvaluationDTO.Snapshot;
import org.enerscope.economic.model.*;
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
    private final EconomicValidator validator;
    private final EconomicEngine engine;
    private final EconomicSimulationAdapter simulator;
    private final ObjectMapper mapper;
    private final AppLogger logger;

    public EconomicConfiguration save(UUID projectId, UUID versionId, EconomicConfiguration configuration) {
        Version version = authorize(projectId, versionId, ProjectMemberPermission.EDIT_PROJECT);
        validator.validate(configuration, nodeIds(version));
        // Validate fiscal coverage/classification before accepting a configuration, with zero observed quantities.
        List<OperationalMetric> validationMetrics = new ArrayList<>();
        for (var m : configuration.conversions()) for (int t = 0; t <= configuration.years(); t++)
            validationMetrics.add(new OperationalMetric(m.nodeId(), configuration.startYear() + t, m.metric(), m.unit(), java.math.BigDecimal.ZERO));
        engine.calculate(configuration, validationMetrics);
        var entity = configurations.findByVersionId(versionId).orElseGet(() -> new EconomicConfigurationEntity(version, ""));
        entity.replace(json(configuration)); configurations.save(entity);
        logger.info("Economic configuration saved for version {}", versionId);
        return configuration;
    }

    @Transactional(readOnly = true)
    public EconomicConfiguration get(UUID projectId, UUID versionId) {
        authorize(projectId, versionId, ProjectMemberPermission.VIEW_PROJECT);
        return configuration(versionId);
    }
    public EvaluationDTO evaluate(UUID projectId, UUID versionId) {
        Version version = authorize(projectId, versionId, ProjectMemberPermission.EDIT_PROJECT);
        EconomicConfiguration c = configuration(versionId); validator.validate(c, nodeIds(version));
        var operational = simulator.simulate(version, c);
        EconomicResult result = engine.calculate(c, operational.metrics());
        // Node entities have no back-reference to versions; Jackson captures their concrete physical parameters.
        Snapshot snapshot = new Snapshot(1, c, mapper.valueToTree(version.getNodeSnapshot()),
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
    private EvaluationDTO dto(EconomicEvaluation e) { return new EvaluationDTO(e.getId(), e.getCreatedAt(), read(e.getSnapshotJson(), Snapshot.class)); }
    private Version authorize(UUID projectId, UUID versionId, ProjectMemberPermission permission) {
        var session = AuthUtil.currentSession();
        if (session == null || session.getUser() == null) throw new UnauthorizedException("Authentication required");
        var project = projects.findById(projectId).orElseThrow(() -> new EntityNotFoundException("Project not found"));
        if (session.getUser().getPlatformRole() != PlatformRole.ADMIN) {
            boolean allowed = project.getMembers().stream().filter(m -> m.isActive() && m.getUser().getId().equals(session.getUser().getId()))
                    .flatMap(m -> m.getRoles().stream()).filter(r -> r.isActive())
                    .anyMatch(r -> r.getPermissions().contains(permission));
            if (!allowed) throw new ForbiddenException("Project permission required: " + permission);
        }
        return project.getVersions().stream().filter(v -> v.getId().equals(versionId) && v.isActive()).findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Version does not belong to project"));
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
