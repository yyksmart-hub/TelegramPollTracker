package com.polltracker.service;

import com.polltracker.model.PollOption;
import com.polltracker.model.Question;
import com.polltracker.util.AppConstants;
import com.polltracker.util.ConfigLoader;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class GeminiService {

    // עדכון למודל העדכני והנתמך ב-v1beta
    private static final String GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent?key=";
    public List<Question> generateQuestions(String topic, int count) throws Exception {
        String apiKey = ConfigLoader.getProperty("gemini.api.key");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            apiKey = ConfigLoader.getProperty("openai.api.key");
        }
        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.startsWith("הכנס")) {
            throw new RuntimeException("מפתח ה-API של Gemini חסר בקובץ ההגדרות (config.properties).");
        }

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost request = new HttpPost(GEMINI_URL + apiKey);
            request.setHeader("Content-Type", "application/json; charset=UTF-8");

            String prompt = String.format(
                    "Create a survey about '%s' with exactly %d questions. " +
                            "Each question must have between %d and %d options. " +
                            "Return ONLY a valid JSON array of objects in this exact structure: " +
                            "[{\"text\": \"question text here?\", \"options\": [\"option 1\", \"option 2\"]}] " +
                            "Do not include markdown blocks or any other text.",
                    topic, count, AppConstants.MIN_OPTIONS_PER_QUESTION, AppConstants.MAX_OPTIONS_PER_QUESTION
            );

            JSONObject payload = new JSONObject();
            JSONArray contents = new JSONArray();
            JSONObject contentObj = new JSONObject();
            JSONArray parts = new JSONArray();
            JSONObject partObj = new JSONObject();

            partObj.put("text", prompt);
            parts.put(partObj);
            contentObj.put("parts", parts);
            contents.put(contentObj);
            payload.put("contents", contents);

            request.setEntity(new StringEntity(payload.toString(), StandardCharsets.UTF_8));

            try (CloseableHttpResponse response = httpClient.execute(request)) {
                String responseString = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                JSONObject jsonResponse = new JSONObject(responseString);

                if (jsonResponse.has("error")) {
                    JSONObject errorObj = jsonResponse.getJSONObject("error");
                    throw new RuntimeException("שגיאת שרת מ-Gemini: " + errorObj.optString("message"));
                }

                String textContent = jsonResponse.getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text");

                return parseAiResponse(textContent);
            }
        }
    }

    private List<Question> parseAiResponse(String content) {
        List<Question> questions = new ArrayList<>();
        try {
            if (content.contains("```json")) {
                content = content.substring(content.indexOf("```json") + 7);
                if (content.contains("```")) {
                    content = content.substring(0, content.indexOf("```"));
                }
            } else if (content.contains("```")) {
                content = content.substring(content.indexOf("```") + 3);
                if (content.contains("```")) {
                    content = content.substring(0, content.indexOf("```"));
                }
            }
            content = content.trim();

            JSONArray jsonArray = new JSONArray(content);
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
            throw new RuntimeException("נכשל בפיענוח התשובה מ-AI: " + e.getMessage() + "\nתוכן שהתקבל: " + content);
        }
        return questions;
    }
}