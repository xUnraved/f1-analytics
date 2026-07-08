package de.htw.f1analytics.api;

import de.htw.f1analytics.domain.User;
import de.htw.f1analytics.service.AuthService;
import de.htw.f1analytics.service.BettingService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * REST-Endpunkte für das Tippspiel.
 * Alle schreibenden Operationen erfordern Authentifizierung via Bearer-Token.
 * Lesende Endpunkte (Community-Picks, Leaderboard) sind öffentlich.
 */
@Path("/api/betting")
@Produces(MediaType.APPLICATION_JSON)
public class BettingResource {

    @Inject
    BettingService svc;

    @Inject
    AuthService authService;

    /** Eingabedaten für einen neuen Tipp. */
    public record TippInput(int year, int round, String category, String pick) {}

    /**
     * Speichert oder überschreibt einen Tipp des eingeloggten Benutzers.
     * Kategorie z. B. "winner", "pole", "podium_p1", "fastest", "h2h_44v1".
     */
    @POST
    @Path("/tipp")
    @Consumes(MediaType.APPLICATION_JSON)
    public BettingService.TippDto submit(@HeaderParam("Authorization") String authHeader, TippInput in) {
        User user = requireUser(authHeader);
        return svc.submit(user, in.year(), in.round(), in.category(), in.pick());
    }

    /** Löscht den Tipp eines Benutzers für eine bestimmte Kategorie eines Rennens. */
    @DELETE
    @Path("/tipp")
    public Response delete(@HeaderParam("Authorization") String authHeader,
                           @QueryParam("year") int year,
                           @QueryParam("round") int round,
                           @QueryParam("category") String category) {
        User user = requireUser(authHeader);
        svc.delete(user, year, round, category);
        return Response.noContent().build();
    }

    /** Alle Tipps aller Benutzer für ein Rennen (Community-Ansicht, öffentlich). */
    @GET
    @Path("/race/{year}/{round}")
    public List<BettingService.TippDto> tippsForRace(@PathParam("year") int year,
                                                     @PathParam("round") int round) {
        return svc.tippsForRace(year, round);
    }

    /** Alle eigenen Tipps des eingeloggten Benutzers in einer Saison. */
    @GET
    @Path("/me/season/{year}")
    public List<BettingService.TippDto> myTipps(@HeaderParam("Authorization") String authHeader,
                                                @PathParam("year") int year) {
        User user = requireUser(authHeader);
        return svc.myTipps(user, year);
    }

    /** Rangliste aller Benutzer nach Gesamtpunkten einer Saison (öffentlich). */
    @GET
    @Path("/leaderboard/{year}")
    public List<BettingService.LeaderboardEntry> leaderboard(@PathParam("year") int year) {
        return svc.leaderboard(year);
    }

    /** Aggregierte Tipp-Verteilung aller Benutzer für ein Rennen (öffentlich). */
    @GET
    @Path("/crowd/{year}/{round}")
    public List<BettingService.CrowdPick> crowdPicks(@PathParam("year") int year,
                                                     @PathParam("round") int round) {
        return svc.crowdPicks(year, round);
    }

    /** Kommende Rennen einer Saison mit Tippfristen und Startzeitpunkten. */
    @GET
    @Path("/upcoming/{year}")
    public List<BettingService.RaceInfo> upcoming(@PathParam("year") int year) {
        return svc.upcomingRaces(year);
    }

    /** Token validieren und User laden – wirft 401 bei ungültigem Token. */
    private User requireUser(String authHeader) {
        String token = AuthResource.extractToken(authHeader);
        User u = authService.validateToken(token);
        if (u == null) throw new NotAuthorizedException("Bearer");
        return u;
    }
}
