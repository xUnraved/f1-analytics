package de.htw.f1analytics.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.util.List;

/**
 * Persistierte GPS-Position eines Fahrers innerhalb einer Session.
 * Die Tabelle f1_location wird von OpenF1 (/location) befüllt und
 * dient als DB-Cache-Schicht für den ReplayService.
 *
 * Zwei Composite-Indizes beschleunigen die häufigsten Zugriffsmuster:
 *   1. Alle Positionen einer Session (Replay laden)
 *   2. Positionen je Session+Fahrer (Fahrer-Filtern)
 */
@Entity
@Table(name = "f1_location", indexes = {
        @Index(name = "idx_location_session", columnList = "session_key"),
        @Index(name = "idx_location_session_driver", columnList = "session_key, driver_number")
})
public class F1LocationEntity extends PanacheEntityBase {

    /** Surrogatschlüssel mit hochem allocationSize (5000) für Bulk-Inserts. */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "f1_location_seq")
    @SequenceGenerator(name = "f1_location_seq", sequenceName = "f1_location_seq", allocationSize = 5000)
    public Long id;

    /** Fremdschlüssel zur OpenF1-Session (nicht als FK modelliert, da schemalos). */
    @Column(name = "session_key")
    public int sessionKey;

    /** Fahrernummer laut OpenF1 (z. B. 1 = Verstappen, 44 = Hamilton). */
    @Column(name = "driver_number")
    public int driverNumber;

    /**
     * Zeitstempel in Millisekunden ab Sessionbeginn.
     * Boxed Long statt primitiv long, damit NULL-Werte aus Legacy-Zeilen
     * (vor Einführung der Spalte) nicht zu einer NPE führen.
     * Null-Zeilen werden durch die findBySession-Query ausgeschlossen.
     */
    @Column(name = "t_ms")
    public Long tMs;

    /** Kartesische X-Koordinate aus dem OpenF1-Koordinatensystem. */
    public double x;

    /** Kartesische Y-Koordinate aus dem OpenF1-Koordinatensystem. */
    public double y;

    /** Prüft, ob mindestens ein Datensatz für diese Session existiert (Cache-Hit-Check). */
    public static boolean existsForSession(int sessionKey) {
        return count("sessionKey", sessionKey) > 0;
    }

    /**
     * Lädt alle gültigen Positionspunkte einer Session sortiert nach Zeit und Fahrer.
     * NULL-tMs-Zeilen werden bewusst ausgeschlossen (Legacy-Daten ohne Zeitstempel).
     */
    public static List<F1LocationEntity> findBySession(int sessionKey) {
        return list("sessionKey = ?1 AND tMs IS NOT NULL order by tMs, driverNumber", sessionKey);
    }

    /** Löscht alle Positionsdaten einer Session (wird bei inkonsistenten Daten aufgerufen). */
    public static void deleteBySession(int sessionKey) {
        delete("sessionKey", sessionKey);
    }
}
