package org.enerscope.node.model;

import org.enerscope.common.BaseEntity;
import org.enerscope.node.model.enums.NodeTypeEnum;
import org.enerscope.node.model.enums.StructuralRoleEnum;
import org.enerscope.node.model.enums.VerticalEnum;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class NodeTypeData extends BaseEntity {

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private VerticalEnum vertical;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private StructuralRoleEnum role;

    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    private NodeTypeEnum nodeType;
}