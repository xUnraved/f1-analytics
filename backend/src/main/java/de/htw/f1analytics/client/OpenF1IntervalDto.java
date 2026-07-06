package de.htw.f1analytics.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** interval/gap_to_leader sind bei OpenF1 je nach Fall Zahl (Sekunden), Text
 *  ("+1 LAP") oder null (z.B. für den Führenden) — daher Object, wie schon bei
 *  OpenF1ResultDto.duration/gapToLeader gehandhabt. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenF1IntervalDto(
        @JsonProperty("session_key") Integer sessionKey,
        @JsonProperty("driver_number") Integer driverNumber,
        @JsonProperty("interval") Object interval,
        @JsonProperty("date") String date
) {}
