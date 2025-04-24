package com.example.remote;

import com.example.model.LogEntry;
import com.example.parser.LogParser;
import javafx.concurrent.Task;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class RemotePagedLogLoader {

    private final RemoteFileAccessor accessor;
    private final LogParser parser;
    private final int pageSize;
    private long filePointer;

    public RemotePagedLogLoader(RemoteFileAccessor accessor, LogParser parser, int pageSize) throws IOException {
        this.accessor = accessor;
        this.parser = parser;
        this.pageSize = pageSize;
        try {
            this.accessor.connect();
            this.filePointer = accessor.getFileSize();
        } catch (Exception e) {
            throw new IOException("Failed to initialize RemotePagedLogLoader", e);
        }
    }

    public RemotePagedLogLoader(RemoteFileAccessor accessor, LogParser parser) throws IOException {
        this(accessor, parser, 500);
    }

    public List<LogEntry> loadNextPage() throws IOException {
        List<LogEntry> entries = new ArrayList<>();

        if (filePointer <= 0) return entries;

        // На первой загрузке читаем последние 1000 КБ файла
        byte[] data = accessor.readLastBytes(1000 * 1024);

        String content = new String(data, StandardCharsets.UTF_8);
        String[] lines = content.split("\n");

        int start = Math.max(0, lines.length - pageSize);
        for (int i = start; i < lines.length; i++) {
            LogEntry entry = parser.parseLine(lines[i]);
            entries.add(entry != null ? entry : new LogEntry("", "", "INVALID", "", "", "", false, lines[i]));
        }

        filePointer = 0;  // После первой загрузки устанавливаем в начало, так как чтение назад не поддерживается
        return entries;
    }

    public boolean hasMore() {
        return false;
    }

    public void reset() throws IOException {
        try {
            filePointer = accessor.getFileSize();
        } catch (Exception e) {
            throw new IOException("Failed to reset remote file pointer", e);
        }
    }

    public Task<List<LogEntry>> loadNextPageAsync(Consumer<List<LogEntry>> onSuccess, Consumer<Throwable> onError) {
        Task<List<LogEntry>> task = new Task<>() {
            @Override
            protected List<LogEntry> call() throws IOException {
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

    public void close() {
        accessor.disconnect();
    }
}