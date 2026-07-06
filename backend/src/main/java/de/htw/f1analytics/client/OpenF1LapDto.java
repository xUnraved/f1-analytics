package de.htw.f1analytics.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenF1LapDto(
        @JsonProperty("session_key") Integer sessionKey,
        @JsonProperty("driver_number") Integer driverNumber,
        @JsonProperty("lap_number") Integer lapNumber,
        @JsonProperty("date_start") String dateStart,
        @JsonProperty("st_speed") Integer stSpeed,
        @JsonProperty("lap_duration") Double lapDuration,
        @JsonProperty("duration_sector_1") Double durationSector1,
        @JsonProperty("duration_sector_2") Double durationSector2,
        @JsonProperty("duration_sector_3") Double durationSector3,
        @JsonProperty("is_pit_out_lap") Boolean isPitOutLap
) {
}