package org.enerscope.economic.service;
import java.util.*;
import org.enerscope.economic.dto.*;
import org.enerscope.economic.model.*;
import org.enerscope.economic.repository.*;
import org.enerscope.logging.AppLogger;
import org.enerscope.project.model.enums.ProjectMemberPermission;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.enerscope.economic.service.EconomicExample.*;
import static org.enerscope.economic.model.enums.EconomicJobStatus.*;

class EconomicJobServiceTest {
    final EconomicService economics=mock(EconomicService.class);
    final EconomicJobRepository jobs=mock(EconomicJobRepository.class);
    final EconomicRoundRepository rounds=mock(EconomicRoundRepository.class);
    final EconomicEvaluationRepository evaluations=mock(EconomicEvaluationRepository.class);
    final EconomicInputCodec codec=new EconomicInputCodec(MAPPER);
    final EconomicJobService service=new EconomicJobService(economics,new EconomicValidator(),codec,jobs,rounds,evaluations,MAPPER,mock(AppLogger.class));
    final UUID project=UUID.randomUUID(),versionId=version().getId(),id=UUID.randomUUID();
    EconomicJob job() throws Exception {
        var job=new EconomicJob(version(),MAPPER.writeValueAsString(codec.freeze(version(),configuration(),2,42)),2,"42");
        ReflectionTestUtils.setField(job,"id",id);
        return job;
    }

    @Test
    void submissionFreezesInputsAndQueuesWithoutPublishingEvaluation() {
        var version=version();
        when(economics.authorize(project,versionId,ProjectMemberPermission.EDIT_PROJECT)).thenReturn(version);
        when(economics.configuration(versionId)).thenReturn(configuration());
        when(economics.nodeIds(version)).thenReturn(Set.of(FIRST,SECOND));
        when(jobs.saveAndFlush(any())).thenAnswer(c->c.getArgument(0));
        var dto=service.submit(project,versionId,new EconomicJobRequest(null,"9223372036854775807"));
        assertEquals(QUEUED,dto.status());assertEquals(10,dto.rounds());assertEquals("9223372036854775807",dto.seed());
        verifyNoInteractions(rounds,evaluations);
    }

    @Test
    void retryUsesFrozenInputAndSeedWithoutReadingCurrentConfiguration() throws Exception {
        var old=job();ReflectionTestUtils.setField(old,"status",INTERRUPTED);
        when(economics.authorize(project,versionId,ProjectMemberPermission.EDIT_PROJECT)).thenReturn(version());
        when(jobs.findByIdAndVersionId(id,versionId)).thenReturn(Optional.of(old));
        when(jobs.saveAndFlush(any())).thenAnswer(c->{
            EconomicJob copy=c.getArgument(0);
            assertEquals(old.getInputJson(),copy.getInputJson());return copy;
        });
        var retry=service.retry(project,versionId,id);
        assertEquals(QUEUED,retry.status());assertEquals(0,retry.completedRounds());assertEquals("42",retry.seed());
        verify(economics,never()).configuration(any());
    }

    @Test
    void runningJobCannotBeRetried() throws Exception {
        var old=job();ReflectionTestUtils.setField(old,"status",RUNNING);
        when(jobs.findByIdAndVersionId(id,versionId)).thenReturn(Optional.of(old));
        assertThrows(IllegalArgumentException.class,()->service.retry(project,versionId,id));
        verify(jobs,never()).saveAndFlush(any());
    }

    @Test
    void lostAtomicClaimDoesNotStartDuplicateWork() throws Exception {
        when(jobs.findFirstByStatusOrderByCreatedAtAsc(QUEUED)).thenReturn(Optional.of(job()));
        when(jobs.claim(eq(id),eq(QUEUED),eq(RUNNING),any())).thenReturn(0);
        assertNull(service.claimNext());verify(jobs,never()).findById(any());
    }

    @Test
    void successfulAtomicClaimReturnsOnlyFrozenInput() throws Exception {
        var job=job();
        when(jobs.findFirstByStatusOrderByCreatedAtAsc(QUEUED)).thenReturn(Optional.of(job));
        when(jobs.claim(eq(id),eq(QUEUED),eq(RUNNING),any())).thenReturn(1);
        when(jobs.findById(id)).thenReturn(Optional.of(job));
        var claim=service.claimNext();
        assertEquals(id,claim.id());assertEquals("42",claim.input().simulation().seed());
        verifyNoInteractions(economics);
    }

    @Test
    void readsRequireViewPermissionAndScopeJobToVersion() throws Exception {
        when(jobs.findByIdAndVersionId(id,versionId)).thenReturn(Optional.of(job()));
        service.get(project,versionId,id);service.list(project,versionId);
        verify(economics,times(2)).authorize(project,versionId,ProjectMemberPermission.VIEW_PROJECT);
        verify(jobs).findByIdAndVersionId(id,versionId);
    }

    @Test
    void invalidExecutionOptionsAreRejectedWithoutSilentCoercion() {
        assertEquals(10,new EconomicJobRequest(null,null).roundCount());
        for(int count:new int[]{-1,0,101}) assertThrows(IllegalArgumentException.class,()->new EconomicJobRequest(count,null).roundCount());
        for(String seed:List.of("1.5","9223372036854775808","abc")) assertThrows(IllegalArgumentException.class,()->new EconomicJobRequest(1,seed).seedValue());
        assertEquals(Long.MIN_VALUE,new EconomicJobRequest(1,Long.toString(Long.MIN_VALUE)).seedValue());
    }

    @Test
    void roundsMustBeSequentialAndCompletionRequiresAllRounds() throws Exception {
        var job=job();
        assertThrows(IllegalStateException.class,()->job.roundCompleted(1));
        ReflectionTestUtils.setField(job,"status",RUNNING);
        assertThrows(IllegalStateException.class,()->job.roundCompleted(2));
        job.roundCompleted(1);
        assertThrows(IllegalStateException.class,()->job.complete(UUID.randomUUID()));
        assertThrows(IllegalStateException.class,()->job.roundCompleted(1));
        job.roundCompleted(2);job.complete(UUID.randomUUID());
        assertEquals(COMPLETED,job.getStatus());
        job.fail("Late error",false);assertEquals(COMPLETED,job.getStatus());
    }
}
