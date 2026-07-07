package de.htw.f1analytics.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

@ApplicationScoped
public class QuizGameService {

    private static final Random RANDOM = new Random();
    private static final int SEASON_QUIZ_SIZE = 12;

    private static final List<Integer> LADDER = List.of(
            100, 200, 300, 500, 1_000,
            2_000, 4_000, 8_000, 16_000, 32_000,
            64_000, 125_000, 250_000, 500_000, 1_000_000);

    private static final List<Integer> SAFE_LEVELS = List.of(5, 10);

    @Inject
    SeasonService seasonService;

    public record QuizQuestion(String question, List<String> options, String answer, int level, int points) {}

    public record SeasonQuiz(int year, List<QuizQuestion> questions) {}

    public record MillionaireQuiz(List<QuizQuestion> questions, List<Integer> ladder, List<Integer> safeLevels) {}

    public SeasonQuiz seasonQuiz(int year, String lang) {
        boolean en = isEn(lang);
        SeasonService.SeasonStats stats = seasonService.seasonStats(year);
        List<SeasonService.Race> races = completedRaces(stats);
        List<SeasonService.DriverStanding> standings = stats.drivers() != null ? stats.drivers() : List.of();
        if (races.isEmpty() || standings.size() < 4) {
            throw new NotFoundException(en
                    ? "Not enough data for the " + year + " season yet."
                    : "Für die Saison " + year + " liegen noch nicht genug Daten vor.");
        }

        List<String> driverPool = standings.stream().map(SeasonService.DriverStanding::name).toList();
        List<QuizQuestion> pool = new ArrayList<>();

        for (SeasonService.Race r : races) {
            if (r.result() != null && !r.result().isEmpty()) {
                pool.add(buildQuestion(en
                                ? "Who won the " + r.gp() + " " + year + "?"
                                : "Wer gewann den " + r.gp() + " " + year + "?",
                        r.result().get(0).name(), driverPool, 2));
                if (r.result().size() > 1) {
                    pool.add(buildQuestion(en
                                    ? "Who finished second at the " + r.gp() + " " + year + "?"
                                    : "Wer wurde beim " + r.gp() + " " + year + " Zweiter?",
                            r.result().get(1).name(), driverPool, 4));
                }
            }
            if (r.qualifyingResult() != null && !r.qualifyingResult().isEmpty()) {
                pool.add(buildQuestion(en
                                ? "Who took pole position at the " + r.gp() + " " + year + "?"
                                : "Wer sicherte sich beim " + r.gp() + " " + year + " die Pole-Position?",
                        r.qualifyingResult().get(0).name(), driverPool, 3));
            }
        }

        SeasonService.DriverStanding mostWins = uniqueMax(standings, true);
        if (mostWins != null && mostWins.wins() > 0) {
            pool.add(buildQuestion(en
                            ? "Which driver took the most wins in the " + year + " season?"
                            : "Welcher Fahrer holte in der Saison " + year + " die meisten Siege?",
                    mostWins.name(), driverPool, 3));
        }
        SeasonService.DriverStanding mostPoints = uniqueMax(standings, false);
        if (mostPoints != null && mostPoints.points() > 0) {
            boolean seasonOver = stats.races().stream().allMatch(SeasonService.Race::completed);
            String q;
            if (seasonOver) {
                q = en ? "Who won the " + year + " drivers' championship?"
                        : "Wer gewann die Fahrerwertung der Saison " + year + "?";
            } else {
                q = en ? "Who currently leads the " + year + " drivers' championship?"
                        : "Wer führt aktuell die Fahrerwertung der Saison " + year + " an?";
            }
            pool.add(buildQuestion(q, mostPoints.name(), driverPool, 2));
        }

        List<SeasonService.TeamStanding> teams = stats.teams() != null ? stats.teams() : List.of();
        SeasonService.TeamStanding topTeam = uniqueTopTeam(teams);
        if (topTeam != null && teams.size() >= 4) {
            List<String> teamPool = teams.stream().map(SeasonService.TeamStanding::team).toList();
            pool.add(buildQuestion(en
                            ? "Which team scored the most points in the " + year + " season?"
                            : "Welches Team sammelte in der Saison " + year + " die meisten Punkte?",
                    topTeam.team(), teamPool, 3));
        }

        pool.add(numericQuestion(en
                        ? "How many races of the " + year + " season have been completed so far?"
                        : "Wie viele Rennen der Saison " + year + " wurden bereits ausgewertet?",
                races.size(), 2));

        Collections.shuffle(pool, RANDOM);
        List<QuizQuestion> questions = new ArrayList<>(pool.subList(0, Math.min(SEASON_QUIZ_SIZE, pool.size())));
        return new SeasonQuiz(year, questions);
    }

    public MillionaireQuiz millionaire(String lang) {
        boolean en = isEn(lang);
        List<StaticQ> bank = new ArrayList<>(STATIC_BANK);
        Collections.shuffle(bank, RANDOM);

        List<QuizQuestion> dynamicHard = dynamicHardQuestions(en);
        Collections.shuffle(dynamicHard, RANDOM);

        List<QuizQuestion> questions = new ArrayList<>(15);
        Set<String> used = new HashSet<>();

        for (int i = 0; i < 15; i++) {
            int tier = tierForIndex(i);
            QuizQuestion q = pickForTier(tier, bank, dynamicHard, used, en);
            if (q == null) q = pickForTier(Math.max(1, tier - 1), bank, dynamicHard, used, en);
            if (q == null) q = pickAny(bank, used, en);
            if (q == null) break;
            questions.add(new QuizQuestion(q.question(), q.options(), q.answer(), i + 1, LADDER.get(i)));
        }
        return new MillionaireQuiz(questions, LADDER, SAFE_LEVELS);
    }

    private static boolean isEn(String lang) {
        return "en".equalsIgnoreCase(lang);
    }

    private QuizQuestion pickForTier(int tier, List<StaticQ> bank, List<QuizQuestion> dynamicHard,
                                     Set<String> used, boolean en) {
        if (tier >= 4 && !dynamicHard.isEmpty() && RANDOM.nextBoolean()) {
            for (int i = 0; i < dynamicHard.size(); i++) {
                QuizQuestion q = dynamicHard.get(i);
                if (q.level() == tier && used.add(q.question())) {
                    dynamicHard.remove(i);
                    return q;
                }
            }
        }
        for (int i = 0; i < bank.size(); i++) {
            StaticQ sq = bank.get(i);
            if (sq.level == tier && used.add(sq.question.de)) {
                bank.remove(i);
                return toQuestion(sq, en);
            }
        }
        for (int i = 0; i < dynamicHard.size(); i++) {
            QuizQuestion q = dynamicHard.get(i);
            if (q.level() == tier && used.add(q.question())) {
                dynamicHard.remove(i);
                return q;
            }
        }
        return null;
    }

    private QuizQuestion pickAny(List<StaticQ> bank, Set<String> used, boolean en) {
        for (int i = 0; i < bank.size(); i++) {
            StaticQ sq = bank.get(i);
            if (used.add(sq.question.de)) {
                bank.remove(i);
                return toQuestion(sq, en);
            }
        }
        return null;
    }

    private List<QuizQuestion> dynamicHardQuestions(boolean en) {
        List<QuizQuestion> result = new ArrayList<>();
        List<Integer> years;
        try {
            years = seasonService.years();
        } catch (Exception e) {
            return result;
        }
        for (Integer year : years) {
            try {
                SeasonService.SeasonStats stats = seasonService.seasonStats(year);
                List<SeasonService.Race> races = completedRaces(stats);
                List<SeasonService.DriverStanding> standings = stats.drivers() != null ? stats.drivers() : List.of();
                if (races.isEmpty() || standings.size() < 4) continue;
                List<String> driverPool = standings.stream().map(SeasonService.DriverStanding::name).toList();
                for (SeasonService.Race r : races) {
                    if (r.result() != null && !r.result().isEmpty()) {
                        result.add(buildQuestion(en
                                        ? "Who won the " + r.gp() + " " + year + "?"
                                        : "Wer gewann den " + r.gp() + " " + year + "?",
                                r.result().get(0).name(), driverPool, 4));
                    }
                    if (r.qualifyingResult() != null && !r.qualifyingResult().isEmpty()) {
                        result.add(buildQuestion(en
                                        ? "Who was on pole at the " + r.gp() + " " + year + "?"
                                        : "Wer stand beim " + r.gp() + " " + year + " auf Pole-Position?",
                                r.qualifyingResult().get(0).name(), driverPool, 5));
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    private static List<SeasonService.Race> completedRaces(SeasonService.SeasonStats stats) {
        if (stats.races() == null) return List.of();
        return stats.races().stream().filter(SeasonService.Race::completed).toList();
    }

    private static SeasonService.DriverStanding uniqueMax(List<SeasonService.DriverStanding> standings, boolean byWins) {
        SeasonService.DriverStanding best = null;
        boolean tie = false;
        for (SeasonService.DriverStanding d : standings) {
            int v = byWins ? d.wins() : d.points();
            int bv = best == null ? Integer.MIN_VALUE : (byWins ? best.wins() : best.points());
            if (v > bv) {
                best = d;
                tie = false;
            } else if (best != null && v == bv) {
                tie = true;
            }
        }
        return tie ? null : best;
    }

    private static SeasonService.TeamStanding uniqueTopTeam(List<SeasonService.TeamStanding> teams) {
        SeasonService.TeamStanding best = null;
        boolean tie = false;
        for (SeasonService.TeamStanding t : teams) {
            if (best == null || t.points() > best.points()) {
                best = t;
                tie = false;
            } else if (t.points() == best.points()) {
                tie = true;
            }
        }
        return tie ? null : best;
    }

    private static QuizQuestion buildQuestion(String question, String answer, List<String> pool, int level) {
        Set<String> wrong = new LinkedHashSet<>();
        List<String> shuffledPool = new ArrayList<>(pool);
        Collections.shuffle(shuffledPool, RANDOM);
        for (String candidate : shuffledPool) {
            if (candidate != null && !candidate.equalsIgnoreCase(answer)) wrong.add(candidate);
            if (wrong.size() == 3) break;
        }
        List<String> options = new ArrayList<>(wrong);
        options.add(answer);
        Collections.shuffle(options, RANDOM);
        return new QuizQuestion(question, options, answer, level, 0);
    }

    private static QuizQuestion numericQuestion(String question, int answer, int level) {
        Set<Integer> opts = new LinkedHashSet<>();
        opts.add(answer);
        int[] offsets = {1, -1, 2, -2, 3, -3};
        for (int o : offsets) {
            if (answer + o > 0) opts.add(answer + o);
            if (opts.size() == 4) break;
        }
        List<String> options = new ArrayList<>();
        for (int v : opts) options.add(String.valueOf(v));
        Collections.shuffle(options, RANDOM);
        return new QuizQuestion(question, options, String.valueOf(answer), level, 0);
    }

    private static QuizQuestion toQuestion(StaticQ sq, boolean en) {
        List<String> options = new ArrayList<>();
        for (T w : sq.wrong) options.add(en ? w.en : w.de);
        String answer = en ? sq.answer.en : sq.answer.de;
        options.add(answer);
        Collections.shuffle(options, RANDOM);
        return new QuizQuestion(en ? sq.question.en : sq.question.de, options, answer, sq.level, 0);
    }

    private static int tierForIndex(int i) {
        if (i < 3) return 1;
        if (i < 6) return 2;
        if (i < 9) return 3;
        if (i < 12) return 4;
        return 5;
    }

    private record T(String de, String en) {}

    private static T t(String same) {
        return new T(same, same);
    }

    private static T t(String de, String en) {
        return new T(de, en);
    }

    private record StaticQ(T question, T answer, List<T> wrong, int level) {}

    private static final List<StaticQ> STATIC_BANK = List.of(
            new StaticQ(t("Wie viele Punkte erhält der Sieger eines Grand Prix?",
                    "How many points does the winner of a Grand Prix receive?"),
                    t("25"), List.of(t("18"), t("15"), t("10")), 1),
            new StaticQ(t("Welche Flagge beendet ein Formel-1-Rennen?",
                    "Which flag ends a Formula 1 race?"),
                    t("Die schwarz-weiß karierte Flagge", "The chequered flag"),
                    List.of(t("Die rote Flagge", "The red flag"),
                            t("Die grüne Flagge", "The green flag"),
                            t("Die blaue Flagge", "The blue flag")), 1),
            new StaticQ(t("Wie nennt man den Startplatz ganz vorne in der ersten Reihe?",
                    "What is the very first starting spot on the grid called?"),
                    t("Pole-Position", "Pole position"),
                    List.of(t("Grid-Slot 1A", "Grid slot 1A"), t("Hot Lap", "Hot lap"), t("Front Row Zero")), 1),
            new StaticQ(t("Wie heißt der Reifenwechsel-Halt während des Rennens?",
                    "What is the tyre-change stop during a race called?"),
                    t("Boxenstopp", "Pit stop"),
                    List.of(t("Pit-Break", "Pit break"), t("Service-Runde", "Service lap"),
                            t("Wechselfenster", "Change window")), 1),
            new StaticQ(t("In welchem Fürstentum findet der berühmteste Stadtkurs der Formel 1 statt?",
                    "In which principality is F1's most famous street circuit held?"),
                    t("Monaco"), List.of(t("Liechtenstein"), t("Andorra"), t("San Marino")), 1),
            new StaticQ(t("Was signalisiert eine gelbe Flagge?",
                    "What does a yellow flag signal?"),
                    t("Gefahr auf der Strecke – Überholverbot", "Danger on track – no overtaking"),
                    List.of(t("Rennende", "End of the race"),
                            t("Boxengasse geschlossen", "Pit lane closed"),
                            t("Regen erwartet", "Rain expected")), 1),
            new StaticQ(t("In welchem Land liegt die Traditionsstrecke Silverstone?",
                    "In which country is the classic Silverstone circuit located?"),
                    t("Großbritannien", "United Kingdom"),
                    List.of(t("Italien", "Italy"), t("Frankreich", "France"), t("Belgien", "Belgium")), 1),
            new StaticQ(t("Wie viele Autos starten regulär bei einem Grand Prix (Stand Saison 2024)?",
                    "How many cars regularly start a Grand Prix (as of the 2024 season)?"),
                    t("20"), List.of(t("18"), t("22"), t("24")), 1),

            new StaticQ(t("Wofür steht die Abkürzung DRS?", "What does the abbreviation DRS stand for?"),
                    t("Drag Reduction System"),
                    List.of(t("Direct Racing Speed"), t("Dynamic Rear Spoiler"), t("Downforce Reduction Switch")), 2),
            new StaticQ(t("Was bedeutet die Abkürzung DNF im Ergebnis?",
                    "What does the abbreviation DNF mean in the results?"),
                    t("Did Not Finish"),
                    List.of(t("Drove New Fastest"), t("Did Not Fight"), t("Driver Needs Fuel")), 2),
            new StaticQ(t("Wie viele Weltmeistertitel gewann Michael Schumacher?",
                    "How many world championship titles did Michael Schumacher win?"),
                    t("7"), List.of(t("5"), t("6"), t("8")), 2),
            new StaticQ(t("Wie viele Weltmeistertitel gewann Lewis Hamilton?",
                    "How many world championship titles did Lewis Hamilton win?"),
                    t("7"), List.of(t("5"), t("6"), t("8")), 2),
            new StaticQ(t("Welches Team hat die meisten Konstrukteurs-WM-Titel?",
                    "Which team has won the most constructors' championships?"),
                    t("Ferrari"), List.of(t("McLaren"), t("Williams"), t("Mercedes")), 2),
            new StaticQ(t("Wer wurde 2021 Fahrerweltmeister?", "Who won the 2021 drivers' championship?"),
                    t("Max Verstappen"),
                    List.of(t("Lewis Hamilton"), t("Valtteri Bottas"), t("Sergio Pérez")), 2),
            new StaticQ(t("Was zeigt die blaue Flagge einem Fahrer an?",
                    "What does the blue flag tell a driver?"),
                    t("Ein schnelleres Auto will überrunden – Platz machen",
                            "A faster car is about to lap you – let it pass"),
                    List.of(t("Boxenstopp-Pflicht", "Mandatory pit stop"),
                            t("Letzte Runde", "Final lap"),
                            t("Streckenlimits verletzt", "Track limits violated")), 2),
            new StaticQ(t("Auf welcher Strecke liegt die Kurvenkombination Eau Rouge/Raidillon?",
                    "On which circuit is the Eau Rouge/Raidillon corner combination?"),
                    t("Spa-Francorchamps"), List.of(t("Monza"), t("Suzuka"), t("Silverstone")), 2),

            new StaticQ(t("Seit welcher Saison ist der Cockpitschutz Halo in der Formel 1 Pflicht?",
                    "Since which season has the Halo cockpit protection been mandatory in F1?"),
                    t("2018"), List.of(t("2014"), t("2016"), t("2020")), 3),
            new StaticQ(t("In welchem Jahr begann die Hybrid-Ära mit den V6-Turbo-Motoren?",
                    "In which year did the hybrid era with V6 turbo engines begin?"),
                    t("2014"), List.of(t("2009"), t("2012"), t("2016")), 3),
            new StaticQ(t("Wie viele Weltmeistertitel gewann Sebastian Vettel?",
                    "How many world championship titles did Sebastian Vettel win?"),
                    t("4"), List.of(t("2"), t("3"), t("5")), 3),
            new StaticQ(t("Mit welchem Team gewann Sebastian Vettel alle seine WM-Titel?",
                    "With which team did Sebastian Vettel win all of his titles?"),
                    t("Red Bull Racing"), List.of(t("Ferrari"), t("Toro Rosso"), t("BMW Sauber")), 3),
            new StaticQ(t("Wer wurde 2016 Fahrerweltmeister?", "Who won the 2016 drivers' championship?"),
                    t("Nico Rosberg"),
                    List.of(t("Lewis Hamilton"), t("Sebastian Vettel"), t("Daniel Ricciardo")), 3),
            new StaticQ(t("Welche Strecke trägt den Spitznamen „Tempel der Geschwindigkeit“?",
                    "Which circuit is nicknamed the 'Temple of Speed'?"),
                    t("Monza"), List.of(t("Spa-Francorchamps"), t("Silverstone"), t("Interlagos")), 3),
            new StaticQ(t("Wie lang ist die Mindest-Renndistanz eines Grand Prix (Ausnahme Monaco)?",
                    "What is the minimum race distance of a Grand Prix (Monaco excepted)?"),
                    t("Rund 305 km", "About 305 km"),
                    List.of(t("Rund 250 km", "About 250 km"),
                            t("Rund 280 km", "About 280 km"),
                            t("Rund 350 km", "About 350 km")), 3),
            new StaticQ(t("Wie viele Weltmeistertitel gewann Ayrton Senna?",
                    "How many world championship titles did Ayrton Senna win?"),
                    t("3"), List.of(t("2"), t("4"), t("5")), 3),

            new StaticQ(t("Wer wurde 1950 der erste Formel-1-Weltmeister der Geschichte?",
                    "Who became the first Formula 1 world champion in 1950?"),
                    t("Giuseppe Farina"),
                    List.of(t("Juan Manuel Fangio"), t("Alberto Ascari"), t("Stirling Moss")), 4),
            new StaticQ(t("In welchem Jahr wurde Kimi Räikkönen mit Ferrari Weltmeister?",
                    "In which year did Kimi Räikkönen win the title with Ferrari?"),
                    t("2007"), List.of(t("2005"), t("2006"), t("2008")), 4),
            new StaticQ(t("Welche Strecke im Kalender hat als einzige ein Achterschleifen-Layout mit Überführung?",
                    "Which circuit on the calendar is the only one with a figure-eight layout and crossover?"),
                    t("Suzuka"), List.of(t("Interlagos"), t("Sepang"), t("Istanbul Park")), 4),
            new StaticQ(t("Was regelt die 107-Prozent-Regel in der Qualifikation?",
                    "What does the 107 percent rule regulate in qualifying?"),
                    t("Wer langsamer als 107 % der Q1-Bestzeit ist, verpasst die Rennzulassung",
                            "Anyone slower than 107% of the fastest Q1 time may miss race qualification"),
                    List.of(t("Maximale Motorleistung im Qualifying", "Maximum engine power in qualifying"),
                            t("Mindestboxenstopp-Zeit", "Minimum pit stop time"),
                            t("Maximales Tankvolumen", "Maximum fuel volume")), 4),
            new StaticQ(t("Mit welchem Team gewann Fernando Alonso seine beiden WM-Titel 2005 und 2006?",
                    "With which team did Fernando Alonso win his two titles in 2005 and 2006?"),
                    t("Renault"), List.of(t("Ferrari"), t("McLaren"), t("Benetton")), 4),
            new StaticQ(t("Bis einschließlich welcher Saison gab es einen Extrapunkt für die schnellste Rennrunde?",
                    "Up to and including which season was there an extra point for the fastest lap?"),
                    t("2024"), List.of(t("2021"), t("2022"), t("2023")), 4),
            new StaticQ(t("Wie viele Punkte erhält der Sieger eines Sprints (Format seit 2022)?",
                    "How many points does a sprint winner receive (format since 2022)?"),
                    t("8"), List.of(t("10"), t("12"), t("5")), 4),
            new StaticQ(t("Welche Rennstrecke wird als „Grüne Hölle“ bezeichnet?",
                    "Which race track is known as the 'Green Hell'?"),
                    t("Nürburgring-Nordschleife"),
                    List.of(t("Spa-Francorchamps"), t("Circuit de la Sarthe"), t("Brands Hatch")), 4),

            new StaticQ(t("Wie oft gewann Ayrton Senna den Grand Prix von Monaco?",
                    "How many times did Ayrton Senna win the Monaco Grand Prix?"),
                    t("6-mal", "6 times"),
                    List.of(t("4-mal", "4 times"), t("5-mal", "5 times"), t("7-mal", "7 times")), 5),
            new StaticQ(t("Wer gewann als erster Fahrer fünf Weltmeistertitel in Folge?",
                    "Who was the first driver to win five consecutive world titles?"),
                    t("Michael Schumacher"),
                    List.of(t("Juan Manuel Fangio"), t("Lewis Hamilton"), t("Alain Prost")), 5),
            new StaticQ(t("In welchem Jahr fand mit Singapur der erste Nacht-Grand-Prix statt?",
                    "In which year did Singapore host the first night Grand Prix?"),
                    t("2008"), List.of(t("2004"), t("2006"), t("2010")), 5),
            new StaticQ(t("Welches Team gewann 1988 15 von 16 Saisonrennen?",
                    "Which team won 15 of 16 races in the 1988 season?"),
                    t("McLaren"), List.of(t("Williams"), t("Ferrari"), t("Lotus")), 5),
            new StaticQ(t("Wer ist der jüngste Grand-Prix-Sieger der Geschichte?",
                    "Who is the youngest Grand Prix winner in history?"),
                    t("Max Verstappen"),
                    List.of(t("Sebastian Vettel"), t("Lando Norris"), t("Charles Leclerc")), 5),
            new StaticQ(t("In welchem Jahr fuhr die Formel 1 erstmals auf dem Strip in Las Vegas?",
                    "In which year did Formula 1 first race on the Las Vegas Strip?"),
                    t("2023"), List.of(t("2019"), t("2021"), t("2022")), 5),
            new StaticQ(t("Wer gewann 1994 und 1995 die Fahrerweltmeisterschaft mit Benetton?",
                    "Who won the 1994 and 1995 drivers' championships with Benetton?"),
                    t("Michael Schumacher"),
                    List.of(t("Damon Hill"), t("Jacques Villeneuve"), t("Mika Häkkinen")), 5),
            new StaticQ(t("Wer gewann 2005 den ersten Grand Prix der Türkei?",
                    "Who won the first Turkish Grand Prix in 2005?"),
                    t("Kimi Räikkönen"),
                    List.of(t("Fernando Alonso"), t("Juan Pablo Montoya"), t("Michael Schumacher")), 5)
    );
}