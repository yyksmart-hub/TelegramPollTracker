package com.polltracker.ui;

import com.polltracker.model.PollOption;
import com.polltracker.model.Question;
import com.polltracker.util.AppConstants;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ManualSurveyDialog extends JDialog {

    private final List<Question> generatedQuestions = new ArrayList<>();
    private boolean confirmed = false;

    public ManualSurveyDialog(Frame parent, int questionCount) {
        super(parent, "הזנת שאלות לסקר ידני", true);
        setLayout(new BorderLayout(10, 10));
        setSize(500, 450);
        setLocationRelativeTo(parent);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        List<JTextField> questionFields = new ArrayList<>();
        List<JTextField> optionsFields = new ArrayList<>();

        for (int i = 1; i <= questionCount; i++) {
            JPanel qPanel = new JPanel(new GridLayout(2, 2, 5, 5));
            qPanel.setBorder(BorderFactory.createTitledBorder("שאלה " + i));

            JTextField qField = new JTextField();
            JTextField optField = new JTextField();

            qPanel.add(new JLabel("נוסח השאלה:"));
            qPanel.add(qField);
            qPanel.add(new JLabel("אפשרויות (מופרדות בפסיק):"));
            qPanel.add(optField);

            questionFields.add(qField);
            optionsFields.add(optField);

            mainPanel.add(qPanel);
            mainPanel.add(Box.createVerticalStrut(10));
        }

        JButton submitBtn = new JButton("אישור ואיסוף שאלות");
        submitBtn.addActionListener(e -> {
            generatedQuestions.clear();
            for (int i = 0; i < questionCount; i++) {
                String qText = questionFields.get(i).getText().trim();
                String rawOpts = optionsFields.get(i).getText().trim();

                if (qText.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "שאלה " + (i + 1) + " ריקה.");
                    return;
                }

                String[] opts = rawOpts.split(",");
                if (opts.length < AppConstants.MIN_OPTIONS_PER_QUESTION || opts.length > AppConstants.MAX_OPTIONS_PER_QUESTION) {
                    JOptionPane.showMessageDialog(this, "שאלה " + (i + 1) + " חייבת להכיל בין " +
                            AppConstants.MIN_OPTIONS_PER_QUESTION + " ל-" + AppConstants.MAX_OPTIONS_PER_QUESTION + " תשובות.");
                    return;
                }

                List<PollOption> optionList = new ArrayList<>();
                for (int j = 0; j < opts.length; j++) {
                    optionList.add(new PollOption(j + 1, opts[j].trim()));
                }
                generatedQuestions.add(new Question(i + 1, qText, optionList));
            }
            confirmed = true;
            dispose();
        });

        add(new JScrollPane(mainPanel), BorderLayout.CENTER);
        add(submitBtn, BorderLayout.SOUTH);
    }

    public List<Question> getQuestions() {
        return confirmed ? generatedQuestions : null;
    }
}