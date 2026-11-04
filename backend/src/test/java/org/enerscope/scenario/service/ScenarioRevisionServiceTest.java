package org.enerscope.scenario.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.enerscope.common.EntityNotFoundException;
import org.enerscope.economic.repository.EconomicConfigurationRepository;
import org.enerscope.economic.service.EconomicInputCodec;
import org.enerscope.logging.AppLogger;
import org.enerscope.project.repository.ProjectRepository;
import org.enerscope.scenario.model.*;
import org.enerscope.scenario.repository.*;
import org.enerscope.version.model.Version;
import org.enerscope.version.repository.VersionRepository;
import org.enerscope.version.service.VersionAccessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class ScenarioRevisionServiceTest {
    private final ScenarioRepository scenarios=mock(ScenarioRepository.class);
    private final ScenarioRevisionRepository revisions=mock(ScenarioRevisionRepository.class);
    private final ProjectRepository projects=mock(ProjectRepository.class);
    private final VersionRepository versions=mock(VersionRepository.class);
    private final EconomicConfigurationRepository configurations=mock(EconomicConfigurationRepository.class);
    private final VersionAccessGuard access=mock(VersionAccessGuard.class);
    private final ObjectMapper mapper=new ObjectMapper();
    private final UUID project=UUID.randomUUID(),id=UUID.randomUUID();
    private final Version version=new Version("Example",null,List.of(),List.of(),List.of(),List.of());
    private final Scenario scenario=new Scenario(id,project,"Example",null,null);
    private ScenarioRevisionService service;
    @BeforeEach void setUp() {
        ReflectionTestUtils.setField(version,"id",id);
        service=new ScenarioRevisionService(scenarios,revisions,projects,versions,configurations,
                new EconomicInputCodec(mapper),mapper,access,mock(AppLogger.class));
        ReflectionTestUtils.setField(service,"em",mock(EntityManager.class));
        when(projects.findIdByVersionId(id)).thenReturn(Optional.of(project));
        when(versions.findById(id)).thenReturn(Optional.of(version));
        when(scenarios.findById(id)).thenReturn(Optional.of(scenario));
        when(configurations.findByVersionId(id)).thenReturn(Optional.empty());
        when(revisions.saveAndFlush(any())).thenAnswer(call->{
            ScenarioRevision revision=call.getArgument(0); UUID key=UUID.randomUUID();
            ReflectionTestUtils.setField(revision,"id",key);
            when(revisions.findById(key)).thenReturn(Optional.of(revision));return revision;
        });
    }
    @Test void initialCapturePreservesIncompleteConfiguration() {
        service.ensure(version); assertNotNull(scenario.getCurrentRevisionId());
        var revision=service.current(version);
        assertTrue(revision.getSnapshotJson().contains("\"configuration\":null")); assertEquals(1,revision.getNumber());
        verify(revisions,times(1)).saveAndFlush(any());
    }
    @Test void unchangedPublicationDoesNotCreateRevision() {
        var first=service.current(version);
        assertSame(first,service.publishValidated(version,first.getId(),UUID.randomUUID()));
        verify(access).assertCanManageVersion(id); verify(revisions,times(1)).saveAndFlush(any());
    }
    @Test void stalePublicationFailsWithoutAppending() {
        service.ensure(version);
        assertThrows(ScenarioConflictException.class,()->service.publishValidated(version,UUID.randomUUID(),UUID.randomUUID()));
        verify(revisions,times(1)).saveAndFlush(any());
    }
    @Test void crossProjectReadIsRejected() {
        assertThrows(EntityNotFoundException.class,()->service.list(UUID.randomUUID(),id));
        verifyNoInteractions(revisions);
    }
    @Test void inaccessibleRevisionCannotBeRead() {
        when(revisions.findByIdAndScenarioId(any(),eq(id))).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class,()->service.get(project,id,UUID.randomUUID()));
        verify(access).assertCanViewVersion(id);
    }
    @Test void preparedMergeBlocksSimulationEvenWithoutWorkspaceChanges() {
        var revision=service.current(version);
        var merges=mock(ScenarioMergeRepository.class);
        ReflectionTestUtils.setField(service,"merges",merges);
        when(merges.findByTargetIdAndState(id,"PREPARED"))
                .thenReturn(Optional.of(new ScenarioMerge(id,UUID.randomUUID(),revision.getId(),null)));
        assertFalse(service.hasWorkspaceChanges(version));
        assertTrue(service.hasPendingPublication(version));
        assertTrue(service.status(project,id).workspaceChanges());
        assertThrows(IllegalArgumentException.class,()->service.assertPublished(version));
    }
    @Test void unresolvedMergeReportsActionablePublicationError() {
        var revision=service.current(version);
        var merges=mock(ScenarioMergeRepository.class);
        ReflectionTestUtils.setField(service,"merges",merges);
        var merge=new ScenarioMerge(id,UUID.randomUUID(),revision.getId(),null);
        merge.prepare("{}","[{\"identity\":\"conflict\"}]","hash");
        when(merges.findByTargetIdAndState(id,"PREPARED")).thenReturn(Optional.of(merge));
        var error=assertThrows(IllegalArgumentException.class,
                ()->service.publishValidated(version,revision.getId(),UUID.randomUUID()));
        assertTrue(error.getMessage().contains("Versionado"));
        assertEquals("PREPARED",merge.getState());
        verify(revisions,times(1)).saveAndFlush(any());
    }
    @Test void savedFormDraftBlocksSimulationEvenWithoutWorkspaceChanges() {
        service.current(version);
        var drafts=mock(ScenarioDraftRepository.class);
        ReflectionTestUtils.setField(service,"drafts",drafts);
        when(drafts.existsByVersionId(id)).thenReturn(true);
        assertFalse(service.hasWorkspaceChanges(version));
        assertTrue(service.hasPendingPublication(version));
        assertThrows(IllegalArgumentException.class,()->service.assertPublished(version));
    }
}
