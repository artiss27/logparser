package com.logparser.parser;

import com.logparser.model.LogEntry;
import java.io.File;
import java.io.IOException;
import java.util.List;

public interface LogParser {
    LogEntry parseLine(String line);
    List<LogEntry> parse(File file) throws IOException;
}