package de.htw.f1analytics.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Startaufstellungs-Eintrag aus dem OpenF1-Endpunkt /v1/starting_grid.
 * Wird verwendet, wenn keine separaten Qualifying-Session-Daten verfügbar sind
 * (Fallback für Sprint-Sessions ohne eigenes Qualifying oder fehlende Daten).
 * lapDuration enthält die Qualifying-Rundenzeit des Fahrers (für F1alytics-Score Q-Komponente).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenF1GridDto(
        @JsonProperty("session_key") Integer sessionKey,
        @JsonProperty("driver_number") Integer driverNumber,
        /** Startposition (1-basiert). */
        @JsonProperty("position") Integer position,
        /** Beste Qualifying-Rundenzeit in Sekunden. */
        @JsonProperty("lap_duration") Double lapDuration,
        @JsonProperty("meeting_key") Integer meetingKey
) {}
