package de.htw.f1analytics.api;

import de.htw.f1analytics.service.ForecastService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/forecast")
@Produces(MediaType.APPLICATION_JSON)
public class ForecastResource {

    @Inject
    ForecastService forecastService;

    @GET
    @Path("/{year}")
    public ForecastService.Forecast forecast(@PathParam("year") int year) {
        return forecastService.forecast(year);
    }
}