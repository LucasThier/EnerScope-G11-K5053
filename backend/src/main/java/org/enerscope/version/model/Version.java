package org.enerscope.version.model;

import java.util.ArrayList;
import java.util.List;

import org.enerscope.common.BaseEntity;
import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.ConnectionChange;
import org.enerscope.node.model.NodeChange;
import org.enerscope.node.model.NodeConnection;
import org.enerscope.simulator.results.FinalResult;
import org.springframework.context.annotation.Lazy;

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
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table
@Lazy
public class Version extends BaseEntity {

    @Column(nullable = false, length = 320)
    private String name;

    @ManyToOne
    @JoinColumn(name = "parent_version_id")
    private Version parentVersion;

    @ManyToMany
    @JoinTable(name = "versionXNode", joinColumns = @JoinColumn(name = "version_id"), inverseJoinColumns = @JoinColumn(name = "node_id"))
    private List<BaseNode> nodeSnapshot = new ArrayList<>();

    @ManyToMany
    @JoinTable(name = "versionXConnection", joinColumns = @JoinColumn(name = "version_id"), inverseJoinColumns = @JoinColumn(name = "connection_id"))
    private List<NodeConnection> connectionSnapshot = new ArrayList<>();

    @JoinColumn(nullable = true)
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ConnectionChange> connectionChanges = new ArrayList<>();

    @JoinColumn(nullable = true)
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    private List<NodeChange> nodeChanges = new ArrayList<>();

    @JoinColumn(name = "version_id", nullable = true)
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FinalResult> finalResults= new ArrayList<>();

    /**
     * Written by hand instead of with {@code @AllArgsConstructor} so that a null
     * collection becomes an empty one: the generated constructor overwrote the
     * field initialisers above, which is how versions created without a parent
     * ended up with null snapshots. The signature is the one Lombok generated,
     * so existing callers are unaffected. {@code Project} and {@code Organization}
     * keep their collections non-null the same way.
     */
    public Version(String name,
                   Version parentVersion,
                   List<BaseNode> nodeSnapshot,
                   List<NodeConnection> connectionSnapshot,
                   List<ConnectionChange> connectionChanges,
                   List<NodeChange> nodeChanges) {
        this.name = name;
        this.parentVersion = parentVersion;
        this.nodeSnapshot = orEmpty(nodeSnapshot);
        this.connectionSnapshot = orEmpty(connectionSnapshot);
        this.connectionChanges = orEmpty(connectionChanges);
        this.nodeChanges = orEmpty(nodeChanges);
    }

    private static <T> List<T> orEmpty(List<T> values) {
        return values == null ? new ArrayList<>() : values;
    }

    public void addResult(FinalResult finalResult){
        this.finalResults.add(finalResult);
    }
}