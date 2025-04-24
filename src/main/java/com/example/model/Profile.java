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
}