package com.polltracker.service;

import com.polltracker.bot.TelegramBotHandler;
import com.polltracker.model.*;
import com.polltracker.util.AppConstants;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

public class SurveyService {

    private final Map<String, Survey> surveys = new ConcurrentHashMap<>();
    private final CommunityService communityService;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final List<Runnable> updateListeners = new CopyOnWriteArrayList<>();
    private TelegramBotHandler botHandler;
    private ScheduledFuture<?> reminderTask;
    private ScheduledFuture<?> autoCloseTask;

    public SurveyService(CommunityService communityService) {
        this.communityService = communityService;
    }

    public void setBotHandler(TelegramBotHandler botHandler) {
        this.botHandler = botHandler;
    }

    public void addUpdateListener(Runnable listener) {
        updateListeners.add(listener);
    }

    private void notifyUI() {
        for (Runnable listener : this.updateListeners) {
            listener.run();
        }
    }

    public synchronized boolean hasActiveSurvey() {
        return this.surveys.values().stream().anyMatch(s -> s.getStatus() == PollStatus.ACTIVE);
    }

    public synchronized Survey getActiveSurvey() {
        return this.surveys.values().stream()
                .filter(s -> s.getStatus() == PollStatus.ACTIVE)
                .findFirst()
                .orElse(null);
    }

    public synchronized Survey getScheduledSurvey() {
        return this.surveys.values().stream()
                // מחפש סקר שטרם הפך לפעיל או נסגר, ושזמן ההתחלה שלו בעתיד
                .filter(s -> s.getStatus() != PollStatus.ACTIVE && s.getStatus() != PollStatus.CLOSED)
                .filter(s -> s.getStartTime() != null && s.getStartTime().isAfter(LocalDateTime.now()))
                .min(Comparator.comparing(Survey::getStartTime))
                .orElse(null);
    }

    public synchronized Survey createAndScheduleSurvey(String topic, List<Question> questions, LocalDateTime scheduledTime) {
        if (communityService.getMemberCount() < AppConstants.MIN_MEMBERS_FOR_SURVEY) {
            throw new IllegalStateException(String.format("לא ניתן ליצור סקר: דרוש לפחות %d חברים בקהילה.", AppConstants.MIN_MEMBERS_FOR_SURVEY));
        }

        // בדיקת מרווח של 5 דקות מכל סקר אחר (פעיל או מתוזמן)
        validateNoTimeOverlap(scheduledTime);

        // חישוב הזמן שנותר בשניות עד למועד שנקבע
        long delaySeconds = java.time.Duration.between(LocalDateTime.now(), scheduledTime).getSeconds();
        if (delaySeconds < 0) {
            throw new IllegalStateException("לא ניתן לתזמן סקר לזמן שעבר.");
        }

        String surveyId = UUID.randomUUID().toString().substring(0, 8);
        Survey survey = new Survey(surveyId, topic, questions, new ConcurrentHashMap<>());
        survey.setStartTime(scheduledTime);
        surveys.put(surveyId, survey);

        scheduler.schedule(() -> startSurvey(surveyId), delaySeconds, TimeUnit.SECONDS);
        notifyUI();
        return survey;
    }

    public synchronized void validateNoTimeOverlap(LocalDateTime newStartTime) {
        LocalDateTime newEndTime = newStartTime.plusMinutes(AppConstants.SURVEY_DURATION_MINUTES);

        for (Survey existing : surveys.values()) {
            // ייעול: התעלמות מיידית מסקרים שכבר נסגרו
            if (existing.getStatus() == PollStatus.CLOSED) {
                continue;
            }

            LocalDateTime existingStart = existing.getStartTime();
            if (existingStart == null) continue;

            LocalDateTime existingEnd = existingStart.plusMinutes(AppConstants.SURVEY_DURATION_MINUTES);

            // בדיקת חפיפת זמנים: (StartA < EndB) וגם (EndA > StartB)
            if (newStartTime.isBefore(existingEnd) && newEndTime.isAfter(existingStart)) {
                throw new IllegalStateException("שגיאה: קיים סקר מתוזמן או פעיל בטווח של פחות מ-5 דקות מהשעה הנבחרת.");
            }
        }
    }

    private synchronized void startSurvey(String surveyId) {
        Survey survey = surveys.get(surveyId);
        if (survey == null) return;

        survey.setStatus(PollStatus.ACTIVE);
        survey.setStartTime(LocalDateTime.now());

        // צילום מצב (Snapshot) של חברי הקהילה הנוכחיים בלבד
        Map<Long, ParticipantProgress> progressMap = survey.getParticipantsProgress();
        for (CommunityMember member : communityService.getAllMembers()) {
            progressMap.put(member.getTelegramId(), new ParticipantProgress(member));
        }

        // שליחת השאלה הראשונה לכל המשתתפים שננעלו
        if (botHandler != null && !survey.getQuestions().isEmpty()) {
            Question firstQuestion = survey.getQuestions().getFirst();
            for (Long chatId : progressMap.keySet()) {
                botHandler.sendQuestionToUser(chatId, surveyId, firstQuestion);
            }
        }

        // 1. תזמון תזכורת כעבור 3 דקות (180 שניות)
        reminderTask = scheduler.schedule(() -> sendReminders(surveyId), AppConstants.REMINDER_TIME_MINUTES, TimeUnit.MINUTES);

        // 2. תזמון סגירה אוטומטית כעבור 5 דקות (300 שניות)
        autoCloseTask = scheduler.schedule(() -> closeSurvey(surveyId), AppConstants.SURVEY_DURATION_MINUTES, TimeUnit.MINUTES);

        notifyUI();
    }

    private void sendReminders(String surveyId) {
        Survey survey = surveys.get(surveyId);
        if (survey == null || survey.getStatus() != PollStatus.ACTIVE) return;

        for (Map.Entry<Long, ParticipantProgress> entry : survey.getParticipantsProgress().entrySet()) {
            if (!entry.getValue().isCompleted()) {
                botHandler.sendMessage(entry.getKey(), AppConstants.MSG_REMINDER);
            }
        }
    }

    public synchronized boolean registerVote(String surveyId, long telegramId, int questionId, int optionIndex) {
        Survey survey = surveys.get(surveyId);
        if (survey == null || survey.getStatus() != PollStatus.ACTIVE) {
            return false;
        }

        ParticipantProgress progress = survey.getParticipantsProgress().get(telegramId);
        if (progress == null || progress.getAnsweredQuestions().contains(questionId)) {
            return false;
        }

        // עדכון הצבעה
        for (Question q : survey.getQuestions()) {
            if (q.getQuestionId() == questionId) {
                for (PollOption opt : q.getOptions()) {
                    if (opt.getOptionIndex() == optionIndex) {
                        opt.incrementVote();
                        break;
                    }
                }
            }
        }

        progress.markQuestionAnswered(questionId, survey.getQuestions().size());

        // שליחת השאלה הבאה אם קיימת
        int nextIndex = progress.getAnsweredQuestions().size();
        if (nextIndex < survey.getQuestions().size()) {
            Question nextQuestion = survey.getQuestions().get(nextIndex);
            botHandler.sendQuestionToUser(telegramId, surveyId, nextQuestion);
        } else {
            botHandler.sendMessage(telegramId, AppConstants.MSG_FINISH);
        }

        checkAndCloseIfAllCompleted(survey);
        notifyUI();
        return true;
    }

    private void checkAndCloseIfAllCompleted(Survey survey) {
        boolean allDone = survey.getParticipantsProgress().values().stream()
                .allMatch(ParticipantProgress::isCompleted);

        if (allDone) {
            closeSurvey(survey.getSurveyId());
        }
    }

    public synchronized void closeSurvey(String surveyId) {
        Survey survey = surveys.get(surveyId);
        if (survey != null && survey.getStatus() == PollStatus.ACTIVE) {
            survey.setStatus(PollStatus.CLOSED);

            if (reminderTask != null) reminderTask.cancel(false);
            if (autoCloseTask != null) autoCloseTask.cancel(false);

            notifyUI();
        }
    }

    public Collection<Survey> getAllSurveys() {
        return surveys.values();
    }
}