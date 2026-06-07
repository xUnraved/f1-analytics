package de.htw.f1analytics.api;

import de.htw.f1analytics.domain.SessionEntity;
import de.htw.f1analytics.service.SessionService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
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
}