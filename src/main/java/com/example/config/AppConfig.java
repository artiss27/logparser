package com.example.config;

/**
 * Application-wide configuration constants
 */
public final class AppConfig {

    private AppConfig() {
        // Prevent instantiation
    }

    // Loading Configuration
    public static final int DEFAULT_PAGE_SIZE = 500;
    public static final int MAX_CACHE_SIZE_MB = 100;
    public static final int DEFAULT_BUFFER_SIZE = 8192;

    // Remote Connection Configuration
    public static final int SFTP_CONNECT_TIMEOUT = 5000; // milliseconds
    public static final int SFTP_CHANNEL_TIMEOUT = 3000; // milliseconds
    public static final int SFTP_SERVER_ALIVE_INTERVAL = 15000; // milliseconds
    public static final int SFTP_SERVER_ALIVE_COUNT_MAX = 3;
    public static final int REMOTE_READ_MAX_BYTES = 1024 * 1024; // 1MB

    // UI Configuration
    public static final int TABLE_SAMPLE_SIZE = 20;
    public static final int MESSAGE_PREVIEW_LENGTH = 100;
    public static final int HIGHLIGHT_DURATION_SECONDS = 15;

    // Thread Pool Configuration
    public static final int EXECUTOR_THREAD_POOL_SIZE = 4;

    // File Configuration
    public static final String PROFILE_FILE_NAME = "profiles.json.enc";
    public static final String CRYPTO_KEY_FILE_NAME = ".logparser.key";

    // Log Levels
    public static final String[] LOG_LEVELS = {"All", "ERROR", "WARNING", "INFO", "NOTICE", "DEBUG"};
}

