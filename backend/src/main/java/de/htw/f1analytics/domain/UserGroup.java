package de.htw.f1analytics.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Tippspiel-Gruppe, in der mehrere Nutzer gegeneinander antreten.
 * Gruppen werden über einen 6-stelligen Einladungscode beigetreten.
 * Der owner ist der Ersteller und darf die Gruppe verwalten.
 *
 * Tabelle heißt "tipp_group" statt "group", da "group" SQL-Schlüsselwort ist.
 */
@Entity
@Table(name = "tipp_group")
public class UserGroup extends PanacheEntity {

    /** Anzeigename der Gruppe (max. 48 Zeichen). */
    @Column(nullable = false, length = 48)
    public String name;

    /**
     * Einladungscode zum Beitreten der Gruppe.
     * 6 alphanumerische Zeichen, vom GroupService zufällig generiert.
     * Muss eindeutig sein (UNIQUE-Constraint).
     */
    @Column(name = "invite_code", nullable = false, unique = true, length = 12)
    public String inviteCode;

    /** Ersteller der Gruppe (hat Admin-Rechte). */
    @ManyToOne(optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    public User owner;

    /** Erstellungszeitpunkt der Gruppe. */
    @Column(name = "created_at")
    public Instant createdAt;

    /** Suche per Einladungscode (wird beim Beitreten einer Gruppe verwendet). */
    public static UserGroup findByInviteCode(String code) {
        return find("inviteCode", code).firstResult();
    }
}
