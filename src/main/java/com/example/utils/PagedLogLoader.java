package com.example.utils;

import com.example.model.LogEntry;
import com.example.parser.LogParser;
import javafx.concurrent.Task;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class PagedLogLoader {

    private final File file;
    private final LogParser parser;
    private final int pageSize;
    private long filePointer;

    public PagedLogLoader(File file, LogParser parser, int pageSize) {
        this.file = file;
        this.parser = parser;
        this.pageSize = pageSize;
        this.filePointer = file.length(); // Start from the end of the file
    }

    public PagedLogLoader(File file, LogParser parser) {
        this(file, parser, 500);
    }

    public List<LogEntry> loadNextPage() throws IOException {
        List<LogEntry> entries = new ArrayList<>();
        StringBuilder sb = new StringBuilder();

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            if (filePointer <= 0) return entries;

            long currentPosition = filePointer;
            int linesRead = 0;

            while (currentPosition > 0 && linesRead < pageSize) {
                currentPosition--;
                raf.seek(currentPosition);
                int readByte = raf.read();

                if (readByte == '\n') {
                    if (sb.length() > 0) {
                        String line = sb.reverse().toString();
                        sb.setLength(0);
                        String decoded = new String(line.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
                        LogEntry entry = parser.parseLine(decoded);
                        entries.add(entry != null ? entry : new LogEntry("", "", "INVALID", "", "", "", false, decoded));
                        linesRead++;
                    }
                } else {
                    sb.append((char) readByte);
                }
            }

            // Обрабатываем первую строку, если она не заканчивалась на \n
            if (sb.length() > 0 && linesRead < pageSize) {
                String line = sb.reverse().toString();
                String decoded = new String(line.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
                LogEntry entry = parser.parseLine(decoded);
                entries.add(entry != null ? entry : new LogEntry("", "", "INVALID", "", "", "", false, decoded));
                linesRead++;
                currentPosition = 0; // дошли до начала файла
            }

            filePointer = currentPosition; // ✅ сохраняем новую позицию
        }

        Collections.reverse(entries); // чтобы новые сверху
        return entries;
    }

    public Task<List<LogEntry>> loadNextPageAsync(Consumer<List<LogEntry>> onSuccess, Consumer<Throwable> onError) {
        Task<List<LogEntry>> task = new Task<>() {
            @Override
            protected List<LogEntry> call() throws Exception {
                return loadNextPage();
            }
        };

        task.setOnSucceeded(e -> onSuccess.accept(task.getValue()));
        task.setOnFailed(e -> onError.accept(task.getException()));

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();

        return task;
    }

    public boolean hasMore() {
        return filePointer > 0;
    }

    public void reset() {
        filePointer = file.length();
    }
}