package de.htw.f1analytics.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenF1DriverDto(
        @JsonProperty("session_key") Integer sessionKey,
        @JsonProperty("driver_number") Integer driverNumber,
        @JsonProperty("broadcast_name") String broadcastName,
        @JsonProperty("name_acronym") String nameAcronym,
        @JsonProperty("first_name") String firstName,
        @JsonProperty("full_name") String fullName,
        @JsonProperty("last_name") String lastName,
        @JsonProperty("team_name") String teamName,
        @JsonProperty("team_colour") String teamColour,
        @JsonProperty("headshot_url") String headshotUrl,
        @JsonProperty("country_code") String countryCode,
        @JsonProperty("meeting_key") Integer meetingKey
) {
}