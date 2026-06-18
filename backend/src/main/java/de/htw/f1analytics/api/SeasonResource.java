package de.htw.f1analytics.api;

import de.htw.f1analytics.service.SeasonService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
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
}