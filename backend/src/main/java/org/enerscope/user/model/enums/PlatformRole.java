package org.enerscope.user.model.enums;

/**
 * The role a {@link org.enerscope.user.model.User} holds in the platform
 * itself (as opposed to organization- or project-scoped roles).
 *
 * <ul>
 *   <li>{@code ADMIN} — full platform administrator; may create any user
 *       (including other admins) through {@code POST /auth/register}.</li>
 *   <li>{@code USER} — a regular application user. This is the default for
 *       every account created through registration.</li>
 * </ul>
 */
public enum PlatformRole {
    ADMIN,
    USER
}
