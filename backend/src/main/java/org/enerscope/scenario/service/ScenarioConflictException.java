package org.enerscope.scenario.service;
public class ScenarioConflictException extends RuntimeException {
 public ScenarioConflictException() {
  super("El escenario cambió desde otra pantalla. Conservá tus cambios y recargá la configuración antes de guardar.");
 }
}
