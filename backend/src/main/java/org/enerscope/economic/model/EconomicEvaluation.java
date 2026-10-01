package org.enerscope.economic.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.enerscope.common.BaseEntity;
import org.enerscope.version.model.Version;

@Entity
@Table(name = "economic_evaluation")
@Getter
@NoArgsConstructor
public class EconomicEvaluation extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id", nullable = false)
    private Version version;
    @Column(name = "snapshot_json", nullable = false, columnDefinition = "text", updatable = false)
    private String snapshotJson;
    public EconomicEvaluation(Version version, String snapshotJson) {
        this.version = version;
        this.snapshotJson = snapshotJson;
    }
}
