package com.polltracker.service;

import com.polltracker.model.PollOption;
import com.polltracker.model.Question;
import com.polltracker.util.AppConstants;
import com.polltracker.util.ConfigLoader;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class AiSurveyService {

    private static final String PROXY_API_URL = "https://shaitest-production-3066.up.railway.app/api-request";

    public List<Question> generateQuestions(String topic, int count) throws Exception {
        String token = ConfigLoader.getProperty("ai.api.token");

        if (token == null || token.trim().isEmpty() || token.startsWith("הכנס")) {
            throw new RuntimeException("מפתח ה-API חסר בקובץ ההגדרות (config.properties).");
        }

        String prompt = buildPrompt(topic, count);

        URIBuilder builder = new URIBuilder(PROXY_API_URL);
        builder.addParameter("token", token);
        builder.addParameter("text", prompt);

        HttpGet request = new HttpGet(builder.build());

        try (CloseableHttpClient httpClient = HttpClients.createDefault();
             CloseableHttpResponse response = httpClient.execute(request)) {

            int statusCode = response.getStatusLine().getStatusCode();
            String responseString = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

            if (statusCode != 200) {
                throw new RuntimeException("שגיאת תקשורת! סטטוס: " + statusCode + " - " + responseString);
            }

            JSONObject jsonResponse = new JSONObject(responseString);
            validateServerResponse(jsonResponse, responseString);

            String textContent = jsonResponse.getString("value");
            String cleanedJsonText = cleanMarkdownFormatting(textContent);

            return parseJsonToQuestions(cleanedJsonText);
        }
    }

    private String buildPrompt(String topic, int count) {
        return String.format(
                "Create a survey about '%s' with exactly %d questions. " +
                        "Each question must have between %d and %d options. " +
                        "Return ONLY a valid JSON array of objects in this exact structure: " +
                        "[{\"text\": \"question text here?\", \"options\": [\"option 1\", \"option 2\"]}] " +
                        "Do not include markdown blocks or any other text.",
                topic, count, AppConstants.MIN_OPTIONS_PER_QUESTION, AppConstants.MAX_OPTIONS_PER_QUESTION
        );
    }

    private void validateServerResponse(JSONObject jsonResponse, String rawResponse) {
        if (jsonResponse.has("error") && jsonResponse.getBoolean("error")) {
            String errorCode = jsonResponse.optString("code", "לא ידוע");
            throw new RuntimeException("השרת של המרצה דחה את הבקשה! קוד שגיאה: " + errorCode);
        }

        if (!jsonResponse.has("value")) {
            throw new RuntimeException("השרת לא החזיר מפתח 'value'. התשובה שהתקבלה: " + rawResponse);
        }
    }

    private String cleanMarkdownFormatting(String content) {
        String cleaned = content.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }

        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        return cleaned.trim();
    }

    private List<Question> parseJsonToQuestions(String jsonContent) {
        List<Question> questions = new ArrayList<>();
        try {
            JSONArray jsonArray = new JSONArray(jsonContent);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject qObj = jsonArray.getJSONObject(i);
                String qText = qObj.getString("text");
                JSONArray optsArray = qObj.getJSONArray("options");

                List<PollOption> options = new ArrayList<>();
                for (int j = 0; j < optsArray.length(); j++) {
                    options.add(new PollOption(j + 1, optsArray.getString(j)));
                }
                questions.add(new Question(i + 1, qText, options));
            }
        } catch (Exception e) {
            throw new RuntimeException("נכשל בפיענוח התשובה מ-AI: " + e.getMessage() + "\nתוכן שהתקבל: " + jsonContent);
        }
        return questions;
    }
}