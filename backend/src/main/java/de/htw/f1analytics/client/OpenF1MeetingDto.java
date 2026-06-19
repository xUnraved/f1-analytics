package de.htw.f1analytics.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenF1MeetingDto(
        @JsonProperty("meeting_key") Integer meetingKey,
        @JsonProperty("location") String location,
        @JsonProperty("circuit_image") String circuitImage
) {}
