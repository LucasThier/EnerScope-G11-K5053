package org.enerscope.version.model;

<<<<<<< HEAD
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.enerscope.common.BaseEntity;
import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.ConnectionChange;
import org.enerscope.node.model.NodeChange;
import org.enerscope.node.model.NodeConnection;

import java.util.List;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table
public class Version extends BaseEntity {

    @Column(nullable = false, length = 320)
    private String name;

    @Column
    private UUID parentVersionId;

    @ManyToMany
    @JoinTable(name = "versionXNode", joinColumns = @JoinColumn(name = "version_id"), inverseJoinColumns = @JoinColumn(name = "node_id"))
    private List<BaseNode> nodeSnapshot;

    @ManyToMany
    @JoinTable(name = "versionXConnection", joinColumns = @JoinColumn(name = "version_id"), inverseJoinColumns = @JoinColumn(name = "connection_id"))
    private List<NodeConnection> connectionSnapshot;

    @JoinColumn(nullable = true)
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ConnectionChange> connectionChanges;

    @JoinColumn(nullable = true)
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    private List<NodeChange> nodeChanges;
=======
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.enerscope.common.BaseEntity;
import org.enerscope.project.model.Project;

@NoArgsConstructor
@Getter
@Entity
@Table(name = "version")
public class Version extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "parent_version_id", nullable = true)
    private Version parentVersion;

    public Version(String name, Project project, Version parentVersion) {
        this.name = name;
        this.project = project;
        this.parentVersion = parentVersion;
    }
>>>>>>> master
}
