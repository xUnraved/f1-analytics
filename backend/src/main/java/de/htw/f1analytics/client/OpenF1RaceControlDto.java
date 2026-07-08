package de.htw.f1analytics.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Race-Control-Ereignisse aus dem OpenF1-Endpunkt /v1/race_control.
 * Enthält Flaggen (GREEN, YELLOW, RED, BLUE, CHEQUERED), Safety-Car-Meldungen und Strafen.
 *
 * flag: Flaggenwert, z. B. "GREEN", "YELLOW", "DOUBLE YELLOW", "RED", "BLUE", "CHEQUERED".
 * scope: Geltungsbereich der Flagge, z. B. "Track" (gesamte Strecke) oder Sektor-Bezeichnung.
 * message: Freitext-Nachricht der Race Control (z. B. Strafbegründungen).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenF1RaceControlDto(
        @JsonProperty("session_key") Integer sessionKey,
        @JsonProperty("category") String category,
        /** Flaggentyp: "GREEN", "YELLOW", "DOUBLE YELLOW", "RED", "BLUE", "CHEQUERED". */
        @JsonProperty("flag") String flag,
        /** Geltungsbereich: "Track" oder spezifischer Sektor. */
        @JsonProperty("scope") String scope,
        /** Freitext-Meldung der Race Control. */
        @JsonProperty("message") String message,
        /** ISO-8601-Zeitstempel des Ereignisses. */
        @JsonProperty("date") String date
) {}
