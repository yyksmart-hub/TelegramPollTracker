package com.polltracker.model;

import java.util.HashSet;
import java.util.Set;

public class ParticipantProgress {
    private final CommunityMember member;
    private final Set<Integer> answeredQuestions;
    private CompletionStatus status;

    public ParticipantProgress(CommunityMember member) {
        this.member = member;
        this.answeredQuestions = new HashSet<>();
        this.status = CompletionStatus.NOT_STARTED;
    }

    public CommunityMember getMember() {
        return member;
    }

    public Set<Integer> getAnsweredQuestions() {
        return this.answeredQuestions;
    }

    public CompletionStatus getStatus() {
        return status;
    }

    public boolean isCompleted() {
        return this.status == CompletionStatus.COMPLETED;
    }

    public void markQuestionAnswered(int questionId, int totalQuestionsInSurvey) {
        this.answeredQuestions.add(questionId);

        if (this.answeredQuestions.size() == totalQuestionsInSurvey) {
            this.status = CompletionStatus.COMPLETED;
        } else {
            this.status = CompletionStatus.IN_PROGRESS;
        }
    }
}