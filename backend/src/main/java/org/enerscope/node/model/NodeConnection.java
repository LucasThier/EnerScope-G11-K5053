package org.enerscope.node.model;

import org.enerscope.common.BaseEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class NodeConnection extends BaseEntity {

    @OneToOne
    @JoinColumn(name = "identityId", nullable = false)
    private ConnectionIdentity identity;

    @OneToOne
    @JoinColumn(name = "fromNodeId", nullable = false)
    private NodeIdentity fromNode;

    @OneToOne
    @JoinColumn(name = "toNodeId", nullable = false)
    private NodeIdentity toNode;
}