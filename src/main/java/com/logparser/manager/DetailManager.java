package com.logparser.manager;

import com.logparser.model.LogEntry;
import com.logparser.utils.JsonHighlighter;
import javafx.geometry.Insets;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;

import java.util.Collections;

/**
 * Управляет правой нижней панелью: отображение деталей выбранного лога с подсветкой JSON и поиска.
 */
public class DetailManager {

    private final MainLayoutManager layoutManager;
    private final VBox detailPane;
    private final CodeArea codeArea;

    public DetailManager(MainLayoutManager layoutManager) {
        this.layoutManager = layoutManager;

        codeArea = new CodeArea();
        codeArea.setEditable(false);
        codeArea.setWrapText(true);
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));

        detailPane = new VBox(codeArea);
        detailPane.setPadding(new Insets(10));
        VBox.setVgrow(codeArea, Priority.ALWAYS);
    }

    public VBox getDetailPane() {
        return detailPane;
    }

    public void showLogDetails(LogEntry log, String search) {
        if (log == null) {
            codeArea.clear();
            codeArea.replaceText("Select a log entry to view details.");
            return;
        }

        if (!log.isValid()) {
            // если лог невалидный — показываем оригинальную строку
            codeArea.clear();
            codeArea.replaceText("Unparsed log entry:\n\n" + log.getRawLine());
            return;
        }

        StringBuilder content = new StringBuilder();
        content.append("Date: ").append(log.getDate()).append("\n");
        content.append("File: ").append(log.getFile()).append("\n");
        content.append("Level: ").append(log.getLevel()).append("\n");
        content.append("Message: ").append(log.getMessage()).append("\n\n");

        if (log.getContext() != null && !log.getContext().isBlank()) {
            content.append("Context:\n").append(formatJson(log.getContext())).append("\n\n");
        }
        if (log.getExtra() != null && !log.getExtra().isBlank()) {
            content.append("Extra:\n").append(formatJson(log.getExtra()));
        }

        codeArea.clear();
        codeArea.replaceText(content.toString());

        applyJsonHighlighting();
        if (search != null && !search.isBlank()) {
            highlightSearchTerm(search);
        }
    }

    private void applyJsonHighlighting() {
        String text = codeArea.getText();
        codeArea.setStyleSpans(0, JsonHighlighter.computeHighlighting(text));
    }

    private void highlightSearchTerm(String searchTerm) {
        String text = codeArea.getText().toLowerCase();
        String lowerSearch = searchTerm.toLowerCase();
        int firstIndex = text.indexOf(lowerSearch);

        if (firstIndex == -1) {
            return;
        }
        // Сброс стилей перед новой подсветкой
        codeArea.setStyle(0, text.length(), Collections.emptyList());

        // Подсвечиваем все вхождения
        int index = 0;
        while ((index = text.indexOf(lowerSearch, index)) >= 0) {
            int end = index + lowerSearch.length();
            codeArea.setStyle(index, end, Collections.singleton("search-highlight"));
            index = end;
        }

        // Прокрутка к первому совпадению
        codeArea.moveTo(firstIndex);
        codeArea.requestFollowCaret();
    }

    private String formatJson(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return "";
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Object json = mapper.readValue(rawJson, Object.class);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
        } catch (Exception e) {
            return rawJson;
        }
    }
}