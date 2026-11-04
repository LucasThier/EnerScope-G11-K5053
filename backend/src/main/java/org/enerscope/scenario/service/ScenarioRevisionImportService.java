package org.enerscope.scenario.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.enerscope.economic.dto.FrozenEconomicInput;
import org.enerscope.economic.repository.EconomicJobRepository;
import org.enerscope.economic.service.EconomicInputCodec;
import org.enerscope.project.repository.ProjectRepository;
import org.enerscope.scenario.model.ScenarioRevision;
import org.enerscope.scenario.repository.ScenarioRevisionRepository;
import org.enerscope.version.repository.VersionRepository;
import org.enerscope.logging.AppLogger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Explicit migration of reconstructible historical inputs; stored JSON remains unchanged. */
@Service @RequiredArgsConstructor
public class ScenarioRevisionImportService {
 private final VersionRepository versions;
 private final ProjectRepository projects;
 private final ScenarioRevisionService service;
 private final ScenarioRevisionRepository revisions;
 private final EconomicJobRepository jobs;
 private final EconomicInputCodec codec;
 private final ObjectMapper mapper;
 private final AppLogger logger;
 @PersistenceContext private EntityManager em;
 @Transactional public void initialize() {
  for(var version:versions.findAll()) {
   if(!version.isActive() || projects.findIdByVersionId(version.getId()).isEmpty())continue;
   var scenario=service.ensure(version);
   for(var job:jobs.findByVersionIdOrderByCreatedAtDesc(version.getId())) {
    if(job.getScenarioRevisionId()!=null)continue;
    FrozenEconomicInput input;
    try {
     var tree=mapper.readTree(job.getInputJson());
     if(!tree.path("configuration").isObject() || !tree.path("nodes").isArray()
        || !tree.path("connections").isArray() || !tree.path("simulation").isObject())continue;
     input=mapper.treeToValue(tree,FrozenEconomicInput.class);
     if(input.configuration()==null || input.nodes()==null || input.simulation()==null)continue;
     for(var node:input.nodes()) if(!node.data().path("id").isTextual() || !node.data().path("identityId").isTextual())
      throw new IllegalArgumentException("Historical node has no stable identity");
     codec.thaw(input);
    } catch(com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException error) {
     logger.warn("Simulation {} retained as legacy: physical input cannot be reconstructed",job.getId());continue;
    }
    var content=new EconomicInputCodec.RevisionContent(input.configuration(),input.nodes(),input.connections());
    String hash=RevisionContentHash.hash(mapper.valueToTree(content));
    var revision=revisions.findFirstByScenarioIdAndContentHash(scenario.getId(),hash).orElse(null);
    if(revision==null)revision=revisions.saveAndFlush(new ScenarioRevision(scenario.getId(),revisions.maximumNumber(scenario.getId())+1,
       null,null,null,null,mapper.valueToTree(content).toString(),hash));
    em.createNativeQuery("UPDATE economic_job SET scenario_revision_id=:revision WHERE id=:id AND scenario_revision_id IS NULL")
      .setParameter("revision",revision.getId()).setParameter("id",job.getId()).executeUpdate();
    if(job.getEvaluationId()!=null)em.createNativeQuery("UPDATE economic_evaluation SET scenario_revision_id=:revision WHERE id=:id AND scenario_revision_id IS NULL")
      .setParameter("revision",revision.getId()).setParameter("id",job.getEvaluationId()).executeUpdate();
    logger.info("Historical simulation {} linked to imported revision {}",job.getId(),revision.getId());
   }
  }
 }
}
