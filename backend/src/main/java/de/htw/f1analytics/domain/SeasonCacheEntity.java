package de.htw.f1analytics.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.time.Instant;

/**
 * Speichert die aggregierten Saisondaten einer kompletten F1-Saison als JSON-Blob.
 * Der SeasonCacheStore legt hier das berechnete StatsResponse-Objekt ab, damit bei
 * wiederholten Anfragen kein teurer API-Aggregations-Lauf nötig ist.
 *
 * Der Primärschlüssel ist das Jahr selbst (eine Zeile pro Saison).
 */
@Entity
public class SeasonCacheEntity extends PanacheEntityBase {

    /** Saison-Jahr als natürlicher Primärschlüssel (z. B. 2024). */
    @Id
    @Column(name = "season_year")
    public int year;

    /** Serialisiertes StatsResponse-JSON. TEXT-Typ, da mehrere MB groß sein kann. */
    @Column(columnDefinition = "TEXT", nullable = false)
    public String statsJson;

    /** Zeitpunkt der letzten Cache-Befüllung (für Cache-Alter-Berechnungen). */
    public Instant cachedAt;

    /**
     * Versionsnummer des Cache-Formats. Wird bei strukturellen Änderungen
     * inkrementiert, damit veraltete JSON-Blobs erkannt und verworfen werden können.
     */
    public Integer cacheVersion;
}
