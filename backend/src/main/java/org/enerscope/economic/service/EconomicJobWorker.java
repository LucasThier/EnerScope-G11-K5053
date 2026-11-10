package org.enerscope.economic.service;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.enerscope.economic.model.results.EconomicRoundResult;
import org.enerscope.logging.AppLogger;
import org.enerscope.simulator.Simulator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;

/** One worker per application. No HTTP authentication or long-lived JPA transaction. */
@Component @RequiredArgsConstructor @EnableScheduling
@ConditionalOnProperty(name="economic.worker.enabled",havingValue="true",matchIfMissing=true)
public class EconomicJobWorker {
    private final EconomicJobService jobs;
    private final EconomicInputCodec codec;
    private final EconomicSimulationAdapter simulator;
    private final EconomicEngine engine;
    private final EconomicStatisticsCalculator statistics;
    private final AppLogger logger;
    private volatile boolean ready;

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        jobs.interruptPending();
        ready=true;
    }

    @Scheduled(fixedDelayString="${economic.worker.poll-ms:1000}")
    public void poll() {
        if(!ready) return;
        var claimed=jobs.claimNext();
        if(claimed==null) return;
        process(claimed);
    }

    public void process(EconomicJobService.ClaimedJob job) {
        try {
            var input = job.input();
            List<EconomicRoundResult> results=new ArrayList<>();
            for(int i=0; i<input.simulation().rounds(); i++) {
                long seed = Simulator.seedForRound(Long.parseLong(input.simulation().seed()),i);
                var observed = simulator.simulate(codec.thaw(input),input.configuration(),seed);
                var result = engine.calculate(input.configuration(),observed.metrics());
                var round = new EconomicRoundResult(i+1,Long.toString(seed),observed.rawMetrics(),result);
                jobs.saveRound(job.id(),round); results.add(round);
            }
            jobs.complete(job.id(),statistics.calculate(results));
        } catch(Exception failure) {
            logger.error("Economic job "+job.id()+" failed",failure);
            jobs.fail(job.id(),failure instanceof IllegalArgumentException ? failure.getMessage()
                    : "Simulation could not be completed; review the server log",Thread.currentThread().isInterrupted());
        }
    }
}
