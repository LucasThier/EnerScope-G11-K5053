package org.enerscope.economic.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.enerscope.common.BaseEntity;
import org.enerscope.version.model.Version;

/** Editor drafts are separate from validated engine configurations. */
@Entity
@Table(name = "economic_draft")
@Getter
@NoArgsConstructor
public class EconomicDraftEntity extends BaseEntity {
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id", nullable = false, unique = true)
    private Version version;
    @Column(name = "draft_json", nullable = false, columnDefinition = "text")
    private String draftJson;
    @jakarta.persistence.Version
    private long revision;
    public EconomicDraftEntity(Version version, String json) { this.version = version; this.draftJson = json; }
    public void replace(String json) { this.draftJson = json; }
}
