package de.htw.f1analytics.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.List;

/**
 * Ein einzelner Tipp eines Benutzers für eine bestimmte Kategorie eines Rennens.
 * Kategorien: "winner", "pole", "podium_p1", "podium_p2", "podium_p3",
 *             "fastest", "h2h_X" (Head-to-Head-Duell zwischen zwei Fahrern).
 *
 * Punkte werden nach dem Rennen durch BettingService.settleRace() vergeben
 * und in der Spalte points gespeichert. Unausgewertete Tipps haben points = NULL.
 */
@Entity
@Table(name = "tipp", indexes = {
        @Index(name = "idx_tipp_race", columnList = "season_year, round"),    // alle Tipps eines Rennens
        @Index(name = "idx_tipp_user_year", columnList = "user_id, season_year") // Tipps eines Users pro Saison
})
public class Tipp extends PanacheEntity {

    /** Tippender Benutzer. */
    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    public User user;

    /** Saison-Jahr des Rennens. */
    @Column(name = "season_year", nullable = false)
    public int year;

    /** Rundennummer innerhalb der Saison (1-basiert). */
    @Column(nullable = false)
    public int round;

    /**
     * Tipp-Kategorie, z. B. "winner", "pole", "podium_p1", "fastest", "h2h_44v1".
     * Definiert, welche Art von Vorhersage getroffen wurde.
     */
    @Column(nullable = false, length = 16)
    public String category;

    /** Gewählte Antwort, z. B. Fahrerkürzel "VER" oder "HAM". */
    @Column(nullable = false, length = 64)
    public String pick;

    /** Zeitpunkt des Tipp-Eintrags. */
    @Column(name = "created_at")
    public Instant createdAt;

    /** Vergebene Punkte nach Auswertung (NULL = noch nicht abgerechnet). */
    public Integer points;

    /** Zeitpunkt der Abrechnung (NULL = noch ausstehend). */
    @Column(name = "settled_at")
    public Instant settledAt;

    /** Findet den Tipp eines Users für eine spezifische Kategorie eines Rennens. */
    public static Tipp findOne(long userId, int year, int round, String category) {
        return find("user.id = ?1 and year = ?2 and round = ?3 and category = ?4",
                userId, year, round, category).firstResult();
    }

    /** Alle Tipps aller User für ein bestimmtes Rennen (für Community-Ansicht und Abrechnung). */
    public static List<Tipp> findByRace(int year, int round) {
        return list("year = ?1 and round = ?2", year, round);
    }

    /** Alle Tipps eines Users in einer Saison, neueste zuerst (für die Profil-Ansicht). */
    public static List<Tipp> findByUserYear(long userId, int year) {
        return list("user.id = ?1 and year = ?2 order by round desc", userId, year);
    }
}
