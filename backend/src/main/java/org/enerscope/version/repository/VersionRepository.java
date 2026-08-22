package org.enerscope.version.repository;

import org.enerscope.version.model.Version;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface VersionRepository extends JpaRepository<Version, UUID> {
}
