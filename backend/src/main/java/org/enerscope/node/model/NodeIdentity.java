package org.enerscope.node.model;

import org.enerscope.common.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Entity
@Setter
// @AttributeOverride(name = "id", column = @Column(name = "VersionNodeId"))
public class NodeIdentity extends BaseEntity {
    // con el id ya realizado de BaseEntity deberia ser suficiente...

}