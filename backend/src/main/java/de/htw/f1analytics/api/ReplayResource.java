package de.htw.f1analytics.api;

import de.htw.f1analytics.service.ReplayService;
import de.htw.f1analytics.service.ReplayTimingService;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/**
 * REST-Endpunkte für den GPS-Renn-Replay.
 *
 * /api/replay        → GPS-Positionen aller Fahrer als Frame-Sequenz
 * /api/replay/timing → Runden-/Stint-/Intervalldaten für das Timing-Panel
 *
 * Beide Endpunkte sind @Blocking, da sie bei Cache-Misses synchron
 * die OpenF1-API abfragen und in PostgreSQL schreiben.
 */
@Path("/api")
public class ReplayResource {

    @Inject
    ReplayService replayService;

    @Inject
    ReplayTimingService replayTimingService;

    /**
     * Lädt die GPS-Replay-Daten einer Session.
     * Beim ersten Aufruf: API-Abruf (~Sekunden), danach aus RAM-/DB-Cache.
     *
     * @param sessionKey  OpenF1-session_key des Rennens
     * @param dateStart   ISO-8601-Startzeit der Session (für API-Filterung)
     */
    @GET
    @Path("/replay")
    @Produces(MediaType.APPLICATION_JSON)
    @Blocking
    public ReplayService.ReplayData replay(@QueryParam("session_key") int sessionKey,
                                           @QueryParam("date_start") String dateStart) {
        return replayService.getReplay(sessionKey, dateStart);
    }

    /**
     * Löscht RAM- und DB-Cache für Replay und Timing einer Session.
     * Nützlich, wenn die API neue Daten liefert oder der Cache inkonsistent ist.
     */
    @DELETE
    @Path("/replay")
    @Blocking
    public void clearReplay(@QueryParam("session_key") int sessionKey) {
        replayService.clearReplay(sessionKey);
        replayTimingService.clearTiming(sessionKey);
    }

    /**
     * Lädt die Timing-Daten (Runden, Sektoren, Intervalle, Stints) einer Session.
     * Wird vom Frontend für das Live-Timing-Panel neben dem Replay verwendet.
     */
    @GET
    @Path("/replay/timing")
    @Produces(MediaType.APPLICATION_JSON)
    @Blocking
    public ReplayTimingService.TimingData timing(@QueryParam("session_key") int sessionKey,
                                                  @QueryParam("date_start") String dateStart) {
        return replayTimingService.getTiming(sessionKey, dateStart);
    }
}
