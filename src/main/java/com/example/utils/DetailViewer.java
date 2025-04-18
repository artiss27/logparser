package com.example.utils;

import com.example.model.LogEntry;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DetailViewer {

    private final VBox detailPane;
    private final CodeArea codeArea;
    private final HBox searchControls;
    private final Button prevButton;
    private final Button nextButton;
    private final Button clearButton;
    private final Label matchLabel;

    private List<Highlight> searchMatches = new ArrayList<>();
    private int currentMatchIndex = -1;
    private String lastSearchTerm = "";

    public DetailViewer() {
        codeArea = new CodeArea();
        codeArea.setEditable(false);
        codeArea.setWrapText(true);
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));

        prevButton = new Button("◀");
        nextButton = new Button("▶");
        clearButton = new Button("✖");
        matchLabel = new Label("0 / 0");

        prevButton.setOnAction(e -> goToPreviousMatch());
        nextButton.setOnAction(e -> goToNextMatch());
        clearButton.setOnAction(e -> clearSearch());

        searchControls = new HBox(5, prevButton, nextButton, matchLabel, clearButton);
        searchControls.setPadding(new Insets(5));

        detailPane = new VBox(codeArea, searchControls);
        detailPane.setPadding(new Insets(10));
        VBox.setVgrow(codeArea, Priority.ALWAYS);
    }

    public VBox getDetailPane() {
        return detailPane;
    }

    public void showLogDetails(LogEntry log, String searchTerm) {
        if (log == null) {
            codeArea.clear();
            codeArea.replaceText("Select a log entry to view details.");
            searchMatches.clear();
            updateMatchLabel();
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Date: ").append(log.getDate()).append("\n");
        sb.append("File: ").append(log.getFile()).append("\n");
        sb.append("Level: ").append(log.getLevel()).append("\n");
        sb.append("Message: ").append(log.getMessage()).append("\n\n");

        if (log.getContext() != null && !log.getContext().isBlank()) {
            sb.append("Context:\n").append(JsonFormatter.formatJson(log.getContext())).append("\n\n");
        }
        if (log.getExtra() != null && !log.getExtra().isBlank()) {
            sb.append("Extra:\n").append(JsonFormatter.formatJson(log.getExtra()));
        }

        codeArea.clear();
        codeArea.replaceText(sb.toString());

        lastSearchTerm = searchTerm;
        applyJsonAndSearchHighlighting(searchTerm);
    }

    private void applyJsonAndSearchHighlighting(String searchTerm) {
        String text = codeArea.getText();
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();

        List<Highlight> highlights = new ArrayList<>();
        searchMatches.clear();
        currentMatchIndex = -1;

        // JSON подсветка
        Matcher matcher = JsonHighlighter.getPattern().matcher(text);
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
                highlights.add(new Highlight(matcher.start(), matcher.end(), Collections.singleton(styleClass)));
            }
        }

        // Поиск подсветка
        if (searchTerm != null && !searchTerm.isBlank()) {
            String lowerText = text.toLowerCase();
            String lowerSearch = searchTerm.toLowerCase();
            int index = lowerText.indexOf(lowerSearch);
            while (index >= 0) {
                searchMatches.add(new Highlight(index, index + searchTerm.length(), Collections.singleton("search-highlight")));
                index = lowerText.indexOf(lowerSearch, index + searchTerm.length());
            }
        }

        // Объединяем стили
        highlights.addAll(searchMatches);
        highlights.sort(Comparator.comparingInt(h -> h.start));

        int lastIndex = 0;
        for (Highlight h : highlights) {
            if (h.start > lastIndex) {
                spansBuilder.add(Collections.emptyList(), h.start - lastIndex);
            }
            spansBuilder.add(h.styleClasses, h.end - h.start);
            lastIndex = h.end;
        }

        spansBuilder.add(Collections.emptyList(), text.length() - lastIndex);

        codeArea.setStyleSpans(0, spansBuilder.create());

        updateMatchLabel();
        if (!searchMatches.isEmpty()) {
            currentMatchIndex = 0;
            scrollToMatch();
        }
    }

    private void clearSearch() {
        lastSearchTerm = "";
        applyJsonAndSearchHighlighting(""); // убираем подсветку поиска, оставляем только JSON
    }

    private void goToNextMatch() {
        if (searchMatches.isEmpty()) return;
        currentMatchIndex = (currentMatchIndex + 1) % searchMatches.size();
        scrollToMatch();
    }

    private void goToPreviousMatch() {
        if (searchMatches.isEmpty()) return;
        currentMatchIndex = (currentMatchIndex - 1 + searchMatches.size()) % searchMatches.size();
        scrollToMatch();
    }

    private void scrollToMatch() {
        if (currentMatchIndex < 0 || currentMatchIndex >= searchMatches.size()) return;

        Highlight match = searchMatches.get(currentMatchIndex);
        codeArea.showParagraphAtCenter(codeArea.offsetToPosition(match.start, null).getMajor());
        updateMatchLabel();
    }

    private void updateMatchLabel() {
        matchLabel.setText((searchMatches.isEmpty() ? "0" : (currentMatchIndex + 1)) + " / " + searchMatches.size());
    }

    private static class Highlight {
        final int start;
        final int end;
        final Collection<String> styleClasses;

        Highlight(int start, int end, Collection<String> styleClasses) {
            this.start = start;
            this.end = end;
            this.styleClasses = styleClasses;
        }
    }
}