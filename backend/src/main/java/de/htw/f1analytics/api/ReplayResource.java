package de.htw.f1analytics.api;

import de.htw.f1analytics.service.ReplayService;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/api")
public class ReplayResource {

    @Inject
    ReplayService replayService;

    @GET
    @Path("/replay")
    @Produces(MediaType.APPLICATION_JSON)
    @Blocking
    public ReplayService.ReplayData replay(@QueryParam("session_key") int sessionKey,
                                           @QueryParam("date_start") String dateStart) {
        return replayService.getReplay(sessionKey, dateStart);
    }

    @DELETE
    @Path("/replay")
    @Blocking
    public void clearReplay(@QueryParam("session_key") int sessionKey) {
        replayService.clearReplay(sessionKey);
    }
}
