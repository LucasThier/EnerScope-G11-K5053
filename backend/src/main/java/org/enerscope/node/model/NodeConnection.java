package org.enerscope.node.model;

import java.util.UUID;

import org.enerscope.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    @Column
    private UUID identityId;

    @Column
    private UUID fromNodeId;

    @Column
    private UUID toNodeId;
}