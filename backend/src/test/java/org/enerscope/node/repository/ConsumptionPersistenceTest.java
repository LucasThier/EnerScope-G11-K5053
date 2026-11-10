package org.enerscope.node.repository;

import org.enerscope.node.model.*;
import org.enerscope.node.model.enums.*;
import org.enerscope.node.model.export.*;
import org.enerscope.node.service.NodeCloner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class ConsumptionPersistenceTest {
 @Autowired TestEntityManager em;
 @Test
 void consumersAndIndependentCopiesPersistAllDemands() {
  var internal = new InternalConsumption();
  internal.setConsumptionSummer(10); internal.setConsumptionAutumn(20);
  internal.setConsumptionWinter(30); internal.setConsumptionSpring(40);
  var industrial = new IndustrialConsumption(); industrial.setConsumption(50);
  for (BaseNode node : List.of(internal, industrial)) {
   node.setName("Consumer"); node.setState(NodeStateEnum.RUNNING); node.setIdentityId(UUID.randomUUID());
   node.setType(new NodeTypeData(VerticalEnum.INTERNAL_CONSUMPTION, StructuralRoleEnum.SINK,
    node == internal ? NodeTypeEnum.INTERNAL_CONSUMPTION : NodeTypeEnum.INDUSTRIAL_CONSUMPTION));
   node.setGraphData(new NodeGraphData()); node.setInvestmentCost(new InvestmentCost(new ArrayList<>()));
   em.persistAndFlush(node);
   var copy = NodeCloner.cloneNode(node); em.persistAndFlush(copy);
   var id = node.getId(); var copyId = copy.getId(); em.clear();
   var saved = em.find(node.getClass(), id); var savedCopy = em.find(node.getClass(), copyId);
   assertEquals(node.getType().getNodeType(), saved.getType().getNodeType());
   assertNotEquals(saved.getType().getId(), savedCopy.getType().getId());
   if (savedCopy instanceof InternalConsumption c) {
    assertEquals(10,c.getConsumptionSummer()); assertEquals(20,c.getConsumptionAutumn());
    assertEquals(30,c.getConsumptionWinter()); assertEquals(40,c.getConsumptionSpring());
    c.setConsumptionWinter(99); em.flush();
    assertEquals(30, ((InternalConsumption)saved).getConsumptionWinter());
   } else {
    assertEquals(50, ((IndustrialConsumption)savedCopy).getConsumption());
    ((IndustrialConsumption)savedCopy).setConsumption(99); em.flush();
    assertEquals(50, ((IndustrialConsumption)saved).getConsumption());
   }
  }
 }
}
