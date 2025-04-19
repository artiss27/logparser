package com.example.manager;

import com.example.model.LogEntry;
import com.example.parser.LogParser;
import com.example.utils.PagedLogLoader;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import java.io.File;
import java.util.*;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.util.Duration;

public class LogManager {

    private final MainLayoutManager layoutManager;
    private final VBox logPane;
    private final TableView<LogEntry> tableView;
    private final ComboBox<String> levelFilter;
    private final TextField searchField;
    private final DatePicker dateFromPicker;
    private final DatePicker dateToPicker;
    private final Button clearFiltersButton;
    private final ObservableList<LogEntry> masterData = FXCollections.observableArrayList();
    private final Map<String, LogParser> parsers = new HashMap<>();
    private LogParser activeParser;
    private FilteredList<LogEntry> filteredData;
    private final ProgressBar progressBar = new ProgressBar();
    private final Button loadMoreButton = new Button("Load more");
    private PagedLogLoader pagedLoader;
    private final Map<String, Boolean> groupColorMap = new HashMap<>();

    public LogManager(MainLayoutManager layoutManager) {
        this.layoutManager = layoutManager;

        logPane = new VBox(10);
        logPane.setPadding(new Insets(10));

        HBox filters = new HBox(10);

        levelFilter = new ComboBox<>(FXCollections.observableArrayList("All", "ERROR", "WARNING", "INFO", "NOTICE", "DEBUG"));
        levelFilter.setValue("All");
        levelFilter.setPrefWidth(120);

        searchField = new TextField();
        searchField.setPromptText("Search...");
        searchField.setPrefWidth(200);

        dateFromPicker = new DatePicker();
        dateFromPicker.setPromptText("From");

        dateToPicker = new DatePicker();
        dateToPicker.setPromptText("To");

        clearFiltersButton = new Button("Clear");
        clearFiltersButton.setOnAction(e -> clearFilters());

        filters.getChildren().addAll(
                new Label("Level:"), levelFilter,
                new Label("Search:"), searchField,
                new Label("Date:"), dateFromPicker,
                new Label("to"), dateToPicker,
                clearFiltersButton
        );

        tableView = createTableView();

        logPane.getChildren().addAll(filters, tableView);
    }

    public VBox getLogViewPane() {
        return logPane;
    }

    public void registerParser(String name, LogParser parser) {
        parsers.put(name, parser);
    }

    public void setActiveParser(String name) {
        this.activeParser = parsers.get(name);
    }

    private void clearFilters() {
        searchField.clear();
        levelFilter.setValue("All");
        dateFromPicker.setValue(null);
        dateToPicker.setValue(null);
        updateFilters();
    }

    private TableView<LogEntry> createTableView() {
        TableView<LogEntry> table = new TableView<>();
        table.setPlaceholder(new Label("No logs to display."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        TableColumn<LogEntry, String> dateColumn = new TableColumn<>("Date/Time");
        dateColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getDate()));
        dateColumn.setPrefWidth(200);
        dateColumn.setCellFactory(column -> new TableCell<>() {
            private final Button loadMoreRowButton = new Button("Load more");

            {
                loadMoreRowButton.setOnAction(e -> loadNextPage());
                loadMoreRowButton.setMaxWidth(Double.MAX_VALUE);
                loadMoreRowButton.setStyle("-fx-background-color: #e0e0e0; -fx-border-radius: 6px; -fx-background-radius: 6px;");
            }

            @Override
            protected void updateItem(String date, boolean empty) {
                super.updateItem(date, empty);
                if (empty) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                LogEntry entry = getTableView().getItems().get(getIndex());
                if ("LM".equals(entry.getLevel())) {
                    setText(null);
                    setGraphic(loadMoreRowButton);
                    setStyle("-fx-alignment: CENTER;");
                } else {
                    setGraphic(null);
                    setText(date);
                    setStyle("-fx-alignment: CENTER-LEFT;");
                }
            }
        });

        TableColumn<LogEntry, String> fileColumn = new TableColumn<>("File");
        fileColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getFile()));
        fileColumn.setPrefWidth(Region.USE_COMPUTED_SIZE);

        TableColumn<LogEntry, String> levelColumn = new TableColumn<>("Level");
        levelColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getLevel()));
        levelColumn.setPrefWidth(100);
        levelColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String level, boolean empty) {
                super.updateItem(level, empty);
                if (empty || level == null) {
                    setText(null);
                    setStyle("");
                    return;
                }

                LogEntry logEntry = getTableView().getItems().get(getIndex());
                setText(level);
                setGraphic(null);

                if (logEntry == null || !logEntry.isValid()) {
                    setStyle("-fx-text-fill: gray; -fx-font-style: italic;");
                } else if ("ERROR".equalsIgnoreCase(level) || "ALERT".equalsIgnoreCase(level)) {
                    setStyle("-fx-text-fill: red;");
                } else if ("WARNING".equalsIgnoreCase(level)) {
                    setStyle("-fx-text-fill: orange;");
                } else if ("INFO".equalsIgnoreCase(level)) {
                    setStyle("-fx-text-fill: #1e88e5;");
                } else if ("NOTICE".equalsIgnoreCase(level)) {
                    setStyle("-fx-text-fill: #dd00ff;");
                } else {
                    setStyle("-fx-text-fill: black;");
                }
            }
        });

        TableColumn<LogEntry, String> messageColumn = new TableColumn<>("Message");
        messageColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getMessage()));
        messageColumn.setPrefWidth(Region.USE_COMPUTED_SIZE);
        messageColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String message, boolean empty) {
                super.updateItem(message, empty);
                if (empty || message == null) {
                    setText(null);
                } else {
                    setText(message.length() > 100 ? message.substring(0, 100) + "..." : message);
                }
            }
        });

        table.getColumns().addAll(dateColumn, fileColumn, levelColumn, messageColumn);

        filteredData = new FilteredList<>(masterData, p -> true);
        SortedList<LogEntry> sortedData = new SortedList<>(filteredData);
        sortedData.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sortedData);

        searchField.textProperty().addListener((obs, oldVal, newVal) -> updateFilters());
        levelFilter.valueProperty().addListener((obs, oldVal, newVal) -> updateFilters());
        dateFromPicker.valueProperty().addListener((obs, oldVal, newVal) -> updateFilters());
        dateToPicker.valueProperty().addListener((obs, oldVal, newVal) -> updateFilters());

        table.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                layoutManager.getDetailManager().showLogDetails(newSelection, searchField.getText().trim());
            }
        });

        dateColumn.setSortType(TableColumn.SortType.DESCENDING);
        table.getSortOrder().add(dateColumn);

        // 🔧 Подсветка новых строк в таблице
        table.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(LogEntry item, boolean empty) {
                super.updateItem(item, empty);

                styleProperty().unbind();

                if (empty || item == null) {
                    setStyle("");
                } else {
                    styleProperty().bind(Bindings
                            .when(item.highlightedProperty())
                            .then("-fx-background-color: rgba(255,255,0,0.4);")
                            .otherwise(""));
                }
            }
        });

        return table;
    }

    public void loadLogsFromFile(String fileName) {
        clearLogs();
        File file = new File(fileName);
        if (!file.exists() || file.isDirectory()) return;

        pagedLoader = new PagedLogLoader(file, activeParser);
        pagedLoader.reset();

        progressBar.setVisible(true);
        loadMoreButton.setVisible(false);

        pagedLoader.loadNextPageAsync(entries -> {
            if (entries != null && !entries.isEmpty()) {
                masterData.addAll(entries);
                appendLoadMoreMarker();
                autoResizeColumns();
            }
            progressBar.setVisible(false);
            loadMoreButton.setVisible(pagedLoader.hasMore());
        }, error -> {
            error.printStackTrace();
            progressBar.setVisible(false);
        });
    }

    private boolean isLoading = false;

    private void loadNextPage() {
        isLoading = true;
        progressBar.setVisible(true);
        loadMoreButton.setVisible(false);

        pagedLoader.loadNextPageAsync(entries -> {
            System.out.println("✅ Loaded entries: " + entries.size());
            if (entries != null && !entries.isEmpty()) {
                masterData.addAll(entries);
                appendLoadMoreMarker();
                autoResizeColumns();
            }

            progressBar.setVisible(false);
            isLoading = false;
            loadMoreButton.setVisible(pagedLoader.hasMore());

        }, error -> {
            error.printStackTrace();
            isLoading = false;
            progressBar.setVisible(false);
            loadMoreButton.setVisible(false);
        });
    }

    private void updateFilters() {
        String search = searchField.getText().toLowerCase();
        String selectedLevel = levelFilter.getValue();
        var dateFrom = dateFromPicker.getValue();
        var dateTo = dateToPicker.getValue();

        updateFilterStyle(searchField, !search.isEmpty());
        updateFilterStyle(levelFilter, !selectedLevel.equals("All"));
        updateFilterStyle(dateFromPicker, dateFrom != null);
        updateFilterStyle(dateToPicker, dateTo != null);

        LogEntry currentSelection = tableView.getSelectionModel().getSelectedItem();

        filteredData.setPredicate(log -> {
            boolean matchesSearch = search.isEmpty()
                    || log.getMessage().toLowerCase().contains(search)
                    || log.getFile().toLowerCase().contains(search)
                    || log.getLevel().toLowerCase().contains(search)
                    || log.getContext().toLowerCase().contains(search)
                    || log.getExtra().toLowerCase().contains(search);

            boolean matchesLevel = selectedLevel.equals("All")
                    || log.getLevel().toUpperCase().contains(selectedLevel.toUpperCase());

            boolean matchesDate = true;
            try {
                if (log.getDate() != null && !log.getDate().isBlank()) {
                    String logDateString = log.getDate().split(" ")[0];

                    LocalDate logDate;
                    if (logDateString.contains(".")) {
                        logDate = LocalDate.parse(logDateString, DateTimeFormatter.ofPattern("dd.MM.yyyy"));
                    } else if (logDateString.contains("-")) {
                        logDate = LocalDate.parse(logDateString, DateTimeFormatter.ISO_LOCAL_DATE);
                    } else {
                        logDate = null;
                    }

                    if (logDate != null) {
                        if (dateFrom != null && logDate.isBefore(dateFrom)) matchesDate = false;
                        if (dateTo != null && logDate.isAfter(dateTo)) matchesDate = false;
                    }
                }
            } catch (Exception ignored) {}

            return matchesSearch && matchesLevel && matchesDate;
        });

        if (currentSelection != null && filteredData.contains(currentSelection)) {
            tableView.getSelectionModel().select(currentSelection);
        } else {
            tableView.getSelectionModel().selectFirst();
        }

        layoutManager.getDetailManager().showLogDetails(
                tableView.getSelectionModel().getSelectedItem(),
                searchField.getText().trim()
        );
    }

    private void updateFilterStyle(Control control, boolean active) {
        if (active) {
            if (!control.getStyleClass().contains("filter-active")) {
                control.getStyleClass().add("filter-active");
            }
        } else {
            control.getStyleClass().remove("filter-active");
        }
    }

    private void autoResizeColumns() {
        tableView.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        int sampleSize = Math.min(20, tableView.getItems().size());

        tableView.getColumns().forEach(column -> {
            Text headerText = new Text(column.getText());
            double max = headerText.getLayoutBounds().getWidth() + 20;

            for (int i = 0; i < sampleSize; i++) {
                Object cellData = column.getCellData(i);
                if (cellData != null) {
                    String textStr = cellData.toString();
                    if (column.getText().equalsIgnoreCase("Message")) {
                        textStr = textStr.length() > 100 ? textStr.substring(0, 100) + "..." : textStr;
                    }
                    Text t = new Text(textStr);
                    double width = t.getLayoutBounds().getWidth() + 20;
                    if (width > max) {
                        max = width;
                    }
                }
            }
            column.setPrefWidth(max);
        });
    }

    public void clearLogs() {
        masterData.clear();
        layoutManager.getDetailManager().showLogDetails(null, null);
    }

    private void appendLoadMoreMarker() {
        // Удаляем предыдущие маркеры, если есть
        masterData.removeIf(entry -> "LM".equals(entry.getLevel()) || "-".equals(entry.getLevel()));

        if (pagedLoader != null && pagedLoader.hasMore()) {
            LogEntry marker = new LogEntry("", "", "LM", "", "", "", true, "");
            LogEntry spacer = new LogEntry("", "", "-", "", "", "", true, "");

            masterData.add(spacer);
            masterData.add(marker);
        }
    }

    public void prependLogEntries(List<LogEntry> entries) {
        for (LogEntry entry : entries) {
            String groupKey = extractGroupKey(entry.getDate());
            entry.setGroupKey(groupKey);
            masterData.add(0, entry);
            highlightEntry(entry);
        }
        rebuildGroupColorMap();
        autoResizeColumns();
    }

    private String extractGroupKey(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return "";
        return dateStr.split("\\.")[0].trim(); // "2024-04-19 12:45:22.124" → "2024-04-19 12:45:22"
    }

    private void rebuildGroupColorMap() {
        groupColorMap.clear();
        List<String> orderedKeys = new ArrayList<>();

        for (LogEntry entry : masterData) {
            String key = entry.getGroupKey();
            if (!groupColorMap.containsKey(key)) {
                boolean useAlt = (orderedKeys.size() % 2 != 0);
                groupColorMap.put(key, useAlt);
                orderedKeys.add(key);
            }
        }
    }

    private void highlightEntry(LogEntry entry) {
        entry.setHighlighted(true);
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.seconds(15), e -> entry.setHighlighted(false))
        );
        timeline.play();
    }

    public LogParser getActiveParser() {
        return activeParser;
    }
}
