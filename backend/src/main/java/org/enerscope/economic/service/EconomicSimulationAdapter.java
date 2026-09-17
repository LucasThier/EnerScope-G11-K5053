package org.enerscope.economic.service;

import java.math.BigDecimal;
import java.util.*;
import org.enerscope.economic.model.*;
import org.enerscope.economic.model.EconomicConfiguration.*;
import org.enerscope.simulator.*;
import org.enerscope.version.model.Version;
import org.springframework.stereotype.Service;
import static org.enerscope.economic.service.EconomicValidator.require;

@Service
public class EconomicSimulationAdapter {
    public record SimulationSnapshot(List<AnnualNodeMetrics> rawMetrics, List<OperationalMetric> metrics) {}

    public SimulationSnapshot simulate(Version source, EconomicConfiguration c) {
        // Isolate result attachment from the managed Version. Simulator state is rebuilt from physical nodes.
        Version isolated = new Version(source.getName(), null,
                source.getNodeSnapshot() == null ? List.of() : List.copyOf(source.getNodeSnapshot()),
                source.getConnectionSnapshot() == null ? List.of() : List.copyOf(source.getConnectionSnapshot()),
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        Simulator simulator = new Simulator(isolated, c.startYear() + 1);
        simulator.simulate(c.years());
        List<OperationalMetric> converted = new ArrayList<>();
        for (MetricConversion conversion : c.conversions()) {
            for (int t = 0; t <= c.years(); t++) {
                BigDecimal raw;
                if (EconomicValidator.observed(conversion.metric())) {
                    if (t == 0) raw = BigDecimal.ZERO;
                    else {
                        int period = t;
                        AnnualNodeMetrics m = simulator.getAnnualMetrics().stream()
                                .filter(v -> v.nodeId().equals(conversion.nodeId()) && v.period() == period).findFirst()
                                .orElseThrow(() -> new IllegalArgumentException("Simulator does not support configured node"));
                        raw = switch (conversion.metric()) {
                            case INPUT_VOLUME -> m.input(); case OUTPUT_VOLUME -> m.output();
                            case EXPORTED_VOLUME -> m.exported(); case OPERATING_HOURS -> m.operatingHours();
                            case EVENT_COUNT -> m.events(); default -> throw new IllegalArgumentException("Unknown observed metric");
                        };
                    }
                    String expected = switch (conversion.metric()) {
                        case OPERATING_HOURS -> "HOUR"; case EVENT_COUNT -> "EVENT"; default -> "SIMULATOR_UNIT";
                    };
                    require(conversion.rawUnit().equals(expected), "Raw metric unit must be " + expected);
                } else raw = conversion.annualQuantity();
                converted.add(new OperationalMetric(conversion.nodeId(), c.startYear() + t,
                        conversion.metric(), conversion.unit(), raw.multiply(conversion.factor())));
            }
        }
        return new SimulationSnapshot(List.copyOf(simulator.getAnnualMetrics()), List.copyOf(converted));
    }
}
