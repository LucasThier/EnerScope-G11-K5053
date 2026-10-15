package org.enerscope.simulation.service;

import org.enerscope.money.MoneyAmount;
import org.enerscope.node.model.GraphPosition;
import org.enerscope.node.model.InvestmentCostComponent;
import org.enerscope.node.model.NodeGraphData;
import org.enerscope.node.model.enums.CostBasisEnum;
import org.enerscope.node.model.extraction.Well;
import org.enerscope.probabilistic.ConstantValue;
import org.enerscope.probabilistic.UniformDistributionCase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.enerscope.simulation.SimulationFixtures.well;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeFingerprintTest {

    private static List<String> differences(Well left, Well right) {
        return NodeFingerprint.differences(NodeFingerprint.of(left), NodeFingerprint.of(right));
    }

    @Test
    void branchCopiesOfTheSameNodeAreEqual() {
        // Different row ids, identities and type-data rows, same parameters.
        assertTrue(differences(well("Pozo", 100f), well("Pozo", 100f)).isEmpty());
    }

    @Test
    void positionsAreNotAChangeToTheModel() {
        Well left = well("Pozo", 100f);
        Well right = well("Pozo", 100f);
        left.setGraphData(new NodeGraphData(new GraphPosition(0.0, 0.0), null));
        right.setGraphData(new NodeGraphData(new GraphPosition(500.0, 80.0), null));
        right.setIdentityId(UUID.randomUUID());

        assertTrue(differences(left, right).isEmpty());
    }

    @Test
    void reportsEachChangedParameterByName() {
        Well left = well("Pozo", 100f);
        Well right = well("Pozo renombrado", 120f);
        right.setOperatingCosts(MoneyAmount.of(999));

        assertEquals(List.of("maxCollectionCapacity", "name", "operatingCosts"), differences(left, right));
    }

    @Test
    void moneyComparesByValueNotByScale() {
        Well left = well("Pozo", 100f);
        Well right = well("Pozo", 100f);
        left.setUpkeepCosts(MoneyAmount.of("10"));
        right.setUpkeepCosts(MoneyAmount.of("10.00"));

        assertTrue(differences(left, right).isEmpty());
    }

    @Test
    void declineCurvesCompareByKindAndParameters() {
        Well left = well("Pozo", 100f);
        Well right = well("Pozo", 100f);
        left.setDeclineCurve(new ConstantValue(5f));
        right.setDeclineCurve(new ConstantValue(5f));
        assertTrue(differences(left, right).isEmpty());

        right.setDeclineCurve(new UniformDistributionCase(4f, 6f));
        assertEquals(List.of("declineCurve"), differences(left, right));
    }

    @Test
    void investmentComponentsAreCompared() {
        Well left = well("Pozo", 100f);
        Well right = well("Pozo", 100f);
        right.getInvestmentCost().getComponents()
                .add(new InvestmentCostComponent("Ampliación", MoneyAmount.of(50), CostBasisEnum.FLAT));

        assertEquals(List.of("investmentCost"), differences(left, right));
    }
}
