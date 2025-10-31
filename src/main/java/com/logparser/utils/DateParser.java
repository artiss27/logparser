package com.logparser.utils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Utility for parsing dates from log entries
 */
public final class DateParser {

    private static final DateTimeFormatter DOT_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter ISO_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private DateParser() {
        // Prevent instantiation
    }

    /**
     * Parse a date string from a log entry
     * @param dateString The date string (may include time)
     * @return LocalDate or null if unparseable
     */
    public static LocalDate parseLogDate(String dateString) {
        if (dateString == null || dateString.isBlank()) {
            return null;
        }

        try {
            // Extract just the date part (before space)
            String datePart = dateString.split(" ")[0];

            // Try dot format first (dd.MM.yyyy)
            if (datePart.contains(".")) {
                return LocalDate.parse(datePart, DOT_FORMAT);
            }

            // Try ISO format (yyyy-MM-dd)
            if (datePart.contains("-")) {
                return LocalDate.parse(datePart, ISO_FORMAT);
            }

        } catch (DateTimeParseException | ArrayIndexOutOfBoundsException e) {
            // Return null if parsing fails
        }

        return null;
    }

    /**
     * Check if a date is between two dates (inclusive)
     * @param date The date to check
     * @param from Start date (null means no lower bound)
     * @param to End date (null means no upper bound)
     * @return true if date is in range
     */
    public static boolean isBetween(LocalDate date, LocalDate from, LocalDate to) {
        if (date == null) {
            return true; // Can't filter null dates
        }

        if (from != null && date.isBefore(from)) {
            return false;
        }

        if (to != null && date.isAfter(to)) {
            return false;
        }

        return true;
    }
}

