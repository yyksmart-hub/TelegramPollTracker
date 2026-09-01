package com.polltracker.util;

public final class AppConstants {
    private AppConstants() {} // מונע יצירת מופע מ-AppConstants

    // זמנים
    public static final int SURVEY_DURATION_MINUTES = 5;
    public static final int REMINDER_TIME_MINUTES = 3;

    // מגבלות שאלות ותשובות
    public static final int MIN_QUESTIONS = 1;
    public static final int MAX_QUESTIONS = 3;
    public static final int MIN_OPTIONS_PER_QUESTION = 2;
    public static final int MAX_OPTIONS_PER_QUESTION = 4;

    // קהילה
    public static final int MIN_MEMBERS_FOR_SURVEY = 3;

    public static final String VOTE_CALLBACK_PREFIX = "VOTE:";
    public static final String REMINDER_MESSAGE ="⏰ תזכורת: נותרו עוד 2 דקות לסיום הסקר! אנא השלם את מענה השאלות.";
    public static final String FINISH_MESSAGE="תודה! השלמת את מענה כל השאלות בסקר. 🙌";
}