package org.enerscope.scenario.service;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.enerscope.project.service.ProjectService;
import org.enerscope.version.dto.VersionDTO;
import org.enerscope.version.model.Version;
import org.enerscope.logging.AppLogger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Fork only the published state; source drafts and results are never copied. */
@Service @RequiredArgsConstructor @Transactional
public class ScenarioForkService {
 private final ScenarioRevisionService revisions;
 private final ProjectService projects;
 private final AppLogger logger;
 public Version fork(UUID project,UUID source,String name,String token,boolean independent) {
  if(name==null || name.isBlank() || name.length()>320)throw new IllegalArgumentException("Scenario name must contain 1 to 320 characters");
  var status=revisions.status(project,source);
  if(!status.token().equals(token))throw new ScenarioConflictException();
  var created=projects.saveVersion(project,new VersionDTO(name.trim(),source));
  if(independent)created.setParentVersion(null);
  logger.info("Scenario {} created from revision {} as {}",created.getId(),status.revisionId(),independent?"copy":"branch");
  return created;
 }
}
