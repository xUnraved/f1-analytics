package de.htw.f1analytics.service;

import de.htw.f1analytics.domain.User;
import de.htw.f1analytics.domain.UserSession;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotAuthorizedException;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

/**
 * Authentifizierungs-Service für das Tippspiel.
 *
 * Passwortsicherheit:
 *   - PBKDF2WithHmacSHA256, 100.000 Iterationen, 256 Bit Schlüssellänge
 *   - Pro-User individueller kryptographischer Salt (24 Byte, SecureRandom)
 *   - Timing-sicherer Vergleich (constantTimeEquals) gegen Timing-Angriffe
 *
 * Session-Management:
 *   - Token: 32 zufällige Bytes, URL-safe Base64-kodiert (256 Bit Entropie)
 *   - Gültigkeitsdauer: 30 Tage (SESSION_DAYS), gespeichert in user_session
 *   - Abgelaufene Tokens werden beim Validieren gelöscht
 */
@ApplicationScoped
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long SESSION_DAYS = 30;
    private static final int PBKDF2_ITERATIONS = 100_000;
    private static final int PBKDF2_KEY_LENGTH = 256;
    private static final int SALT_BYTES = 24;
    private static final int TOKEN_BYTES = 32;

    private static final String[] PALETTE = {
            "#ff8c00", "#3b82f6", "#22d3ee", "#ec4899",
            "#a855f7", "#facc15", "#34d399", "#f43f5e"
    };

    public record UserDto(Long id, String username, String email, String color) {}

    public record AuthResult(String token, UserDto user) {}

    /** Registriert einen neuen Benutzer und gibt direkt ein gültiges Session-Token zurück. */
    @Transactional
    public AuthResult register(String username, String email, String password) {
        validateUsername(username);
        validateEmail(email);
        validatePassword(password);

        String uname = username.trim();
        String mail = email.trim().toLowerCase();

        if (User.findByUsername(uname) != null) throw new BadRequestException("Nutzername bereits vergeben.");
        if (User.findByEmail(mail) != null) throw new BadRequestException("Diese Email ist bereits registriert.");

        byte[] saltBytes = new byte[SALT_BYTES];
        RANDOM.nextBytes(saltBytes);
        String saltStr = Base64.getEncoder().encodeToString(saltBytes);

        User u = new User();
        u.username = uname;
        u.email = mail;
        u.passwordHash = hashPassword(password, saltBytes);
        u.salt = saltStr;
        u.color = pickColor(uname);
        u.createdAt = Instant.now();
        u.persist();

        return createSession(u);
    }

    /** Validiert Login-Daten und gibt ein neues Session-Token zurück. */
    @Transactional
    public AuthResult login(String usernameOrEmail, String password) {
        if (usernameOrEmail == null || usernameOrEmail.isBlank() || password == null || password.isEmpty()) {
            throw new NotAuthorizedException("Account oder Passwort falsch.");
        }
        User u = User.findByLogin(usernameOrEmail.trim());
        if (u == null) throw new NotAuthorizedException("Account oder Passwort falsch.");

        byte[] saltBytes = Base64.getDecoder().decode(u.salt);
        String hash = hashPassword(password, saltBytes);
        if (!constantTimeEquals(hash, u.passwordHash)) {
            throw new NotAuthorizedException("Account oder Passwort falsch.");
        }
        return createSession(u);
    }

    @Transactional
    public void logout(String token) {
        if (token == null) return;
        UserSession s = UserSession.findByToken(token);
        if (s != null) s.delete();
    }

    /**
     * Prüft ein Token auf Gültigkeit und gibt den zugehörigen Benutzer zurück.
     * Abgelaufene Tokens werden automatisch aus der DB gelöscht.
     * Gibt null zurück bei ungültigem/fehlendem/abgelaufenem Token.
     */
    @Transactional
    public User validateToken(String token) {
        if (token == null || token.isBlank()) return null;
        UserSession s = UserSession.findByToken(token);
        if (s == null) return null;
        if (s.expiresAt != null && s.expiresAt.isBefore(Instant.now())) {
            s.delete();
            return null;
        }
        return s.user;
    }

    private AuthResult createSession(User u) {
        byte[] tokenBytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);

        UserSession s = new UserSession();
        s.user = u;
        s.token = token;
        s.createdAt = Instant.now();
        s.expiresAt = Instant.now().plus(SESSION_DAYS, ChronoUnit.DAYS);
        s.persist();

        return new AuthResult(token, new UserDto(u.id, u.username, u.email, u.color));
    }

    private String hashPassword(String password, byte[] salt) {
        try {
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH);
            byte[] hash = skf.generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException("Passwort-Hashing fehlgeschlagen", e);
        }
    }

    /**
     * Zeitkonstanter String-Vergleich gegen Timing-Angriffe.
     * Normale String-Equals-Methoden brechen bei erstem Mismatch ab und lecken
     * dadurch Timing-Informationen über die richtige Passwortlänge.
     */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) result |= a.charAt(i) ^ b.charAt(i);
        return result == 0;
    }

    private void validateUsername(String username) {
        if (username == null) throw new BadRequestException("Nutzername fehlt.");
        String u = username.trim();
        if (u.length() < 3 || u.length() > 32) throw new BadRequestException("Nutzername: 3 bis 32 Zeichen.");
        if (!u.matches("^[A-Za-z0-9_-]+$")) throw new BadRequestException("Nutzername: nur Buchstaben, Zahlen, _ und -");
    }

    private void validateEmail(String email) {
        if (email == null) throw new BadRequestException("Email fehlt.");
        String e = email.trim();
        if (e.length() > 128) throw new BadRequestException("Email zu lang.");
        if (!e.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw new BadRequestException("Ungültige Email-Adresse.");
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 6) throw new BadRequestException("Passwort: mindestens 6 Zeichen.");
        if (password.length() > 128) throw new BadRequestException("Passwort zu lang.");
    }

    private String pickColor(String s) {
        return PALETTE[Math.floorMod(s.hashCode(), PALETTE.length)];
    }
}