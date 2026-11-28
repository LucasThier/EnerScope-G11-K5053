package org.enerscope.scenario.service;

public class ScenarioLimitReachedException extends ScenarioRuleConflictException {
    public ScenarioLimitReachedException(int limit) {
        super("Una versión admite como máximo " + limit + " escenarios. Eliminá alguno antes de crear otro.");
    }
}
