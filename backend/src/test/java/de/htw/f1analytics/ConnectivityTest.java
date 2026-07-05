package de.htw.f1analytics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.HttpURLConnection;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class ConnectivityTest {

    private static final String JDBC_URL = "jdbc:postgresql://localhost:5432/f1analytics";
    private static final String DB_USER  = "postgres";
    private static final String DB_PASS  = "Openf1";

    private static final String OPENF1_URL =
            "https://api.openf1.org/v1/sessions?year=2024&limit=1";

    // ── PostgreSQL ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("PostgreSQL: Verbindung zu localhost:5432/f1analytics herstellbar")
    void postgreSQLConnectionCanBeOpened() throws Exception {
        try (Connection conn = DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASS)) {
            assertNotNull(conn, "DriverManager liefert keine Verbindung");
            assertTrue(conn.isValid(3), "Verbindung nicht valide (Timeout 3 s)");
        }
    }

    @Test
    @DisplayName("PostgreSQL: SELECT 1 liefert Ergebnis")
    void postgreSQLSelectOneReturnsOne() throws Exception {
        try (Connection conn = DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASS);
             Statement  stmt = conn.createStatement();
             ResultSet  rs   = stmt.executeQuery("SELECT 1")) {

            assertTrue(rs.next(), "ResultSet ist leer");
            assertEquals(1, rs.getInt(1), "SELECT 1 liefert nicht 1");
        }
    }

    @Test
    @DisplayName("PostgreSQL: Tabellen f1_session und f1_result existieren")
    void postgreSQLRequiredTablesExist() throws Exception {
        String sql = """
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('f1_session', 'f1_result')
                """;
        try (Connection conn = DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASS);
             Statement  stmt = conn.createStatement();
             ResultSet  rs   = stmt.executeQuery(sql)) {

            assertTrue(rs.next());
            int found = rs.getInt(1);
            assertEquals(2, found,
                    "Erwartet 2 Tabellen (f1_session, f1_result), gefunden: " + found);
        }
    }

    // ── OpenF1 API ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("OpenF1 API: Host api.openf1.org antwortet")
    void openF1ApiHostResponds() throws Exception {
        HttpURLConnection conn = openConnection();
        int status;
        try {
            status = conn.getResponseCode();
        } finally {
            conn.disconnect();
        }

        // 200 = normal, 401 = Live-Session-Block (erreichbar, gesperrt),
        // 429 = Rate-Limit – alles < 500 bedeutet: Server ist erreichbar
        assertTrue(status < 500,
                "OpenF1 API antwortet mit HTTP " + status + " (5xx = Server-Fehler)");
    }

    @Test
    @DisplayName("OpenF1 API: Antwort enthält JSON Content-Type")
    void openF1ApiReturnsJsonContentType() throws Exception {
        HttpURLConnection conn = openConnection();
        int    status;
        String contentType;
        try {
            status      = conn.getResponseCode();
            contentType = conn.getHeaderField("Content-Type");
        } finally {
            conn.disconnect();
        }

        // Bei 200 und 401 (Live-Session) schickt OpenF1 immer JSON zurück
        if (status == 200 || status == 401) {
            assertNotNull(contentType, "Content-Type Header fehlt");
            assertTrue(contentType.contains("json"),
                    "Content-Type ist kein JSON: " + contentType);
        }
        // 429 Rate-Limit: kein JSON erwartet → Test gilt als bestanden
    }

    // ── Hilfsmethode ─────────────────────────────────────────────────────────

    private static HttpURLConnection openConnection() throws Exception {
        HttpURLConnection conn = (HttpURLConnection)
                java.net.URI.create(OPENF1_URL).toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(8_000);
        conn.setReadTimeout(15_000);
        conn.setRequestProperty("Accept", "application/json");
        return conn;
    }
}
