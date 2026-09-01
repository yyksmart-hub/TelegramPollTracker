package com.polltracker.model;

public class PollOption {
    private final int optionIndex;
    private final String text;
    private int voteCount;

    public PollOption(int optionIndex, String text) {
        this.optionIndex = optionIndex;
        this.text = text;
        this.voteCount = 0;
    }

    public int getOptionIndex() {
        return optionIndex;
    }

    public String getText() {
        return text;
    }

    public int getVoteCount() {
        return voteCount;
    }

    public void incrementVote() {
        this.voteCount++;
    }
}