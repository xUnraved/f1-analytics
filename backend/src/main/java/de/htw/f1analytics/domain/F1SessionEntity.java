package de.htw.f1analytics.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.util.List;

/**
 * Repräsentiert eine F1-Session (Rennen, Qualifying oder Training).
 * Der session_key von OpenF1 dient direkt als Primärschlüssel.
 * Enthält neben Meta-Daten auch Bild-URLs für Streckenlayout und Landesflagge.
 */
@Entity
@Table(name = "f1_session", indexes = {
        @Index(name = "idx_session_year", columnList = "year"),
        @Index(name = "idx_session_meeting", columnList = "meeting_key")
})
public class F1SessionEntity extends PanacheEntityBase {

    /** OpenF1 session_key als natürlicher Primärschlüssel (keine Auto-Generierung nötig). */
    @Id
    @Column(name = "session_key")
    public int sessionKey;

    /** Saison-Jahr (z. B. 2024). */
    public int year;

    /** Meeting-Schlüssel, um alle Sessions eines Grand-Prix-Wochenendes zu gruppieren. */
    @Column(name = "meeting_key")
    public Integer meetingKey;

    /** Typ der Session: "Race", "Qualifying", "Sprint", "Practice 1/2/3". */
    @Column(name = "session_name")
    public String sessionName;

    /** Austragungsort (Stadt/Strecke), z. B. "Silverstone". */
    public String location;

    /** Vollständiger Landesname, z. B. "United Kingdom". */
    @Column(name = "country_name")
    public String countryName;

    /** Kurzname der Strecke für UI-Anzeige, z. B. "Silverstone". */
    @Column(name = "circuit_short_name")
    public String circuitShortName;

    /** ISO-8601-Startzeitpunkt der Session (String, da direkt von API übernommen). */
    @Column(name = "date_start")
    public String dateStart;

    /** Base64-kodiertes SVG oder URL des Streckenlayouts (TEXT, da potenziell groß). */
    @Column(name = "circuit_image", columnDefinition = "text")
    public String circuitImage;

    /** URL oder Base64-kodiertes Bild der Landesflagge. */
    @Column(name = "country_flag", columnDefinition = "text")
    public String countryFlag;

    /** Geographische Breite der Strecke (für den 3D-Globus). */
    public double lat;

    /** Geographische Länge der Strecke (für den 3D-Globus). */
    public double lon;

    /** Lädt eine Session direkt per session_key (PanacheEntityBase::findById-Wrapper). */
    public static F1SessionEntity findByKey(int key) {
        return findById(key);
    }

    /** Gibt alle Sessions eines bestimmten Jahres und Typs zurück (z. B. alle Rennen 2024). */
    public static List<F1SessionEntity> findByYearAndName(int year, String sessionName) {
        return list("year = ?1 and sessionName = ?2", year, sessionName);
    }

    /** Schnelle Prüfung, ob für ein Jahr bereits Session-Daten im Cache vorhanden sind. */
    public static boolean existsForYear(int year) {
        return count("year", year) > 0;
    }
}
