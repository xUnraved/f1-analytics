package de.htw.f1analytics.service;

import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BettingServiceTest {

    private final BettingService service = new BettingService();

    private static SeasonService.ResultRow row(String abbr, int pos) {
        return new SeasonService.ResultRow(abbr, abbr, "Team", "#ffffff",
                pos, 0, null, false, false, false, null, null);
    }

    private static SeasonService.SessionResultRow qrow(String abbr, int pos) {
        return new SeasonService.SessionResultRow(abbr, abbr, "Team", "#ffffff",
                pos, null, null, false, false, false);
    }

    private static SeasonService.Race race(List<SeasonService.ResultRow> result,
                                           SeasonService.ResultRow fastestLap,
                                           List<SeasonService.SessionResultRow> quali) {
        return new SeasonService.Race("Test GP", "Testland", "Testring", 0, 0,
                "2026-01-01", 1, true,
                result, fastestLap, null, null,
                quali, List.of(), 0, null);
    }

    private static SeasonService.Race standardRace() {
        return race(
                List.of(row("VER", 1), row("NOR", 2), row("LEC", 3), row("HAM", 4)),
                row("NOR", 2),
                List.of(qrow("LEC", 1), qrow("VER", 2)));
    }

    @Test
    @DisplayName("WINNER: richtiger Sieger gibt 10 Punkte, falscher 0")
    void winnerPoints() {
        assertEquals(10, service.computePoints(standardRace(), BettingService.CAT_WINNER, "VER"));
        assertEquals(0, service.computePoints(standardRace(), BettingService.CAT_WINNER, "NOR"));
    }

    @Test
    @DisplayName("WINNER: Vergleich ignoriert Groß-/Kleinschreibung und Leerzeichen")
    void winnerMatchingIsLenient() {
        assertEquals(10, service.computePoints(standardRace(), BettingService.CAT_WINNER, " ver "));
    }

    @Test
    @DisplayName("POLE: richtiger Polesetter gibt 5 Punkte, leeres Qualifying 0")
    void polePoints() {
        assertEquals(5, service.computePoints(standardRace(), BettingService.CAT_POLE, "LEC"));
        assertEquals(0, service.computePoints(standardRace(), BettingService.CAT_POLE, "VER"));
        SeasonService.Race noQuali = race(List.of(row("VER", 1)), null, List.of());
        assertEquals(0, service.computePoints(noQuali, BettingService.CAT_POLE, "VER"));
    }

    @Test
    @DisplayName("PODIUM: exaktes Podium gibt 15, Teiltreffer je 5 Punkte")
    void podiumPoints() {
        assertEquals(15, service.computePoints(standardRace(), BettingService.CAT_PODIUM, "VER|NOR|LEC"));
        assertEquals(5, service.computePoints(standardRace(), BettingService.CAT_PODIUM, "NOR|VER|LEC"));
        assertEquals(10, service.computePoints(standardRace(), BettingService.CAT_PODIUM, "VER|HAM|LEC"));
        assertEquals(0, service.computePoints(standardRace(), BettingService.CAT_PODIUM, "HAM|LEC|VER"));
    }

    @Test
    @DisplayName("FASTEST_LAP: richtiger Fahrer gibt 3 Punkte, fehlende Daten 0")
    void fastestLapPoints() {
        assertEquals(3, service.computePoints(standardRace(), BettingService.CAT_FASTEST_LAP, "NOR"));
        assertEquals(0, service.computePoints(standardRace(), BettingService.CAT_FASTEST_LAP, "VER"));
        SeasonService.Race noFl = race(List.of(row("VER", 1)), null, List.of());
        assertEquals(0, service.computePoints(noFl, BettingService.CAT_FASTEST_LAP, "VER"));
    }

    @Test
    @DisplayName("H2H: 4 Punkte, wenn der erste Fahrer vor dem zweiten landet")
    void h2hPoints() {
        assertEquals(4, service.computePoints(standardRace(), BettingService.CAT_H2H, "VER|NOR"));
        assertEquals(0, service.computePoints(standardRace(), BettingService.CAT_H2H, "NOR|VER"));
        assertEquals(4, service.computePoints(standardRace(), BettingService.CAT_H2H, "NOR|HAM"));
    }

    @Test
    @DisplayName("H2H: 0 Punkte, wenn ein Fahrer nicht im Ergebnis steht")
    void h2hMissingDriver() {
        assertEquals(0, service.computePoints(standardRace(), BettingService.CAT_H2H, "VER|XYZ"));
        assertEquals(0, service.computePoints(standardRace(), BettingService.CAT_H2H, "XYZ|VER"));
    }

    @Test
    @DisplayName("Unbekannte Kategorie gibt 0 Punkte")
    void unknownCategory() {
        assertEquals(0, service.computePoints(standardRace(), "BANANE", "VER"));
    }

    @Test
    @DisplayName("validatePick: gültige Picks werfen keine Exception")
    void validPicks() {
        assertDoesNotThrow(() -> service.validatePick(BettingService.CAT_WINNER, "VER"));
        assertDoesNotThrow(() -> service.validatePick(BettingService.CAT_PODIUM, "VER|NOR|LEC"));
        assertDoesNotThrow(() -> service.validatePick(BettingService.CAT_H2H, "VER|NOR"));
    }

    @Test
    @DisplayName("validatePick: leerer Tipp wird abgelehnt")
    void emptyPickRejected() {
        assertThrows(BadRequestException.class,
                () -> service.validatePick(BettingService.CAT_WINNER, "  "));
        assertThrows(BadRequestException.class,
                () -> service.validatePick(BettingService.CAT_WINNER, null));
    }

    @Test
    @DisplayName("validatePick: Podium braucht 3 unterschiedliche Fahrer")
    void podiumValidation() {
        assertThrows(BadRequestException.class,
                () -> service.validatePick(BettingService.CAT_PODIUM, "VER|NOR"));
        assertThrows(BadRequestException.class,
                () -> service.validatePick(BettingService.CAT_PODIUM, "VER|VER|LEC"));
        assertThrows(BadRequestException.class,
                () -> service.validatePick(BettingService.CAT_PODIUM, "VER|NOR|ver"));
    }

    @Test
    @DisplayName("validatePick: Head-to-Head braucht 2 unterschiedliche Fahrer")
    void h2hValidation() {
        assertThrows(BadRequestException.class,
                () -> service.validatePick(BettingService.CAT_H2H, "VER"));
        assertThrows(BadRequestException.class,
                () -> service.validatePick(BettingService.CAT_H2H, "VER|ver"));
        assertThrows(BadRequestException.class,
                () -> service.validatePick(BettingService.CAT_H2H, "VER|"));
    }
}