package org.enerscope.version;

import java.util.UUID;

public class VersionNotFoundException extends RuntimeException {
    public VersionNotFoundException(UUID id) {
        super("Version not found with id: " + id);
    }
}