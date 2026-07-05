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

@Path("/api")
public class SeasonResource {

    @Inject
    SeasonService seasonService;

    @GET
    @Path("/seasons")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Integer> seasons() {
        return seasonService.years();
    }

    @GET
    @Path("/season")
    @Produces(MediaType.APPLICATION_JSON)
    public SeasonService.SeasonStats season(@QueryParam("year") int year) {
        return seasonService.seasonStats(year);
    }

    @DELETE
    @Path("/season/cache")
    @Blocking
    public void clearCache(@QueryParam("year") int year) {
        seasonService.clearCache(year);
    }

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