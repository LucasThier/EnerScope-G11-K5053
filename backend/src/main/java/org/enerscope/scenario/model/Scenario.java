package org.enerscope.scenario.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.enerscope.common.BaseEntity;
import org.enerscope.version.model.Version;

/**
 * The economic and simulation configuration that rests on a {@link Version} (the physical base).
 * A version can have several; each version has one default scenario, {@value #DEFAULT_NAME},
 * created by migration V26 for the existing versions and on first use for new ones.
 * Names are unique inside a version ignoring case among the active scenarios (a database index).
 */
@Entity
@Table(name = "scenario")
@Getter
@NoArgsConstructor
public class Scenario extends BaseEntity {
    /** Name of the default scenario; the only name the application ever generates itself. */
    public static final String DEFAULT_NAME = "Escenario base";
    public static final int NAME_MAX = 120;

    /** Deleting a version row deletes its scenarios, as V26's foreign key does on PostgreSQL (the schema H2 builds says so too). */
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id", nullable = false, updatable = false)
    private Version version;

    @Column(nullable = false, length = NAME_MAX)
    private String name;

    @Column(name = "is_default", nullable = false)
    private boolean defaultScenario;

    public void rename(String name) {
        this.name = name;
    }

    public void markDefault() {
        this.defaultScenario = true;
    }

    public void clearDefault() {
        this.defaultScenario = false;
    }

    public Scenario(Version version, String name, boolean defaultScenario) {
        this.version = version;
        this.name = name;
        this.defaultScenario = defaultScenario;
    }
}
