package de.htw.f1analytics.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Benutzerkonto für das Tippspiel.
 * Passwörter werden niemals im Klartext gespeichert – stattdessen
 * speichert passwordHash den PBKDF2-Hash und salt den individuellen Salt.
 *
 * Tabelle heißt "user_account" statt "user", da "user" in SQL ein reserviertes Wort ist.
 */
@Entity
@Table(name = "user_account")
public class User extends PanacheEntity {

    /** Eindeutiger Anzeigename (max. 32 Zeichen). */
    @Column(nullable = false, unique = true, length = 32)
    public String username;

    /** Eindeutige E-Mail-Adresse (max. 128 Zeichen). */
    @Column(nullable = false, unique = true, length = 128)
    public String email;

    /** PBKDF2-Hash des Passworts (Base64-kodiert). Nie als Klartext speichern. */
    @Column(name = "password_hash", nullable = false, length = 128)
    public String passwordHash;

    /** Kryptographisch zufälliger Salt, individuell pro Benutzer. */
    @Column(nullable = false, length = 64)
    public String salt;

    /** Optionale Akzentfarbe für die Profilanzeige im Tippspiel (Hex, z. B. "#E10600"). */
    @Column(length = 7)
    public String color;

    /** Erstellungszeitpunkt des Kontos. */
    @Column(name = "created_at")
    public Instant createdAt;

    /** Suche nach exaktem Benutzernamen (case-sensitiv). */
    public static User findByUsername(String username) {
        return find("username", username).firstResult();
    }

    /** Suche nach exakter E-Mail-Adresse. */
    public static User findByEmail(String email) {
        return find("email", email).firstResult();
    }

    /** Login-Suche: findet den Benutzer egal ob Benutzername oder E-Mail eingegeben wurde. */
    public static User findByLogin(String usernameOrEmail) {
        return find("username = ?1 or email = ?1", usernameOrEmail).firstResult();
    }
}
