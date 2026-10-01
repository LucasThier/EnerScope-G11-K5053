package org.enerscope.economic.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import jakarta.persistence.EntityManager;
import org.enerscope.economic.model.configuration.EconomicConfiguration;
import org.enerscope.money.MoneyAmount;
import org.enerscope.node.model.*;
import org.enerscope.node.model.extraction.Well;
import org.enerscope.node.model.enums.*;
import org.enerscope.organization.model.Organization;
import org.enerscope.project.model.Project;
import org.enerscope.session.model.Session;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.enerscope.version.model.Version;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.enerscope.economic.service.EconomicExample.*;

/** Run explicitly against a disposable PostgreSQL database; never silently skipped. */
@SpringBootTest(properties={"spring.flyway.enabled=true","spring.jpa.hibernate.ddl-auto=validate"})
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@Transactional
class PostgreSqlEconomicIT {
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        String url=System.getenv("ECONOMIC_TEST_DATABASE_URL");
        if(url==null || !url.contains("enerscope_economic_test")) throw new IllegalStateException("Set ECONOMIC_TEST_DATABASE_URL to a disposable enerscope_economic_test database");
        r.add("spring.datasource.url",()->url);
        r.add("spring.datasource.username",()->System.getenv().getOrDefault("ECONOMIC_TEST_DATABASE_USER","postgres"));
        r.add("spring.datasource.password",()->System.getenv().getOrDefault("ECONOMIC_TEST_DATABASE_PASSWORD",""));
    }
    @Autowired EntityManager em;
    @Autowired EconomicService service;
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired org.enerscope.jwt.JwtService jwt;
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void completeHttpExampleCreatesVersionNodesAndEvaluation() throws Exception {
        Organization organization=new Organization("HTTP example");em.persist(organization);
        Project project=new Project("HTTP example","Disposable fixture",organization);em.persist(project);em.flush();
        String token=jwt.generateAccessToken(User.fromJwtClaims(UUID.randomUUID(),"api@example.com","API","Admin",PlatformRole.ADMIN));
        var response=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/projects/"+project.getId()+"/version")
                .header("Authorization","Bearer "+token).contentType("application/json").content("{\"name\":\"HTTP economic example\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andReturn();
        String versionId=MAPPER.readTree(response.getResponse().getContentAsString()).path("data").path("id").asText();
        String config=MAPPER.writeValueAsString(configuration());
        for(int i=0;i<2;i++) {
            var node=(com.fasterxml.jackson.databind.node.ObjectNode)MAPPER.readTree(java.nio.file.Files.readString(java.nio.file.Path.of("src/test/resources/economic-examples/economic-well.json")));
            node.put("startupDate",i==0 ? "2031-01-01T00:00:00Z" : "2032-01-01T00:00:00Z");
            node.put("maxCollectionCapacity",i==0 ? 100 : 500);
            var created=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/version/"+versionId+"/node")
                    .header("Authorization","Bearer "+token).contentType("application/json").content(MAPPER.writeValueAsString(node)))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andReturn();
            String nodeId=MAPPER.readTree(created.getResponse().getContentAsString()).path("data").path("id").asText();
            config=config.replace((i==0 ? FIRST : SECOND).toString(),nodeId);
        }
        String economic="/projects/"+project.getId()+"/versions/"+versionId+"/economics";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(economic+"/configuration")
                .header("Authorization","Bearer "+token).contentType("application/json").content(config))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(economic+"/evaluations")
                .header("Authorization","Bearer "+token))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.snapshot.result.npv").value(169.20))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.snapshot.result.indicators.irr.rate").value(0.1483447840));
    }

    @Test void migrationsValidateAndEconomicSnapshotSurvivesReloadAndConfigurationChanges() throws Exception {
        var source=version(); List<org.enerscope.node.model.BaseNode> nodes=new ArrayList<>();
        Map<String,String> ids=new HashMap<>();
        for(var original:source.getNodeSnapshot()) {
            Well well=(Well)original; String old=well.getId().toString(); ReflectionTestUtils.setField(well,"id",null);
            well.setType(new NodeTypeData(VerticalEnum.EXTRACTION,StructuralRoleEnum.GENERATOR,NodeTypeEnum.WELL));
            well.setGraphData(new NodeGraphData(0d,0d,0d));well.setInvestmentCost(new InvestmentCost(new ArrayList<>()));
            well.setUpkeepCosts(MoneyAmount.of(0));well.setOperatingCosts(MoneyAmount.of(0));well.setDTMCost(MoneyAmount.of(0));well.setIdentityId(UUID.randomUUID());
            em.persist(well);ids.put(old,well.getId().toString());nodes.add(well);
        }
        Organization organization=new Organization("Economic integration");em.persist(organization);
        Project project=new Project("Economic integration","Disposable fixture",organization);
        Version version=new Version("Economic integration",null,nodes,List.of(),new ArrayList<>(),new ArrayList<>(),new ArrayList<>());
        em.persist(version);project.addVersion(version);em.persist(project);em.flush();
        var caller=User.fromJwtClaims(UUID.randomUUID(),"admin@example.com","Admin","Test",PlatformRole.ADMIN);
        var auth=new UsernamePasswordAuthenticationToken(caller.getId(),null,List.of());
        auth.setDetails(new Session("test",caller,Instant.now().plusSeconds(3600)));SecurityContextHolder.getContext().setAuthentication(auth);
        String json=MAPPER.writeValueAsString(configuration()); for(var entry:ids.entrySet()) json=json.replace(entry.getKey(),entry.getValue());
        EconomicConfiguration config=MAPPER.readValue(json,EconomicConfiguration.class);
        service.save(project.getId(),version.getId(),config);
        var evaluation=service.evaluate(project.getId(),version.getId());
        assertEquals(new BigDecimal("169.20"),evaluation.snapshot().result().npv());
        var changed=(com.fasterxml.jackson.databind.node.ObjectNode)MAPPER.valueToTree(config);changed.put("wacc",0);
        service.save(project.getId(),version.getId(),MAPPER.convertValue(changed,EconomicConfiguration.class));
        UUID projectId=project.getId(), versionId=version.getId();em.flush();em.clear();
        var loaded=service.getEvaluation(projectId,versionId,evaluation.id());
        assertEquals(new BigDecimal("169.20"),loaded.snapshot().result().npv());
        assertEquals(2, loaded.snapshot().schemaVersion());
        assertEquals(evaluation.snapshot().result().indicators(), loaded.snapshot().result().indicators());
        assertEquals(org.enerscope.economic.model.enums.IndicatorOrigin.STORED, loaded.snapshot().result().indicators().origin());
        assertEquals(5, loaded.snapshot().result().indicators().discountedPayback().period());
        assertEquals(0,new BigDecimal("0.1").compareTo(loaded.snapshot().configuration().wacc()));
        assertEquals(0,service.get(projectId,versionId).wacc().signum());
        assertEquals(1,service.list(projectId,versionId).size());
        // Check the legacy Result relation too: H2 previously hid its broken mapping.
        Version stored=em.find(Version.class,versionId);var result=new org.enerscope.simulator.Result(1);
        result.addAllResultPerNodes(List.of(new org.enerscope.simulator.ResultPerNode(nodes.getFirst().getId(),"Well",1,0,1)));
        stored.addResult(result);em.flush();em.clear();
        assertEquals(1,em.find(Version.class,versionId).getResults().getFirst().getResultPerNodes().size());
    }

    @Test void historicalIndicatorsAreDerivedWithoutUpdatingPersistedJson() throws Exception {
        Organization organization = new Organization("Historical indicators"); em.persist(organization);
        Version version = new Version("Historical indicators", null, List.of(), List.of(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        em.persist(version);
        Project project = new Project("Historical indicators", "Disposable fixture", organization);
        project.addVersion(version); em.persist(project);
        var result = new EconomicEngine(new EconomicValidator(), new EconomicIndicatorsCalculator()).calculate(configuration(), metrics());
        var snapshot = new org.enerscope.economic.dto.EvaluationDTO.Snapshot(1, configuration(), null, null, List.of(), result);
        com.fasterxml.jackson.databind.node.ObjectNode tree = MAPPER.valueToTree(snapshot);
        ((com.fasterxml.jackson.databind.node.ObjectNode)tree.path("result")).remove("indicators");
        String original = tree.toString();
        var stored = new org.enerscope.economic.model.EconomicEvaluation(version, original); em.persist(stored);
        // The current configuration deliberately has a different WACC.
        var current = mutate(c -> c.put("wacc", 0));
        em.persist(new org.enerscope.economic.model.EconomicConfigurationEntity(version, MAPPER.writeValueAsString(current)));
        em.flush();
        UUID projectId = project.getId(), versionId = version.getId(), evaluationId = stored.getId(); em.clear();
        var caller = User.fromJwtClaims(UUID.randomUUID(), "history@example.com", "History", "Admin", PlatformRole.ADMIN);
        var auth = new UsernamePasswordAuthenticationToken(caller.getId(), null, List.of());
        auth.setDetails(new Session("test", caller, Instant.now().plusSeconds(3600)));
        SecurityContextHolder.getContext().setAuthentication(auth);
        var single = service.getEvaluation(projectId, versionId, evaluationId);
        var listed = service.list(projectId, versionId).getFirst();
        assertEquals(single.snapshot(), listed.snapshot());
        assertEquals(1, single.snapshot().schemaVersion());
        assertEquals(org.enerscope.economic.model.enums.IndicatorOrigin.DERIVED_FROM_SNAPSHOT, single.snapshot().result().indicators().origin());
        assertEquals(5, single.snapshot().result().indicators().discountedPayback().period());
        assertEquals(new BigDecimal("0.1483447840"), single.snapshot().result().indicators().irr().rate());
        assertEquals(0, single.snapshot().configuration().wacc().compareTo(new BigDecimal("0.1")));
        em.flush(); em.clear();
        assertEquals(original, em.find(org.enerscope.economic.model.EconomicEvaluation.class, evaluationId).getSnapshotJson());
    }
}
