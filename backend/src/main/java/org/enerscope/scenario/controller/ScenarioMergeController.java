package org.enerscope.scenario.controller;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.enerscope.scenario.service.*;
import org.enerscope.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor
@RequestMapping("/projects/{projectId}/scenarios/{scenarioId}/merge")
public class ScenarioMergeController {
 private final ScenarioMergeService service;
 @PostMapping public ResponseEntity<ApiResponse<ScenarioMergeService.View>> prepare(@PathVariable UUID projectId,@PathVariable UUID scenarioId) {
  return Responses.ok("Operational merge prepared",service.prepare(projectId,scenarioId));
 }
 @GetMapping public ResponseEntity<ApiResponse<ScenarioMergeService.View>> get(@PathVariable UUID projectId,@PathVariable UUID scenarioId) {
  return Responses.ok("Prepared operational merge",service.get(projectId,scenarioId));
 }
 @GetMapping("/history") public ResponseEntity<ApiResponse<List<ScenarioMergeService.View>>> history(@PathVariable UUID projectId,@PathVariable UUID scenarioId) {
  return Responses.ok("Operational merge history",service.history(projectId,scenarioId));
 }
 @PutMapping public ResponseEntity<ApiResponse<ScenarioMergeService.View>> resolve(@PathVariable UUID projectId,@PathVariable UUID scenarioId,
   @RequestHeader("If-Match") String token,@RequestBody Map<String,OperationalThreeWayMerge.Choice> choices) {
  return Responses.ok("Operational merge resolutions saved",service.resolve(projectId,scenarioId,token,choices));
 }
}
