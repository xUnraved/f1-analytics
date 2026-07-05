package de.htw.f1analytics.api;

import de.htw.f1analytics.service.QuizService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/quiz")
public class QuizResource {

    @Inject
    QuizService quizService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public QuizService.QuizData quiz() {
        return quizService.getQuizData();
    }
}
