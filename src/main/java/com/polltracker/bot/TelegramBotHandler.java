package com.polltracker.bot;

import com.polltracker.event.CommunityChangeListener;
import com.polltracker.model.*;
import com.polltracker.service.CommunityService;
import com.polltracker.service.SurveyService;
import com.polltracker.util.AppConstants;
import com.polltracker.util.ConfigLoader;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TelegramBotHandler extends TelegramLongPollingBot implements CommunityChangeListener {

    private static final String[] JOINING_WORDS ={"/start","היי","Hi"};

    private final CommunityService communityService;
    private SurveyService surveyService;

    public TelegramBotHandler(CommunityService communityService) {
        super(ConfigLoader.getProperty("telegram.bot.token"));
        this.communityService = communityService;
        this.communityService.addListener(this);
    }

    public void setSurveyService(SurveyService surveyService) {
        this.surveyService = surveyService;
    }

    @Override
    public String getBotUsername() {
        return ConfigLoader.getProperty("telegram.bot.username");
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            handleTextMessage(update);
        } else if (update.hasCallbackQuery()) {
            handleCallbackQuery(update.getCallbackQuery());
        }
    }

    private void handleTextMessage(Update update) {
        String text = update.getMessage().getText().trim();
        long chatId = update.getMessage().getChatId();
        String firstName = update.getMessage().getFrom().getFirstName();
        String lastName = update.getMessage().getFrom().getLastName();
        String fullName = lastName != null ? firstName + " " + lastName : firstName;        String username = update.getMessage().getFrom().getUserName();

        // בדיקה קפדנית של תנאי ההצטרפות
        // בדיקה האם הטקסט תואם לאחת ממילות ההצטרפות
        boolean isJoiningCommand = Arrays.asList(JOINING_WORDS).contains(text);

        if (isJoiningCommand) {
            boolean added = this.communityService.registerMember(chatId, fullName.trim(), username != null ? username : "N/A");
            if (added) {
                sendMessage(chatId, "ברוך הבא לקהילת הסקרים! הצטרפת בהצלחה. 🎉");
            } else {
                sendMessage(chatId, "אתה כבר חבר רשום בקהילה.");
            }
        }
    }

    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        long chatId = callbackQuery.getMessage().getChatId();
        String data = callbackQuery.getData(); // פורמט: VOTE:surveyId:questionId:optionIndex

        if (data.startsWith(AppConstants.VOTE_CALLBACK_PREFIX)) {
            String[] parts = data.split(":");
            String surveyId = parts[1];
            int questionId = Integer.parseInt(parts[2]);
            int optionIndex = Integer.parseInt(parts[3]);

            boolean success = this.surveyService.registerVote(surveyId, chatId, questionId, optionIndex);

            AnswerCallbackQuery answer = new AnswerCallbackQuery();
            answer.setCallbackQueryId(callbackQuery.getId());

            if (success) {
                answer.setText("תשובתך נקלטה בהצלחה!");
            } else {
                answer.setText("לא ניתן לקלוט את ההצבעה (הסקר הסתיים או שכבר הצבעת).");
            }

            try {
                execute(answer);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }
    }

    public void sendQuestionToUser(long chatId, String surveyId, Question question) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("❓ *שאלה " + question.getQuestionId() + ":* " + question.getText());
        message.setParseMode("Markdown");

        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

        for (PollOption option : question.getOptions()) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            InlineKeyboardButton btn = new InlineKeyboardButton();
            btn.setText(option.getText());
            btn.setCallbackData(AppConstants.VOTE_CALLBACK_PREFIX + surveyId + ":" + question.getQuestionId() + ":" + option.getOptionIndex());
            row.add(btn);
            rowsInline.add(row);
        }

        markupInline.setKeyboard(rowsInline);
        message.setReplyMarkup(markupInline);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    public void sendMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onMemberJoined(CommunityMember newMember, int totalMembers) {
        // התראה לכל חברי הקהילה הקיימים על הצטרפות חבר חדש
        String notification = "👤 חבר קהילה חדש הצטרף: " + newMember.getFullName() +
                "\nגודל הקהילה העדכני: " + totalMembers + " חברים.";
        for (CommunityMember member : this.communityService.getAllMembers()) {
            if (member.getTelegramId() != newMember.getTelegramId()) {
                sendMessage(member.getTelegramId(), notification);
            }
        }
    }
}