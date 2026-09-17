package org.enerscope.economic.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.enerscope.common.BaseEntity;
import org.enerscope.version.model.Version;

/** JSON is a validated, typed aggregate; this avoids sharing mutable child rules across versions. */
@Entity
@Table(name = "economic_configuration")
@Getter
@NoArgsConstructor
public class EconomicConfigurationEntity extends BaseEntity {
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id", nullable = false, unique = true)
    private Version version;
    @Column(name = "configuration_json", nullable = false, columnDefinition = "text")
    private String configurationJson;
    @jakarta.persistence.Version
    private long revision;

    public EconomicConfigurationEntity(Version version, String json) {
        this.version = version;
        this.configurationJson = json;
    }
    public void replace(String json) { this.configurationJson = json; }
}
