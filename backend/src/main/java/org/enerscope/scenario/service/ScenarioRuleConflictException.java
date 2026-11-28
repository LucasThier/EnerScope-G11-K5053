package org.enerscope.scenario.service;

/** A scenario rule that the current state refuses (HTTP 409): repeated name, limit reached, deletion refused. */
public abstract class ScenarioRuleConflictException extends RuntimeException {
    protected ScenarioRuleConflictException(String message) {
        super(message);
    }
}
