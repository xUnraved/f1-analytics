package de.htw.f1analytics.api;

import de.htw.f1analytics.client.OpenF1DriverDto;
import de.htw.f1analytics.client.OpenF1ResultDto;
import de.htw.f1analytics.domain.SessionEntity;
import de.htw.f1analytics.service.SessionService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/api/sessions")
public class SessionResource {

    @Inject
    SessionService sessionService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<SessionEntity> getSessions(@QueryParam("year") int year) {
        return sessionService.getSessionsByYear(year);
    }

    @GET
    @Path("/{sessionKey}/results")
    @Produces(MediaType.APPLICATION_JSON)
    public List<OpenF1ResultDto> getRaceResults(@PathParam("sessionKey") int sessionKey) {
        return sessionService.getRaceResults(sessionKey);
    }

    @GET
    @Path("/{sessionKey}/drivers")
    @Produces(MediaType.APPLICATION_JSON)
    public List<OpenF1DriverDto> getDrivers(@PathParam("sessionKey") int sessionKey) {
        return sessionService.getDrivers(sessionKey);
    }
}