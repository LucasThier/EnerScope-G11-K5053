package org.enerscope.node.model;

import org.enerscope.node.model.enums.ChangeTypeEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NodeChange {

    @Column
    @Enumerated
    private ChangeTypeEnum changeType;

    @OneToOne
    @JoinColumn(nullable = false)
    private NodeIdentity changedNodeIdentity;

    @OneToOne
    @JoinColumn(nullable = false)
    private BaseNode changedNode;

    @OneToOne
    @JoinColumn(nullable = false)
    private BaseNode resultNode;
}
