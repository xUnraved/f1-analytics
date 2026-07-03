package de.htw.f1analytics.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenF1LapDto(
        @JsonProperty("session_key") Integer sessionKey,
        @JsonProperty("driver_number") Integer driverNumber,
        @JsonProperty("st_speed") Integer stSpeed,
        @JsonProperty("lap_duration") Double lapDuration,
        @JsonProperty("is_pit_out_lap") Boolean isPitOutLap
) {
}