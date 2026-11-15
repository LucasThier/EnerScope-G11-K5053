package org.enerscope.project.model.enums;

public enum ProjectMemberType {
    ADMIN,
    EDITOR,
    /** Read-only: only {@code VIEW_PROJECT}. Sees every version of the project; never holds version roles. */
    EXECUTIVE
}
