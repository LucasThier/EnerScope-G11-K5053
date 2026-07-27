package org.enerscope.node.model;

import org.enerscope.node.model.ConnectionIdentity;
import org.enerscope.node.model.NodeIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.MockitoAnnotations.openMocks;

class NodeConnectionTest {

    @Mock
    private ConnectionIdentity mockIdentity;

    @Mock
    private NodeIdentity mockFromNode;

    @Mock
    private NodeIdentity mockToNode;

    private NodeConnection nodeConnection;

    @BeforeEach
    void setUp() {
        openMocks(this);
        nodeConnection = new NodeConnection(
                mockIdentity,
                mockFromNode,
                mockToNode
        );
    }

    @Test
    void nodeConnectionShouldHaveCorrectFromNode() {
        assertEquals(mockFromNode, nodeConnection.getFromNode());
    }

    @Test
    void nodeConnectionShouldHaveCorrectToNode() {
        assertEquals(mockToNode, nodeConnection.getToNode());
    }

    @Test
    void nodeConnectionShouldHaveCorrectIdentity() {
        assertEquals(mockIdentity, nodeConnection.getIdentity());
    }
}