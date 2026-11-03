package org.enerscope.comparison.service;

import java.math.BigDecimal;
import java.util.*;
import org.enerscope.comparison.dto.ComparisonDTO.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ComparisonEngineTest {
    final ComparisonEngine engine = new ComparisonEngine();

    static BigDecimal n(String value) {
        return new BigDecimal(value);
    }

    static Criterion criterion(Metric metric, String weight, String bad, String good, String min, String max) {
        return new Criterion(metric, n(weight), n(bad), n(good), min == null ? null : n(min), max == null ? null : n(max));
    }

    static Request request(List<Criterion> criteria) {
        return new Request("Comparison", "Common scope", true, List.of(new Context("low", "Low", n("25")), new Context("high", "High", n("75"))),
            List.of(alternative("A"), alternative("B")), criteria);
    }

    static Alternative alternative(String id) {
        return new Alternative(id, id, List.of(new Cell("low", UUID.randomUUID(), UUID.randomUUID(), List.of()), new Cell("high", UUID.randomUUID(), UUID.randomUUID(), List.of())));
    }
    List<ComparisonEngine.Observation> observations(Request request, String... numbers) {
        List<ComparisonEngine.Observation> list = new ArrayList<>(); int i = 0;
        for (var a : request.alternatives()) for (var c : a.cells()) {
            var values = new EnumMap<Metric, ComparisonEngine.Measurement>(Metric.class);
            for (var criterion : request.criteria()) {
                String value = numbers[i++]; values.put(criterion.metric(), new ComparisonEngine.Measurement(value == null ? null : n(value), "USD", null));
            }
            list.add(new ComparisonEngine.Observation(a.id(), c.contextId(), c.versionId(), c.evaluationId(), 1, values, List.of()));
        }
        return list;
    }

    Result calculate(Request r, String... values) { return engine.calculate(r, observations(r, values), "USD", 2030, 5, List.of()); }

    @Test
    void probabilitiesProduceExpectedRankingAndWorstContext() {
        var r = request(List.of(criterion(Metric.NPV, "100", "0", "100", null, null)));
        var result = calculate(r, "20", "100", "80", "60");
        assertEquals(List.of("A"), result.winnerIds());
        assertEquals(0, n("80").compareTo(result.alternatives().getFirst().score()));
        assertEquals(0, n("20").compareTo(result.alternatives().getFirst().worstScore()));
    }

    @Test
    void costsAreMinimizedAndScoresAreClamped() {
        var r = request(List.of(criterion(Metric.OPERATING_COST, "100", "100", "0", null, null)));
        var result = calculate(r, "-20", "-20", "150", "150");
        assertEquals(0, n("100").compareTo(result.alternatives().getFirst().score()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.alternatives().get(1).score()));
    }

    @Test
    void oneContextLimitExcludesHighScoringAlternative() {
        var r = request(List.of(criterion(Metric.NPV, "100", "0", "100", "30", null)));
        var result = calculate(r, "20", "100", "80", "60");
        assertEquals(List.of("B"), result.winnerIds()); assertFalse(result.alternatives().getFirst().eligible());
    }

    @Test
    void noEligibleAlternativeHasNoWinner() {
        var r = request(List.of(criterion(Metric.NPV, "100", "0", "100", "90", null)));
        assertEquals("NO_ELIGIBLE_ALTERNATIVE", calculate(r, "20", "100", "80", "60").status());
    }

    @Test
    void missingValueBlocksEntireRecommendationWithoutZeroImputation() {
        var r = request(List.of(criterion(Metric.NPV, "100", "0", "100", null, null)));
        var result = calculate(r, null, "100", "80", "60");
        assertEquals("BLOCKED", result.status()); assertTrue(result.winnerIds().isEmpty());
        assertNull(result.alternatives().get(1).rank()); assertNull(result.alternatives().getFirst().contexts().getFirst().values().getFirst().value());
    }

    @Test
    void tiesRemainTiesAndReferencesDoNotDependOnOtherAlternatives() {
        var r = request(List.of(criterion(Metric.NPV, "100", "0", "100", null, null)));
        var tie = calculate(r, "50", "50", "50", "50"); assertEquals(List.of("A", "B"), tie.winnerIds());
        assertEquals(0, tie.alternatives().getFirst().score().compareTo(calculate(r, "50", "50", "0", "1000").alternatives().getFirst().score()));
    }

    @Test
    void zeroWeightCriterionStillEnforcesLimits() {
        var r = request(List.of(criterion(Metric.NPV, "100", "0", "100", null, null), criterion(Metric.INVESTMENT, "0", "100", "0", null, "50")));
        assertEquals(List.of("B"), calculate(r, "100", "60", "100", "40", "50", "30", "50", "30").winnerIds());
    }

    @Test
    void incompatibleSourcesBlockRanking() {
        var r = request(List.of(criterion(Metric.NPV, "100", "0", "100", null, null)));
        assertEquals("BLOCKED", engine.calculate(r, observations(r, "50", "50", "50", "50"), "USD", 2030, 5, List.of("Currency mismatch")).status());
    }

    @Test
    void invalidWeightsProbabilitiesReferencesAndMatrixAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> engine.validate(request(List.of(criterion(Metric.NPV, "90", "0", "100", null, null)))));
        assertThrows(IllegalArgumentException.class, () -> engine.validate(request(List.of(criterion(Metric.NPV, "100", "0", "0", null, null)))));
        var r = request(List.of(criterion(Metric.NPV, "100", "0", "100", null, null)));
        assertThrows(IllegalArgumentException.class, () -> engine.validate(new Request(r.name(), r.scopeDescription(), true, List.of(new Context("low", "Low", n("90"))), r.alternatives(), r.criteria())));
        assertThrows(IllegalArgumentException.class, () -> engine.validate(new Request(r.name(), r.scopeDescription(), false, r.contexts(), r.alternatives(), r.criteria())));
        assertThrows(IllegalArgumentException.class, () -> engine.validate(new Request(r.name(), r.scopeDescription(), true, r.contexts(), List.of(new Alternative("A", "A", List.of()), alternative("B")), r.criteria())));
    }
}
