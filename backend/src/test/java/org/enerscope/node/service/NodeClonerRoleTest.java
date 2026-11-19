package org.enerscope.node.service;

import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.NodeTypeData;
import org.enerscope.node.model.extraction.Well;
import org.enerscope.node.model.enums.NodeTypeEnum;
import org.enerscope.node.model.enums.StructuralRoleEnum;
import org.enerscope.node.model.enums.VerticalEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NodeClonerRoleTest {

    @Test
    void cloneRecomputesTheRoleFromTheTypeWhenTheStoredRoleIsInconsistent() {
        Well source = new Well();
        source.setType(new NodeTypeData(VerticalEnum.EXTRACTION, StructuralRoleEnum.SINK, NodeTypeEnum.WELL));

        BaseNode clone = NodeCloner.cloneNode(source);

        assertEquals(StructuralRoleEnum.GENERATOR, clone.getType().getRole());
        assertEquals(NodeTypeEnum.WELL, clone.getType().getNodeType());
    }

    @Test
    void cloneCopiesTheStoredRoleWhenTheTypeIsNull() {
        Well source = new Well();
        source.setType(new NodeTypeData(VerticalEnum.EXTRACTION, StructuralRoleEnum.INTERMEDIATE, null));

        BaseNode clone = NodeCloner.cloneNode(source);

        assertEquals(StructuralRoleEnum.INTERMEDIATE, clone.getType().getRole());
        assertEquals(VerticalEnum.EXTRACTION, clone.getType().getVertical());
        assertNull(clone.getType().getNodeType());
    }
}
