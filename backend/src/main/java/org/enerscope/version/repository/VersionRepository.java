package org.enerscope.version.repository;

<<<<<<< HEAD
import org.springframework.data.jpa.repository.JpaRepository;
import org.enerscope.version.model.Version;

import java.util.List;
import java.util.UUID;

public interface VersionRepository extends JpaRepository<Version, UUID> {

    public List<Version> findByParentVersionId(UUID parentVersion);

=======
import org.enerscope.version.model.Version;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface VersionRepository extends JpaRepository<Version, UUID> {
>>>>>>> master
}
