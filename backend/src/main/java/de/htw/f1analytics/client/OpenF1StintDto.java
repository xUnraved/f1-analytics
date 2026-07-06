package de.htw.f1analytics.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenF1StintDto(
        @JsonProperty("session_key") Integer sessionKey,
        @JsonProperty("driver_number") Integer driverNumber,
        @JsonProperty("stint_number") Integer stintNumber,
        @JsonProperty("compound") String compound,
        @JsonProperty("lap_start") Integer lapStart,
        @JsonProperty("lap_end") Integer lapEnd
) {}
