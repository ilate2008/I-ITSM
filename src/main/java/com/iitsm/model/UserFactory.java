package com.iitsm.model;

import com.iitsm.exception.ValidationException;

/** Factory pattern: single place that knows how to build each kind of user. */
public final class UserFactory {
    private UserFactory() { }

    /** Rebuilds a user from storage: the password is already hashed, so no password rules are applied. */
    public static User restore(Role role, String id, String name, String email, String passwordHash, TechnicianProfile profile) {
        return switch (role) {
            case EMPLOYEE -> new Employee(id, name, email, passwordHash);
            case ADMIN -> new Administrator(id, name, email, passwordHash);
            case TECHNICIAN -> new Technician(id, name, email, passwordHash, profile);
        };
    }

    public static User create(Role role, String id, String name, String email, String rawPassword, TechnicianProfile profile) {
        if (rawPassword == null || rawPassword.length() < 8) {
            throw new ValidationException("Password must be at least 8 characters");
        }
        String hash = PasswordHasher.hash(rawPassword);
        return switch (role) {
            case EMPLOYEE -> new Employee(id, name, email, hash);
            case ADMIN -> new Administrator(id, name, email, hash);
            case TECHNICIAN -> {
                if (profile == null) throw new ValidationException("Technician profile required");
                yield new Technician(id, name, email, hash, profile);
            }
        };
    }
}
