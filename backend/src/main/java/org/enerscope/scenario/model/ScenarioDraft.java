package org.enerscope.scenario.model;
import jakarta.persistence.*;
import lombok.*;
import org.enerscope.common.BaseEntity;
import org.enerscope.version.model.Version;

@Entity
@Table(name="scenario_draft")
@Getter
@NoArgsConstructor
public class ScenarioDraft extends BaseEntity {
    @OneToOne(fetch=FetchType.LAZY, optional=false)
    @JoinColumn(name="version_id",nullable=false,unique=true)
    private Version version;
    @Column(name="draft_json",nullable=false,columnDefinition="text")
    private String draftJson;
    @jakarta.persistence.Version
    private long revision;

    public ScenarioDraft(Version version,String json) {
        this.version = version;
        this.draftJson = json;
    }

    public void replace(String json) {
        draftJson = json;
    }
}
