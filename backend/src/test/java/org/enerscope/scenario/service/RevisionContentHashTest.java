package org.enerscope.scenario.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RevisionContentHashTest {
    private final ObjectMapper mapper=new ObjectMapper();
    @Test void objectPropertyOrderAndAuditTimesDoNotChangeHash() throws Exception {
        assertEquals(RevisionContentHash.hash(mapper.readTree("{\"a\":1,\"b\":2,\"createdAt\":\"old\"}")),
            RevisionContentHash.hash(mapper.readTree("{\"b\":2,\"a\":1,\"createdAt\":\"new\"}")));
    }
    @Test void presentationChangesArePartOfRevision() throws Exception {
        assertNotEquals(RevisionContentHash.hash(mapper.readTree("{\"graphData\":{\"x\":1}}")),
            RevisionContentHash.hash(mapper.readTree("{\"graphData\":{\"x\":2}}")));
    }
    @Test void arrayOrderIsNotSilentlyDiscarded() throws Exception {
        assertNotEquals(RevisionContentHash.hash(mapper.readTree("[1,2]")),RevisionContentHash.hash(mapper.readTree("[2,1]")));
    }
}
