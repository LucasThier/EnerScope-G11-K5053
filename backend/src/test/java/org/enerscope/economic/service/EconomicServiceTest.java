package org.enerscope.economic.service;

import java.time.Instant;
import java.util.*;
import org.enerscope.common.*;
import org.enerscope.economic.model.*;
import org.enerscope.economic.repository.*;
import org.enerscope.logging.AppLogger;
import org.enerscope.organization.model.Organization;
import org.enerscope.project.model.*;
import org.enerscope.project.model.enums.*;
import org.enerscope.project.repository.ProjectRepository;
import org.enerscope.session.model.Session;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.enerscope.economic.service.EconomicExample.*;

class EconomicServiceTest {
    final ProjectRepository projects=mock(ProjectRepository.class);
    final EconomicConfigurationRepository configs=mock(EconomicConfigurationRepository.class);
    final EconomicEvaluationRepository evaluations=mock(EconomicEvaluationRepository.class);
    final EconomicService service=new EconomicService(projects,configs,evaluations,new EconomicValidator(),new EconomicEngine(new EconomicValidator()),
            new EconomicSimulationAdapter(),MAPPER,mock(AppLogger.class));
    final UUID projectId=UUID.randomUUID(); final varHolder holder=new varHolder();
    static class varHolder { final org.enerscope.version.model.Version version=version(); }
    Project project; User user;
    @BeforeEach void setup() {
        user=User.fromJwtClaims(UUID.randomUUID(),"reader@example.com","Reader","User");
        project=new Project("Example","Example",new Organization("Example"));project.addVersion(holder.version);
        when(projects.findById(projectId)).thenReturn(Optional.of(project));
        authenticate(user);
    }
    private void authenticate(User caller) {
        var token=new UsernamePasswordAuthenticationToken(caller.getId(),null,List.of());
        token.setDetails(new Session("test",caller,Instant.now().plusSeconds(3600)));
        SecurityContextHolder.getContext().setAuthentication(token);
    }
    private void grant(ProjectMemberPermission... permissions) {
        ProjectMember member=new ProjectMember(user,project);
        member.addRole(new ProjectMemberRole("Custom",ProjectMemberType.EDITOR,Set.of(permissions)));project.addMember(member);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void outsiderCannotReadOrWriteConfiguration() {
        assertThrows(ForbiddenException.class,()->service.get(projectId,holder.version.getId()));
        assertThrows(ForbiddenException.class,()->service.save(projectId,holder.version.getId(),configuration()));
        verifyNoInteractions(configs);
    }
    @Test void viewerCanReadButCannotEvaluate() throws Exception {
        grant(ProjectMemberPermission.VIEW_PROJECT);
        when(configs.findByVersionId(holder.version.getId())).thenReturn(Optional.of(new EconomicConfigurationEntity(holder.version,MAPPER.writeValueAsString(configuration()))));
        assertEquals(configuration(),service.get(projectId,holder.version.getId()));
        assertThrows(ForbiddenException.class,()->service.evaluate(projectId,holder.version.getId()));
    }
    @Test void editorCanSaveValidatedConfiguration() {
        grant(ProjectMemberPermission.EDIT_PROJECT);
        assertEquals(configuration(),service.save(projectId,holder.version.getId(),configuration()));
        verify(configs).save(any(EconomicConfigurationEntity.class));
    }
    @Test void adminStillCannotAddressVersionInAnotherProject() {
        user.updatePlatformRole(PlatformRole.ADMIN);
        assertThrows(EntityNotFoundException.class,()->service.get(projectId,UUID.randomUUID()));
        verifyNoInteractions(configs);
    }
    @Test void foreignNodeIsRejectedBeforePersistence() {
        grant(ProjectMemberPermission.EDIT_PROJECT);
        var c=mutate(t->((com.fasterxml.jackson.databind.node.ObjectNode)t.withArray("nodeProfiles").get(0)).put("nodeId",UUID.randomUUID().toString()));
        assertThrows(IllegalArgumentException.class,()->service.save(projectId,holder.version.getId(),c));
        verify(configs,never()).save(any());
    }
    @Test void evaluationIsScopedToVersion() {
        grant(ProjectMemberPermission.VIEW_PROJECT);UUID evaluation=UUID.randomUUID();
        assertThrows(EntityNotFoundException.class,()->service.getEvaluation(projectId,holder.version.getId(),evaluation));
        verify(evaluations).findByIdAndVersionId(evaluation,holder.version.getId());
    }
}
