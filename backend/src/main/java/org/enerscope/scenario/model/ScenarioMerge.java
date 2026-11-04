package org.enerscope.scenario.model;
import jakarta.persistence.*;
import lombok.*;
import org.enerscope.common.BaseEntity;
import java.util.UUID;
@Entity @Table(name="scenario_merge") @Getter @NoArgsConstructor
public class ScenarioMerge extends BaseEntity {
 @Column(name="target_id",nullable=false,updatable=false) private UUID targetId;
 @Column(name="source_revision_id",nullable=false,updatable=false) private UUID sourceRevisionId;
 @Column(name="target_revision_id",nullable=false,updatable=false) private UUID targetRevisionId;
 @Column(name="base_revision_id",updatable=false) private UUID baseRevisionId;
 @Column(nullable=false,length=20) private String state="PREPARED";
 @Column(name="resolutions_json",nullable=false,columnDefinition="text") private String resolutionsJson="{}";
 @Column(name="conflicts_json",nullable=false,columnDefinition="text") private String conflictsJson="[]";
 @Column(name="workspace_hash",length=64) private String workspaceHash;
 @Version private long generation;
 public ScenarioMerge(UUID target,UUID source,UUID current,UUID base) {
  targetId=target;sourceRevisionId=source;targetRevisionId=current;baseRevisionId=base;
 }
 public void prepare(String resolutions,String conflicts,String hash) {
  if(!state.equals("PREPARED"))throw new IllegalStateException("Merge is already closed");
  resolutionsJson=resolutions;conflictsJson=conflicts;workspaceHash=hash;
 }
 public void complete(){state="PUBLISHED";}
 public void cancel(){state="CANCELLED";}
}
