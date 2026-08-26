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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table
public class ConnectionChange extends BaseEntity {

    @Column
    @Enumerated(EnumType.STRING)
    private ChangeTypeEnum changeType;

    @Column(nullable = true)
    private UUID changedConnectionId;

    @Column(nullable = true)
    private UUID resultConnectionId;

}
