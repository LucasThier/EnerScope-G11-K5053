package org.enerscope.economic.service;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.enerscope.economic.service.EconomicExample.*;

class EconomicSimulationAdapterTest {
    @Test void actualAnnualSimulationFeedsCompleteExampleAndRepeatsWithoutStateLeak() {
        var adapter=new EconomicSimulationAdapter();var version=version();
        var first=adapter.simulate(version,configuration());var second=adapter.simulate(version,configuration());
        assertEquals(first,second); assertTrue(version.getResults().isEmpty());
        assertEquals(10,first.rawMetrics().size());
        var raw=first.rawMetrics().stream().filter(m->m.nodeId().equals(FIRST)&&m.period()==1).findFirst().orElseThrow();
        assertEquals(0,new BigDecimal("876000").compareTo(raw.output()));
        assertEquals(0,new BigDecimal("8760").compareTo(raw.operatingHours()));
        var expansion=first.rawMetrics().stream().filter(m->m.nodeId().equals(SECOND)&&m.period()==1).findFirst().orElseThrow();
        assertEquals(0,expansion.output().signum());
        var result=new EconomicEngine(new EconomicValidator()).calculate(configuration(),first.metrics());
        assertEquals(new BigDecimal("169.20"),result.npv());
    }
    @Test void unknownRawUnitsAreRejected() {
        var c=mutate(t->((com.fasterxml.jackson.databind.node.ObjectNode)t.withArray("conversions").get(0)).put("rawUnit","TONNE"));
        assertThrows(IllegalArgumentException.class,()->new EconomicSimulationAdapter().simulate(version(),c));
    }
}
