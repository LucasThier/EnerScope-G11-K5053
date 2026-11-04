package org.enerscope.scenario.service;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
/** Opt-in migration. Ordinary startup never rewrites historical associations. */
@Component @RequiredArgsConstructor
@ConditionalOnProperty(name="scenario.revisions.import-history",havingValue="true")
public class ScenarioRevisionInitializer implements ApplicationRunner {
 private final ScenarioRevisionImportService service;
 @Override public void run(ApplicationArguments arguments) {service.initialize();}
}
