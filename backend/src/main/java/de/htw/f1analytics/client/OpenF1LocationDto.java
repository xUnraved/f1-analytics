package de.htw.f1analytics.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * GPS-Positionspunkt aus dem OpenF1-Endpunkt /v1/location.
 * x und y sind kartesische Koordinaten im Strecken-Koordinatensystem
 * (keine Geo-Koordinaten, keine Einheit definiert).
 * date ist ein ISO-8601-Zeitstempel mit bis zu Mikrosekunden-Auflösung.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenF1LocationDto(
        @JsonProperty("session_key") Integer sessionKey,
        @JsonProperty("driver_number") Integer driverNumber,
        @JsonProperty("x") Double x,
        @JsonProperty("y") Double y,
        /** ISO-8601-Zeitstempel des GPS-Messpunkts (ca. 3-4 Hz). */
        @JsonProperty("date") String date
) {}
