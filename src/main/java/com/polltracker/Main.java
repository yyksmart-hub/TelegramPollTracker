package com.polltracker;

import com.polltracker.bot.TelegramBotHandler;
import com.polltracker.service.CommunityService;
import com.polltracker.service.AiSurveyService;
import com.polltracker.service.SurveyService;
import com.polltracker.ui.MainFrame;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        // 1. אתחול השירותים
        CommunityService communityService = new CommunityService();
        SurveyService surveyService = new SurveyService(communityService);
        AiSurveyService aiSurveyService = new AiSurveyService();

        // 2. יצירת הבוט וקישור השירותים
        TelegramBotHandler botHandler = new TelegramBotHandler(communityService);
        botHandler.setSurveyService(surveyService);
        surveyService.setBotHandler(botHandler);

        // 3. הפעלת הבוט בטלגרם
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(botHandler);
            System.out.println("🤖 הבוט הופעל בהצלחה ומאזין לטלגרם!");
        } catch (TelegramApiException e) {
            System.err.println("שגיאה בהפעלת בוט הטלגרם: " + e.getMessage());
            e.printStackTrace();
        }

        // 4. העלאת ממשק ה-Swing (ב-Event Dispatch Thread)
        SwingUtilities.invokeLater(() -> {
            MainFrame mainFrame = new MainFrame(communityService, surveyService, aiSurveyService);
            mainFrame.setVisible(true);
            System.out.println("🖥️ ממשק הניהול ב-Swing הופעל בהצלחה!");
        });
    }
}