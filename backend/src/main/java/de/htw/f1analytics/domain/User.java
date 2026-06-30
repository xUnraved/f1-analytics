package de.htw.f1analytics.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "user_account")
public class User extends PanacheEntity {

    @Column(nullable = false, unique = true, length = 32)
    public String username;

    @Column(nullable = false, unique = true, length = 128)
    public String email;

    @Column(name = "password_hash", nullable = false, length = 128)
    public String passwordHash;

    @Column(nullable = false, length = 64)
    public String salt;

    @Column(length = 7)
    public String color;

    @Column(name = "created_at")
    public Instant createdAt;

    public static User findByUsername(String username) {
        return find("username", username).firstResult();
    }

    public static User findByEmail(String email) {
        return find("email", email).firstResult();
    }

    public static User findByLogin(String usernameOrEmail) {
        return find("username = ?1 or email = ?1", usernameOrEmail).firstResult();
    }
}