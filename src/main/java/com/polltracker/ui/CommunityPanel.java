package com.polltracker.ui;

import com.polltracker.event.CommunityChangeListener;
import com.polltracker.model.CommunityMember;
import com.polltracker.service.CommunityService;
import com.polltracker.util.DateFormatter;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

public class CommunityPanel extends JPanel implements CommunityChangeListener {

    private final CommunityService communityService;
    private final DefaultTableModel tableModel;
    private final JLabel totalLabel;

    public CommunityPanel(CommunityService communityService) {
        this.communityService = communityService;
        this.communityService.addListener(this);

        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // כותרת וספירת חברים
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        totalLabel = new JLabel("סה\"כ חברי קהילה: 0");
        totalLabel.setFont(new Font("Arial", Font.BOLD, 14));
        headerPanel.add(totalLabel);
        add(headerPanel, BorderLayout.NORTH);

        // טבלת חברים
        String[] columnNames = {"מזהה טלגרם", "שם מלא", "שם משתמש", "תאריך הצטרפות"};
        tableModel = new DefaultTableModel(columnNames, 0);
        JTable table = new JTable(tableModel);
        add(new JScrollPane(table), BorderLayout.CENTER);

        refreshTable();
    }

    private void refreshTable() {
        tableModel.setRowCount(0);
        for (CommunityMember member : communityService.getAllMembers()) {
            tableModel.addRow(new Object[]{
                    member.getTelegramId(),
                    member.getFullName(),
                    "@" + member.getUsername(),
                    DateFormatter.format(member.getJoinedAt())
            });
        }
        totalLabel.setText("סה\"כ חברי קהילה: " + communityService.getMemberCount());
    }

    @Override
    public void onMemberJoined(CommunityMember newMember, int totalMembers) {
        SwingUtilities.invokeLater(this::refreshTable);
    }
}