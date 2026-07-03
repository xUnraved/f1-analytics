package de.htw.f1analytics.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "user_session")
public class UserSession extends PanacheEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    public User user;

    @Column(nullable = false, unique = true, length = 128)
    public String token;

    @Column(name = "created_at")
    public Instant createdAt;

    @Column(name = "expires_at")
    public Instant expiresAt;

    public static UserSession findByToken(String token) {
        return find("token", token).firstResult();
    }
}