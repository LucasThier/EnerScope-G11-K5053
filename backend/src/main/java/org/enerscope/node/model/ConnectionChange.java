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
public class ConnectionChange {

    @Column
    @Enumerated
    private ChangeTypeEnum changeType;

    @OneToOne
    @JoinColumn(nullable = false)
    private NodeConnection changedConnection;

    @OneToOne
    @JoinColumn(nullable = false)
    private NodeConnection resultConnection;
}
