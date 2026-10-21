package com.chatapp.server.view;

import com.chatapp.server.model.LogType;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.RowConstraints;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The server window: a header with the server status, a live list of connected users (each with
 * its own random background color) and an activity log.
 *
 * <p>Pure presentation. It exposes methods to update what is shown and one hook for the
 * start/stop button. It has no reference to the model. All methods must be called on the
 * JavaFX application thread.
 */
public final class ServerView {

    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final GridPane root = new GridPane();
    private final Label endpointLabel = new Label();
    private final Circle statusDot = new Circle(6);
    private final Label statusLabel = new Label("Stopped");
    private final Button toggleButton = new Button("Start server");
    private final Label usersTitle = new Label();

    private final ObservableList<String> users = FXCollections.observableArrayList();
    private final ObservableList<LogEntry> logEntries = FXCollections.observableArrayList();
    private final ListView<String> userList = new ListView<>(users);
    private final ListView<LogEntry> logList = new ListView<>(logEntries);

    private final Map<String, Color> userColors = new HashMap<>();
    private final Set<String> readOnlyUsers = new HashSet<>();
    private final Random random = new Random();

    private Runnable onToggleServer = () -> { };

    public ServerView() {
        buildLayout();
        updateUserCount();
        showRunning(false);
    }

    public Parent getRoot() {
        return root;
    }

    // ----------------------------------------------------------------- updates from the controller

    public void setEndpoint(String endpoint) {
        endpointLabel.setText(endpoint);
    }

    public void showRunning(boolean running) {
        statusLabel.setText(running ? "Running" : "Stopped");
        statusDot.getStyleClass().setAll("status-dot", running ? "online" : "offline");
        toggleButton.setText(running ? "Stop server" : "Start server");
        toggleButton.getStyleClass().setAll("button", running ? "danger-button" : "primary-button");
    }

    public void appendLog(LogType type, String message) {
        logEntries.add(new LogEntry(LocalTime.now().format(LOG_TIME), type, message));
        logList.scrollTo(logEntries.size() - 1);
    }

    public void addUser(String username, boolean readOnly) {
        if (readOnly) {
            readOnlyUsers.add(username);
        }
        userColors.computeIfAbsent(username, name -> randomPastel());
        users.add(username);
        updateUserCount();
    }

    public void removeUser(String username) {
        users.remove(username);
        readOnlyUsers.remove(username);
        userColors.remove(username);
        updateUserCount();
    }

    public void clearUsers() {
        users.clear();
        readOnlyUsers.clear();
        userColors.clear();
        updateUserCount();
    }

    public void setOnToggleServer(Runnable action) {
        this.onToggleServer = action;
    }

    // ------------------------------------------------------------------------------------- layout

    private void buildLayout() {
        root.getStyleClass().add("server-root");
        root.setHgap(16);
        root.setVgap(16);
        root.setPadding(new Insets(0, 0, 20, 0));

        ColumnConstraints usersColumn = new ColumnConstraints(220, 260, 340);
        ColumnConstraints logColumn = new ColumnConstraints();
        logColumn.setHgrow(Priority.ALWAYS);
        root.getColumnConstraints().addAll(usersColumn, logColumn);

        RowConstraints headerRow = new RowConstraints();
        RowConstraints contentRow = new RowConstraints();
        contentRow.setVgrow(Priority.ALWAYS);
        root.getRowConstraints().addAll(headerRow, contentRow);

        root.add(buildHeader(), 0, 0, 2, 1);
        root.add(buildUsersPanel(), 0, 1);
        root.add(buildLogPanel(), 1, 1);
    }

    private GridPane buildHeader() {
        Label title = new Label("Chat Server");
        title.getStyleClass().add("title");
        endpointLabel.getStyleClass().add("subtitle");

        statusLabel.getStyleClass().add("status-label");
        GridPane status = new GridPane();
        status.getStyleClass().add("status-pill");
        status.setHgap(8);
        status.add(statusDot, 0, 0);
        status.add(statusLabel, 1, 0);

        toggleButton.setOnAction(event -> onToggleServer.run());

        GridPane header = new GridPane();
        header.getStyleClass().add("header");
        header.setHgap(16);
        ColumnConstraints grow = new ColumnConstraints();
        grow.setHgrow(Priority.ALWAYS);
        header.getColumnConstraints().addAll(grow, new ColumnConstraints(), new ColumnConstraints());
        header.add(title, 0, 0);
        header.add(endpointLabel, 0, 1);
        header.add(status, 1, 0, 1, 2);
        header.add(toggleButton, 2, 0, 1, 2);
        GridPane.setValignment(status, VPos.CENTER);
        GridPane.setValignment(toggleButton, VPos.CENTER);
        return header;
    }

    private GridPane buildUsersPanel() {
        usersTitle.getStyleClass().add("panel-title");
        userList.getStyleClass().add("user-list");
        userList.setPlaceholder(new Label("No one is connected yet"));
        userList.setCellFactory(list -> new UserCell());
        return panel(usersTitle, userList, new Insets(0, 0, 0, 20));
    }

    private GridPane buildLogPanel() {
        Label logTitle = new Label("Activity log");
        logTitle.getStyleClass().add("panel-title");
        logList.getStyleClass().add("log-list");
        logList.setCellFactory(list -> new LogCell());
        return panel(logTitle, logList, new Insets(0, 20, 0, 0));
    }

    private static GridPane panel(Label title, ListView<?> list, Insets margin) {
        GridPane panel = new GridPane();
        panel.getStyleClass().add("panel");
        panel.setVgap(10);
        ColumnConstraints fill = new ColumnConstraints();
        fill.setHgrow(Priority.ALWAYS);
        fill.setFillWidth(true);
        panel.getColumnConstraints().add(fill);
        RowConstraints titleRow = new RowConstraints();
        RowConstraints listRow = new RowConstraints();
        listRow.setVgrow(Priority.ALWAYS);
        panel.getRowConstraints().addAll(titleRow, listRow);
        panel.add(title, 0, 0);
        panel.add(list, 0, 1);
        GridPane.setHalignment(title, HPos.LEFT);
        GridPane.setMargin(panel, margin);
        return panel;
    }

    private void updateUserCount() {
        usersTitle.setText("Connected users (" + users.size() + ")");
    }

    /** A light, random hue: different for each user, always readable with dark text. */
    private Color randomPastel() {
        return Color.hsb(random.nextDouble() * 360, 0.30 + random.nextDouble() * 0.20, 0.97);
    }

    private static String toCss(Color color) {
        return String.format(Locale.ROOT, "#%02x%02x%02x",
                Math.round(color.getRed() * 255),
                Math.round(color.getGreen() * 255),
                Math.round(color.getBlue() * 255));
    }

    // ---------------------------------------------------------------------------------- list cells

    private final class UserCell extends ListCell<String> {

        UserCell() {
            getStyleClass().add("user-cell");
        }

        @Override
        protected void updateItem(String username, boolean empty) {
            super.updateItem(username, empty);
            if (empty || username == null) {
                setText(null);
                setGraphic(null);
                setStyle(null);
                return;
            }
            Color color = userColors.computeIfAbsent(username, name -> randomPastel());
            Circle avatar = new Circle(5, color.deriveColor(0, 2.2, 0.55, 1));
            setGraphic(avatar);
            setText(readOnlyUsers.contains(username) ? username + "   · read-only" : username);
            setStyle("-fx-background-color: " + toCss(color) + ";");
        }
    }

    private static final class LogCell extends ListCell<LogEntry> {

        @Override
        protected void updateItem(LogEntry entry, boolean empty) {
            super.updateItem(entry, empty);
            getStyleClass().removeIf(style -> style.startsWith("log-"));
            if (empty || entry == null) {
                setText(null);
                return;
            }
            setText(entry.time() + "   " + entry.message());
            getStyleClass().add("log-" + entry.type().name().toLowerCase(Locale.ROOT));
        }
    }

    private record LogEntry(String time, LogType type, String message) {
    }
}
