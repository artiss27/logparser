package com.example.parser;

import com.example.model.LogEntry;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SymfonyLogParser implements LogParser {

    private static final Pattern logPattern = Pattern.compile(
            "\\[(\\d{4}-\\d{2}-\\d{2})T(\\d{2}:\\d{2}:\\d{2})\\.[^\\]]+]\\s+(\\w+)\\.(\\w+):\\s+(.*)"
    );

    @Override
    public LogEntry parseLine(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }

        Matcher matcher = logPattern.matcher(line);
        if (matcher.find()) {
            String datePart = matcher.group(1);
            String timePart = matcher.group(2);
            String type = matcher.group(3);
            String level = matcher.group(4);
            String remaining = matcher.group(5);

            String context = "";
            String message = remaining;

            // Если в оставшейся части есть JSON
            int jsonStart = remaining.indexOf('{');
            int jsonEnd = remaining.lastIndexOf('}');

            if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
                message = remaining.substring(0, jsonStart).trim();
                context = remaining.substring(jsonStart, jsonEnd + 1).trim();
            }

            return new LogEntry(datePart + " " + timePart, "", level, message, context, "");
        }

        return null;
    }

    @Override
    public List<LogEntry> parse(File file) throws IOException {
        List<String> lines = Files.readAllLines(file.toPath());
        List<LogEntry> entries = new ArrayList<>();

        StringBuilder currentLog = new StringBuilder();

        for (String line : lines) {
            if (line.startsWith("[")) {
                if (currentLog.length() > 0) {
                    LogEntry entry = parseLine(currentLog.toString());
                    if (entry != null) {
                        entries.add(entry);
                    }
                    currentLog.setLength(0); // очищаем буфер
                }
            }
            if (currentLog.length() > 0) {
                currentLog.append("\n");
            }
            currentLog.append(line);
        }

        // Последний лог, если остался
        if (currentLog.length() > 0) {
            LogEntry entry = parseLine(currentLog.toString());
            if (entry != null) {
                entries.add(entry);
            }
        }

        return entries;
    }
}