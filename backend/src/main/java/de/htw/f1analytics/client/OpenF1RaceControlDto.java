package de.htw.f1analytics.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenF1RaceControlDto(
        @JsonProperty("session_key") Integer sessionKey,
        @JsonProperty("category") String category,
        @JsonProperty("flag") String flag,
        @JsonProperty("scope") String scope,
        @JsonProperty("message") String message,
        @JsonProperty("date") String date
) {}
