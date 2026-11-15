package org.enerscope.node.model.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NodeTypeEnumTest {

    @ParameterizedTest
    @CsvSource({
            "WELL, EXTRACTION",
            "GATHERING_NETWORK, EXTRACTION",
            "TREATMENT_PLANT, EXTRACTION",
            "PIPELINE, TRANSPORTATION",
            "PIPELINE_CONECTION, TRANSPORTATION",
            "COMPRESSING_PLANT, TRANSPORTATION",
            "GROUND_LIQUEFACTION_PLANT, LIQUEFACTION",
            "FLNG_UNIT, LIQUEFACTION",
            "SEAPORT_TERMINAL, EXPORT",
            "LNG_CAMER, EXPORT",
            "INTERNAL_CONSUMPTION, INTERNAL_CONSUMPTION",
            "INDUSTRIAL_CONSUMPTION, INTERNAL_CONSUMPTION"
    })
    void everyNodeTypeBelongsToItsVertical(NodeTypeEnum type, VerticalEnum vertical) {
        assertEquals(vertical, type.vertical());
    }

    @Test
    void theMappingCoversEveryNodeType() {
        assertEquals(12, NodeTypeEnum.values().length);
        for (NodeTypeEnum type : NodeTypeEnum.values()) {
            assertNotNull(type.vertical());
        }
    }

    @ParameterizedTest
    @EnumSource(NodeTypeEnum.class)
    void checkedVerticalAcceptsAMissingOrMatchingDeclaration(NodeTypeEnum type) {
        assertEquals(type.vertical(), type.checkedVertical(null));
        assertEquals(type.vertical(), type.checkedVertical(type.vertical()));
    }

    @Test
    void checkedVerticalRejectsADeclarationThatDoesNotMatchTheType() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> NodeTypeEnum.LNG_CAMER.checkedVertical(VerticalEnum.EXTRACTION));
        assertEquals("Node vertical does not match its type: LNG_CAMER belongs to EXPORT, not EXTRACTION",
                error.getMessage());
    }

    @ParameterizedTest
    @CsvSource({
            "WELL, GENERATOR",
            "GATHERING_NETWORK, INTERMEDIATE",
            "TREATMENT_PLANT, INTERMEDIATE",
            "PIPELINE, INTERMEDIATE",
            "PIPELINE_CONECTION, INTERMEDIATE",
            "COMPRESSING_PLANT, INTERMEDIATE",
            "GROUND_LIQUEFACTION_PLANT, INTERMEDIATE",
            "FLNG_UNIT, INTERMEDIATE",
            "SEAPORT_TERMINAL, SINK",
            "LNG_CAMER, SINK",
            "INTERNAL_CONSUMPTION, SINK",
            "INDUSTRIAL_CONSUMPTION, SINK"
    })
    void everyNodeTypeHasItsStructuralRole(NodeTypeEnum type, StructuralRoleEnum role) {
        assertEquals(role, type.role());
        assertEquals(role, type.checkedRole(null));
        assertEquals(role, type.checkedRole(role));
    }

    @ParameterizedTest
    @EnumSource(NodeTypeEnum.class)
    void checkedRoleRejectsEveryOtherRole(NodeTypeEnum type) {
        for (StructuralRoleEnum other : StructuralRoleEnum.values()) {
            if (other != type.role()) {
                assertThrows(IllegalArgumentException.class, () -> type.checkedRole(other));
            }
        }
    }
}
