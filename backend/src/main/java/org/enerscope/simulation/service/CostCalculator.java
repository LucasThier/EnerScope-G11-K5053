package org.enerscope.simulation.service;

import org.enerscope.money.MoneyAmount;
import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.InvestmentCost;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Cost of a model over a simulation horizon, node by node.
 *
 * <ul>
 *   <li><b>CAPEX</b> — the node's investment cost components, counted once.</li>
 *   <li><b>OPEX</b> — the monthly operating cost for the time the node operates
 *       inside the horizon.</li>
 *   <li><b>Upkeep</b> — the cost of one maintenance stop times the stops that fall
 *       inside that time.</li>
 * </ul>
 *
 * <p>"The time the node operates" uses the simulator's own calendar, so a cost
 * and the production it is compared with describe the same period: the horizon
 * is {@code years × 365} days and a node stops at {@code lifespanInMonths × 30}
 * days (see {@code SimBaseNode.checkLifeSpan}). {@code BaseNode.CalculateTotalCost}
 * is not reused because it costs the whole lifespan regardless of the horizon,
 * so a 30-year asset in a 10-year comparison would be charged for 20 years
 * nobody simulated.</p>
 */
@Component
public class CostCalculator {

    static final int DAYS_PER_MONTH = 30;
    static final int DAYS_PER_YEAR = 365;

    /** The cost of one node over a horizon. Amounts carry a scale of 2. */
    public record NodeCost(BaseNode node, BigDecimal capex, BigDecimal opex, BigDecimal upkeep) {
        public BigDecimal total() {
            return capex.add(opex).add(upkeep);
        }
    }

    public List<NodeCost> costOf(List<BaseNode> nodes, int horizonYears) {
        return nodes.stream().map(node -> costOf(node, horizonYears)).toList();
    }

    public NodeCost costOf(BaseNode node, int horizonYears) {
        if (horizonYears < 1) {
            throw new IllegalArgumentException("The cost horizon must be at least one year");
        }
        int days = operatingDays(node, horizonYears);
        BigDecimal opex = amount(node.getOperatingCosts())
                .multiply(BigDecimal.valueOf(days))
                .divide(BigDecimal.valueOf(DAYS_PER_MONTH), 2, RoundingMode.HALF_UP);
        BigDecimal upkeep = amount(node.getUpkeepCosts())
                .multiply(BigDecimal.valueOf(maintenanceStops(node, days)));
        return new NodeCost(node, scaled(investment(node)), scaled(opex), scaled(upkeep));
    }

    /** Days the node operates inside the horizon: until its lifespan ends, or the horizon does. */
    static int operatingDays(BaseNode node, int horizonYears) {
        long lifespanDays = (long) Math.max(0, node.getLifespanInMonths()) * DAYS_PER_MONTH;
        long horizonDays = (long) horizonYears * DAYS_PER_YEAR;
        return (int) Math.min(lifespanDays, horizonDays);
    }

    /** Maintenance stops in {@code days} of operation; none when the node has no interval. */
    static int maintenanceStops(BaseNode node, int days) {
        int interval = node.getMaintenanceIntervalInDays();
        return interval <= 0 ? 0 : days / interval;
    }

    private static BigDecimal investment(BaseNode node) {
        InvestmentCost investmentCost = node.getInvestmentCost();
        if (investmentCost == null || investmentCost.getComponents() == null
                || investmentCost.getComponents().isEmpty()) {
            return BigDecimal.ZERO;
        }
        return node.CalculateInvestmentCost().value();
    }

    private static BigDecimal amount(MoneyAmount money) {
        return money == null ? BigDecimal.ZERO : money.value();
    }

    private static BigDecimal scaled(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
