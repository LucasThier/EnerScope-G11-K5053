package org.enerscope.scenario.service;

import java.util.*;
import org.enerscope.node.model.*;
import org.enerscope.node.model.enums.NodeStateEnum;
import org.enerscope.version.model.Version;

/** Current scenario projection; stored snapshots and historical evaluations remain intact. */
public final class CurrentScenarioNodes {
 private CurrentScenarioNodes() {}

 public static boolean includes(BaseNode node) {
  return node.getState() != NodeStateEnum.REMOVED;
 }

 public static List<BaseNode> nodes(Version version) {
  return version.getNodeSnapshot() == null ? List.of() : version.getNodeSnapshot().stream().filter(CurrentScenarioNodes::includes).toList();
 }

 public static List<NodeConnection> connections(Version version) {
  Set<UUID> removed = new HashSet<>();
  if(version.getNodeSnapshot() != null) version.getNodeSnapshot().stream().filter(node -> !includes(node)).forEach(node -> removed.add(node.getId()));
  return version.getConnectionSnapshot() == null ? List.of() : version.getConnectionSnapshot().stream()
    .filter(edge -> !removed.contains(edge.getFromNodeId()) && !removed.contains(edge.getToNodeId())).toList();
 }
}
