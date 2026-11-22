package org.enerscope.scenario.service;

public class ScenarioNameTakenException extends ScenarioRuleConflictException {
    public ScenarioNameTakenException(String name) {
        super("Ya existe un escenario llamado «" + name + "» en esta versión. Elegí otro nombre (no se distinguen mayúsculas).");
    }
}
