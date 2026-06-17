package de.htw.f1analytics.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OpenF1ResultDto(
        @JsonProperty("session_key") Integer sessionKey,
        @JsonProperty("driver_number") Integer driverNumber,
        @JsonProperty("position") Integer position,
        @JsonProperty("number_of_laps") Integer numberOfLaps,
        @JsonProperty("duration") Double duration,
        @JsonProperty("gap_to_leader") Object gapToLeader,
        @JsonProperty("dnf") Boolean dnf,
        @JsonProperty("dns") Boolean dns,
        @JsonProperty("dsq") Boolean dsq,
        @JsonProperty("meeting_key") Integer meetingKey
) {
}
