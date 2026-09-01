package com.polltracker.model;

import java.util.List;

public class Question {
    private final int questionId;
    private final String text;
    private final List<PollOption> options;


    public Question(int questionId, String text, List<PollOption> options) {
        this.questionId = questionId;
        this.text = text;
        this.options = options;
    }

    public int getQuestionId() {
        return questionId;
    }

    public String getText() {
        return text;
    }

    public List<PollOption> getOptions() {
        return options;
    }
}