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

/**
 * REST-Endpunkte für Tippspiel-Gruppen.
 * Alle Endpunkte erfordern Authentifizierung (Bearer-Token).
 *
 * POST /api/groups           → Neue Gruppe erstellen
 * POST /api/groups/join      → Gruppe per Einladungscode beitreten
 * GET  /api/groups/mine      → Eigene Gruppen auflisten
 * GET  /api/groups/{id}/...  → Mitglieder / Rangliste einer Gruppe
 * POST /api/groups/{id}/leave → Gruppe verlassen
 */
@Path("/api/groups")
@Produces(MediaType.APPLICATION_JSON)
public class GroupResource {

    @Inject
    GroupService svc;

    @Inject
    AuthService authService;

    /** Eingabedaten zum Erstellen einer Gruppe. */
    public record CreateInput(String name) {}

    /** Eingabedaten zum Beitreten einer Gruppe per Einladungscode. */
    public record JoinInput(String code) {}

    /** Erstellt eine neue Gruppe mit dem eingeloggten Benutzer als Owner. */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public GroupService.GroupDto create(@HeaderParam("Authorization") String authHeader, CreateInput in) {
        User user = requireUser(authHeader);
        return svc.create(user, in.name());
    }

    /** Tritt einer bestehenden Gruppe per 6-stelligem Einladungscode bei. */
    @POST
    @Path("/join")
    @Consumes(MediaType.APPLICATION_JSON)
    public GroupService.GroupDto join(@HeaderParam("Authorization") String authHeader, JoinInput in) {
        User user = requireUser(authHeader);
        return svc.join(user, in.code());
    }

    /** Gibt alle Gruppen zurück, in denen der eingeloggte Benutzer Mitglied ist. */
    @GET
    @Path("/mine")
    public List<GroupService.GroupDto> mine(@HeaderParam("Authorization") String authHeader) {
        User user = requireUser(authHeader);
        return svc.myGroups(user);
    }

    /** Mitgliederliste einer Gruppe (nur für Mitglieder sichtbar). */
    @GET
    @Path("/{id}/members")
    public List<GroupService.MemberDto> members(@HeaderParam("Authorization") String authHeader,
                                                @PathParam("id") long id) {
        User user = requireUser(authHeader);
        return svc.members(user, id);
    }

    /** Gruppen-interne Rangliste nach Gesamtpunkten einer Saison. */
    @GET
    @Path("/{id}/leaderboard/{year}")
    public List<BettingService.LeaderboardEntry> leaderboard(@HeaderParam("Authorization") String authHeader,
                                                             @PathParam("id") long id,
                                                             @PathParam("year") int year) {
        User user = requireUser(authHeader);
        return svc.leaderboard(user, id, year);
    }

    /** Verlässt eine Gruppe. Owner kann die Gruppe nicht verlassen (wirft Exception). */
    @POST
    @Path("/{id}/leave")
    public Response leave(@HeaderParam("Authorization") String authHeader, @PathParam("id") long id) {
        User user = requireUser(authHeader);
        svc.leave(user, id);
        return Response.noContent().build();
    }

    /** Token validieren und User laden – wirft 401 bei ungültigem Token. */
    private User requireUser(String authHeader) {
        String token = AuthResource.extractToken(authHeader);
        User u = authService.validateToken(token);
        if (u == null) throw new NotAuthorizedException("Bearer");
        return u;
    }
}
