package com.example.model;

public class LogEntry {
    private String date;
    private String file;
    private String level;
    private String message;
    private String context;
    private String extra;
    private boolean valid;
    private String rawLine;

    public LogEntry(String date, String file, String level, String message, String context, String extra) {
        this(date, file, level, message, context, extra, true, null);
    }

    public LogEntry(String date, String file, String level, String message, String context, String extra, boolean valid, String rawLine) {
        this.date = date;
        this.file = file;
        this.level = level;
        this.message = message;
        this.context = context;
        this.extra = extra;
        this.valid = valid;
        this.rawLine = rawLine;
    }

    public String getDate() {
        return date;
    }

    public String getFile() {
        return file;
    }

    public String getLevel() {
        return level;
    }

    public String getMessage() {
        return message;
    }

    public String getContext() {
        return context;
    }

    public String getExtra() {
        return extra;
    }

    public boolean isValid() {
        return valid;
    }

    public String getRawLine() {
        return rawLine;
    }
}