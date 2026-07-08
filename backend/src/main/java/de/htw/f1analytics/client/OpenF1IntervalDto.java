package de.htw.f1analytics.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Zeitabstands-Daten aus dem OpenF1-Endpunkt /v1/intervals.
 *
 * interval ist der Abstand zum direkt vorausfahrenden Fahrer in Sekunden.
 * Der Typ ist Object, da OpenF1 je nach Situation verschiedene Werte liefert:
 *   - Double: Abstand in Sekunden (z. B. 1.234)
 *   - String: "+1 LAP" (überrundeter Fahrer oder Führender zu sich selbst)
 *   - null: Führender (kein Abstand messbar)
 * Parsing-Logik: ReplayTimingService.parseGap()
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenF1IntervalDto(
        @JsonProperty("session_key") Integer sessionKey,
        @JsonProperty("driver_number") Integer driverNumber,
        /**
         * Zeitabstand zum Vordermann: Double (Sekunden), "+1 LAP" (String) oder null.
         * @see de.htw.f1analytics.service.ReplayTimingService#parseGap(Object)
         */
        @JsonProperty("interval") Object interval,
        /** ISO-8601-Zeitstempel der Messung. */
        @JsonProperty("date") String date
) {}
