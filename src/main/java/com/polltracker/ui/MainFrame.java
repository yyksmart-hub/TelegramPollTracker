package com.polltracker.ui;

import com.polltracker.service.CommunityService;
import com.polltracker.service.AiSurveyService;
import com.polltracker.service.SurveyService;

import javax.swing.*;

public class MainFrame extends JFrame {

    private static final int WINDOW_WIDTH = 1000;
    private static final int WINDOW_HEIGHT = 600;

    public MainFrame(CommunityService communityService, SurveyService surveyService, AiSurveyService aiSurveyService) {
        setTitle("מערכת ניהול סקרים - PollTracker");
        setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("ניהול סקרים", new SurveyPanel(surveyService, aiSurveyService));
        tabbedPane.addTab("חברי קהילה", new CommunityPanel(communityService));

        add(tabbedPane);
    }
}