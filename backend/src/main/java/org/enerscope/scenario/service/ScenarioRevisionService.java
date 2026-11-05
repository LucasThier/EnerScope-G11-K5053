package org.enerscope.scenario.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.enerscope.common.EntityNotFoundException;
import org.enerscope.economic.model.configuration.EconomicConfiguration;
import org.enerscope.economic.repository.EconomicConfigurationRepository;
import org.enerscope.economic.service.EconomicInputCodec;
import org.enerscope.logging.AppLogger;
import org.enerscope.project.repository.ProjectRepository;
import org.enerscope.scenario.model.Scenario;
import org.enerscope.scenario.model.ScenarioRevision;
import org.enerscope.scenario.repository.ScenarioRepository;
import org.enerscope.scenario.repository.ScenarioRevisionRepository;
import org.enerscope.version.model.Version;
import org.enerscope.version.repository.VersionRepository;
import org.enerscope.version.service.VersionAccessGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Revision storage is independent of the simulator and never validates by executing it. */
@Service
@RequiredArgsConstructor
@Transactional
public class ScenarioRevisionService {
    private final ScenarioRepository scenarios;
    private final ScenarioRevisionRepository revisions;
    private final ProjectRepository projects;
    private final VersionRepository versions;
    private final EconomicConfigurationRepository configurations;
    private final EconomicInputCodec codec;
    private final ObjectMapper mapper;
    private final VersionAccessGuard access;
    private final AppLogger logger;
    @PersistenceContext private EntityManager em;
    @org.springframework.beans.factory.annotation.Autowired private org.enerscope.scenario.repository.ScenarioMergeRepository merges;
    @org.springframework.beans.factory.annotation.Autowired private org.enerscope.scenario.repository.ScenarioDraftRepository drafts;
    @org.springframework.beans.factory.annotation.Autowired private org.enerscope.scenario.repository.EconomicDraftRepository economicDrafts;

    /** Capture the original state before the first workspace mutation, including incomplete configurations. */
    public Scenario ensure(Version version) {
        em.lock(version, LockModeType.PESSIMISTIC_WRITE);
        UUID projectId = projects.findIdByVersionId(version.getId())
                .orElseThrow(() -> new EntityNotFoundException("Scenario project not found"));
        var scenario = scenarios.findById(version.getId()).orElseGet(() -> scenarios.saveAndFlush(
                new Scenario(version.getId(), projectId, version.getName(), null, null)));
        if (scenario.getCurrentRevisionId() == null) append(scenario, version, null, null, null);
        return scenario;
    }

    public Version branchWorkspace(Version parent) {
        if(projects.findIdByVersionId(parent.getId()).isEmpty())return parent;
        access.assertCanViewVersion(parent.getId());
        var restored=codec.restoreRevision(parent.getName(), read(current(parent).getSnapshotJson(), EconomicInputCodec.RevisionContent.class));
        for(var node:restored.getNodeSnapshot()) if(node instanceof org.enerscope.node.model.extraction.Well well && well.getDeclineCurve()!=null)well.getDeclineCurve().setId(null);
        return restored;
    }

    public void registerNew(Version version) {
        em.flush();
        UUID project = projects.findIdByVersionId(version.getId()).orElseThrow();
        if(scenarios.existsById(version.getId())) return;
        var parent = version.getParentVersion();
        var origin = parent == null ? null : current(parent);
        var scenario = new Scenario(version.getId(), project, version.getName(), parent == null ? null : parent.getId(),
                origin == null ? null : origin.getId());
        scenarios.saveAndFlush(scenario);
        if(origin != null) {
            var content = read(origin.getSnapshotJson(), EconomicInputCodec.RevisionContent.class);
            if(content.configuration() != null) {
                var ids = new HashMap<String,String>();
                for(var node:content.nodes()) for(var copied:version.getNodeSnapshot())
                    if(copied.getIdentityId().toString().equals(node.data().path("identityId").asText()))
                        ids.put(node.data().path("id").asText(), copied.getId().toString());
                var tree = mapper.valueToTree(content.configuration());ScenarioConfigurationService.remapReferences(tree,ids);
                configurations.saveAndFlush(new org.enerscope.economic.model.EconomicConfigurationEntity(version,json(tree)));
            }
        }
        append(scenario,version,org.enerscope.util.AuthUtil.requireSession().getUser().getId(),null,null);
    }

    public boolean isScenario(UUID id) { return projects.findIdByVersionId(id).isPresent(); }

    public void archiveTree(UUID id) {
        UUID project=projects.findIdByVersionId(id).orElseThrow();
        var pending=new ArrayDeque<UUID>();var visited=new HashSet<UUID>();pending.add(id);
        while(!pending.isEmpty()) {
            UUID next=pending.remove();if(!visited.add(next))continue;
            if(!projects.findIdByVersionId(next).filter(project::equals).isPresent())
                throw new IllegalArgumentException("A scenario tree cannot cross projects");
            access.assertCanManageVersion(next);
            var version=versions.findById(next).orElseThrow();ensure(version);version.deactivate();
            versions.findByParentVersionId(next).forEach(child->pending.add(child.getId()));
        }
        logger.info("Archived {} scenarios under {} while preserving revisions and simulations",visited.size(),id);
    }

    public void beforeWorkspaceEdit(Version version) {
        if (projects.findIdByVersionId(version.getId()).isPresent()) ensure(version);
    }

    public boolean canPublish(UUID id) {
        try { access.assertCanManageVersion(id); return true; }
        catch (org.enerscope.common.ForbiddenException denied) { return false; }
    }

    public void assertCanSimulate(UUID id) { access.assertCanSimulateVersion(id); }
    public void assertCanView(UUID id) { access.assertCanViewVersion(id); }

    public void assertCanEdit(UUID id) { access.assertCanEditVersion(id); }

    public void assertCanEditNode(UUID id, org.enerscope.node.model.BaseNode node) {
        access.assertCanEditNodeVertical(id, node.getType().getVertical());
    }

    public boolean hasWorkspaceChanges(Version version) {
        var previous = current(version);
        return !previous.getContentHash().equals(workspaceHash(version));
    }

    public String workspaceHash(Version version) {
        EconomicConfiguration configuration = configurations.findByVersionId(version.getId())
                .map(value -> read(value.getConfigurationJson(), EconomicConfiguration.class)).orElse(null);
        return RevisionContentHash.hash(mapper.valueToTree(codec.captureRevision(version, configuration)));
    }

    public boolean hasPendingPublication(Version version) {
        return (merges != null && merges.findByTargetIdAndState(version.getId(), "PREPARED").isPresent())
                || (drafts != null && drafts.existsByVersionId(version.getId()))
                || (economicDrafts != null && economicDrafts.findByVersionId(version.getId()).isPresent())
                || hasWorkspaceChanges(version);
    }

    public void assertPublished(Version version) {
        if (hasPendingPublication(version)) throw new IllegalArgumentException(
                "Hay cambios operativos o económicos pendientes de publicación en Configuración del Escenario.");
    }

    public ScenarioRevision current(Version version) {
        return revisions.findById(ensure(version).getCurrentRevisionId()).orElseThrow();
    }

    public org.enerscope.economic.dto.EvaluationDTO.RevisionTag tag(UUID revisionId) {
        if(revisionId==null)return null;
        var revision=revisions.findById(revisionId).orElseThrow(()->new EntityNotFoundException("Scenario revision not found"));
        var scenario=scenarios.findById(revision.getScenarioId()).orElseThrow();
        return new org.enerscope.economic.dto.EvaluationDTO.RevisionTag(revisionId,revision.getNumber(),!revisionId.equals(scenario.getCurrentRevisionId()));
    }

    public record RevisionStatus(UUID revisionId, int number, boolean workspaceChanges, boolean canPublish, String token) {}
    public RevisionStatus status(UUID projectId, UUID scenarioId) {
        var workspace = authorized(projectId, scenarioId);
        var revision = current(workspace);
        return new RevisionStatus(revision.getId(), revision.getNumber(), hasPendingPublication(workspace), canPublish(scenarioId), workspaceToken(workspace));
    }

    private String workspaceToken(Version version) {
        var tree=mapper.createObjectNode().put("currentRevision",current(version).getId().toString()).put("workspace",workspaceHash(version));
        if(drafts!=null)tree.put("draft",drafts.findByVersionId(version.getId()).map(value->value.getDraftJson()).orElse(""));
        if(economicDrafts!=null)tree.put("economicDraft",economicDrafts.findByVersionId(version.getId()).map(value->value.getDraftJson()).orElse(""));
        return RevisionContentHash.hash(tree);
    }

    public RevisionStatus restore(UUID project,UUID scenarioId,UUID revisionId,String token,boolean replacePending) {
        var workspace=authorized(project,scenarioId);access.assertCanManageVersion(scenarioId);
        em.lock(workspace,LockModeType.PESSIMISTIC_WRITE);
        if(!workspaceToken(workspace).equals(token))throw new ScenarioConflictException();
        boolean pending=hasWorkspaceChanges(workspace) || drafts.existsByVersionId(scenarioId)
                || economicDrafts.findByVersionId(scenarioId).isPresent() || merges.findByTargetIdAndState(scenarioId,"PREPARED").isPresent();
        if(pending && !replacePending)throw new ScenarioConflictException();
        var revision=revisions.findByIdAndScenarioId(revisionId,scenarioId).orElseThrow(()->new EntityNotFoundException("Scenario revision not found"));
        applyContent(workspace,read(revision.getSnapshotJson(),EconomicInputCodec.RevisionContent.class));
        drafts.deleteByVersionId(scenarioId);economicDrafts.deleteByVersionId(scenarioId);
        merges.findByTargetIdAndState(scenarioId,"PREPARED").ifPresent(org.enerscope.scenario.model.ScenarioMerge::cancel);
        em.flush();logger.info("Revision {} restored into scenario {} draft",revisionId,scenarioId);
        return status(project,scenarioId);
    }

    public List<RevisionSummary> list(UUID projectId, UUID scenarioId) {
        Version version = authorized(projectId, scenarioId);
        ensure(version);
        return revisions.findByScenarioIdOrderByNumberDesc(scenarioId).stream().map(this::summary).toList();
    }

    public RevisionDetail get(UUID projectId, UUID scenarioId, UUID revisionId) {
        authorized(projectId, scenarioId);
        var revision = revisions.findByIdAndScenarioId(revisionId, scenarioId)
                .orElseThrow(() -> new EntityNotFoundException("Scenario revision not found"));
        return new RevisionDetail(summary(revision), read(revision.getSnapshotJson(), EconomicInputCodec.RevisionContent.class));
    }

    /** Caller must have validated the entire draft and hold the workspace lock in this transaction. */
    public ScenarioRevision publishValidated(Version version, UUID expectedRevision, UUID author) {
        access.assertCanManageVersion(version.getId());
        var scenario = ensure(version);
        if (!Objects.equals(expectedRevision, scenario.getCurrentRevisionId())) throw new ScenarioConflictException();
        var merge = merges == null ? Optional.<org.enerscope.scenario.model.ScenarioMerge>empty()
                : merges.findByTargetIdAndState(version.getId(), "PREPARED");
        if (merge.isPresent()) {
            if (!merge.get().getTargetRevisionId().equals(expectedRevision)) throw new ScenarioConflictException();
            if (!read(merge.get().getConflictsJson(), com.fasterxml.jackson.databind.JsonNode.class).isEmpty())
                throw new IllegalArgumentException("Resolvé los conflictos del merge en Versionado antes de publicar.");
        }
        var published = append(scenario, version, author, merge.map(value -> value.getSourceRevisionId()).orElse(null),
                merge.map(value -> value.getBaseRevisionId()).orElse(null));
        merge.ifPresent(org.enerscope.scenario.model.ScenarioMerge::complete);
        return published;
    }

    public org.enerscope.economic.dto.FrozenEconomicInput savedInput(UUID revisionId,
            org.enerscope.economic.model.results.SimulationMetadata metadata) {
        var revision = revisions.findById(revisionId).orElseThrow(() -> new EntityNotFoundException("Simulation revision not found"));
        var content = read(revision.getSnapshotJson(), EconomicInputCodec.RevisionContent.class);
        if (content.configuration() == null) throw new IllegalArgumentException("Revision has no economic configuration");
        return new org.enerscope.economic.dto.FrozenEconomicInput(content.configuration(), "Revision " + revision.getNumber(),
                content.nodes(), content.connections(), metadata);
    }

    public org.enerscope.economic.dto.FrozenEconomicInput executionInput(UUID revisionId,
            org.enerscope.economic.model.results.SimulationMetadata metadata) {
        var input = savedInput(revisionId, metadata);
        var workspace = codec.thaw(input);
        return codec.freeze(workspace, input.configuration(), metadata.rounds(), Long.parseLong(metadata.seed()));
    }

    public void discardWorkspace(Version version) {
        access.assertCanManageVersion(version.getId());
        applyContent(version, read(current(version).getSnapshotJson(), EconomicInputCodec.RevisionContent.class));
        if(merges!=null) merges.findByTargetIdAndState(version.getId(),"PREPARED").ifPresent(org.enerscope.scenario.model.ScenarioMerge::cancel);
    }

    /** Recreate owned physical rows, preserving logical identities and remapping economic references. */
    public void applyContent(Version workspace, EconomicInputCodec.RevisionContent content) {
        var restored = codec.restoreRevision(workspace.getName(), content);
        var ids = new HashMap<String, String>();
        var nodes = new ArrayList<org.enerscope.node.model.BaseNode>();
        for (var node : restored.getNodeSnapshot()) {
            if (node instanceof org.enerscope.node.model.extraction.Well well && well.getDeclineCurve() != null)
                well.getDeclineCurve().setId(null);
            var clone = org.enerscope.node.service.NodeCloner.cloneNode(node);
            em.persist(clone); nodes.add(clone); ids.put(node.getId().toString(), clone.getId().toString());
        }
        var connections = new ArrayList<org.enerscope.node.model.NodeConnection>();
        for (var connection : restored.getConnectionSnapshot()) {
            if (!ids.containsKey(connection.getFromNodeId().toString()) || !ids.containsKey(connection.getToNodeId().toString()))
                throw new IllegalArgumentException("Revision connection references an unknown node");
            var clone = org.enerscope.node.service.NodeCloner.cloneConnection(connection);
            clone.setFromNodeId(UUID.fromString(ids.get(connection.getFromNodeId().toString())));
            clone.setToNodeId(UUID.fromString(ids.get(connection.getToNodeId().toString())));
            em.persist(clone); connections.add(clone);
        }
        workspace.setNodeSnapshot(nodes); workspace.setConnectionSnapshot(connections);
        if (content.configuration() == null) configurations.findByVersionId(workspace.getId()).ifPresent(configurations::delete);
        else {
            var tree = mapper.valueToTree(content.configuration()); ScenarioConfigurationService.remapReferences(tree, ids);
            var entity = configurations.findByVersionId(workspace.getId()).orElseGet(() ->
                    new org.enerscope.economic.model.EconomicConfigurationEntity(workspace, ""));
            entity.replace(json(tree)); configurations.save(entity);
        }
        em.flush();
    }

    private ScenarioRevision append(Scenario scenario, Version version, UUID author, UUID source, UUID base) {
        EconomicConfiguration configuration = configurations.findByVersionId(version.getId())
                .map(value -> read(value.getConfigurationJson(), EconomicConfiguration.class)).orElse(null);
        var content = codec.captureRevision(version, configuration);
        String hash = RevisionContentHash.hash(mapper.valueToTree(content));
        var previous = Optional.ofNullable(scenario.getCurrentRevisionId()).flatMap(revisions::findById);
        if (previous.isPresent() && previous.get().getContentHash().equals(hash)) return previous.get();
        var revision = revisions.saveAndFlush(new ScenarioRevision(scenario.getId(),
                Math.max(revisions.maximumNumber(scenario.getId()),previous.map(ScenarioRevision::getNumber).orElse(0))+1, author, scenario.getCurrentRevisionId(),
                source, base, json(content), hash));
        scenario.publish(revision);
        scenarios.saveAndFlush(scenario);
        logger.info("Scenario {} revision {} captured", scenario.getId(), revision.getNumber());
        return revision;
    }

    private Version authorized(UUID projectId, UUID id) {
        if (!projects.findIdByVersionId(id).filter(projectId::equals).isPresent())
            throw new EntityNotFoundException("Scenario not found in project");
        access.assertCanViewVersion(id);
        return versions.findById(id).orElseThrow(() -> new EntityNotFoundException("Scenario not found"));
    }
    private RevisionSummary summary(ScenarioRevision value) {
        return new RevisionSummary(value.getId(), value.getNumber(), value.getCreatedAt(), value.getCreatedBy(),
                value.getParentRevisionId(), value.getMergeSourceRevisionId(), value.getMergeBaseRevisionId());
    }
    public record RevisionSummary(UUID id, int number, java.time.Instant createdAt, UUID createdBy,
                                  UUID parentRevisionId, UUID mergeSourceRevisionId, UUID mergeBaseRevisionId) {}
    public record RevisionDetail(RevisionSummary revision, EconomicInputCodec.RevisionContent content) {}
    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException error) { throw new IllegalStateException("Cannot serialize revision", error); }
    }
    private <T> T read(String json, Class<T> type) {
        try { return mapper.readValue(json, type); }
        catch (JsonProcessingException error) { throw new IllegalStateException("Cannot read revision", error); }
    }
}
