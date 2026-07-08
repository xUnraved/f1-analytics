package de.htw.f1analytics.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.util.List;

/**
 * Persistiertes Einzelergebnis eines Fahrers in einer F1-Session.
 * Wird für alle Session-Typen verwendet: Race, Qualifying und Practice.
 * Felder, die nur in bestimmten Session-Typen relevant sind, bleiben NULL.
 */
@Entity
@Table(name = "f1_result", indexes = {
        @Index(name = "idx_result_session", columnList = "session_key"),
        @Index(name = "idx_result_session_driver", columnList = "session_key, driver_number")
})
public class F1ResultEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "f1_result_seq")
    @SequenceGenerator(name = "f1_result_seq", sequenceName = "f1_result_seq", allocationSize = 50)
    public Long id;

    /** Schlüssel der zugehörigen OpenF1-Session. */
    @Column(name = "session_key")
    public int sessionKey;

    /** Fahrernummer laut OpenF1. */
    @Column(name = "driver_number")
    public int driverNumber;

    /** Dreistelliges Kürzel (z. B. "VER", "HAM"). */
    public String abbr;

    /** Vollständiger Fahrername. */
    public String name;

    /** Teamname des Fahrers. */
    public String team;

    /** Teamfarbe als Hex-Code (z. B. "#3671C6" für Red Bull). */
    public String color;

    /** Finale Position in der Session (1-basiert). */
    public int pos;

    /** WM-Punkte – nur bei Race-Sessions belegt, sonst NULL. */
    public Integer pts;

    /** Zeitabstand zum Führenden als formatierten String (z. B. "+5.123s"). Nur Race. */
    @Column(name = "gap_text")
    public String gapText;

    /** Beste Rundenzeit als formatierten String. Nur Qualifying und Training. */
    @Column(name = "best_lap")
    public String bestLap;

    /** Did Not Finish – Fahrer hat das Rennen nicht beendet. */
    public boolean dnf;

    /** Did Not Start – Fahrer ist nicht gestartet. */
    public boolean dns;

    /** Disqualified – Fahrer wurde aus der Wertung gestrichen. */
    public boolean dsq;

    /** Anzahl absolvierter Runden (nur Race). */
    public Integer laps;

    /** Gibt alle Ergebnisse einer Session aufsteigend nach Position zurück. */
    public static List<F1ResultEntity> findBySession(int sessionKey) {
        return list("sessionKey = ?1 order by pos", sessionKey);
    }

    /** Cache-Hit-Prüfung: Existiert bereits mindestens ein Ergebnis für diese Session? */
    public static boolean existsForSession(int sessionKey) {
        return count("sessionKey", sessionKey) > 0;
    }

    /** Löscht alle Ergebnisse einer Session (z. B. bei Cache-Invalidierung). */
    public static void deleteBySession(int sessionKey) {
        delete("sessionKey", sessionKey);
    }
}
