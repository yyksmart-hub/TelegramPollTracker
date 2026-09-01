package com.polltracker.util;

import java.io.InputStream;
import java.util.Properties;

public class ConfigLoader {
    private static final Properties properties = new Properties();

    static {
        try (InputStream input = ConfigLoader.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                System.err.println("שגיאה: הקובץ config.properties לא נמצא בתיקיית resources!");
            } else {
                properties.load(input);
            }
        } catch (Exception ex) {
            System.err.println("שגיאה בטעינת קובץ ההגדרות: " + ex.getMessage());
        }
    }

    public static String getProperty(String key) {
        return properties.getProperty(key);
    }
}