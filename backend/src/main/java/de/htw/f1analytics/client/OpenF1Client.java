package de.htw.f1analytics.client;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

@RegisterRestClient(configKey = "openf1")
@Path("/v1")
public interface OpenF1Client {

    @GET
    @Path("/sessions")
    List<OpenF1SessionDto> getSessions(@QueryParam("year") int year);
}