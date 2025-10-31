package com.example.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Profile {
    private String name;
    private String path; // используется и для локальных, и для удалённых
    private String format;
    private boolean remote;
    private String host;
    private int port;
    private String username;
    private String password; // пока без шифрования

    public Profile() {
        // Default constructor for Jackson
    }

    public Profile(String name, String path, String format) {
        this.name = name;
        this.path = path;
        this.format = format;
        this.remote = false;
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public String getFormat() {
        return format;
    }

    public boolean isRemote() {
        return remote;
    }

    public void setRemote(boolean remote) {
        this.remote = remote;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    @Override
    public String toString() {
        return name;
    }

    public String getId() {
        if (host == null || username == null) {
            return name + "_" + System.identityHashCode(this);
        }
        return host + ":" + port + "/" + username;
    }

    /**
     * Validate the profile configuration
     * @return true if profile is valid
     */
    public boolean isValid() {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        if (path == null || path.trim().isEmpty()) {
            return false;
        }
        if (remote) {
            if (host == null || host.trim().isEmpty()) {
                return false;
            }
            if (port < 1 || port > 65535) {
                return false;
            }
            if (username == null || username.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Get validation error message if invalid
     * @return Error message or null if valid
     */
    public String getValidationError() {
        if (name == null || name.trim().isEmpty()) {
            return "Profile name is required";
        }
        if (path == null || path.trim().isEmpty()) {
            return "Path is required";
        }
        if (remote) {
            if (host == null || host.trim().isEmpty()) {
                return "Host is required for remote profiles";
            }
            if (port < 1 || port > 65535) {
                return "Port must be between 1 and 65535";
            }
            if (username == null || username.trim().isEmpty()) {
                return "Username is required for remote profiles";
            }
        }
        return null;
    }
}