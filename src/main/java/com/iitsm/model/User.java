package com.iitsm.model;

import com.iitsm.exception.ValidationException;
import java.io.Serializable;

/** Abstract base of all system users. State is private; exposed through read-only accessors. */
public abstract class User implements Serializable {
    private final String id;
    private final String name;
    private final String email;
    private final String passwordHash;

    protected User(String id, String name, String email, String passwordHash) {
        if (id == null || id.isBlank() || name == null || name.isBlank() || email == null || !email.contains("@")) {
            throw new ValidationException("A user needs an id, a name and a valid email");
        }
        this.id = id; this.name = name; this.email = email.toLowerCase(); this.passwordHash = passwordHash;
    }

    public abstract Role role();

    public String getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    /** The salted hash (never the password). Exposed so a repository can store it. */
    public String getPasswordHash() { return passwordHash; }
    public boolean checkPassword(String password) { return password != null && PasswordHasher.verify(password, passwordHash); }

    @Override public String toString() { return role() + " " + id + " (" + name + ")"; }
}
