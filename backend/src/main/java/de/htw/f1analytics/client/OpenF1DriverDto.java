package de.htw.f1analytics.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Fahrerprofil aus dem OpenF1-Endpunkt /v1/drivers.
 * Enthält alle relevanten Fahrer-Metadaten inkl. Foto-URL und Team-Farbe.
 *
 * teamColour wird ohne führendes "#" geliefert (z. B. "3671C6" für Red Bull),
 * muss also mit "#" präfixiert werden (siehe ReplayService/SeasonService).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenF1DriverDto(
        @JsonProperty("session_key") Integer sessionKey,
        @JsonProperty("driver_number") Integer driverNumber,
        @JsonProperty("broadcast_name") String broadcastName,
        /** Dreistelliges Kürzel (z. B. "VER", "HAM"). */
        @JsonProperty("name_acronym") String nameAcronym,
        @JsonProperty("first_name") String firstName,
        @JsonProperty("full_name") String fullName,
        @JsonProperty("last_name") String lastName,
        @JsonProperty("team_name") String teamName,
        /** Hex-Teamfarbe OHNE "#"-Präfix (z. B. "3671C6"). */
        @JsonProperty("team_colour") String teamColour,
        /** URL des offiziellen Fahrerfotos für das Quiz. */
        @JsonProperty("headshot_url") String headshotUrl,
        /** ISO-3166-1-Alpha-3-Ländercode (z. B. "NLD" für Niederlande). */
        @JsonProperty("country_code") String countryCode,
        @JsonProperty("meeting_key") Integer meetingKey
) {
}
