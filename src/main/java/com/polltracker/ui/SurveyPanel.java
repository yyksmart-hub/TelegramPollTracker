package com.polltracker.ui;

import com.polltracker.model.*;
import com.polltracker.service.AiSurveyService;
import com.polltracker.service.SurveyService;
import com.polltracker.util.AppConstants;
import com.polltracker.util.DateFormatter;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;

public class SurveyPanel extends JPanel {

    private static final int RESULTS_DIALOG_WIDTH = 450;
    private static final int RESULTS_DIALOG_HEIGHT = 300;

    private static final int UI_REFRESH_INTERVAL_MS = 1000;
    private static final int TOAST_NOTIFICATION_MS = 3000;

    private final SurveyService surveyService;
    private final AiSurveyService geminiService;

    // רכיבי ניטור סקר פעיל
    private final JLabel activeSurveyStatusLabel = new JLabel("אין סקר פעיל כעת", SwingConstants.CENTER);
    private final JLabel timerLabel = new JLabel("זמן נותר: --:--", SwingConstants.CENTER);
    private final JLabel statsLabel = new JLabel("משתתפים: 0 | השלימו: 0 | טרם השלימו: 0", SwingConstants.CENTER);
    private final DefaultTableModel activeTableModel;
    private final JButton viewResultsBtn = new JButton("צפה בתוצאות הסקר");

    // משתנה עזר לזיהוי רגע תחילת סקר כדי להציג הודעת קופצת (Toast)
    private String lastActiveSurveyId = null;
    // טבלת היסטוריית סקרים
    private final DefaultTableModel historyTableModel;

    public SurveyPanel(SurveyService surveyService, AiSurveyService geminiService) {
        this.surveyService = surveyService;
        this.geminiService = geminiService;

        // 1. אתחול מודלי הטבלאות (חובה בבנאי כי הם final)
        this.activeTableModel = new DefaultTableModel(new String[]{"שם משתתף", "התקדמות", "סטטוס"}, 0);
        this.historyTableModel = new DefaultTableModel(new String[]{"מזהה סקר", "נושא", "סטטוס", "זמן התחלה"}, 0);

        // 2. הגדרות עיצוב בסיסיות של הפאנל הראשי
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 3. יצירת רכיבי הקלט (שנדרשים גם לתצוגה וגם לאירועי הלחיצה)
        JTextField topicField = new JTextField(12);
        JSpinner countSpinner = new JSpinner(new SpinnerNumberModel(AppConstants.MIN_QUESTIONS, AppConstants.MIN_QUESTIONS, AppConstants.MAX_QUESTIONS, 1));
        ((JSpinner.DefaultEditor) countSpinner.getEditor()).getTextField().setEditable(false);
        JButton geminiBtn = new JButton("צור סקר עם Gemini");
        JButton manualBtn = new JButton("צור סקר ידני");

        // 4. הרכבת תתי-הפאנלים
        JPanel formPanel = buildFormPanel(topicField, countSpinner, geminiBtn, manualBtn);
        JPanel activePanel = buildActivePanel();
        JPanel historyPanel = buildHistoryPanel();

        // 5. סידור המסך הראשי
        setupMainLayout(formPanel, activePanel, historyPanel);

        // 6. הפעלת מאזינים וטיימרים
        setupEventsAndObservers(geminiBtn, manualBtn, topicField, countSpinner);
    }

    private JPanel buildFormPanel(JTextField topicField, JSpinner countSpinner, JButton geminiBtn, JButton manualBtn) {
        JPanel formPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        formPanel.add(manualBtn);
        formPanel.add(geminiBtn);
        formPanel.add(new JLabel("כמות שאלות (" + AppConstants.MIN_QUESTIONS + "-" + AppConstants.MAX_QUESTIONS + "):"));
        formPanel.add(countSpinner);
        formPanel.add(new JLabel("נושא הסקר:"));
        formPanel.add(topicField);
        return formPanel;
    }

    private JPanel buildActivePanel() {
        JPanel activePanel = new JPanel(new BorderLayout(5, 5));
        activePanel.setBorder(BorderFactory.createTitledBorder("📊 ניטור סקר פעיל בזמן אמת"));

        JPanel activeHeader = new JPanel(new GridLayout(2, 1));
        activeSurveyStatusLabel.setFont(new Font("Arial", Font.BOLD, 14));
        timerLabel.setFont(new Font("Arial", Font.BOLD, 13));
        timerLabel.setForeground(Color.BLUE);
        activeHeader.add(activeSurveyStatusLabel);
        activeHeader.add(timerLabel);

        JTable activeTable = new JTable(activeTableModel);

        JPanel activeFooter = new JPanel(new BorderLayout());
        activeFooter.add(statsLabel, BorderLayout.CENTER);
        viewResultsBtn.setEnabled(false);
        activeFooter.add(viewResultsBtn, BorderLayout.EAST);

        activePanel.add(activeHeader, BorderLayout.NORTH);
        activePanel.add(new JScrollPane(activeTable), BorderLayout.CENTER);
        activePanel.add(activeFooter, BorderLayout.SOUTH);

        return activePanel;
    }

    private JPanel buildHistoryPanel() {
        JPanel historyPanel = new JPanel(new BorderLayout());
        historyPanel.setBorder(BorderFactory.createTitledBorder("📜 היסטוריית סקרים"));
        JTable historyTable = new JTable(this.historyTableModel);
        historyPanel.add(new JScrollPane(historyTable), BorderLayout.CENTER);
        return historyPanel;
    }

    private void setupMainLayout(JPanel formPanel, JPanel activePanel, JPanel historyPanel) {
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, activePanel, historyPanel);
        splitPane.setResizeWeight(0.6);

        JPanel mainContainer = new JPanel(new BorderLayout());
        mainContainer.add(formPanel, BorderLayout.NORTH);
        mainContainer.add(splitPane, BorderLayout.CENTER);

        add(mainContainer, BorderLayout.CENTER);
    }

    private void setupEventsAndObservers(JButton geminiBtn, JButton manualBtn, JTextField topicField, JSpinner countSpinner) {
        // אירועי כפתורים
        geminiBtn.addActionListener(e -> createGeminiSurvey(topicField, countSpinner, geminiBtn));
        manualBtn.addActionListener(e -> createManualSurvey(topicField, countSpinner));
        viewResultsBtn.addActionListener(e -> showResultsDialog());

        // טיימר לעדכון ה-UI והספירה לאחור בכל שנייה
        Timer uiTimer = new Timer(UI_REFRESH_INTERVAL_MS, e -> refreshUI());
        uiTimer.start();

        this.surveyService.addUpdateListener(() -> SwingUtilities.invokeLater(this::refreshUI));
    }

    private Integer showSchedulingDialog() {
        JPanel panel = new JPanel(new GridLayout(3, 1, 5, 5));
        JRadioButton immediateRadio = new JRadioButton("שליחה מיידית (עכשיו)", true);
        JRadioButton delayRadio = new JRadioButton("השהיית שליחה למספר דקות:");
        ButtonGroup group = new ButtonGroup();
        group.add(immediateRadio);
        group.add(delayRadio);

        JSpinner minutesSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 60, 1));
        minutesSpinner.setEnabled(false); // כבוי כברירת מחדל

        ((JSpinner.DefaultEditor) minutesSpinner.getEditor()).getTextField().setEditable(false);

        delayRadio.addActionListener(e -> minutesSpinner.setEnabled(true));
        immediateRadio.addActionListener(e -> minutesSpinner.setEnabled(false));

        JPanel delayPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        delayPanel.add(delayRadio);
        delayPanel.add(minutesSpinner);

        panel.add(new JLabel("מתי תרצה לשלוח את הסקר למשתתפים?"));
        panel.add(immediateRadio);
        panel.add(delayPanel);

        int result = JOptionPane.showConfirmDialog(this, panel, "תזמון שליחת סקר", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            return immediateRadio.isSelected() ? 0 : (Integer) minutesSpinner.getValue();
        }
        return null;
    }

    private void showToastNotification() {
        JWindow toast = new JWindow(SwingUtilities.getWindowAncestor(this));
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createLineBorder(new Color(34, 139, 34), 2));
        panel.setBackground(new Color(224, 255, 224));

        JLabel label = new JLabel(AppConstants.MSG_SURVEY_STARTED, SwingConstants.CENTER);
        label.setFont(new Font("Arial", Font.BOLD, 14));
        label.setBorder(BorderFactory.createEmptyBorder(15, 30, 15, 30));
        label.setForeground(new Color(0, 100, 0));
        panel.add(label);

        toast.add(panel);
        toast.pack();

        Window parent = SwingUtilities.getWindowAncestor(this);
        if (parent != null) {
            int x = parent.getX() + (parent.getWidth() - toast.getWidth()) / 2;
            int y = parent.getY() + 50;
            toast.setLocation(x, y);
        }

        toast.setAlwaysOnTop(true);
        toast.setVisible(true);

        Timer timer = new Timer(TOAST_NOTIFICATION_MS, e -> toast.dispose());
        timer.setRepeats(false);
        timer.start();
    }

    private void createGeminiSurvey(JTextField topicField, JSpinner countSpinner, JButton geminiBtn) {
        String topic = topicField.getText().trim();
        int count = (int) countSpinner.getValue();

        if (topic.isEmpty()) {
            JOptionPane.showMessageDialog(this, "נא להזין נושא לסקר.");
            return;
        }

        geminiBtn.setEnabled(false);
        new Thread(() -> {
            try {
                List<Question> questions = this.geminiService.generateQuestions(topic, count);
                StringBuilder preview = new StringBuilder("השאלות שנוצרו:\n\n");
                for (Question q : questions) {
                    preview.append("Q: ").append(q.getText()).append("\n");
                    for (PollOption opt : q.getOptions()) {
                        preview.append("  - ").append(opt.getText()).append("\n");
                    }
                }
                preview.append("\nאישור ותזמון הסקר למועד שנבחר?");

                SwingUtilities.invokeLater(() -> {
                    int confirm = JOptionPane.showConfirmDialog(this, preview.toString(), "אישור סקר Gemini", JOptionPane.YES_NO_OPTION);
                    if (confirm == JOptionPane.YES_OPTION) {
                        // קריאה לפונקציית העזר המשותפת
                        finalizeAndScheduleSurvey(topic, questions, topicField);
                    }
                    geminiBtn.setEnabled(true);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, "שגיאה מ-Gemini: " + ex.getMessage());
                    geminiBtn.setEnabled(true);
                });
            }
        }).start();
    }

    private void createManualSurvey(JTextField topicField, JSpinner countSpinner) {
        String topic = topicField.getText().trim();
        int count = (int) countSpinner.getValue();

        if (topic.isEmpty()) {
            JOptionPane.showMessageDialog(this, "נא להזין נושא לסקר.");
            return;
        }

        // פתיחת חלונית הקלט החדשה
        Frame parentFrame = (Frame) SwingUtilities.getWindowAncestor(this);
        ManualSurveyDialog dialog = new ManualSurveyDialog(parentFrame, count);
        dialog.setVisible(true);

        List<Question> questions = dialog.getQuestions();
        if (questions == null) return; // המשתמש ביטל או סגר את החלון

        // קריאה לפונקציית העזר המשותפת
        finalizeAndScheduleSurvey(topic, questions, topicField);
    }

    private void finalizeAndScheduleSurvey(String topic, List<Question> questions, JTextField topicField) {
        Integer delayMinutes = showSchedulingDialog();
        if (delayMinutes != null) {
            try {
                LocalDateTime scheduledTime = LocalDateTime.now().plusMinutes(delayMinutes);
                Survey createdSurvey = this.surveyService.createAndScheduleSurvey(topic, questions, scheduledTime);
                System.out.println("New survey scheduled successfully. ID: " + createdSurvey.getSurveyId());
                topicField.setText("");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "שגיאה: " + ex.getMessage());
            }
        }
    }

    private void refreshUI() {
        refreshHistoryTable();

        Survey active = this.surveyService.getActiveSurvey();
        Survey scheduled = this.surveyService.getScheduledSurvey();

        if (active != null) {
            checkAndNotifySurveyStarted(active);
            refreshActiveSurveyDisplay(active);
        } else if (scheduled != null) {
            this.lastActiveSurveyId = null; // איפוס
            refreshScheduledSurveyDisplay(scheduled);
        } else {
            refreshEmptySurveyDisplay();
        }
    }

// ==========================================
// פונקציות תצוגה ראשיות (Display Handlers)
// ==========================================

    private void refreshHistoryTable() {
        this.historyTableModel.setRowCount(0);
        for (Survey s : this.surveyService.getAllSurveys()) {
            this.historyTableModel.addRow(new Object[]{
                    s.getSurveyId(), s.getTopic(), s.getStatus(), DateFormatter.format(s.getStartTime())
            });
        }
    }

    private void refreshActiveSurveyDisplay(Survey active) {
        activeSurveyStatusLabel.setText("סקר פעיל: " + active.getTopic());
        activeSurveyStatusLabel.setForeground(new Color(0, 102, 0)); // ירוק כהה

        long secondsPassed = Duration.between(active.getStartTime(), LocalDateTime.now()).getSeconds();
        long remainingSeconds = Math.max(0, (AppConstants.SURVEY_DURATION_MINUTES * 60L) - secondsPassed);

        updateTimerDisplay(remainingSeconds, Color.BLUE, "זמן נותר לסיום: %02d:%02d");
        updateActiveParticipantsTableAndStats(active);

        viewResultsBtn.setEnabled(false);
    }

    private void refreshScheduledSurveyDisplay(Survey scheduled) {
        activeSurveyStatusLabel.setText("סקר בהמתנה לשליחה: " + scheduled.getTopic());
        activeSurveyStatusLabel.setForeground(new Color(204, 102, 0)); // כתום/חום

        activeTableModel.setRowCount(0);
        long delaySeconds = Math.max(0, Duration.between(LocalDateTime.now(), scheduled.getStartTime()).getSeconds());

        updateTimerDisplay(delaySeconds, Color.RED, "הסקר יישלח אוטומטית בעוד: %02d:%02d");
        statsLabel.setText("ממתין לתחילת זמן הסקר...");

        viewResultsBtn.setEnabled(checkIfResultsAvailable());
    }

    private void refreshEmptySurveyDisplay() {
        activeTableModel.setRowCount(0);
        activeSurveyStatusLabel.setText("אין סקר פעיל כעת");
        activeSurveyStatusLabel.setForeground(Color.BLACK);

        timerLabel.setText("זמן נותר: --:--");
        timerLabel.setForeground(Color.BLACK);
        statsLabel.setText("משתתפים: 0 | השלימו: 0 | טרם השלימו: 0");

        viewResultsBtn.setEnabled(checkIfResultsAvailable());
    }

// ==========================================
// פונקציות לוגיקה ועזר ממוקדות (Helper Methods)
// ==========================================

    private void checkAndNotifySurveyStarted(Survey active) {
        if (this.lastActiveSurveyId == null || !this.lastActiveSurveyId.equals(active.getSurveyId())) {
            this.lastActiveSurveyId = active.getSurveyId();
            showToastNotification();
        }
    }

    private void updateTimerDisplay(long totalSeconds, Color color, String formatPattern) {
        long mins = totalSeconds / 60;
        long secs = totalSeconds % 60;
        timerLabel.setForeground(color);
        timerLabel.setText(String.format(formatPattern, mins, secs));
    }

    private void updateActiveParticipantsTableAndStats(Survey active) {
        activeTableModel.setRowCount(0);
        int total = active.getParticipantsProgress().size();
        long completed = 0;

        for (ParticipantProgress p : active.getParticipantsProgress().values()) {
            if (p.isCompleted()) {
                completed++;
            }
            String statusStr = p.isCompleted() ? "השלים" : (p.getAnsweredQuestions().isEmpty() ? "טרם ענה" : "בתהליך");
            activeTableModel.addRow(new Object[]{
                    p.getMember().getFullName(),
                    p.getAnsweredQuestions().size() + " / " + active.getQuestions().size(),
                    statusStr
            });
        }

        statsLabel.setText("משתתפים: " + total + " | השלימו: " + completed + " | טרם השלימו: " + (total - completed));
    }

    private boolean checkIfResultsAvailable() {
        return this.surveyService.getAllSurveys().stream()
                .anyMatch(s -> s.getStatus() == PollStatus.CLOSED);
    }

    private void showResultsDialog() {
        Survey target = getLatestClosedSurvey();

        if (target == null) {
            JOptionPane.showMessageDialog(this, "אין סקרים שהסתיימו להצגה.");
            return;
        }

        // קריאה לפונקציה שמייצרת רק את הטקסט
        String resultsReport = generateSurveyResultsReport(target);

        // קריאה לפונקציה שמציגה את החלונית
        displayResultsDialog(resultsReport, target.getTopic());
    }

    private Survey getLatestClosedSurvey() {
        return this.surveyService.getAllSurveys().stream()
                .filter(s -> s.getStatus() == PollStatus.CLOSED)
                .max(Comparator.comparing(Survey::getStartTime))
                .orElse(null);
    }

    private String generateSurveyResultsReport(Survey target) {
        StringBuilder sb = new StringBuilder();
        sb.append("📊 תוצאות עבור הסקר: ").append(target.getTopic()).append("\n\n");

        for (Question q : target.getQuestions()) {
            appendQuestionResults(sb, q);
        }

        return sb.toString();
    }

    private void appendQuestionResults(StringBuilder sb, Question q) {
        sb.append("❓ שאלה ").append(q.getQuestionId()).append(": ").append(q.getText()).append("\n");

        int totalVotes = q.getOptions().stream().mapToInt(PollOption::getVoteCount).sum();

        // מיון התשובות בסדר יורד לפי כמות ההצבעות
        List<PollOption> sortedOpts = new ArrayList<>(q.getOptions());
        sortedOpts.sort(Comparator.comparingInt(PollOption::getVoteCount).reversed());

        for (PollOption opt : sortedOpts) {
            double pct = totalVotes > 0 ? ((double) opt.getVoteCount() / totalVotes) * 100 : 0.0;
            sb.append(String.format("  - %s: %d הצבעות (%.1f%%)\n", opt.getText(), opt.getVoteCount(), pct));
        }
        sb.append("\n");
    }

    private void displayResultsDialog(String reportText, String topic) {
        JTextArea textArea = new JTextArea(reportText);
        textArea.setEditable(false);

        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(RESULTS_DIALOG_WIDTH, RESULTS_DIALOG_HEIGHT));

        JOptionPane.showMessageDialog(this, scrollPane, "תוצאות הסקר: " + topic, JOptionPane.INFORMATION_MESSAGE);
    }
}