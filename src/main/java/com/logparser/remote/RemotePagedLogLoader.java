package com.logparser.remote;

import com.logparser.config.AppConfig;
import com.logparser.loader.PagedLoader;
import com.logparser.model.LogEntry;
import com.logparser.parser.LogParser;
import com.logparser.utils.LogEntryFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class RemotePagedLogLoader implements PagedLoader {

    private final RemoteFileAccessor accessor;
    private final LogParser parser;
    private final int pageSize;
    private long filePointer; // Current position in file (reading backwards)
    private final long fileSize; // Total file size

    public RemotePagedLogLoader(RemoteFileAccessor accessor, LogParser parser, int pageSize) throws IOException {
        this.accessor = accessor;
        this.parser = parser;
        this.pageSize = pageSize;
        try {
            this.accessor.connect();
            this.fileSize = accessor.getFileSize();
            this.filePointer = fileSize; // Start from the end
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

        if (filePointer <= 0) {
            return entries; // No more data to load
        }

        int bytesToRead = (int) Math.min(AppConfig.REMOTE_READ_MAX_BYTES, filePointer);
        long startOffset = filePointer - bytesToRead;

        byte[] data = accessor.readChunk(startOffset, bytesToRead);
        String content = new String(data, StandardCharsets.UTF_8);

        String[] lines = content.split("\n");

        int start = Math.max(0, lines.length - pageSize);
        for (int i = start; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue; // Skip empty lines
            }
            entries.add(LogEntryFactory.parseOrInvalid(parser, line));
        }

        filePointer = startOffset;

        return entries;
    }

    @Override
    public boolean hasMore() {
        return filePointer > 0;
    }

    @Override
    public void reset() throws IOException {
        try {
            filePointer = accessor.getFileSize();
        } catch (Exception e) {
            throw new IOException("Failed to reset remote file pointer", e);
        }
    }

    @Override
    public void close() throws IOException {
        accessor.disconnect();
    }
}