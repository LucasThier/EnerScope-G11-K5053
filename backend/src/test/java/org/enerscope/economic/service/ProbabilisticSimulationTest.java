package org.enerscope.economic.service;
import java.math.BigDecimal;
import java.util.*;
import org.enerscope.economic.dto.FrozenEconomicInput;
import org.enerscope.economic.model.results.*;
import org.enerscope.node.model.NodeConnection;
import org.enerscope.node.model.extraction.Well;
import org.enerscope.probabilistic.*;
import org.enerscope.simulator.Simulator;
import org.enerscope.simulator.simNode.SimWell;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.enerscope.economic.service.EconomicExample.*;

class ProbabilisticSimulationTest {
    final EconomicInputCodec codec=new EconomicInputCodec(MAPPER);
    final EconomicSimulationAdapter adapter=new EconomicSimulationAdapter();
    final EconomicEngine engine=new EconomicEngine(new EconomicValidator(),new EconomicIndicatorsCalculator());
    @Test void constantRoundsPreserveTheCompleteIndependentExample() {
        var input=codec.freeze(version(),configuration(),3,92L);
        for(int i=0;i<3;i++) {
            var run=adapter.simulate(codec.thaw(input),input.configuration(),Simulator.seedForRound(92,i));
            var result=engine.calculate(input.configuration(),run.metrics());
            assertEquals(new BigDecimal("169.20"),result.npv());
            assertEquals(new BigDecimal("0.1483447840"),result.indicators().irr().rate());
            assertEquals(2034,result.indicators().simplePayback().year());
            assertEquals(2035,result.indicators().discountedPayback().year());
            assertEquals(10,run.rawMetrics().size());
        }
    }
    @Test void seedReproducesRoundAndDifferentRoundSeedsChangeAnnualQuantities() {
        var source=version(); ((Well)source.getNodeSnapshot().getFirst()).setDeclineCurve(new UniformDistributionCase(1,20));
        var input=codec.freeze(source,configuration(),3,73L);
        var a=adapter.simulate(codec.thaw(input),configuration(),Simulator.seedForRound(73,0));
        var b=adapter.simulate(codec.thaw(input),configuration(),Simulator.seedForRound(73,1));
        var again=adapter.simulate(codec.thaw(input),configuration(),Simulator.seedForRound(73,0));
        assertEquals(a,again); assertNotEquals(a,b);
        assertTrue(source.getFinalResults().isEmpty());
    }
    @Test void snapshotRoundTripPreservesDistributionsAndIgnoresLaterPhysicalEdits() throws Exception {
        var source=version(); var well=(Well)source.getNodeSnapshot().getFirst();
        well.setDeclineCurve(new NormalDistributionCase(10,2));
        var frozen=codec.freeze(source,configuration(),10,Long.MIN_VALUE);
        var loaded=MAPPER.readValue(MAPPER.writeValueAsString(frozen),FrozenEconomicInput.class);
        well.setMaxCollectionCapacity(999);
        assertEquals(100,((Well)codec.thaw(loaded).getNodeSnapshot().getFirst()).getMaxCollectionCapacity());
        assertEquals(adapter.simulate(codec.thaw(frozen),configuration(),11),adapter.simulate(codec.thaw(loaded),configuration(),11));
        assertEquals(Long.toString(Long.MIN_VALUE),loaded.simulation().seed());
    }
    @Test void reorderingNodesDoesNotChangeTheirRandomStreams() {
        var source=version(); ((Well)source.getNodeSnapshot().getFirst()).setDeclineCurve(new UniformDistributionCase(5,15));
        var first=adapter.simulate(source,configuration(),123);
        var reversed=new ArrayList<>(source.getNodeSnapshot());Collections.reverse(reversed);source.setNodeSnapshot(reversed);
        assertEquals(first,adapter.simulate(source,configuration(),123));
    }
    @Test void annualBoundsAndDelayedStartupRemainExact() {
        var run=adapter.simulate(version(),configuration(),4);
        for(var metric:run.rawMetrics()) {
            assertEquals(metric.nodeId().equals(SECOND)&&metric.period()==1?0:8760,metric.operatingHours().intValueExact());
        }
    }
    @Test void declineUsesOperatingAgeAndClampsEachSample() {
        for(float sample:new float[]{-10,150}) {
            var physical=well(FIRST,100,"2031-01-01T00:00:00Z");physical.setDeclineCurve(new ConstantValue(sample));
            var node=new SimWell(physical);node.setCalendarControlled(true);
            node.setOperatingAgeHours(8759);node.simulate(10000);
            assertEquals(100,node.getToDeliver().getAmount());
            node.setOperatingAgeHours(8760);node.simulate(10001);
            assertEquals(sample<0?100:0,node.getToDeliver().getAmount());
            assertEquals(sample<0?0:100,node.getAcumDecline());
        }
    }
    @Test void graphRejectsCyclesMissingReferencesAndDuplicateEdges() {
        var source=version();
        source.setConnectionSnapshot(List.of(new NodeConnection(UUID.randomUUID(),FIRST,SECOND),new NodeConnection(UUID.randomUUID(),SECOND,FIRST)));
        assertThrows(IllegalArgumentException.class,()->new Simulator(source));
        source.setConnectionSnapshot(List.of(new NodeConnection(UUID.randomUUID(),FIRST,UUID.randomUUID())));
        assertThrows(IllegalArgumentException.class,()->new Simulator(source));
        var edge=new NodeConnection(UUID.randomUUID(),FIRST,SECOND);source.setConnectionSnapshot(List.of(edge,edge));
        assertThrows(IllegalArgumentException.class,()->new Simulator(source));
    }
    @Test void distributionSamplesAreIndependentOfEntitySamplerState() {
        var distribution=new NormalDistributionCase(5,1);
        var first=distribution.sampler(8); distribution.generateValue(); var second=distribution.sampler(8);
        for(int i=0;i<10;i++)assertEquals(first.getAsDouble(),second.getAsDouble());
        assertThrows(IllegalArgumentException.class,()->new UniformDistributionCase().sampler(0));
    }
}
