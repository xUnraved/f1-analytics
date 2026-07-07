package de.htw.f1analytics.api;

import de.htw.f1analytics.domain.User;
import de.htw.f1analytics.service.AuthService;
import de.htw.f1analytics.service.BettingService;
import de.htw.f1analytics.service.GroupService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/api/groups")
@Produces(MediaType.APPLICATION_JSON)
public class GroupResource {

    @Inject
    GroupService svc;

    @Inject
    AuthService authService;

    public record CreateInput(String name) {}
    public record JoinInput(String code) {}

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public GroupService.GroupDto create(@HeaderParam("Authorization") String authHeader, CreateInput in) {
        User user = requireUser(authHeader);
        return svc.create(user, in.name());
    }

    @POST
    @Path("/join")
    @Consumes(MediaType.APPLICATION_JSON)
    public GroupService.GroupDto join(@HeaderParam("Authorization") String authHeader, JoinInput in) {
        User user = requireUser(authHeader);
        return svc.join(user, in.code());
    }

    @GET
    @Path("/mine")
    public List<GroupService.GroupDto> mine(@HeaderParam("Authorization") String authHeader) {
        User user = requireUser(authHeader);
        return svc.myGroups(user);
    }

    @GET
    @Path("/{id}/members")
    public List<GroupService.MemberDto> members(@HeaderParam("Authorization") String authHeader,
                                                @PathParam("id") long id) {
        User user = requireUser(authHeader);
        return svc.members(user, id);
    }

    @GET
    @Path("/{id}/leaderboard/{year}")
    public List<BettingService.LeaderboardEntry> leaderboard(@HeaderParam("Authorization") String authHeader,
                                                             @PathParam("id") long id,
                                                             @PathParam("year") int year) {
        User user = requireUser(authHeader);
        return svc.leaderboard(user, id, year);
    }

    @POST
    @Path("/{id}/leave")
    public Response leave(@HeaderParam("Authorization") String authHeader, @PathParam("id") long id) {
        User user = requireUser(authHeader);
        svc.leave(user, id);
        return Response.noContent().build();
    }

    private User requireUser(String authHeader) {
        String token = AuthResource.extractToken(authHeader);
        User u = authService.validateToken(token);
        if (u == null) throw new NotAuthorizedException("Bearer");
        return u;
    }
}