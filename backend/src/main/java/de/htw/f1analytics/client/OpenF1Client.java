package de.htw.f1analytics.client;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

/**
 * MicroProfile REST-Client für die OpenF1 API (https://api.openf1.org/v1).
 * Konfiguration in application.properties: quarkus.rest-client.openf1.url
 *
 * Alle Methoden sind synchron und werden mit THROTTLE_MS Pause zwischen Aufrufen
 * ausgeführt (in SeasonService/ReplayService), um das Rate-Limit zu respektieren.
 *
 * HTTP 429 → 65 s Pause + Retry (max. MAX_RETRY Versuche)
 * HTTP 401 mit "Live F1 session" → API gesperrt während laufender Session
 */
@RegisterRestClient(configKey = "openf1")
@Path("/v1")
public interface OpenF1Client {

    /** Alle Sessions eines Jahres (Rennen, Qualifying, Training). */
    @GET
    @Path("/sessions")
    List<OpenF1SessionDto> getSessions(@QueryParam("year") int year);

    /** Ergebnisliste einer Session (Positionen, Zeitabstände, DNF/DNS/DSQ). */
    @GET
    @Path("/session_result")
    List<OpenF1ResultDto> getResults(@QueryParam("session_key") int sessionKey);

    /** Fahrerdaten einer Session (Name, Team, Headshot-URL, Ländercode). */
    @GET
    @Path("/drivers")
    List<OpenF1DriverDto> getDrivers(@QueryParam("session_key") int sessionKey);

    /** Meetings eines Jahres (Grand-Prix-Wochenenden, Streckenbilder, Flaggen). */
    @GET
    @Path("/meetings")
    List<OpenF1MeetingDto> getMeetings(@QueryParam("year") int year);

    /** Startaufstellung einer Session (Position + Qualifying-Rundenzeit). */
    @GET
    @Path("/starting_grid")
    List<OpenF1GridDto> getStartingGrid(@QueryParam("session_key") int sessionKey);

    /** Runden-Daten einer Session (Zeiten, Sektoren, Topspeed, Pit-Out-Lap). */
    @GET
    @Path("/laps")
    List<OpenF1LapDto> getLaps(@QueryParam("session_key") int sessionKey);

    /**
     * GPS-Positionsdaten pro Fahrer (~3-4 Hz, kartesische X/Y-Koordinaten).
     * Wird pro Fahrer einzeln abgerufen, da die API einen driver_number-Filter
     * benötigt, um das Datenvolumen zu begrenzen.
     */
    @GET
    @Path("/location")
    List<OpenF1LocationDto> getLocations(@QueryParam("session_key") int sessionKey,
                                         @QueryParam("driver_number") int driverNumber);

    /** Reifenstint-Daten (Compound, Startrunde, Endrunde pro Fahrer). */
    @GET
    @Path("/stints")
    List<OpenF1StintDto> getStints(@QueryParam("session_key") int sessionKey);

    /** Positionsverlauf (Live-Reihenfolge mit Zeitstempel, ~1 Hz). */
    @GET
    @Path("/position")
    List<OpenF1PositionDto> getPosition(@QueryParam("session_key") int sessionKey);

    /** Race Control-Ereignisse (Flaggen, Nachrichten, Strafen). */
    @GET
    @Path("/race_control")
    List<OpenF1RaceControlDto> getRaceControl(@QueryParam("session_key") int sessionKey);

    /** Zeitabstände zwischen Fahrern (Intervall/Gap-to-Leader in Sekunden). */
    @GET
    @Path("/intervals")
    List<OpenF1IntervalDto> getIntervals(@QueryParam("session_key") int sessionKey);
}
