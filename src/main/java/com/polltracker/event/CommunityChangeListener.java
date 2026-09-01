package com.polltracker.event;

import com.polltracker.model.CommunityMember;

public interface CommunityChangeListener {
    void onMemberJoined(CommunityMember newMember, int totalMembers);
}