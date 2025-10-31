package com.logparser.utils;

import com.logparser.config.AppConfig;
import com.logparser.loader.PagedLoader;
import com.logparser.model.LogEntry;
import com.logparser.parser.LogParser;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PagedLogLoader implements PagedLoader {

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
        this(file, parser, AppConfig.DEFAULT_PAGE_SIZE);
    }

    @Override
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
                        entries.add(LogEntryFactory.parseOrInvalid(parser, decoded));
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
                entries.add(LogEntryFactory.parseOrInvalid(parser, decoded));
                linesRead++;
                currentPosition = 0; // дошли до начала файла
            }

            filePointer = currentPosition; // ✅ сохраняем новую позицию
        }

        Collections.reverse(entries); // чтобы новые сверху
        return entries;
    }

    @Override
    public boolean hasMore() {
        return filePointer > 0;
    }

    @Override
    public void reset() {
        filePointer = file.length();
    }

    @Override
    public void close() throws IOException {
        // No resources to close for local file
    }

    /**
     * Загружает новые строки, добавленные в файл с момента previousSize.
     * Используется для инкрементального обновления при мониторинге изменений файла.
     *
     * @param previousSize предыдущий размер файла (offset, с которого начинать чтение)
     * @return список новых записей логов
     * @throws IOException при ошибке чтения файла
     */
    public List<LogEntry> loadNewLines(long previousSize) throws IOException {
        List<LogEntry> newEntries = new ArrayList<>();

        long currentSize = file.length();
        if (previousSize >= currentSize) return newEntries;

        // Ограничиваем количество читаемых данных за раз
        long maxReadSize = AppConfig.MAX_INCREMENTAL_READ_MB * 1024L * 1024L;
        long bytesToRead = currentSize - previousSize;

        if (bytesToRead > maxReadSize) {
            // Если изменений слишком много, читаем только последние N МБ
            System.out.println("⚠️ File changed by " + (bytesToRead / 1024 / 1024) + " MB, limiting to last "
                + AppConfig.MAX_INCREMENTAL_READ_MB + " MB");
            previousSize = currentSize - maxReadSize;
        }

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(previousSize);

            String line;
            while ((line = raf.readLine()) != null) {
                String decoded = new String(line.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
                newEntries.add(LogEntryFactory.parseOrInvalid(parser, decoded));
            }
        }

        return newEntries;
    }
}