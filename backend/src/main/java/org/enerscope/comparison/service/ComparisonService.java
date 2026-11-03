package org.enerscope.comparison.service;

import java.util.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.enerscope.comparison.dto.ComparisonDTO.*;
import org.enerscope.comparison.model.ScenarioComparison;
import org.enerscope.comparison.repository.ComparisonRepository;
import org.enerscope.common.EntityNotFoundException;
import org.enerscope.economic.dto.EvaluationDTO;
import org.enerscope.economic.service.EconomicService;
import org.enerscope.economic.service.EconomicJobService;
import org.enerscope.economic.model.results.EconomicRoundResult;
import org.enerscope.logging.AppLogger;
import org.enerscope.project.model.enums.ProjectMemberPermission;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ComparisonService {
    private final EconomicService economics;
    private final EconomicJobService jobs;
    private final ComparisonEngine engine;
    private final ComparisonMeasurements measurements;
    private final ComparisonRepository repository;
    private final ObjectMapper mapper;
    private final AppLogger logger;

    public Result preview(UUID projectId, Request request) {
        economics.authorizeProject(projectId, ProjectMemberPermission.VIEW_PROJECT);
        engine.validate(request);
        List<ComparisonEngine.Observation> observations = new ArrayList<>();
        List<String> issues = new ArrayList<>();
        Map<String, EvaluationDTO> evaluations = new HashMap<>();
        Map<String, List<EconomicRoundResult>> rounds = new HashMap<>();
        EvaluationDTO.Snapshot first = null;
        Map<Metric, String> units = new EnumMap<>(Metric.class);
        for (var alternative : request.alternatives()) for (var cell : alternative.cells()) {
            String key = cell.versionId() + ":" + cell.evaluationId();
            var evaluation = evaluations.computeIfAbsent(key, k -> economics.getEvaluation(projectId, cell.versionId(), cell.evaluationId()));
            var snapshot = evaluation.snapshot();
            if (snapshot == null || snapshot.configuration() == null) throw new IllegalArgumentException("La evaluación no tiene configuración guardada.");
            if (first == null) first = snapshot;
            var base = first.configuration(); var c = snapshot.configuration();
            if (c.startYear() != base.startYear() || c.years() != base.years() || !Objects.equals(c.currency(), base.currency())
                    || c.wacc() == null || base.wacc() == null || c.wacc().compareTo(base.wacc()) != 0 || !Objects.equals(c.boundary(), base.boundary()))
                issues.add(alternative.name() + ": moneda, horizonte, año de valoración, WACC o perímetro incompatibles.");
            if (first.simulation() != null && snapshot.simulation() != null && (!Objects.equals(first.simulation().algorithm(), snapshot.simulation().algorithm()) || first.simulation().calculationVersion() != snapshot.simulation().calculationVersion()))
                issues.add(alternative.name() + ": las versiones del simulador son incompatibles.");
            var samples = rounds.computeIfAbsent(key, k -> {
                if (snapshot.schemaVersion() < 3) return List.of(new EconomicRoundResult(1, "", snapshot.rawMetrics(), snapshot.result()));
                if (snapshot.simulation() == null || snapshot.simulation().rounds() < 1 || snapshot.simulation().rounds() > 100) throw new IllegalArgumentException("Cantidad de rondas inválida.");
                List<EconomicRoundResult> list = new ArrayList<>();
                for (int i = 1; i <= snapshot.simulation().rounds(); i++) list.add(jobs.round(projectId, cell.versionId(), cell.evaluationId(), i));
                return list;
            });
            Map<Metric, ComparisonEngine.Measurement> values = new EnumMap<>(Metric.class);
            for (var criterion : request.criteria()) {
                var value = measurements.average(criterion.metric(), samples.stream().map(r -> measurements.extract(criterion.metric(), snapshot, r.result(), r.rawMetrics(), cell)).toList());
                values.put(criterion.metric(), value);
                if (value.value() != null) {
                    String previous = units.putIfAbsent(criterion.metric(), value.unit());
                    if (previous != null && !previous.equals(value.unit())) issues.add("Unidades incompatibles para " + criterion.metric());
                }
            }
            observations.add(new ComparisonEngine.Observation(alternative.id(), cell.contextId(), cell.versionId(), cell.evaluationId(), samples.size(), values, List.of()));
        }
        var c = Objects.requireNonNull(first).configuration();
        logger.debug("Comparison preview for project {} with {} alternatives", projectId, request.alternatives().size());
        return engine.calculate(request, observations, c.currency(), c.startYear(), c.years(), issues.stream().distinct().toList());
    }

    @Transactional
    public Saved save(UUID projectId, Request request) {
        var project = economics.authorizeProject(projectId, ProjectMemberPermission.EDIT_PROJECT);
        var result = preview(projectId, request);
        if (result.status().equals("BLOCKED")) throw new IllegalArgumentException("Resolvé los problemas de comparabilidad antes de guardar.");
        var saved = repository.saveAndFlush(new ScenarioComparison(project, request.name(), json(request), json(result)));
        logger.info("Scenario comparison {} saved for project {}", saved.getId(), projectId);
        return new Saved(saved.getId(), saved.getCreatedAt(), request, result);
    }

    public List<Summary> list(UUID projectId) {
        economics.authorizeProject(projectId, ProjectMemberPermission.VIEW_PROJECT);
        return repository.findByProjectIdOrderByCreatedAtDesc(projectId).stream().map(e -> new Summary(e.getId(), e.getCreatedAt(), e.getName())).toList();
    }

    public Saved get(UUID projectId, UUID id) {
        economics.authorizeProject(projectId, ProjectMemberPermission.VIEW_PROJECT);
        var entity = repository.findByIdAndProjectId(id, projectId).orElseThrow(() -> new EntityNotFoundException("Comparison not found"));
        return new Saved(entity.getId(), entity.getCreatedAt(), read(entity.getConfigurationJson(), Request.class), read(entity.getResultJson(), Result.class));
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        }
        catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize comparison", e);
        }
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        }
        catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot read comparison", e);
        }
    }
}
