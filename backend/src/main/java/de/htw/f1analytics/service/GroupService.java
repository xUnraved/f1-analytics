package de.htw.f1analytics.service;

import de.htw.f1analytics.domain.GroupMember;
import de.htw.f1analytics.domain.User;
import de.htw.f1analytics.domain.UserGroup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class GroupService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;
    private static final int MAX_GROUPS_PER_USER = 20;

    @Inject
    BettingService bettingService;

    public record GroupDto(Long id, String name, String inviteCode, String ownerName,
                           long memberCount, boolean isOwner) {}

    public record MemberDto(String username, String color, boolean isOwner, Instant joinedAt) {}

    @Transactional
    public GroupDto create(User user, String name) {
        if (name == null || name.trim().length() < 3 || name.trim().length() > 48) {
            throw new BadRequestException("Gruppenname: 3 bis 48 Zeichen.");
        }
        if (GroupMember.findByUser(user.id).size() >= MAX_GROUPS_PER_USER) {
            throw new BadRequestException("Maximale Anzahl an Gruppen erreicht.");
        }

        UserGroup g = new UserGroup();
        g.name = name.trim();
        g.inviteCode = generateUniqueCode();
        g.owner = user;
        g.createdAt = Instant.now();
        g.persist();

        GroupMember m = new GroupMember();
        m.group = g;
        m.user = user;
        m.joinedAt = Instant.now();
        m.persist();

        return toDto(g, user, 1);
    }

    @Transactional
    public GroupDto join(User user, String code) {
        if (code == null || code.trim().isEmpty()) throw new BadRequestException("Einladungscode fehlt.");
        UserGroup g = UserGroup.findByInviteCode(code.trim().toUpperCase());
        if (g == null) throw new NotFoundException("Keine Gruppe mit diesem Code gefunden.");
        if (GroupMember.findOne(g.id, user.id) != null) {
            throw new BadRequestException("Du bist bereits Mitglied dieser Gruppe.");
        }
        GroupMember m = new GroupMember();
        m.group = g;
        m.user = user;
        m.joinedAt = Instant.now();
        m.persist();
        return toDto(g, user, GroupMember.countByGroup(g.id));
    }

    @Transactional
    public void leave(User user, long groupId) {
        UserGroup g = UserGroup.findById(groupId);
        if (g == null) throw new NotFoundException("Gruppe nicht gefunden.");
        GroupMember m = GroupMember.findOne(groupId, user.id);
        if (m == null) throw new BadRequestException("Du bist kein Mitglied dieser Gruppe.");
        m.delete();

        List<GroupMember> remaining = GroupMember.findByGroup(groupId);
        if (remaining.isEmpty()) {
            g.delete();
            return;
        }
        if (g.owner.id.equals(user.id)) {
            g.owner = remaining.get(0).user;
        }
    }

    public List<GroupDto> myGroups(User user) {
        List<GroupDto> result = new ArrayList<>();
        for (GroupMember m : GroupMember.findByUser(user.id)) {
            result.add(toDto(m.group, user, GroupMember.countByGroup(m.group.id)));
        }
        return result;
    }

    public List<MemberDto> members(User user, long groupId) {
        UserGroup g = requireMembership(user, groupId);
        List<MemberDto> result = new ArrayList<>();
        for (GroupMember m : GroupMember.findByGroup(groupId)) {
            result.add(new MemberDto(m.user.username, m.user.color,
                    g.owner.id.equals(m.user.id), m.joinedAt));
        }
        return result;
    }

    @Transactional
    public List<BettingService.LeaderboardEntry> leaderboard(User user, long groupId, int year) {
        requireMembership(user, groupId);
        Set<String> memberNames = new HashSet<>();
        for (GroupMember m : GroupMember.findByGroup(groupId)) {
            memberNames.add(m.user.username);
        }
        List<BettingService.LeaderboardEntry> result = new ArrayList<>();
        for (BettingService.LeaderboardEntry e : bettingService.leaderboard(year)) {
            if (memberNames.contains(e.username())) result.add(e);
        }
        return result;
    }

    private UserGroup requireMembership(User user, long groupId) {
        UserGroup g = UserGroup.findById(groupId);
        if (g == null) throw new NotFoundException("Gruppe nicht gefunden.");
        if (GroupMember.findOne(groupId, user.id) == null) {
            throw new ForbiddenException("Nur Gruppenmitglieder haben Zugriff.");
        }
        return g;
    }

    private GroupDto toDto(UserGroup g, User viewer, long memberCount) {
        return new GroupDto(g.id, g.name, g.inviteCode, g.owner.username,
                memberCount, g.owner.id.equals(viewer.id));
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < 50; attempt++) {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
            }
            String code = sb.toString();
            if (UserGroup.findByInviteCode(code) == null) return code;
        }
        throw new IllegalStateException("Konnte keinen freien Einladungscode erzeugen.");
    }
}