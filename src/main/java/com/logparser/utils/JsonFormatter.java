package com.logparser.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

public class JsonFormatter {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final ObjectWriter prettyWriter = mapper.writerWithDefaultPrettyPrinter();

    private JsonFormatter() {
        // Utility class - закрываем конструктор
    }

    public static String formatJson(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return "";
        }
        try {
            Object json = mapper.readValue(rawJson, Object.class);
            return prettyWriter.writeValueAsString(json);
        } catch (Exception e) {
            return rawJson;
        }
    }
}