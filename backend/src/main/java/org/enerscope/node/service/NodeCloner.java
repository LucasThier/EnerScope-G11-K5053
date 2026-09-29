package org.enerscope.node.service;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.enerscope.common.BaseEntity;
import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.InvestmentCost;
import org.enerscope.node.model.InvestmentCostComponent;
import org.enerscope.node.model.NodeConnection;
import org.enerscope.node.model.NodeGraphData;
import org.enerscope.node.model.NodeTypeData;

/**
 * Clones nodes/connections into fresh rows that keep the same
 * {@code identityId} but a new {@code id}. A version must never mutate a
 * node/connection row it inherited from its parent in place — that row is
 * still referenced by the parent's (and possibly a sibling's) snapshot, so
 * mutating it corrupts every other version sharing the reference. Any edit of
 * an entity a version doesn't already own exclusively must clone-on-write
 * instead.
 *
 * <p>Static/stateless on purpose: both {@code VersionService} and
 * {@code VersionConflictService} need this, and neither has to declare it as
 * a Spring dependency (or mock it in tests) to get real behavior.
 */
public final class NodeCloner {

    private NodeCloner() {
    }

    /** Instantiates a new row of the same concrete type as {@code source}, with {@code source}'s data. */
    public static BaseNode cloneNode(BaseNode source) {
        try {
            BaseNode clone = source.getClass().getDeclaredConstructor().newInstance();
            clone.setIdentityId(source.getIdentityId());
            copyNodeData(clone, source);
            return clone;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot clone node of type " + source.getClass().getSimpleName(), e);
        }
    }

    /**
     * Copies every data field of {@code source} onto {@code target} (same
     * concrete type required). The owned {@code type}/{@code investmentCost}/
     * {@code graphData} associations are deep-copied into fresh rows rather
     * than shared, since both are {@code orphanRemoval=true}. {@code identityId}
     * (and every {@link BaseEntity} field) is left untouched on the target.
     */
    public static void copyNodeData(BaseNode target, BaseNode source) {
        if (!target.getClass().equals(source.getClass())) {
            throw new IllegalStateException("Cannot copy data between different node types: "
                    + target.getClass().getSimpleName() + " and " + source.getClass().getSimpleName());
        }

        target.setType(cloneTypeData(source.getType()));
        target.setInvestmentCost(cloneInvestmentCost(source.getInvestmentCost()));
        target.setGraphData(cloneGraphData(source.getGraphData()));

        Set<String> handled = Set.of("identityId", "type", "investmentCost", "graphData");
        for (Class<?> type = source.getClass(); type != null && BaseEntity.class.isAssignableFrom(type)
                && !type.equals(BaseEntity.class); type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || handled.contains(field.getName())) {
                    continue;
                }
                field.setAccessible(true);
                try {
                    field.set(target, field.get(source));
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("Cannot copy field " + field.getName(), e);
                }
            }
        }
    }

    /** Instantiates a new connection row with {@code source}'s identity and endpoints. */
    public static NodeConnection cloneConnection(NodeConnection source) {
        NodeConnection clone = new NodeConnection();
        clone.setIdentityId(source.getIdentityId());
        clone.setFromNodeId(source.getFromNodeId());
        clone.setToNodeId(source.getToNodeId());
        return clone;
    }

    private static NodeTypeData cloneTypeData(NodeTypeData source) {
        return source == null ? null : new NodeTypeData(source.getVertical(), source.getRole(), source.getNodeType());
    }

    private static NodeGraphData cloneGraphData(NodeGraphData source) {
        return source == null ? null
                : new NodeGraphData(source.getXPosition(), source.getYPosition(), source.getCoordinates());
    }

    private static InvestmentCost cloneInvestmentCost(InvestmentCost source) {
        if (source == null) {
            return null;
        }
        List<InvestmentCostComponent> components = source.getComponents() == null
                ? new ArrayList<>()
                : source.getComponents().stream()
                        .map(c -> new InvestmentCostComponent(c.getName(), c.getAmount(), c.getCostBasis()))
                        .collect(Collectors.toCollection(ArrayList::new));
        return new InvestmentCost(components);
    }
}
