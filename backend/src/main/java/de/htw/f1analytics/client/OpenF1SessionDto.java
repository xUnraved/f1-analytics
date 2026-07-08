package de.htw.f1analytics.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Session-Metadaten aus dem OpenF1-Endpunkt /v1/sessions.
 * Eine "Session" ist ein einzelner Programmteil eines Grand-Prix-Wochenendes:
 * Rennen, Qualifying, Qualifying 2/3, Sprint, Sprint Qualifying oder ein Training.
 *
 * Mehrere Sessions teilen sich einen meeting_key (das Grand-Prix-Wochenende).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenF1SessionDto(
        @JsonProperty("session_key") Integer sessionKey,
        /** Typ der Session: "Race", "Qualifying", "Sprint", "Practice 1/2/3". */
        @JsonProperty("session_name") String sessionName,
        @JsonProperty("session_type") String sessionType,
        @JsonProperty("country_name") String countryName,
        /** Austragungsort (Stadt/Strecke), z. B. "Silverstone". */
        @JsonProperty("location") String location,
        @JsonProperty("circuit_short_name") String circuitShortName,
        /** ISO-8601-Startzeit der Session. */
        @JsonProperty("date_start") String dateStart,
        @JsonProperty("year") Integer year,
        /** Gruppierschlüssel für alle Sessions eines Grand-Prix-Wochenendes. */
        @JsonProperty("meeting_key") Integer meetingKey
) {}
