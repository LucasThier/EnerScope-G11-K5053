package org.enerscope.simulator;

import java.util.function.Supplier;

/**
 * A stored value chain graph the simulator cannot process. It is an {@link IllegalArgumentException}, so every path
 * that already treats "the simulator rejects this scenario" handles it, and the API answers 400 with the message.
 * The original failure stays as the cause for the log.
 *
 * <p>It does not add validation rules: it names failures the simulator already had.</p>
 */
public class InvalidSimulationGraphException extends IllegalArgumentException {

    public static final String INVALID_CONNECTION =
            "The value chain graph has a connection the simulator cannot process";
    public static final String CARRIER_WITHOUT_TERMINAL =
            "An LNG carrier has no seaport terminal or FLNG unit connected before it";
    public static final String UNPROCESSABLE_GRAPH =
            "The simulator could not process the value chain graph; review its nodes and connections";

    public InvalidSimulationGraphException(String message) {
        super(message);
    }

    public InvalidSimulationGraphException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Runs a step of the in-memory simulator, turning an unexpected failure into this exception. Only wrap pure
     * simulator work: nothing that reads the database, so an infrastructure error can never be reported as a bad graph.
     * The simulator's own {@link IllegalArgumentException}s and an interruption of the thread pass through unchanged.
     */
    public static <T> T translating(Supplier<T> simulatorWork) {
        try {
            return simulatorWork.get();
        } catch (IllegalArgumentException known) {
            throw known;
        } catch (RuntimeException unexpected) {
            if (Thread.currentThread().isInterrupted()) {
                throw unexpected;
            }
            // A node casts its predecessor to the type it expects: a failed cast is a connection it cannot take.
            throw new InvalidSimulationGraphException(
                    unexpected instanceof ClassCastException ? INVALID_CONNECTION : UNPROCESSABLE_GRAPH, unexpected);
        }
    }
}
