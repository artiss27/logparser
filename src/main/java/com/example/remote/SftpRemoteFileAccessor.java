package com.example.remote;

import com.example.config.AppConfig;
import com.example.model.LogEntry;
import com.example.parser.LogParser;
import com.example.utils.LogEntryFactory;
import com.jcraft.jsch.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.*;

public class SftpRemoteFileAccessor implements RemoteFileAccessor {

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
            System.out.println("🔌 SFTP session.connect()");
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
            System.out.println("🛰 Opening SFTP channel...");
            Channel channel = session.openChannel("sftp");
            channel.connect(AppConfig.SFTP_CHANNEL_TIMEOUT);
            sftp = (ChannelSftp) channel;
            System.out.println("✅ SFTP channel connected.");
        }
    }

    @Override
    public void disconnect() {
        if (sftp != null && sftp.isConnected()) {
            System.out.println("🛑 Disconnecting SFTP channel...");
            sftp.disconnect();
        }
        if (session != null && session.isConnected()) {
            System.out.println("🛑 Disconnecting SFTP session...");
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
            e.printStackTrace();
            return new byte[0]; // Возврат пустого массива в случае ошибки
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
            e.printStackTrace();
            return new byte[0];
        } finally {
            if (localSftp != null && localSftp.isConnected()) localSftp.disconnect();
        }
    }

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
            e.printStackTrace();
        } finally {
            if (localSftp != null && localSftp.isConnected()) localSftp.disconnect();
        }
        return entries;
    }

    private InputStream getWithTimeout(ChannelSftp sftp, String path, long timeoutMillis) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<InputStream> future = executor.submit(() -> sftp.get(path));

        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new IOException("⏰ Timeout while opening remote file: " + path);
        } finally {
            executor.shutdownNow();
        }
    }

    public void setRemotePath(String remotePath) {
        this.remotePath = remotePath;
        System.out.println("📄 Remote path set to: " + remotePath);
    }

    public Session getSession() { return session; }

    public boolean isAlive() {
        return session != null && session.isConnected() && sftp != null && sftp.isConnected();
    }
}
