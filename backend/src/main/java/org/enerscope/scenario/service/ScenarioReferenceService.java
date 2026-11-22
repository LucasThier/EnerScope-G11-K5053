package org.enerscope.scenario.service;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.enerscope.economic.repository.EconomicConfigurationRepository;
import org.enerscope.scenario.repository.EconomicDraftRepository;
import org.enerscope.scenario.repository.ScenarioDraftRepository;
import org.enerscope.node.model.*;
import org.enerscope.node.model.enums.ChangeTypeEnum;
import org.enerscope.node.service.NodeCloner;
import org.enerscope.version.model.Version;
import org.springframework.stereotype.Service;
/**
 * Keep version-local references coherent when the existing map clones an inherited node: the connections of the version
 * and, for every scenario of it, its configuration and its drafts ({@code scenario_draft} and the legacy
 * {@code economic_draft}). All of them go through {@link ScenarioConfigurationService#remapReferences}.
 */
@Service
@RequiredArgsConstructor
public class ScenarioReferenceService {
 private final EconomicConfigurationRepository configurations;
 private final ScenarioDraftRepository scenarioDrafts;
 private final EconomicDraftRepository economicDrafts;
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
  // Every scenario of the version keeps pointing at the node it referenced: all their configurations are remapped.
  configurations.findAllByVersionId(version.getId()).forEach(config->{
   try {
    var tree=mapper.readTree(config.getConfigurationJson());
    ScenarioConfigurationService.remapReferences(tree,Map.of(oldId.toString(),newId.toString()));
    config.replace(mapper.writeValueAsString(tree));
   }catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("Invalid stored economic configuration",e);}
  });
  // Pending drafts point at nodes too: the same rewrite, for the draft of every scenario of the version.
  var ids=Map.of(oldId.toString(),newId.toString());
  scenarioDrafts.findAllByVersionId(version.getId()).forEach(draft->{
   String next=remapped(draft.getDraftJson(),ids);
   if(next!=null) draft.replace(next);
  });
  economicDrafts.findAllByVersionId(version.getId()).forEach(draft->{
   String next=remapped(draft.getDraftJson(),ids);
   if(next!=null) draft.replace(next);
  });
 }

 /** The draft JSON with its node references rewritten, or null when there is nothing to change (blank or no reference). */
 private String remapped(String json,Map<String,String> ids) {
  if(json==null || json.isBlank()) return null;
  try {
   var tree=mapper.readTree(json);
   var before=tree.deepCopy();
   ScenarioConfigurationService.remapReferences(tree,ids);
   return tree.equals(before) ? null : mapper.writeValueAsString(tree);
  }catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("Invalid stored scenario draft",e);}
 }
}
