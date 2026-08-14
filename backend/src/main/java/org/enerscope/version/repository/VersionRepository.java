package org.enerscope.version.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.enerscope.version.model.Version;
import java.util.UUID;

public interface VersionRepository extends JpaRepository<Version, UUID> {
}
