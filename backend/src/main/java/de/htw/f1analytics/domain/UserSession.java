package de.htw.f1analytics.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Persistierte Benutzersession (Token-basierte Authentifizierung).
 * Das Token wird vom AuthService als kryptographisch zufällige UUID generiert
 * und hat eine Gültigkeitsdauer von 30 Tagen (expiresAt).
 *
 * Der AuthResource-Filter prüft bei jeder gesicherten Anfrage, ob ein
 * gültiges nicht-abgelaufenes Token in dieser Tabelle vorliegt.
 */
@Entity
@Table(name = "user_session")
public class UserSession extends PanacheEntity {

    /** Verknüpfter Benutzer (EAGER-geladen durch JPA-Default bei @ManyToOne). */
    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    public User user;

    /** Zufälliges Session-Token (UUID-basiert), das als Bearer-Token gesendet wird. */
    @Column(nullable = false, unique = true, length = 128)
    public String token;

    /** Zeitpunkt der Token-Ausstellung. */
    @Column(name = "created_at")
    public Instant createdAt;

    /** Ablaufzeitpunkt – nach diesem Zeitpunkt wird das Token als ungültig abgelehnt. */
    @Column(name = "expires_at")
    public Instant expiresAt;

    /** Schnelle Token-Suche für den Auth-Filter (wird bei jeder Anfrage aufgerufen). */
    public static UserSession findByToken(String token) {
        return find("token", token).firstResult();
    }
}
