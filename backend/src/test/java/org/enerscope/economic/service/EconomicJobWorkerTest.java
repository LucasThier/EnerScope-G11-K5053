package org.enerscope.economic.service;
import java.util.*;
import org.enerscope.economic.model.results.*;
import org.enerscope.logging.AppLogger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.enerscope.economic.service.EconomicExample.*;

class EconomicJobWorkerTest {
    final EconomicJobService jobs=mock(EconomicJobService.class);
    final EconomicInputCodec codec=new EconomicInputCodec(MAPPER);
    final EconomicSimulationAdapter simulator=spy(new EconomicSimulationAdapter());
    final EconomicEngine engine=new EconomicEngine(new EconomicValidator(),new EconomicIndicatorsCalculator());
    final EconomicJobWorker worker=new EconomicJobWorker(jobs,codec,simulator,engine,
            new EconomicStatisticsCalculator(),mock(AppLogger.class));

    @Test
    void completedJobSimulatesEachRoundOnceAndPublishesAfterSavingAllRounds() {
        UUID id=UUID.randomUUID();
        worker.process(new EconomicJobService.ClaimedJob(id,codec.freeze(version(),configuration(),3,42)));
        var rounds=ArgumentCaptor.forClass(EconomicRoundResult.class);
        verify(jobs,times(3)).saveRound(eq(id),rounds.capture());
        assertEquals(List.of(1,2,3),rounds.getAllValues().stream().map(EconomicRoundResult::round).toList());
        assertEquals(3,rounds.getAllValues().stream().map(EconomicRoundResult::seed).distinct().count());
        for(var round:rounds.getAllValues()) assertEquals(new java.math.BigDecimal("169.20"),round.result().npv());
        var order=inOrder(jobs);
        order.verify(jobs).saveRound(eq(id),argThat(r->r.round()==1));
        order.verify(jobs).saveRound(eq(id),argThat(r->r.round()==2));
        order.verify(jobs).saveRound(eq(id),argThat(r->r.round()==3));
        order.verify(jobs).complete(eq(id),argThat(s->s.rounds()==3));
        verify(simulator,times(3)).simulate(any(),eq(configuration()),anyLong());
        verify(jobs,never()).fail(any(),any(),anyBoolean());
    }

    @Test
    void physicalFailureKeepsCompletedRoundsButNeverPublishesPartialEvaluation() {
        var input=codec.freeze(version(),configuration(),3,7);
        var first=new EconomicSimulationAdapter().simulate(codec.thaw(input),configuration(),1);
        doReturn(first).doThrow(new IllegalArgumentException("Invalid physical state"))
                .when(simulator).simulate(any(),any(),anyLong());
        UUID id=UUID.randomUUID();
        worker.process(new EconomicJobService.ClaimedJob(id,input));
        verify(jobs).saveRound(eq(id),argThat(r->r.round()==1));
        verify(jobs).fail(id,"Invalid physical state",false);
        verify(jobs,never()).complete(any(),any());
        verify(simulator,times(2)).simulate(any(),any(),anyLong());
    }

    @Test
    void startupInterruptsUnfinishedJobsBeforePollingAndEmptyQueueDoesNothing() {
        worker.poll(); verifyNoInteractions(jobs);
        worker.initialize(); worker.poll();
        var order=inOrder(jobs);
        order.verify(jobs).interruptPending();
        order.verify(jobs).claimNext();
        verifyNoInteractions(simulator);
    }

    @Test
    void nonCalculableIrrDoesNotFailAnOtherwiseValidJob() {
        var nonConventional=mutate(t->t.put("wacc",0));
        UUID id=UUID.randomUUID();
        var invalidEngine=mock(EconomicEngine.class);
        var result=engine.calculate(configuration(),metrics())
                .withIndicators(new EconomicIndicatorsCalculator().insufficientData());
        when(invalidEngine.calculate(any(),any())).thenReturn(result);
        var other=new EconomicJobWorker(jobs,codec,simulator,invalidEngine,new EconomicStatisticsCalculator(),mock(AppLogger.class));
        other.process(new EconomicJobService.ClaimedJob(id,codec.freeze(version(),nonConventional,1,4)));
        verify(jobs).complete(eq(id),argThat(s->s.irr().count()==0));
        verify(jobs,never()).fail(any(),any(),anyBoolean());
    }
}
