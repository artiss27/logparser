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

        // Читаем чанк данных (по умолчанию 1 МБ или меньше, если файл меньше)
        int bytesToRead = (int) Math.min(AppConfig.REMOTE_READ_MAX_BYTES, filePointer);
        long startOffset = filePointer - bytesToRead;
        
        byte[] data = accessor.readChunk(startOffset, bytesToRead);
        String content = new String(data, StandardCharsets.UTF_8);
        
        // Разбиваем на строки
        String[] lines = content.split("\n");
        
        // Берем последние N строк (pageSize) из прочитанного чанка
        int start = Math.max(0, lines.length - pageSize);
        for (int i = start; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue; // Skip empty lines
            }
            entries.add(LogEntryFactory.parseOrInvalid(parser, line));
        }

        // Обновляем filePointer - сдвигаемся назад на прочитанные данные
        filePointer = startOffset;
        
        System.out.println("📦 RemotePagedLogLoader: loaded " + entries.size() 
            + " entries, remaining bytes: " + filePointer + "/" + fileSize);

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
        System.out.println("🔌 RemotePagedLogLoader closed.");
    }
}