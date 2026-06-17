package de.htw.f1analytics.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OpenF1DriverDto(
        @JsonProperty("session_key") Integer sessionKey,
        @JsonProperty("driver_number") Integer driverNumber,
        @JsonProperty("broadcast_name") String broadcastName,
        @JsonProperty("first_name") String firstName,
        @JsonProperty("full_name") String fullName,
        @JsonProperty("last_name") String lastName,
        @JsonProperty("team_name") String teamName,
        @JsonProperty("meeting_key") Integer meetingKey
) {
}
