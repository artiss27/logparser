package com.example.remote;

import java.io.IOException;

public interface RemoteFileAccessor {
    void connect() throws Exception;

    void disconnect();

    long getFileSize() throws Exception;

    byte[] read(long offset, int length) throws IOException;

    byte[] readLastBytes(int maxBytes) throws IOException;
}