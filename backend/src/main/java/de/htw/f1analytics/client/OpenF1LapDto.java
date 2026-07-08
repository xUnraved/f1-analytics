package de.htw.f1analytics.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Runden-Daten eines Fahrers aus dem OpenF1-Endpunkt /v1/laps.
 * Enthält Zeitmessungen für Rundenzeiten, Sektoren und den Speed-Trap.
 *
 * stSpeed (Speed Trap): Höchstgeschwindigkeit an einem festen Messpunkt
 * der Geraden (Speed Trap), in km/h.
 *
 * isPitOutLap: true wenn der Fahrer in dieser Runde aus der Box gefahren ist
 * (Runde nicht für Fastest-Lap-Auswertung verwenden, da deutlich langsamer).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenF1LapDto(
        @JsonProperty("session_key") Integer sessionKey,
        @JsonProperty("driver_number") Integer driverNumber,
        @JsonProperty("lap_number") Integer lapNumber,
        /** ISO-8601-Zeitstempel des Rundenbeginns (für Timing-Panel-Synchronisierung). */
        @JsonProperty("date_start") String dateStart,
        /** Speed-Trap-Geschwindigkeit in km/h. */
        @JsonProperty("st_speed") Integer stSpeed,
        /** Gesamtrundenzeit in Sekunden. */
        @JsonProperty("lap_duration") Double lapDuration,
        @JsonProperty("duration_sector_1") Double durationSector1,
        @JsonProperty("duration_sector_2") Double durationSector2,
        @JsonProperty("duration_sector_3") Double durationSector3,
        @JsonProperty("is_pit_out_lap") Boolean isPitOutLap
) {
}
