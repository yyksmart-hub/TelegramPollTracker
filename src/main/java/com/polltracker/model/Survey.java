package com.polltracker.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class Survey {
    private final String surveyId;
    private final String topic;
    private final List<Question> questions;
    private PollStatus status;
    private LocalDateTime startTime;
    private final Map<Long, ParticipantProgress> participantsProgress;

    public Survey(String surveyId, String topic, List<Question> questions, Map<Long, ParticipantProgress> participantsProgress) {
        this.surveyId = surveyId;
        this.topic = topic;
        this.questions = questions;
        this.participantsProgress = participantsProgress;
        this.status = PollStatus.SCHEDULED;
    }

    public String getSurveyId() {
        return surveyId;
    }

    public String getTopic() {
        return topic;
    }

    public List<Question> getQuestions() {
        return questions;
    }

    public PollStatus getStatus() {
        return status;
    }

    public void setStatus(PollStatus status) {
        this.status = status;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public Map<Long, ParticipantProgress> getParticipantsProgress() {
        return participantsProgress;
    }
}