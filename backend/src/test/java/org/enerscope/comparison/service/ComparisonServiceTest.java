package org.enerscope.comparison.service;

import java.time.Instant;
import java.util.*;
import org.enerscope.comparison.dto.ComparisonDTO.*;
import org.enerscope.comparison.repository.ComparisonRepository;
import org.enerscope.common.ForbiddenException;
import org.enerscope.common.EntityNotFoundException;
import org.enerscope.economic.dto.EvaluationDTO;
import org.enerscope.economic.model.results.*;
import org.enerscope.economic.service.*;
import org.enerscope.logging.AppLogger;
import org.enerscope.project.model.Project;
import org.enerscope.project.model.enums.ProjectMemberPermission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ComparisonServiceTest {
    final EconomicService economics = mock(EconomicService.class);
    final EconomicJobService jobs = mock(EconomicJobService.class);
    final ComparisonRepository repository = mock(ComparisonRepository.class);
    final ComparisonService service = new ComparisonService(economics, jobs, new ComparisonEngine(), new ComparisonMeasurements(), repository, EconomicExample.MAPPER, mock(AppLogger.class));
    final UUID projectId = UUID.randomUUID();
    final Request request = ComparisonEngineTest.request(List.of(ComparisonEngineTest.criterion(Metric.NPV, "100", "0", "1000", null, null)));
    final EconomicResult result = new EconomicEngine(new EconomicValidator(), new EconomicIndicatorsCalculator()).calculate(EconomicExample.configuration(), EconomicExample.metrics());

    @BeforeEach
    void sources() {
        when(economics.authorizeProject(eq(projectId), any())).thenReturn(mock(Project.class));
        when(economics.getEvaluation(eq(projectId), any(), any())).thenAnswer(call -> new EvaluationDTO(call.getArgument(2), Instant.now(), new EvaluationDTO.Snapshot(2, EconomicExample.configuration(), null, null, List.of(), result)));
    }

    @Test
    void previewReadsAuthorizedSnapshotsWithoutSavingOrSimulating() {
        assertEquals("READY", service.preview(projectId, request).status());
        verify(economics, times(4)).getEvaluation(eq(projectId), any(), any());
        verify(economics, never()).evaluate(any(), any()); verifyNoInteractions(repository, jobs);
    }

    @Test
    void unauthorizedProjectIsRejectedBeforeSourcesAreLoaded() {
        when(economics.authorizeProject(projectId, ProjectMemberPermission.VIEW_PROJECT)).thenThrow(new ForbiddenException("Denied"));
        assertThrows(ForbiddenException.class, () -> service.preview(projectId, request));
        verify(economics, never()).getEvaluation(any(), any(), any()); verifyNoInteractions(repository);
    }

    @Test
    void foreignEvaluationIsRejectedByAuthorizedSourceLookup() {
        when(economics.getEvaluation(any(), any(), any())).thenThrow(new EntityNotFoundException("Wrong version"));
        assertThrows(EntityNotFoundException.class, () -> service.preview(projectId, request));
        verifyNoInteractions(repository);
    }

    @Test
    void incompatibleHorizonBlocksRecommendation() {
        var cell = request.alternatives().getFirst().cells().getFirst();
        when(economics.getEvaluation(projectId, cell.versionId(), cell.evaluationId())).thenReturn(new EvaluationDTO(cell.evaluationId(), Instant.now(), new EvaluationDTO.Snapshot(2, EconomicExample.mutate(c -> c.put("years", 10)), null, null, List.of(), result)));
        assertEquals("BLOCKED", service.preview(projectId, request).status());
        assertThrows(IllegalArgumentException.class, () -> service.save(projectId, request));
        verifyNoInteractions(repository);
    }

    @Test
    void probabilisticCellsReadEveryRoundAndAverageStoredResults() {
        when(economics.getEvaluation(eq(projectId), any(), any())).thenAnswer(call -> new EvaluationDTO(call.getArgument(2), Instant.now(), new EvaluationDTO.Snapshot(3, EconomicExample.configuration(), null, null, null, null, new SimulationMetadata(2, "1", "TEST", 1), null)));
        when(jobs.round(any(), any(), any(), anyInt())).thenReturn(new EconomicRoundResult(1, "1", List.of(), result));
        var calculated = service.preview(projectId, request);
        assertEquals("READY", calculated.status()); assertEquals(2, calculated.alternatives().getFirst().contexts().getFirst().rounds());
        verify(jobs, times(8)).round(eq(projectId), any(), any(), anyInt());
    }

    @Test
    void saveRequiresEditAndStoresConfigurationAndServerCalculatedResult() {
        when(repository.saveAndFlush(any())).thenAnswer(call -> {
            var entity = call.getArgument(0, org.enerscope.comparison.model.ScenarioComparison.class);
            ReflectionTestUtils.setField(entity, "id", UUID.randomUUID()); ReflectionTestUtils.setField(entity, "createdAt", Instant.now());
            return entity;
        });
        var stored = service.save(projectId, request);
        assertNotNull(stored.id()); assertEquals(request, stored.configuration()); assertEquals("READY", stored.result().status());
        verify(economics).authorizeProject(projectId, ProjectMemberPermission.EDIT_PROJECT);
        verify(repository).saveAndFlush(argThat(e -> e.getResultJson().contains("calculationVersion") && e.getConfigurationJson().contains("Common scope")));
    }

    @Test
    void historyReadsStoredResultWithoutRecalculatingSources() throws Exception {
        var result = service.preview(projectId, request);
        clearInvocations(economics);
        UUID id = UUID.randomUUID();
        var stored = new org.enerscope.comparison.model.ScenarioComparison(mock(Project.class), request.name(), EconomicExample.MAPPER.writeValueAsString(request), EconomicExample.MAPPER.writeValueAsString(result));
        when(repository.findByIdAndProjectId(id, projectId)).thenReturn(Optional.of(stored));
        assertEquals(result, service.get(projectId, id).result());
        verify(economics, never()).getEvaluation(any(), any(), any());
        verify(repository).findByIdAndProjectId(id, projectId);
    }
}
