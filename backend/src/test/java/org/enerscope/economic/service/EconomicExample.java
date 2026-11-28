package org.enerscope.economic.service;

import com.fasterxml.jackson.databind.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.enerscope.economic.model.*;
import org.enerscope.economic.model.configuration.EconomicConfiguration;
import org.enerscope.economic.model.enums.*;
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

    /** The id of the default scenario {@link #scenarioDefaults()} gives a version, so tests can stub reads by scenario. */
    public static UUID scenarioIdOf(UUID versionId) {
        return UUID.nameUUIDFromBytes(("default-scenario:" + versionId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** A ScenarioDefaults whose default scenario of any version always exists, with the id {@link #scenarioIdOf}. */
    public static org.enerscope.scenario.service.ScenarioDefaults scenarioDefaults() {
        var defaults = org.mockito.Mockito.mock(org.enerscope.scenario.service.ScenarioDefaults.class);
        org.mockito.Mockito.when(defaults.existing(org.mockito.ArgumentMatchers.any())).thenAnswer(call -> {
            var scenario = new org.enerscope.scenario.model.Scenario(null, org.enerscope.scenario.model.Scenario.DEFAULT_NAME, true);
            ReflectionTestUtils.setField(scenario, "id", scenarioIdOf(call.getArgument(0)));
            return java.util.Optional.of(scenario);
        });
        org.mockito.Mockito.when(defaults.forVersion(org.mockito.ArgumentMatchers.any())).thenAnswer(call -> {
            Version version = call.getArgument(0);
            var scenario = new org.enerscope.scenario.model.Scenario(version, org.enerscope.scenario.model.Scenario.DEFAULT_NAME, true);
            ReflectionTestUtils.setField(scenario, "id", scenarioIdOf(version.getId()));
            return scenario;
        });
        return defaults;
    }

    /** A scenario repository with no scenarios yet: the default one is created (and returned) on first use. */
    public static org.enerscope.scenario.repository.ScenarioRepository noScenariosYet() {
        var repository = org.mockito.Mockito.mock(org.enerscope.scenario.repository.ScenarioRepository.class);
        org.mockito.Mockito.when(repository.findByVersionIdAndDefaultScenarioTrueAndActiveTrue(org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Optional.empty());
        org.mockito.Mockito.when(repository.saveAndFlush(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(call -> call.getArgument(0));
        return repository;
    }

    public static Version version() {
        Well first = well(FIRST, 100, "2031-01-01T00:00:00Z");
        Well second = well(SECOND, 500, "2032-01-01T00:00:00Z");
        Version version = new Version("Economic example", null, List.of(first, second), List.of(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        ReflectionTestUtils.setField(version, "id", UUID.fromString("33333333-3333-3333-3333-333333333333"));
        return version;
    }

     static Well well(UUID id, float output, String start) {
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
