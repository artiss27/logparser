package com.example.model;

public class Profile {
    private String name;
    private String path;
    private String format;

    public Profile() {
        // Default constructor for Jackson
    }

    public Profile(String name, String path, String format) {
        this.name = name;
        this.path = path;
        this.format = format;
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

    @Override
    public String toString() {
        return name;
    }
}