package de.htw.f1analytics.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "tipp", indexes = {
        @Index(name = "idx_tipp_race", columnList = "season_year, round"),
        @Index(name = "idx_tipp_user_year", columnList = "user_id, season_year")
})
public class Tipp extends PanacheEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    public User user;

    @Column(name = "season_year", nullable = false)
    public int year;

    @Column(nullable = false)
    public int round;

    @Column(nullable = false, length = 16)
    public String category;

    @Column(nullable = false, length = 64)
    public String pick;

    @Column(name = "created_at")
    public Instant createdAt;

    public Integer points;

    @Column(name = "settled_at")
    public Instant settledAt;

    public static Tipp findOne(long userId, int year, int round, String category) {
        return find("user.id = ?1 and year = ?2 and round = ?3 and category = ?4",
                userId, year, round, category).firstResult();
    }

    public static List<Tipp> findByRace(int year, int round) {
        return list("year = ?1 and round = ?2", year, round);
    }

    public static List<Tipp> findByUserYear(long userId, int year) {
        return list("user.id = ?1 and year = ?2 order by round desc", userId, year);
    }
}