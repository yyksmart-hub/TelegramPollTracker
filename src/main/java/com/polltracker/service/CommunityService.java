package com.polltracker.service;

import com.polltracker.event.CommunityChangeListener;
import com.polltracker.model.CommunityMember;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class CommunityService {
    private final Map<Long, CommunityMember> members = new LinkedHashMap<>();
    private final List<CommunityChangeListener> listeners = new CopyOnWriteArrayList<>();

    public synchronized boolean registerMember(long telegramId, String fullName, String username) {
        if (this.members.containsKey(telegramId)) {
            return false;
        }

        CommunityMember newMember = new CommunityMember(telegramId, fullName, username, LocalDateTime.now());
        this.members.put(telegramId, newMember);

        notifyMemberJoined(newMember);
        return true;
    }

    public synchronized List<CommunityMember> getAllMembers() {
        return new ArrayList<>(this.members.values());
    }

    public synchronized int getMemberCount() {
        return this.members.size();
    }

    public void addListener(CommunityChangeListener listener) {
        this.listeners.add(listener);
    }

    private void notifyMemberJoined(CommunityMember newMember) {
        int total = getMemberCount();
        for (CommunityChangeListener listener : this.listeners) {
            listener.onMemberJoined(newMember, total);
        }
    }
}