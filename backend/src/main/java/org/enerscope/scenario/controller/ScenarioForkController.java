package org.enerscope.scenario.controller;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.enerscope.scenario.service.ScenarioForkService;
import org.enerscope.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor
@RequestMapping("/projects/{projectId}/scenarios/{scenarioId}")
public class ScenarioForkController {
 private final ScenarioForkService service;
 public record Request(String name) {}
 public record Created(UUID id) {}
 @PostMapping("/copies") public ResponseEntity<ApiResponse<Created>> copy(@PathVariable UUID projectId,@PathVariable UUID scenarioId,
   @RequestHeader("If-Match") String token,@RequestBody Request request) {
  return Responses.ok("Scenario copied",new Created(service.fork(projectId,scenarioId,request.name(),token,true).getId()));
 }
 @PostMapping("/branches") public ResponseEntity<ApiResponse<Created>> branch(@PathVariable UUID projectId,@PathVariable UUID scenarioId,
   @RequestHeader("If-Match") String token,@RequestBody Request request) {
  return Responses.ok("Scenario branch created",new Created(service.fork(projectId,scenarioId,request.name(),token,false).getId()));
 }
}
