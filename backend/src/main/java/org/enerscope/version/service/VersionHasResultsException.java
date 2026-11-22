package org.enerscope.version.service;

/** A version (or one below it) that has simulations or saved comparisons cannot be deleted (HTTP 409). */
public class VersionHasResultsException extends RuntimeException {
    public VersionHasResultsException() {
        super("La versión tiene simulaciones o comparaciones guardadas y no se puede eliminar.");
    }
}
