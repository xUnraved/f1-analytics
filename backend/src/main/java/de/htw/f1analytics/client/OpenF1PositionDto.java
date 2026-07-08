package de.htw.f1analytics.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Live-Positionsdaten aus dem OpenF1-Endpunkt /v1/position.
 * Liefert die aktuelle Rennposition eines Fahrers zu einem Zeitpunkt (~1 Hz).
 * Wird im Timing-Panel des Replays für den Positionsverlauf-Chart verwendet.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenF1PositionDto(
        @JsonProperty("session_key") Integer sessionKey,
        @JsonProperty("driver_number") Integer driverNumber,
        /** Aktuelle Rennposition (1-basiert). */
        @JsonProperty("position") Integer position,
        /** ISO-8601-Zeitstempel der Positionsmeldung. */
        @JsonProperty("date") String date
) {}
