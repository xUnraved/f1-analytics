package de.htw.f1analytics.api;

import de.htw.f1analytics.domain.User;
import de.htw.f1analytics.service.AuthService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * REST-Endpunkte für Benutzerauthentifizierung.
 *
 * Alle Endpunkte unter /api/auth.
 * Authentifizierte Endpunkte erwarten einen "Authorization: Bearer <token>"-Header.
 * Das Token wird vom AuthService als kryptographisch zufällige UUID ausgegeben.
 */
@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    AuthService auth;

    /** Eingabedaten für die Registrierung. */
    public record RegisterInput(String username, String email, String password) {}

    /** Eingabedaten für den Login (Benutzername oder E-Mail + Passwort). */
    public record LoginInput(String login, String password) {}

    /**
     * Registriert einen neuen Benutzer.
     * Gibt ein AuthResult mit Token zurück, damit der User direkt eingeloggt ist.
     */
    @POST
    @Path("/register")
    @Consumes(MediaType.APPLICATION_JSON)
    public AuthService.AuthResult register(RegisterInput in) {
        return auth.register(in.username(), in.email(), in.password());
    }

    /**
     * Loggt einen bestehenden Benutzer ein.
     * Das zurückgegebene Token ist 30 Tage gültig.
     */
    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_JSON)
    public AuthService.AuthResult login(LoginInput in) {
        return auth.login(in.login(), in.password());
    }

    /**
     * Invalidiert das übergebene Session-Token (Logout).
     * Gibt 204 No Content zurück, auch wenn das Token bereits abgelaufen war.
     */
    @POST
    @Path("/logout")
    public Response logout(@HeaderParam("Authorization") String authHeader) {
        String token = extractToken(authHeader);
        auth.logout(token);
        return Response.noContent().build();
    }

    /**
     * Gibt das Profil des eingeloggten Benutzers zurück.
     * Wirft 401, wenn kein gültiges Token vorhanden ist.
     */
    @GET
    @Path("/me")
    public AuthService.UserDto me(@HeaderParam("Authorization") String authHeader) {
        User u = auth.validateToken(extractToken(authHeader));
        if (u == null) throw new NotAuthorizedException("Bearer");
        return new AuthService.UserDto(u.id, u.username, u.email, u.color);
    }

    /**
     * Extrahiert das Token aus einem "Bearer <token>"-Authorization-Header.
     * Gibt null zurück, wenn der Header fehlt oder falsches Format hat.
     * Statisch, damit BettingResource und GroupResource es wiederverwenden können.
     */
    public static String extractToken(String authHeader) {
        if (authHeader == null) return null;
        if (authHeader.startsWith("Bearer ")) return authHeader.substring(7).trim();
        return null;
    }
}
