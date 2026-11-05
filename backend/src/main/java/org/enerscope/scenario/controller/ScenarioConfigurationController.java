package org.enerscope.scenario.controller;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.enerscope.scenario.dto.ScenarioConfigurationDTO;
import org.enerscope.scenario.dto.ScenarioConfigurationDTO.*;
import org.enerscope.scenario.service.ScenarioConfigurationService;
import org.enerscope.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor
@RequestMapping("/projects/{projectId}/versions")
public class ScenarioConfigurationController {
 private final ScenarioConfigurationService service;
 @GetMapping("/{versionId}/scenario-configuration")
 public ResponseEntity<ApiResponse<ScenarioConfigurationDTO>> get(@PathVariable UUID projectId,@PathVariable UUID versionId) {
  return Responses.ok("Scenario configuration",service.get(projectId,versionId));
 }
 @PutMapping("/{versionId}/scenario-configuration")
 public ResponseEntity<ApiResponse<ScenarioConfigurationDTO>> save(@PathVariable UUID projectId,@PathVariable UUID versionId,@RequestBody SaveRequest request) {
  return Responses.ok("Scenario saved",service.save(projectId,versionId,request));
 }
 @DeleteMapping("/{versionId}/scenario-configuration/draft")
 public ResponseEntity<ApiResponse<ScenarioConfigurationDTO>> discard(@PathVariable UUID projectId,@PathVariable UUID versionId,@RequestHeader("If-Match") String token) {
  return Responses.ok("Scenario draft discarded",service.discard(projectId,versionId,token));
 }
 @GetMapping("/simulation-readiness")
 public ResponseEntity<ApiResponse<List<Readiness>>> readiness(@PathVariable UUID projectId) {
  return Responses.ok("Simulation readiness",service.readiness(projectId));
 }
}
