package org.enerscope.version.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.enerscope.version.model.ConflictEntityType;
import org.enerscope.version.model.Version;
import org.enerscope.version.model.VersionConflict;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class VersionConflictRepositoryTest {

    @Autowired
    private VersionConflictRepository conflictRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Version parent;
    private Version source;
    private Version sibling;
    private Version otherSibling;
    private UUID identityId;
    private UUID otherIdentityId;
    private VersionConflict exactPending;

    private Version version(String name) {
        return entityManager.persist(
                new Version(name, null, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>()));
    }

    @BeforeEach
    void setUp() {
        parent = version("parent");
        source = version("source");
        sibling = version("sibling");
        otherSibling = version("other-sibling");
        identityId = UUID.randomUUID();
        otherIdentityId = UUID.randomUUID();

        exactPending = entityManager.persist(
                new VersionConflict(parent, source, sibling, ConflictEntityType.NODE, identityId));
    }

    private void sync() {
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void findPendingDuplicate_ExactMatch_ReturnsIt() {
        sync();

        Optional<VersionConflict> found = conflictRepository.findPendingDuplicate(parent.getId(), source.getId(),
                sibling.getId(), ConflictEntityType.NODE, identityId);

        assertTrue(found.isPresent());
        assertEquals(exactPending.getId(), found.get().getId());
    }

    @Test
    void findPendingDuplicate_DifferentSibling_DoesNotMatch() {
        entityManager.persist(new VersionConflict(parent, source, otherSibling, ConflictEntityType.NODE, identityId));
        sync();

        Optional<VersionConflict> found = conflictRepository.findPendingDuplicate(parent.getId(), source.getId(),
                otherSibling.getId(), ConflictEntityType.NODE, identityId);

        assertTrue(found.isPresent());
        assertEquals(otherSibling.getId(), found.get().getConflictingVersion().getId());
    }

    @Test
    void findPendingDuplicate_DifferentIdentity_DoesNotMatchTheOriginalRow() {
        VersionConflict otherIdentityRow = entityManager
                .persist(new VersionConflict(parent, source, sibling, ConflictEntityType.NODE, otherIdentityId));
        sync();

        Optional<VersionConflict> found = conflictRepository.findPendingDuplicate(parent.getId(), source.getId(),
                sibling.getId(), ConflictEntityType.NODE, otherIdentityId);

        assertTrue(found.isPresent());
        assertEquals(otherIdentityRow.getId(), found.get().getId());
        assertEquals(1, conflictRepository
                .findPendingDuplicates(parent.getId(), source.getId(), sibling.getId(), ConflictEntityType.NODE,
                        identityId, PageRequest.of(0, 10))
                .size());
    }

    @Test
    void findPendingDuplicate_DifferentEntityType_DoesNotMatchTheOriginalRow() {
        entityManager.persist(new VersionConflict(parent, source, sibling, ConflictEntityType.CONNECTION, identityId));
        sync();

        List<VersionConflict> nodeMatches = conflictRepository.findPendingDuplicates(parent.getId(), source.getId(),
                sibling.getId(), ConflictEntityType.NODE, identityId, PageRequest.of(0, 10));

        assertEquals(1, nodeMatches.size());
        assertEquals(ConflictEntityType.NODE, nodeMatches.get(0).getEntityType());
    }

    @Test
    void findPendingDuplicate_OnlyAResolvedRowExistsForTheExactTuple_ReturnsEmpty() {
        exactPending.resolve();
        entityManager.persist(exactPending);
        sync();

        Optional<VersionConflict> found = conflictRepository.findPendingDuplicate(parent.getId(), source.getId(),
                sibling.getId(), ConflictEntityType.NODE, identityId);

        assertTrue(found.isEmpty());
    }

    @Test
    void findPendingDuplicate_HistoricalDuplicatePendingRowsAlreadyExist_StillReturnsExactlyOne() {
        entityManager.persist(new VersionConflict(parent, source, sibling, ConflictEntityType.NODE, identityId));
        sync();

        Optional<VersionConflict> found = conflictRepository.findPendingDuplicate(parent.getId(), source.getId(),
                sibling.getId(), ConflictEntityType.NODE, identityId);

        assertTrue(found.isPresent());
        List<VersionConflict> all = conflictRepository.findByMergedVersionId(parent.getId());
        assertEquals(2, all.size(), "the pre-existing historical duplicate must not be removed by the query itself");
    }
}
