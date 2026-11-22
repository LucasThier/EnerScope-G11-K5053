package org.enerscope.scenario.service;

public class ScenarioDeletionRefusedException extends ScenarioRuleConflictException {
    private ScenarioDeletionRefusedException(String message) {
        super(message);
    }

    public static ScenarioDeletionRefusedException lastScenario() {
        return new ScenarioDeletionRefusedException("No se puede eliminar el único escenario de la versión.");
    }

    public static ScenarioDeletionRefusedException simulationInProgress() {
        return new ScenarioDeletionRefusedException("El escenario tiene una simulación en cola o en curso. Esperá a que termine para eliminarlo.");
    }
}
