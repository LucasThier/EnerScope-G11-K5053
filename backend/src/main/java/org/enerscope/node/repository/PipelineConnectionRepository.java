package org.enerscope.node.repository;

import org.enerscope.node.model.transportation.PipelineConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface PipelineConnectionRepository extends JpaRepository<PipelineConnection, UUID> {

}