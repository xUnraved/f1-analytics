package de.htw.f1analytics.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "tipp_group")
public class UserGroup extends PanacheEntity {

    @Column(nullable = false, length = 48)
    public String name;

    @Column(name = "invite_code", nullable = false, unique = true, length = 12)
    public String inviteCode;

    @ManyToOne(optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    public User owner;

    @Column(name = "created_at")
    public Instant createdAt;

    public static UserGroup findByInviteCode(String code) {
        return find("inviteCode", code).firstResult();
    }
}