package org.enerscope.economic.model;
import jakarta.persistence.*;
import lombok.*;
import org.enerscope.common.BaseEntity;

@Entity
@Getter
@NoArgsConstructor
@Table(name="economic_round",uniqueConstraints=@UniqueConstraint(columnNames={"job_id","round_number"}))
public class EconomicRound extends BaseEntity {
    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="job_id",nullable=false,updatable=false)
    private EconomicJob job;
    @Column(name="round_number",nullable=false,updatable=false)
    private int roundNumber;
    @Column(name="snapshot_json",nullable=false,updatable=false,columnDefinition="text")
    private String snapshotJson;
    @Column(name="summary_json",nullable=false,updatable=false,columnDefinition="text")
    private String summaryJson;
    public EconomicRound(EconomicJob job,int number,String snapshot,String summary) {
        this.job = job;
        roundNumber = number;
        snapshotJson = snapshot;
        summaryJson = summary;
    }
}
