package com.example.remote;

import com.example.model.LogEntry;
import com.example.parser.LogParser;
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
    private final String remotePath;
    private final LogParser parser;

    private Session session;
    private ChannelSftp sftp;

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
        if (session != null && session.isConnected()) return;

        JSch jsch = new JSch();
        session = jsch.getSession(username, host, port);
        session.setPassword(password);

        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "no");
        session.setConfig(config);

        System.out.println("🔌 SFTP session.connect()");
        session.connect(5000);
        System.out.println("🛰 Opening SFTP channel...");
        Channel channel = session.openChannel("sftp");
        channel.connect(3000);
        System.out.println("✅ SFTP channel connected.");
        sftp = (ChannelSftp) channel;
    }

    @Override
    public void disconnect() {
        if (sftp != null && sftp.isConnected()) {
            sftp.disconnect();
        }
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
    }

    @Override
    public long getFileSize() throws SftpException {
        return sftp.lstat(remotePath).getSize();
    }

    @Override
    public byte[] read(long offset, int length) {
        try (InputStream input = sftp.get(remotePath);
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {

            input.skip(offset);

            byte[] chunk = new byte[8192];
            int remaining = length;
            int read;

            while ((read = input.read(chunk, 0, Math.min(chunk.length, remaining))) != -1 && remaining > 0) {
                buffer.write(chunk, 0, read);
                remaining -= read;
            }

            return buffer.toByteArray();

        } catch (Exception e) {
            e.printStackTrace();
            return new byte[0];
        }
    }

    public List<LogEntry> readFromOffset(long offset) {
        System.out.println("🔍 Reading remote file from offset: " + offset);
        List<LogEntry> entries = new ArrayList<>();

        InputStream input = null;

        try {
            System.out.println("📡 Calling sftp.get(...) for: " + remotePath);
            input = sftp.get(remotePath);
            System.out.println("📨 Stream opened successfully");

            if (input == null) {
                throw new IOException("❌ InputStream is null — failed to open remote file.");
            }

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;

            while ((read = input.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }

            String[] lines = buffer.toString(StandardCharsets.UTF_8).split("\n");
            for (String line : lines) {
                String decoded = new String(line.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
                LogEntry entry = parser.parseLine(decoded);
                entries.add(entry != null ? entry : new LogEntry("", "", "INVALID", "", "", "", false, decoded));
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (input != null) {
                try {
                    input.close();
                    System.out.println("✅ InputStream closed.");
                } catch (IOException e) {
                    System.err.println("❌ Failed to close InputStream");
                    e.printStackTrace();
                }
            }

            try {
                disconnect(); // очень важно
                System.out.println("🔌 SFTP disconnected.");
            } catch (Exception e) {
                System.err.println("❌ Error while disconnecting SFTP");
                e.printStackTrace();
            }
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

    public ChannelSftp getSftpChannel() {
        return sftp;
    }

    public byte[] readLastBytes(int maxBytes) {
        try {
            long fileSize = getFileSize();
            long offset = Math.max(0, fileSize - maxBytes);

            try (InputStream input = sftp.get(remotePath);
                 ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {

                input.skip(offset);

                byte[] chunk = new byte[8192];
                int read;

                while ((read = input.read(chunk)) != -1) {
                    buffer.write(chunk, 0, read);
                }

                return buffer.toByteArray();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new byte[0];
        }
    }
}
