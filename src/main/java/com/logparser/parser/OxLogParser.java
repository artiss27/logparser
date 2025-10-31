package com.logparser.parser;

import com.logparser.model.LogEntry;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.List;

public class OxLogParser implements LogParser {

    private static final Pattern LOG_START_PATTERN = Pattern.compile(
            "(\\d{2}\\.\\d{2}\\.\\d{4}) (\\d{2}:\\d{2}:\\d{2}) \\[([^\\]]+)] (\\S+)[:-] (.*)", Pattern.DOTALL
    );

    @Override
    public LogEntry parseLine(String block) {
        if (block == null || block.isBlank()) {
            return null;
        }

        Matcher matcher = LOG_START_PATTERN.matcher(block);
        if (matcher.find()) {
            String date = matcher.group(1);
            String time = matcher.group(2);
            String dateTime = date + " " + time;
            String file = matcher.group(3);
            String levelFull = matcher.group(4);
            String remaining = matcher.group(5).trim();

            String level = levelFull.contains(".") ? levelFull.substring(levelFull.indexOf('.') + 1) : levelFull;

            String message = "";
            String context = "";
            String extra = "";

            int contextStart = remaining.indexOf("<[context]>");
            int contextEnd = remaining.indexOf("<[/context]>");
            int extraStart = remaining.indexOf("<[extra]>");
            int extraEnd = remaining.indexOf("<[/extra]>");

            if (contextStart != -1 && contextEnd != -1) {
                context = remaining.substring(contextStart + "<[context]>".length(), contextEnd).trim();
            }

            if (extraStart != -1 && extraEnd != -1) {
                extra = remaining.substring(extraStart + "<[extra]>".length(), extraEnd).trim();
            }

            int messageEnd = contextStart != -1 ? contextStart : (extraStart != -1 ? extraStart : remaining.length());
            message = remaining.substring(0, messageEnd).trim();

            // --- beautify stack trace in message ---
            message = formatStackTrace(message);

            return new LogEntry(dateTime, file, level, message, context, extra);
        }

        return null;
    }

    // --- Utility for beautifying stack trace in message ---
    private String formatStackTrace(String input) {
        if (input == null) return null;
        int idx = input.indexOf("Stack trace:");
        if (idx == -1) return input;

        String before = input.substring(0, idx).trim();
        String stack = input.substring(idx).trim();

        // каждый #N переносим на новую строку (и саму первую строку тоже)
        stack = stack.replaceAll("\\s*#(\\d+)\\s*", "\n#$1 ");
        // чтобы "Stack trace:" начинался с новой строки
        stack = "\n\n" + stack.trim();

        return before + stack;
    }

    @Override
    public List<LogEntry> parse(File file) throws IOException {
        String content = Files.readString(file.toPath());

        List<LogEntry> entries = new ArrayList<>();

        Pattern splitPattern = Pattern.compile("(?=\\d{2}\\.\\d{2}\\.\\d{4} \\d{2}:\\d{2}:\\d{2} \\[)");
        String[] blocks = splitPattern.split(content);

        for (String block : blocks) {
            block = block.trim();
            if (!block.isEmpty()) {
                LogEntry entry = parseLine(block);
                if (entry != null) {
                    entries.add(entry);
                }
            }
        }

        return entries;
    }
}