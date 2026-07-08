package de.htw.f1analytics.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Cacht das vollständig aufbereitete Ergebnis-Objekt eines einzelnen Rennens als JSON.
 * Primärschlüssel ist der OpenF1-session_key des Rennens.
 *
 * Ermöglicht schnelle Einzelabrufe ohne erneuten API-Call, solange die Daten
 * konsistent sind. Die Spalte year erlaubt effizientes Löschen einer ganzen Saison.
 */
@Entity
@Table(name = "race_cache")
public class RaceCacheEntity extends PanacheEntityBase {

    /** OpenF1-session_key des Rennens als natürlicher Primärschlüssel. */
    @Id
    @Column(name = "session_key")
    public int sessionKey;

    /** Saison-Jahr – ermöglicht jahresweises Löschen des Caches. */
    @Column(name = "year", nullable = false)
    public int year;

    /** Serialisiertes RaceResult-JSON. TEXT-Typ wegen potenziell großer Fahrerlisten. */
    @Column(name = "race_json", columnDefinition = "TEXT", nullable = false)
    public String raceJson;

    /** Zeitpunkt des Cache-Eintrags für Debugging und Cache-Alter-Diagnose. */
    @Column(name = "cached_at")
    public Instant cachedAt;
}
