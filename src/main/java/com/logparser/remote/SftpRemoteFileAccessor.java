package com.logparser.remote;

import com.logparser.config.AppConfig;
import com.logparser.model.LogEntry;
import com.logparser.parser.LogParser;
import com.logparser.utils.LogEntryFactory;
import com.jcraft.jsch.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.*;

public class SftpRemoteFileAccessor implements RemoteFileAccessor {

    private static final Logger log = LoggerFactory.getLogger(SftpRemoteFileAccessor.class);

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private String remotePath;
    private final LogParser parser;

    private ChannelSftp sftp;
    private Session session;

    public SftpRemoteFileAccessor(String host, int port, String username, String password, String remotePath, LogParser parser) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.remotePath = remotePath;
        this.parser = parser;
    }

    @Override
    public void connect() throws Exception {
        if (isAlive()) return;

        if (session == null || !session.isConnected()) {
            log.debug("Connecting to SFTP: {}@{}:{}", username, host, port);
            JSch jsch = new JSch();
            session = jsch.getSession(username, host, port);
            session.setPassword(password);
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            config.put("PreferredAuthentications", "password");
            session.setConfig(config);
            session.setServerAliveInterval(AppConfig.SFTP_SERVER_ALIVE_INTERVAL);
            session.setServerAliveCountMax(AppConfig.SFTP_SERVER_ALIVE_COUNT_MAX);
            session.connect(AppConfig.SFTP_CONNECT_TIMEOUT);
        }

        if (sftp == null || !sftp.isConnected()) {
            Channel channel = session.openChannel("sftp");
            channel.connect(AppConfig.SFTP_CHANNEL_TIMEOUT);
            sftp = (ChannelSftp) channel;
            log.debug("SFTP channel connected");
        }
    }

    @Override
    public void disconnect() {
        if (sftp != null && sftp.isConnected()) {
            sftp.disconnect();
        }
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
        sftp = null;
        session = null;
    }

    @Override
    public long getFileSize() throws Exception {
        if (sftp == null || !sftp.isConnected()) {
            connect();
        }
        return sftp.lstat(remotePath).getSize();
    }


    @Override
    public byte[] read(long offset, int length) {
        try {
            if (sftp == null || !sftp.isConnected()) {
                connect();
            }
            try (InputStream input = sftp.get(remotePath);
                 ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {

                input.skip(offset);

                byte[] chunk = new byte[AppConfig.DEFAULT_BUFFER_SIZE];
                int remaining = length;
                int read;

                while ((read = input.read(chunk, 0, Math.min(chunk.length, remaining))) != -1 && remaining > 0) {
                    buffer.write(chunk, 0, read);
                    remaining -= read;
                }

                return buffer.toByteArray();
            }
        } catch (Exception e) {
            log.error("Failed to read from remote file: {}", remotePath, e);
            return new byte[0];
        }
    }

    @Override
    public byte[] readLastBytes(int maxBytes) {
        ChannelSftp localSftp = null;
        try {
            if (!isAlive()) connect();

            Channel channel = session.openChannel("sftp");
            channel.connect(3000);
            localSftp = (ChannelSftp) channel;

            long fileSize = localSftp.lstat(remotePath).getSize();
            if (fileSize == 0) return new byte[0];
            long bytesToRead = Math.min(maxBytes, fileSize);
            long startOffset = Math.max(0, fileSize - bytesToRead);

            try (InputStream input = localSftp.get(remotePath, null, startOffset);
                 ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {

                byte[] chunk = new byte[AppConfig.DEFAULT_BUFFER_SIZE];
                int read;
                long remaining = bytesToRead;

                while (remaining > 0 && (read = input.read(chunk, 0, (int) Math.min(chunk.length, remaining))) != -1) {
                    buffer.write(chunk, 0, read);
                    remaining -= read;
                }

                return buffer.toByteArray();
            }

        } catch (Exception e) {
            log.error("Failed to read last {} bytes from: {}", maxBytes, remotePath, e);
            return new byte[0];
        } finally {
            if (localSftp != null && localSftp.isConnected()) localSftp.disconnect();
        }
    }

    /**
     * Reads new lines from a remote file starting from the specified offset.
     * Limits reading to prevent excessive data transfer.
     *
     * @param offset position in the file to start reading from
     * @return list of new log entries
     */
    public List<LogEntry> readFromOffset(long offset) {
        List<LogEntry> entries = new ArrayList<>();
        ChannelSftp localSftp = null;

        try {
            if (!isAlive()) connect();
            Channel channel = session.openChannel("sftp");
            channel.connect(AppConfig.SFTP_CHANNEL_TIMEOUT);
            localSftp = (ChannelSftp) channel;

            long fileSize = localSftp.lstat(remotePath).getSize();
            if (offset >= fileSize) return entries;

            long bytesToRead = fileSize - offset;
            long maxReadSize = AppConfig.MAX_INCREMENTAL_READ_MB * 1024L * 1024L;

            if (bytesToRead > maxReadSize) {
                offset = fileSize - maxReadSize;
            }

            try (InputStream input = localSftp.get(remotePath, null, offset);
                 ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {

                byte[] chunk = new byte[AppConfig.DEFAULT_BUFFER_SIZE];
                int read;
                while ((read = input.read(chunk)) != -1) buffer.write(chunk, 0, read);

                String[] lines = buffer.toString(StandardCharsets.UTF_8).split("\n");
                for (String line : lines) {
                    String decoded = new String(line.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
                    entries.add(LogEntryFactory.parseOrInvalid(parser, decoded));
                }
            }

        } catch (Exception e) {
            log.error("Failed to read from offset {} in: {}", offset, remotePath, e);
        } finally {
            if (localSftp != null && localSftp.isConnected()) localSftp.disconnect();
        }
        return entries;
    }

    @Override
    public byte[] readChunk(long offset, int length) {
        ChannelSftp localSftp = null;
        try {
            if (!isAlive()) connect();

            Channel channel = session.openChannel("sftp");
            channel.connect(AppConfig.SFTP_CHANNEL_TIMEOUT);
            localSftp = (ChannelSftp) channel;

            try (InputStream input = localSftp.get(remotePath, null, offset);
                 ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {

                byte[] chunk = new byte[AppConfig.DEFAULT_BUFFER_SIZE];
                int read;
                long remaining = length;

                while (remaining > 0 && (read = input.read(chunk, 0, (int) Math.min(chunk.length, remaining))) != -1) {
                    buffer.write(chunk, 0, read);
                    remaining -= read;
                }

                return buffer.toByteArray();
            }

        } catch (Exception e) {
            log.error("Failed to read chunk at offset {} from: {}", offset, remotePath, e);
            return new byte[0];
        } finally {
            if (localSftp != null && localSftp.isConnected()) localSftp.disconnect();
        }
    }

    private InputStream getWithTimeout(ChannelSftp sftp, String path, long timeoutMillis) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<InputStream> future = executor.submit(() -> sftp.get(path));

        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new IOException("Timeout while opening remote file: " + path);
        } finally {
            executor.shutdownNow();
        }
    }

    public void setRemotePath(String remotePath) {
        this.remotePath = remotePath;
    }

    public Session getSession() { return session; }

    public boolean isAlive() {
        return session != null && session.isConnected() && sftp != null && sftp.isConnected();
    }
}
