package de.htw.f1analytics.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.List;

/**
 * Zuordnung eines Benutzers zu einer Tippspiel-Gruppe (n:m-Verknüpfungstabelle).
 * Ein Benutzer kann Mitglied mehrerer Gruppen sein; eine Gruppe hat viele Mitglieder.
 *
 * Der UNIQUE-Constraint (group_id, user_id) verhindert doppelte Mitgliedschaften.
 */
@Entity
@Table(name = "tipp_group_member",
        uniqueConstraints = @UniqueConstraint(columnNames = {"group_id", "user_id"}))
public class GroupMember extends PanacheEntity {

    /** Zugehörige Gruppe. */
    @ManyToOne(optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    public UserGroup group;

    /** Mitglied-Benutzer. */
    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    public User user;

    /** Zeitpunkt des Beitritts (für chronologische Sortierung der Mitgliederliste). */
    @Column(name = "joined_at")
    public Instant joinedAt;

    /** Prüft, ob ein bestimmter User bereits Mitglied einer Gruppe ist. */
    public static GroupMember findOne(long groupId, long userId) {
        return find("from GroupMember m where m.group.id = ?1 and m.user.id = ?2", groupId, userId).firstResult();
    }

    /** Alle Mitglieder einer Gruppe, sortiert nach Beitrittsdatum. */
    public static List<GroupMember> findByGroup(long groupId) {
        return find("from GroupMember m where m.group.id = ?1 order by m.joinedAt", groupId).list();
    }

    /** Alle Gruppen-Mitgliedschaften eines Benutzers. */
    public static List<GroupMember> findByUser(long userId) {
        return find("from GroupMember m where m.user.id = ?1 order by m.joinedAt", userId).list();
    }

    /** Anzahl der Mitglieder einer Gruppe (für die Gruppenübersicht). */
    public static long countByGroup(long groupId) {
        return find("from GroupMember m where m.group.id = ?1", groupId).count();
    }
}
