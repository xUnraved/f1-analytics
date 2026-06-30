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

@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    AuthService auth;

    public record RegisterInput(String username, String email, String password) {}
    public record LoginInput(String login, String password) {}

    @POST
    @Path("/register")
    @Consumes(MediaType.APPLICATION_JSON)
    public AuthService.AuthResult register(RegisterInput in) {
        return auth.register(in.username(), in.email(), in.password());
    }

    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_JSON)
    public AuthService.AuthResult login(LoginInput in) {
        return auth.login(in.login(), in.password());
    }

    @POST
    @Path("/logout")
    public Response logout(@HeaderParam("Authorization") String authHeader) {
        String token = extractToken(authHeader);
        auth.logout(token);
        return Response.noContent().build();
    }

    @GET
    @Path("/me")
    public AuthService.UserDto me(@HeaderParam("Authorization") String authHeader) {
        User u = auth.validateToken(extractToken(authHeader));
        if (u == null) throw new NotAuthorizedException("Bearer");
        return new AuthService.UserDto(u.id, u.username, u.email, u.color);
    }

    public static String extractToken(String authHeader) {
        if (authHeader == null) return null;
        if (authHeader.startsWith("Bearer ")) return authHeader.substring(7).trim();
        return null;
    }
}