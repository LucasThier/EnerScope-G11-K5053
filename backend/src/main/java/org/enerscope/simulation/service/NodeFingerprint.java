package org.enerscope.simulation.service;

import org.enerscope.common.BaseEntity;
import org.enerscope.money.MoneyAmount;
import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.InvestmentCost;
import org.enerscope.node.model.InvestmentCostComponent;
import org.enerscope.probabilistic.ProbabilisticDistribution;
import org.hibernate.Hibernate;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The parameters of a node that matter to a simulation or a cost, flattened to
 * comparable values, so two versions' copies of the same node can be told apart
 * field by field.
 *
 * <p>Read by reflection over the concrete class up to {@link BaseNode}, the way
 * {@code VersionService.cloneNodeForBranch} copies nodes, so a field added to a
 * node type is compared without touching this class. Left out on purpose: the
 * ids and audit fields of {@link BaseEntity} (every branch copy has new ones),
 * {@code identityId} (the key the comparison matches on) and {@code graphData}
 * (positions on the canvas or the map are presentation, not a change to the
 * model).</p>
 */
final class NodeFingerprint {

    private static final Set<String> IGNORED = Set.of("identityId", "graphData");

    private NodeFingerprint() {}

    static Map<String, Object> of(BaseNode node) {
        Map<String, Object> values = new TreeMap<>();
        for (Class<?> type = node.getClass(); type != null && type != BaseEntity.class && type != Object.class;
             type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || IGNORED.contains(field.getName())
                        || field.isSynthetic()) {
                    continue;
                }
                values.put(field.getName(), comparable(read(field, node)));
            }
        }
        return values;
    }

    /** Names of the fields whose values differ between two fingerprints, sorted. */
    static List<String> differences(Map<String, Object> left, Map<String, Object> right) {
        Set<String> keys = new TreeSet<>(left.keySet());
        keys.addAll(right.keySet());
        List<String> changed = new ArrayList<>();
        for (String key : keys) {
            if (!Objects.equals(left.get(key), right.get(key))) {
                changed.add(key);
            }
        }
        return changed;
    }

    private static Object read(Field field, Object target) {
        try {
            field.setAccessible(true);
            return field.get(target);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot read node field " + field.getName(), e);
        }
    }

    private static Object comparable(Object value) {
        if (value instanceof MoneyAmount money) {
            return normalized(money.value());
        }
        if (value instanceof ProbabilisticDistribution distribution) {
            return distribution.distributionType() + "(" + distribution.firstParameter() + ","
                    + distribution.secondParameter() + ")";
        }
        if (value instanceof InvestmentCost investment) {
            List<String> components = new ArrayList<>();
            if (investment.getComponents() != null) {
                for (InvestmentCostComponent component : investment.getComponents()) {
                    components.add(component.getName() + ":" + component.getCostBasis() + ":"
                            + (component.getAmount() == null ? null : normalized(component.getAmount().value())));
                }
            }
            components.sort(null);
            return components;
        }
        if (value instanceof BaseEntity entity) {
            // Any other owned entity (the node's type data) compares by its own
            // fields, not by identity: each branch copy is a separate row.
            return fieldsOf(Hibernate.unproxy(entity));
        }
        return value;
    }

    private static Map<String, Object> fieldsOf(Object entity) {
        Map<String, Object> values = new TreeMap<>();
        for (Field field : entity.getClass().getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
                values.put(field.getName(), read(field, entity));
            }
        }
        return values;
    }

    private static BigDecimal normalized(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros();
    }
}
