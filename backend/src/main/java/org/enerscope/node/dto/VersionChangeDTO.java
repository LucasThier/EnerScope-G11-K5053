package org.enerscope.node.dto;

import java.util.List;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class VersionChangeDTO {
    
    private List<ConnectionChangeDTO> ConnectionChanges;
    private List<NodeChangeDTO> nodeChanges;
}
