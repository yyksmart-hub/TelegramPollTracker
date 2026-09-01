package com.polltracker.model;

import java.time.LocalDateTime;
import java.util.Objects;

public class CommunityMember {
    private final long telegramId;
    private final String fullName;
    private final String username;
    private final LocalDateTime joinedAt;

    public CommunityMember(long telegramId, String fullName, String username, LocalDateTime joinedAt) {
        this.telegramId = telegramId;
        this.fullName = fullName;
        this.username = username;
        this.joinedAt = joinedAt;
    }

    public long getTelegramId() {
        return telegramId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getUsername() {
        return username;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CommunityMember that = (CommunityMember) o;
        return telegramId == that.telegramId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(telegramId);
    }
}