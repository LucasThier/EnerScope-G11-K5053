package org.enerscope.version.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.enerscope.common.BaseEntity;
import org.hibernate.annotations.ManyToAny;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Entity
@Table
public class Version extends BaseEntity {

    @Column(nullable = false, length = 320)
    private String name;

    @Column
    private Instant creationDate;

    @Column
    @ManyToOne
    private Version parentVersion;

    @Column
    @ManyToMany
    private List<BaseNode> nodeSnapshot;

    @Column
    @ManyToMany
    private List<Connection> connectionSnapshot;

    @Column
    @ManyToMany
    private List<ConnectionChange> connectionChanges;

    @Column
    @ManyToMany
    private List<NodeChange> nodeChanges;

}
