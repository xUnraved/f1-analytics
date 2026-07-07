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

@Path("/api/quiz")
@Produces(MediaType.APPLICATION_JSON)
public class QuizResource {

    @Inject
    QuizService quizService;

    @Inject
    QuizGameService quizGameService;

    @GET
    public QuizService.QuizData quiz() {
        return quizService.getQuizData();
    }

    @GET
    @Path("/season/{year}")
    public QuizGameService.SeasonQuiz seasonQuiz(@PathParam("year") int year,
                                                 @QueryParam("lang") @DefaultValue("de") String lang) {
        return quizGameService.seasonQuiz(year, lang);
    }

    @GET
    @Path("/millionaire")
    public QuizGameService.MillionaireQuiz millionaire(@QueryParam("lang") @DefaultValue("de") String lang) {
        return quizGameService.millionaire(lang);
    }
}