package org.enerscope.node.dto;

import java.util.UUID;

import org.enerscope.node.model.enums.ChangeTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NodeChangeDTO {

    private ChangeTypeEnum changeType;
    private UUID changedNode;
    private UUID resultNode;
}
