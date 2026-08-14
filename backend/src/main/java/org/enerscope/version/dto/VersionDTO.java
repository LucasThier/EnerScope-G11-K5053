package org.enerscope.version.dto;

import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class VersionDTO {

    private String name;
    private UUID parentVersion; // doesn´t make sense to bring all of the data of the parents at the request
    private List<UUID> nodeSnapshot;
    private List<UUID> connectionSnapshot;
    // private List<ConnectionChangeDTO> connectionChanges; una versión empieza sin
    // cambios, y sólo se guardan los cambios diferenciales con respecto a su
    // version master
    // private List<NodeChangeDTO> nodeChanges; Una vez q otra rama haga un merge
    // con "su" master, se van a tener que updatear las subversiones con nuevos
    // cambios
}
