package de.htw.f1analytics.api;

import de.htw.f1analytics.service.ForecastService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * REST-Endpunkt für KI-basierte Rennprognosen.
 *
 * GET /api/forecast/{year} → Softmax-gewichtete Gewinn-/Podiumswahrscheinlichkeiten
 *                            für alle Fahrer der angegebenen Saison.
 * Öffentlich, kein Auth erforderlich.
 */
@Path("/api/forecast")
@Produces(MediaType.APPLICATION_JSON)
public class ForecastResource {

    @Inject
    ForecastService forecastService;

    /**
     * Berechnet die Rennprognose für die angegebene Saison.
     * Die Berechnung basiert auf WM-Punkten, aktueller Form (letzte 3 Rennen),
     * Saisonsiegen und streckenspezifischer Performance.
     */
    @GET
    @Path("/{year}")
    public ForecastService.Forecast forecast(@PathParam("year") int year) {
        return forecastService.forecast(year);
    }
}
