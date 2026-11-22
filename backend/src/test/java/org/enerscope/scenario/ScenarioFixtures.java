package org.enerscope.scenario;

import jakarta.persistence.EntityManager;
import org.enerscope.scenario.model.Scenario;
import org.enerscope.version.model.Version;

/** Test data written straight through the EntityManager must hold the default scenario the services now read by. */
public final class ScenarioFixtures {
    private ScenarioFixtures() {}

    public static Scenario defaultScenario(EntityManager em, Version version) {
        Scenario scenario = new Scenario(version, Scenario.DEFAULT_NAME, true);
        em.persist(scenario);
        return scenario;
    }
}
