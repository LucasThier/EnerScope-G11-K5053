package org.enerscope.project.repository;

import org.enerscope.project.model.ProjectMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, UUID> {
    boolean existsByProjectIdAndUserId(UUID projectId, UUID userId);
}
