package com.example.remote;

import com.example.config.AppConfig;
import com.example.loader.PagedLoader;
import com.example.model.LogEntry;
import com.example.parser.LogParser;
import com.example.utils.LogEntryFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class RemotePagedLogLoader implements PagedLoader {

    private final RemoteFileAccessor accessor;
    private final LogParser parser;
    private final int pageSize;
    private long filePointer;
    private boolean initialLoadDone;

    public RemotePagedLogLoader(RemoteFileAccessor accessor, LogParser parser, int pageSize) throws IOException {
        this.accessor = accessor;
        this.parser = parser;
        this.pageSize = pageSize;
        this.initialLoadDone = false;
        try {
            this.accessor.connect();
            this.filePointer = accessor.getFileSize();
        } catch (Exception e) {
            throw new IOException("Failed to initialize RemotePagedLogLoader", e);
        }
    }

    public RemotePagedLogLoader(RemoteFileAccessor accessor, LogParser parser) throws IOException {
        this(accessor, parser, AppConfig.DEFAULT_PAGE_SIZE);
    }

    @Override
    public List<LogEntry> loadNextPage() throws IOException {
        List<LogEntry> entries = new ArrayList<>();

        if (filePointer <= 0 && initialLoadDone) {
            return entries; // No more data to load
        }

        // На первой загрузке читаем последние N КБ файла
        byte[] data = accessor.readLastBytes(AppConfig.REMOTE_READ_MAX_BYTES);

        String content = new String(data, StandardCharsets.UTF_8);
        String[] lines = content.split("\n");

        int start = Math.max(0, lines.length - pageSize);
        for (int i = start; i < lines.length; i++) {
            if (lines[i].trim().isEmpty()) {
                continue; // Skip empty lines
            }
            entries.add(LogEntryFactory.parseOrInvalid(parser, lines[i]));
        }

        System.out.println("📦 RemotePagedLogLoader loaded " + entries.size() + " entries");

        initialLoadDone = true;
        // После первой загрузки помечаем, что больше данных нет (пока не поддерживается backward reading)
        filePointer = 0;

        return entries;
    }

    @Override
    public boolean hasMore() {
        // Remote paged loading currently only supports initial load
        // For true paging, need to implement backward file reading via SFTP
        return !initialLoadDone && filePointer > 0;
    }

    @Override
    public void reset() throws IOException {
        try {
            filePointer = accessor.getFileSize();
            initialLoadDone = false;
        } catch (Exception e) {
            throw new IOException("Failed to reset remote file pointer", e);
        }
    }

    @Override
    public void close() throws IOException {
        accessor.disconnect();
        System.out.println("🔌 RemotePagedLogLoader closed.");
    }
}