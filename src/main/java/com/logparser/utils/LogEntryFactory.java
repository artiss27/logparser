package com.logparser.utils;

import com.logparser.model.LogEntry;
import com.logparser.parser.LogParser;

/**
 * Factory for creating LogEntry objects
 */
public final class LogEntryFactory {

    private LogEntryFactory() {
        // Prevent instantiation
    }

    /**
     * Parse a line using the given parser, returning a valid entry or invalid placeholder
     * @param parser The log parser to use
     * @param line The line to parse
     * @return A valid LogEntry or an invalid entry containing the raw line
     */
    public static LogEntry parseOrInvalid(LogParser parser, String line) {
        if (line == null || line.trim().isEmpty()) {
            return createInvalid("");
        }

        LogEntry entry = parser.parseLine(line);
        return entry != null ? entry : createInvalid(line);
    }

    /**
     * Create an invalid LogEntry with the raw line
     * @param rawLine The unparseable raw line
     * @return An invalid LogEntry
     */
    public static LogEntry createInvalid(String rawLine) {
        return new LogEntry("", "", "INVALID", "", "", "", false, rawLine);
    }

    /**
     * Create a "Load More" marker entry
     * @return A special LogEntry for "load more" button
     */
    public static LogEntry createLoadMoreMarker() {
        return new LogEntry("", "", "LM", "", "", "", true, "");
    }

    /**
     * Create a spacer entry
     * @return A special LogEntry for spacing
     */
    public static LogEntry createSpacer() {
        return new LogEntry("", "", "-", "", "", "", true, "");
    }
}

