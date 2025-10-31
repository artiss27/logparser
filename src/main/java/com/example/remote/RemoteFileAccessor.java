package com.example.remote;

import java.io.IOException;

public interface RemoteFileAccessor {
    void connect() throws Exception;
    void disconnect();
    long getFileSize() throws Exception;
    byte[] read(long offset, int length);
    byte[] readLastBytes(int maxBytes);

    /**
     * Читает чанк данных из файла начиная с указанного offset
     * @param offset позиция начала чтения
     * @param length количество байт для чтения
     * @return массив прочитанных байт
     */
    byte[] readChunk(long offset, int length);
}