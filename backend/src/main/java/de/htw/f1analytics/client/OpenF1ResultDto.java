package de.htw.f1analytics.client;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Ergebnisdaten eines Fahrers aus dem OpenF1-Endpunkt /v1/session_result.
 * Wird für Rennen, Qualifying und Training verwendet.
 *
 * duration und gapToLeader sind Object (nicht Double), da OpenF1 sie je nach Kontext
 * als Zahl (Sekunden), String ("+1 LAP") oder null liefert.
 * Siehe gapText()-Methode in SeasonService für die Formatierungslogik.
 */
public record OpenF1ResultDto(
        @JsonProperty("session_key") Integer sessionKey,
        @JsonProperty("driver_number") Integer driverNumber,
        /** Finale Position (1-basiert). */
        @JsonProperty("position") Integer position,
        @JsonProperty("number_of_laps") Integer numberOfLaps,
        /** Gesamtzeit (Rennen) oder beste Rundenzeit (Quali/Training). Typ variiert. */
        @JsonProperty("duration") Object duration,
        /** Zeitabstand zum Führenden. Typ variiert: Double, String oder null. */
        @JsonProperty("gap_to_leader") Object gapToLeader,
        @JsonProperty("dnf") Boolean dnf,
        @JsonProperty("dns") Boolean dns,
        @JsonProperty("dsq") Boolean dsq,
        @JsonProperty("meeting_key") Integer meetingKey
) {
}
