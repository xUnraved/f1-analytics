package de.htw.f1analytics.api;

import de.htw.f1analytics.service.QuizGameService;
import de.htw.f1analytics.service.QuizService;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/**
 * REST-Endpunkte für das Quiz-System.
 * Alle Endpunkte sind öffentlich (kein Auth erforderlich).
 *
 * GET /api/quiz                   → Basis-Quiz-Daten (Strecken, Fahrer)
 * GET /api/quiz/season/{year}     → Saison-spezifische Fragen
 * GET /api/quiz/millionaire       → 15-Fragen-Millionär-Quiz
 */
@Path("/api/quiz")
@Produces(MediaType.APPLICATION_JSON)
public class QuizResource {

    @Inject
    QuizService quizService;

    @Inject
    QuizGameService quizGameService;

    /**
     * Liefert alle Quiz-Grunddaten: Fahrer (mit Fotos) und Strecken (mit Bildern).
     * Wird vom Frontend für Strecken-Quiz und Fahrer-Quiz verwendet.
     */
    @GET
    public QuizService.QuizData quiz() {
        return quizService.getQuizData();
    }

    /**
     * Generiert Fragen zu einer bestimmten Saison (Sieger, Poles, Wertung).
     * Der lang-Parameter steuert die Sprache der Fragen (de/en).
     */
    @GET
    @Path("/season/{year}")
    public QuizGameService.SeasonQuiz seasonQuiz(@PathParam("year") int year,
                                                 @QueryParam("lang") @DefaultValue("de") String lang) {
        return quizGameService.seasonQuiz(year, lang);
    }

    /**
     * Generiert ein 15-Fragen-Millionär-Quiz mit steigender Schwierigkeit.
     * Fragen umfassen Fahrer, Strecken und Saison-Daten aus allen verfügbaren Jahren.
     */
    @GET
    @Path("/millionaire")
    public QuizGameService.MillionaireQuiz millionaire(@QueryParam("lang") @DefaultValue("de") String lang) {
        return quizGameService.millionaire(lang);
    }
}
