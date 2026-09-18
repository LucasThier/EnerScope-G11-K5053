package org.enerscope.economic.service;

import com.fasterxml.jackson.databind.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.enerscope.economic.model.*;
import org.enerscope.economic.model.EconomicConfiguration.Driver;
import org.enerscope.node.model.extraction.Well;
import org.enerscope.node.model.enums.NodeStateEnum;
import org.enerscope.version.model.Version;
import org.springframework.test.util.ReflectionTestUtils;

public final class EconomicExample {
    public static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    public static final UUID FIRST = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID SECOND = UUID.fromString("22222222-2222-2222-2222-222222222222");
    public static EconomicConfiguration configuration() {
        try { return MAPPER.readValue(Files.readString(Path.of("../backend/src/test/resources/economic-examples/economic-configuration.json")), EconomicConfiguration.class); }
        catch (Exception e) { throw new AssertionError(e); }
    }
    public static List<OperationalMetric> metrics() {
        List<OperationalMetric> metrics = new ArrayList<>();
        for (int y = 2031; y <= 2035; y++) {
            metrics.add(new OperationalMetric(FIRST, y, Driver.OUTPUT_VOLUME, "UNIT", new BigDecimal("100")));
            metrics.add(new OperationalMetric(SECOND, y, Driver.OUTPUT_VOLUME, "UNIT", new BigDecimal(y == 2031 ? "0" : "500")));
        }
        return metrics;
    }
    public static Version version() {
        Well first = well(FIRST, 100, "2031-01-01T00:00:00Z");
        Well second = well(SECOND, 500, "2032-01-01T00:00:00Z");
        Version version = new Version("Economic example", null, List.of(first, second), List.of(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        ReflectionTestUtils.setField(version, "id", UUID.fromString("33333333-3333-3333-3333-333333333333"));
        return version;
    }
    private static Well well(UUID id, float output, String start) {
        Well well = new Well(); ReflectionTestUtils.setField(well, "id", id);
        well.setName("Example " + id); well.setMaxCollectionCapacity(output); well.setLifespanInMonths(120);
        well.setState(NodeStateEnum.RUNNING); well.setStartupDate(Instant.parse(start));
        return well;
    }
    public static EconomicConfiguration mutate(java.util.function.Consumer<com.fasterxml.jackson.databind.node.ObjectNode> change) {
        var tree = (com.fasterxml.jackson.databind.node.ObjectNode) MAPPER.valueToTree(configuration()); change.accept(tree);
        return MAPPER.convertValue(tree, EconomicConfiguration.class);
    }
}
