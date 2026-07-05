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

    @GET
    @Path("/session_result")
    List<OpenF1ResultDto> getResults(@QueryParam("session_key") int sessionKey);

    @GET
    @Path("/drivers")
    List<OpenF1DriverDto> getDrivers(@QueryParam("session_key") int sessionKey);

    @GET
    @Path("/meetings")
    List<OpenF1MeetingDto> getMeetings(@QueryParam("year") int year);

    @GET
    @Path("/starting_grid")
    List<OpenF1GridDto> getStartingGrid(@QueryParam("session_key") int sessionKey);

    @GET
    @Path("/laps")
    List<OpenF1LapDto> getLaps(@QueryParam("session_key") int sessionKey);

    @GET
    @Path("/location")
    List<OpenF1LocationDto> getLocations(@QueryParam("session_key") int sessionKey,
                                         @QueryParam("driver_number") int driverNumber);
}