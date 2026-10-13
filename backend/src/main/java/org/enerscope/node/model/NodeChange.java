package org.enerscope.node.model;

import java.util.UUID;

import org.enerscope.common.BaseEntity;
import org.enerscope.node.model.enums.ChangeTypeEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table
@NoArgsConstructor
@AllArgsConstructor
public class NodeChange extends BaseEntity {

    @Column
    @Enumerated(EnumType.STRING)
    private ChangeTypeEnum changeType;

    // ManyToOne, not OneToOne: several NodeChange rows can legitimately
    // reference the same node (e.g. its resultNode from an ADD becomes another
    // change's changedNode later) - OneToOne would make Hibernate infer a
    // unique constraint on these FK columns that the real schema doesn't have.
    @ManyToOne
    @JoinColumn(nullable = true)
    private BaseNode changedNode;

    @ManyToOne
    @JoinColumn(nullable = true)
    private BaseNode resultNode;

}
