package org.enerscope.version.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.enerscope.common.BaseEntity;
import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.ConnectionChange;
import org.enerscope.node.model.NodeChange;
import org.enerscope.node.model.NodeConnection;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Entity
@Table
public class Version extends BaseEntity {

    @Column(nullable = false, length = 320)
    private String name;

    @JoinColumn
    @ManyToOne
    private Version parentVersion;

    @ManyToMany
    @JoinTable(name = "versionXNode", joinColumns = @JoinColumn(name = "version_id"), inverseJoinColumns = @JoinColumn(name = "node_id"))
    private List<BaseNode> nodeSnapshot;

    @ManyToMany
    @JoinTable(name = "versionXConnection", joinColumns = @JoinColumn(name = "version_id"), inverseJoinColumns = @JoinColumn(name = "connection_id"))
    private List<NodeConnection> connectionSnapshot;

    @JoinColumn
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ConnectionChange> connectionChanges;

    @JoinColumn
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    private List<NodeChange> nodeChanges;
}
