package org.enerscope.version.dto;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table
public class VersionDTO {

    private String name;
    private Version parentVersion;
    private List<BaseNodeDTO> nodeSnapshot;
    private List<ConnectionDTO> connectionSnapshot;
    private List<ConnectionChangeDTO> connectionChanges;
    private List<NodeChangeDTO> nodeChanges;
}
