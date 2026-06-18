package de.htw.f1analytics.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenF1SessionDto(
        @JsonProperty("session_key") Integer sessionKey,
        @JsonProperty("session_name") String sessionName,
        @JsonProperty("session_type") String sessionType,
        @JsonProperty("country_name") String countryName,
        @JsonProperty("location") String location,
        @JsonProperty("circuit_short_name") String circuitShortName,
        @JsonProperty("date_start") String dateStart,
        @JsonProperty("year") Integer year
) {}