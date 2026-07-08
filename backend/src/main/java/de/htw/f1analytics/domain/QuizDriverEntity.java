package de.htw.f1analytics.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.util.List;

/**
 * Fahrerprofil für das Quiz-System.
 * Wird beim ersten Saison-Load aus OpenF1-Fahrerdaten befüllt und
 * durch den QuizService um statische Geburtsjahresdaten ergänzt.
 *
 * Primärschlüssel ist die Fahrernummer (eindeutig über alle Saisons).
 */
@Entity
@Table(name = "quiz_driver")
public class QuizDriverEntity extends PanacheEntityBase {

    /** Fahrernummer (1 = Verstappen, 44 = Hamilton usw.) als natürlicher Primärschlüssel. */
    @Id
    @Column(name = "driver_number")
    public int driverNumber;

    /** Dreistelliges Kürzel (z. B. "VER"). */
    public String abbr;

    /** Vollständiger Fahrername für die Anzeige im Quiz. */
    public String name;

    /** URL des offiziellen Fahrerfotos von OpenF1 (für Fahrer-Quiz). */
    @Column(name = "headshot_url", columnDefinition = "text")
    public String headshotUrl;

    /** ISO-3166-1-Alpha-3-Ländercode (z. B. "NLD"). */
    @Column(name = "country_code")
    public String countryCode;

    /** Vollständiger Landesname auf Englisch (Quiz-Antwort). */
    @Column(name = "country_name")
    public String countryName;

    /**
     * Geburtsjahr des Fahrers – wird nicht von OpenF1 geliefert, sondern
     * aus der statischen BIRTH_YEARS-Map im QuizService beim Startup befüllt.
     */
    @Column(name = "birth_year")
    public Integer birthYear;

    /** Alle Fahrer mit Foto und Landesname (für Fahrer- und Länder-Quiz). */
    public static List<QuizDriverEntity> withHeadshotAndCountry() {
        return list("headshotUrl IS NOT NULL AND countryName IS NOT NULL");
    }

    /** Alle Fahrer mit Foto und Geburtsjahr (für den Alters-Quiz-Modus). */
    public static List<QuizDriverEntity> withBirthYear() {
        return list("headshotUrl IS NOT NULL AND birthYear IS NOT NULL");
    }
}
