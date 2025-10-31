package com.logparser.utils;

import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JsonHighlighter {

    private static final Pattern PATTERN = Pattern.compile(
            "(?<KEY>\"(\\\\.|[^\"])*\"\\s*:)" +
                    "|(?<STRING>\"(\\\\.|[^\"])*\")" +
                    "|(?<NUMBER>-?\\d+(\\.\\d+)?)" +
                    "|(?<BRACE>[{}])" +
                    "|(?<BRACKET>[\\[\\]])" +
                    "|(?<COLON>:)" +
                    "|(?<COMMA>,)"
    );

    public static Pattern getPattern() {
        return PATTERN;
    }

    public static StyleSpans<Collection<String>> computeHighlighting(String text) {
        Matcher matcher = PATTERN.matcher(text);
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();

        int lastEnd = 0;
        while (matcher.find()) {
            String styleClass =
                    matcher.group("KEY") != null ? "json-key" :
                            matcher.group("STRING") != null ? "json-string" :
                                    matcher.group("NUMBER") != null ? "json-number" :
                                            matcher.group("BRACE") != null ? "json-brace" :
                                                    matcher.group("BRACKET") != null ? "json-bracket" :
                                                            matcher.group("COLON") != null ? "json-colon" :
                                                                    matcher.group("COMMA") != null ? "json-comma" :
                                                                            null;

            if (styleClass != null) {
                spansBuilder.add(Collections.emptyList(), matcher.start() - lastEnd);
                spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
                lastEnd = matcher.end();
            }
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastEnd);
        return spansBuilder.create();
    }
}