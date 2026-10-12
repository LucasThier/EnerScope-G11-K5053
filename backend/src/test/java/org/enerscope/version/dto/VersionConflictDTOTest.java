package org.enerscope.version.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.enerscope.money.MoneyAmount;
import org.enerscope.node.model.GeographicalPosition;
import org.enerscope.node.model.GraphPosition;
import org.enerscope.node.model.InvestmentCost;
import org.enerscope.node.model.InvestmentCostComponent;
import org.enerscope.node.model.NodeGraphData;
import org.enerscope.node.model.NodeTypeData;
import org.enerscope.node.model.enums.CostBasisEnum;
import org.enerscope.node.model.enums.NodeStateEnum;
import org.enerscope.node.model.enums.NodeTypeEnum;
import org.enerscope.node.model.enums.StructuralRoleEnum;
import org.enerscope.node.model.enums.VerticalEnum;
import org.enerscope.node.model.extraction.Well;
import org.enerscope.version.model.ConflictEntityType;
import org.enerscope.version.model.Version;
import org.enerscope.version.model.VersionConflict;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class VersionConflictDTOTest {

    private Version version(String name) {
        Version v = new Version(name, null, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>());
        ReflectionTestUtils.setField(v, "id", UUID.randomUUID());
        return v;
    }

    private Well well(String name, UUID identityId) {
        Well well = new Well(name, NodeStateEnum.PROPOSED, null, 120,
                MoneyAmount.of(1000), 30, MoneyAmount.of(500), 0.1f,
                new InvestmentCost(new ArrayList<>(List.of(
                        new InvestmentCostComponent("drilling", MoneyAmount.of(100), CostBasisEnum.PER_M)))),
                new NodeGraphData(new GraphPosition(1.0, 2.0), new GeographicalPosition(3.0, 4.0)),
                identityId,
                new NodeTypeData(VerticalEnum.EXTRACTION, StructuralRoleEnum.GENERATOR, NodeTypeEnum.WELL),
                10f, 0.5f, 0.5f, 5, MoneyAmount.of(50), 100f);
        ReflectionTestUtils.setField(well, "id", UUID.randomUUID());
        return well;
    }

    @Test
    void from_WithNullSourceVersion_IsNullSafeAndStillDescribesBothSides() {
        Version merged = version("merged");
        Version conflicting = version("conflicting");
        UUID identityId = UUID.randomUUID();

        merged.getNodeSnapshot().add(well("Merged Well", identityId));
        conflicting.getNodeSnapshot().add(well("Conflicting Well", identityId));

        VersionConflict conflict = new VersionConflict(merged, null, conflicting, ConflictEntityType.NODE,
                identityId);
        ReflectionTestUtils.setField(conflict, "id", UUID.randomUUID());

        VersionConflictDTO dto = VersionConflictDTO.from(conflict);

        assertNull(dto.getSourceVersionId());
        assertNull(dto.getSourceChangeType());
        assertNotNull(dto.getMergedDescription());
        assertNotNull(dto.getConflictingDescription());
        assertEquals(merged.getId(), dto.getMergedVersionId());
        assertEquals(conflicting.getId(), dto.getConflictingVersionId());
        assertEquals(identityId, dto.getIdentityId());
    }
}
