package de.htw.f1analytics.api;

import de.htw.f1analytics.service.SeasonService;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/**
 * REST-Endpunkte für Saison- und Renndaten.
 * Alle Pfade liegen unter /api.
 *
 * Lesende Endpunkte delegieren direkt an SeasonService, der intern
 * einen zweistufigen Cache (RAM + PostgreSQL) verwaltet.
 * @Blocking-Annotation ist nötig, da SeasonService synchrone DB-/API-Calls macht.
 */
@Path("/api")
public class SeasonResource {

    @Inject
    SeasonService seasonService;

    /** Gibt alle bekannten Saison-Jahre zurück (z. B. [2023, 2024, 2025]). */
    @GET
    @Path("/seasons")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Integer> seasons() {
        return seasonService.years();
    }

    /**
     * Gibt die vollständigen aggregierten Statistiken einer Saison zurück.
     * Wird bei der ersten Anfrage aus der OpenF1-API aufgebaut (dauert Minuten)
     * und danach aus dem Cache bedient.
     */
    @GET
    @Path("/season")
    @Produces(MediaType.APPLICATION_JSON)
    public SeasonService.SeasonStats season(@QueryParam("year") int year) {
        return seasonService.seasonStats(year);
    }

    /**
     * Löscht den kompletten Cache einer Saison (RAM + DB).
     * Der nächste GET /season?year=X baut ihn neu auf.
     */
    @DELETE
    @Path("/season/cache")
    @Blocking
    public void clearCache(@QueryParam("year") int year) {
        seasonService.clearCache(year);
    }

    /** Löscht einen einzelnen Renncache, ohne die gesamte Saison zu invalidieren. */
    @DELETE
    @Path("/race")
    public void clearRace(@QueryParam("session_key") int sessionKey, @QueryParam("year") int year) {
        seasonService.clearRace(sessionKey, year);
    }

    /**
     * Löscht nur dieses eine Rennen aus dem Cache und lädt es synchron neu von der OpenF1-API.
     * Gibt die vollständig aktualisierten SeasonStats zurück — kein Polling nötig.
     */
    @POST
    @Path("/race/refresh")
    @Produces(MediaType.APPLICATION_JSON)
    @Blocking
    public SeasonService.SeasonStats refreshSingleRace(
            @QueryParam("session_key") int sessionKey,
            @QueryParam("year") int year) {
        return seasonService.refreshSingleRace(sessionKey, year);
    }
}
