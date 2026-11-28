package org.enerscope.simulator;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class InvalidSimulationGraphExceptionTest {

    private static Throwable translated(RuntimeException failure) {
        return assertThrows(Throwable.class, () -> InvalidSimulationGraphException.translating(() -> {
            throw failure;
        }));
    }

    @Test
    void aFailedCastIsReportedAsAnInvalidConnectionAndKeptAsTheCause() {
        var cast = new ClassCastException("SimWell cannot be cast to SimGatheringNetwork");

        var result = translated(cast);

        assertInstanceOf(InvalidSimulationGraphException.class, result);
        assertEquals(InvalidSimulationGraphException.INVALID_CONNECTION, result.getMessage());
        assertSame(cast, result.getCause());
    }

    @Test
    void anyOtherUnexpectedFailureIsReportedAsAnUnprocessableGraph() {
        for (RuntimeException failure : new RuntimeException[] {new NullPointerException(), new ArithmeticException(),
                new IllegalStateException()}) {
            var result = translated(failure);

            assertEquals(InvalidSimulationGraphException.UNPROCESSABLE_GRAPH, result.getMessage());
            assertSame(failure, result.getCause());
        }
    }

    @Test
    void theSimulatorsOwnRejectionsPassThroughUnchanged() {
        var cycle = new IllegalArgumentException("Simulation graph contains a cycle");

        assertSame(cycle, translated(cycle));
    }

    @Test
    void anInterruptedSimulationIsNotReportedAsABadGraph() {
        var interrupted = new IllegalStateException("Simulation interrupted");
        Thread.currentThread().interrupt();
        try {
            assertSame(interrupted, translated(interrupted));
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void errorsAreNeverCaught() {
        var error = new StackOverflowError();

        assertSame(error, assertThrows(StackOverflowError.class, () -> InvalidSimulationGraphException.translating(() -> {
            throw error;
        })));
    }
}
