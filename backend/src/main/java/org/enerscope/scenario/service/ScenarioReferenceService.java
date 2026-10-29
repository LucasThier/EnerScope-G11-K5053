package org.enerscope.scenario.service;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.enerscope.economic.repository.EconomicConfigurationRepository;
import org.enerscope.node.model.*;
import org.enerscope.node.model.enums.ChangeTypeEnum;
import org.enerscope.node.service.NodeCloner;
import org.enerscope.version.model.Version;
import org.springframework.stereotype.Service;
/** Keep version-local references coherent when the existing map clones an inherited node. */
@Service
@RequiredArgsConstructor
public class ScenarioReferenceService {
 private final EconomicConfigurationRepository configurations;
 private final ObjectMapper mapper;
 @PersistenceContext
 private EntityManager em;

 public void remap(Version version,UUID oldId,UUID newId) {
  if(oldId.equals(newId))return;
  for(int i=0;i<version.getConnectionSnapshot().size();i++) {
   var old=version.getConnectionSnapshot().get(i);
   if(!oldId.equals(old.getFromNodeId()) && !oldId.equals(old.getToNodeId()))continue;
   var next=NodeCloner.cloneConnection(old);
   if(oldId.equals(next.getFromNodeId()))next.setFromNodeId(newId);
   if(oldId.equals(next.getToNodeId()))next.setToNodeId(newId);
   em.persist(next);version.getConnectionSnapshot().set(i,next);
   version.getConnectionChanges().add(new ConnectionChange(ChangeTypeEnum.EDIT,old,next));
  }
  configurations.findByVersionId(version.getId()).ifPresent(config->{
   try {
    var tree=mapper.readTree(config.getConfigurationJson());
    ScenarioConfigurationService.remapReferences(tree,Map.of(oldId.toString(),newId.toString()));
    config.replace(mapper.writeValueAsString(tree));
   }catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("Invalid stored economic configuration",e);}
  });
 }
}
