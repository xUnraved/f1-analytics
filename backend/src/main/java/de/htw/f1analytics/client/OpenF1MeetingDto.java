package de.htw.f1analytics.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Meeting-Metadaten aus dem OpenF1-Endpunkt /v1/meetings.
 * Ein Meeting entspricht einem Grand-Prix-Wochenende und gruppiert
 * mehrere Sessions (Training, Qualifying, Rennen).
 *
 * circuitImage und countryFlag werden als Base64-URLs oder externe URLs geliefert
 * und im F1SessionEntity für die UI-Anzeige gespeichert.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenF1MeetingDto(
        @JsonProperty("meeting_key") Integer meetingKey,
        /** Austragungsort, dient als Join-Key mit F1SessionEntity.location. */
        @JsonProperty("location") String location,
        /** Base64-kodiertes SVG oder URL des Streckenlayout-Bilds. */
        @JsonProperty("circuit_image") String circuitImage,
        /** URL oder Base64-Bild der Landesflagge. */
        @JsonProperty("country_flag") String countryFlag
) {}
