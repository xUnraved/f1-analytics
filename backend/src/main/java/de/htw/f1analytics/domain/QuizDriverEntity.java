package de.htw.f1analytics.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.util.List;

@Entity
@Table(name = "quiz_driver")
public class QuizDriverEntity extends PanacheEntityBase {

    @Id
    @Column(name = "driver_number")
    public int driverNumber;

    public String abbr;
    public String name;

    @Column(name = "headshot_url", columnDefinition = "text")
    public String headshotUrl;

    @Column(name = "country_code")
    public String countryCode;

    @Column(name = "country_name")
    public String countryName;

    @Column(name = "birth_year")
    public Integer birthYear;

    public static List<QuizDriverEntity> withHeadshotAndCountry() {
        return list("headshotUrl IS NOT NULL AND countryName IS NOT NULL");
    }

    public static List<QuizDriverEntity> withBirthYear() {
        return list("headshotUrl IS NOT NULL AND birthYear IS NOT NULL");
    }
}
