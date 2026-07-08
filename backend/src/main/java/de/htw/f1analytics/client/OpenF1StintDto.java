package de.htw.f1analytics.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Stint-Daten aus dem OpenF1-Endpunkt /v1/stints.
 * Ein Stint ist ein Abschnitt zwischen zwei Boxenstopps.
 *
 * compound: Reifenmischung ("SOFT", "MEDIUM", "HARD", "INTERMEDIATE", "WET")
 * lapStart/lapEnd: Runden des Stint-Beginns/-Endes (für Timing-Panel-Anzeige).
 * lapEnd kann null sein, wenn der Stint noch läuft.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenF1StintDto(
        @JsonProperty("session_key") Integer sessionKey,
        @JsonProperty("driver_number") Integer driverNumber,
        @JsonProperty("stint_number") Integer stintNumber,
        /** Reifencompound: "SOFT", "MEDIUM", "HARD", "INTERMEDIATE", "WET". */
        @JsonProperty("compound") String compound,
        @JsonProperty("lap_start") Integer lapStart,
        @JsonProperty("lap_end") Integer lapEnd
) {}
