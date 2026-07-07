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

@Entity
@Table(name = "tipp_group_member",
        uniqueConstraints = @UniqueConstraint(columnNames = {"group_id", "user_id"}))
public class GroupMember extends PanacheEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    public UserGroup group;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    public User user;

    @Column(name = "joined_at")
    public Instant joinedAt;

    public static GroupMember findOne(long groupId, long userId) {
        return find("from GroupMember m where m.group.id = ?1 and m.user.id = ?2", groupId, userId).firstResult();
    }

    public static List<GroupMember> findByGroup(long groupId) {
        return find("from GroupMember m where m.group.id = ?1 order by m.joinedAt", groupId).list();
    }

    public static List<GroupMember> findByUser(long userId) {
        return find("from GroupMember m where m.user.id = ?1 order by m.joinedAt", userId).list();
    }

    public static long countByGroup(long groupId) {
        return find("from GroupMember m where m.group.id = ?1", groupId).count();
    }
}