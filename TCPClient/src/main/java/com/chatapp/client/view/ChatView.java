package com.chatapp.client.view;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.shape.Circle;

import java.util.function.Consumer;

/**
 * The chat screen: header with the "Online" status label and indicator circle, the conversation,
 * and the input area with the SEND and LEAVE buttons.
 *
 * <p>Pure presentation, with no reference to the model. Enter sends the message; Shift+Enter
 * inserts a new line.
 */
public final class ChatView {

    private final GridPane root = new GridPane();
    private final Label userLabel = new Label();
    private final Circle statusDot = new Circle(6);
    private final Label statusLabel = new Label();
    private final Label readOnlyBanner = new Label("READ-ONLY MODE  —  you can read, but not send messages");
    private final ObservableList<ChatEntry> entries = FXCollections.observableArrayList();
    private final ListView<ChatEntry> messageList = new ListView<>(entries);
    private final TextArea input = new TextArea();
    private final Button sendButton = new Button("SEND");
    private final Button leaveButton = new Button("LEAVE");

    private Consumer<String> onSend = text -> { };
    private Runnable onLeave = () -> { };

    public ChatView() {
        buildLayout();
        setReadOnly(false);
        setOnline(false);
    }

    public Parent getRoot() {
        return root;
    }

    // ----------------------------------------------------------------- updates from the controller

    public void setUser(String username, String serverAddress) {
        userLabel.setText("Signed in as " + username + "  ·  " + serverAddress);
    }

    public void setReadOnly(boolean readOnly) {
        readOnlyBanner.setVisible(readOnly);
        readOnlyBanner.setManaged(readOnly);
        input.setPromptText(readOnly
                ? "Read-only: type allUsers to see who is online, or bye to leave"
                : "Type a message…   (Enter to send, Shift+Enter for a new line)");
    }

    public void setOnline(boolean online) {
        statusLabel.setText(online ? "Online" : "Offline");
        statusDot.getStyleClass().setAll("status-dot", online ? "online" : "offline");
        statusLabel.getStyleClass().setAll("label", "status-label", online ? "online" : "offline");
        input.setDisable(!online);
        sendButton.setDisable(!online);
        leaveButton.setText(online ? "LEAVE" : "EXIT");
    }

    public void addEntry(ChatEntry entry) {
        entries.add(entry);
        messageList.scrollTo(entries.size() - 1);
    }

    public void clearInput() {
        input.clear();
    }

    public void focusInput() {
        input.requestFocus();
    }

    public void setOnSend(Consumer<String> action) {
        this.onSend = action;
    }

    public void setOnLeave(Runnable action) {
        this.onLeave = action;
    }

    // ------------------------------------------------------------------------------------- layout

    private void buildLayout() {
        root.getStyleClass().add("chat-root");
        ColumnConstraints fill = new ColumnConstraints();
        fill.setHgrow(Priority.ALWAYS);
        root.getColumnConstraints().add(fill);
        RowConstraints header = new RowConstraints();
        RowConstraints banner = new RowConstraints();
        RowConstraints messages = new RowConstraints();
        messages.setVgrow(Priority.ALWAYS);
        RowConstraints inputRow = new RowConstraints();
        RowConstraints hintRow = new RowConstraints();
        root.getRowConstraints().addAll(header, banner, messages, inputRow, hintRow);

        root.add(buildHeader(), 0, 0);

        readOnlyBanner.getStyleClass().add("read-only-banner");
        readOnlyBanner.setWrapText(true);
        readOnlyBanner.setMaxWidth(Double.MAX_VALUE);
        root.add(readOnlyBanner, 0, 1);

        messageList.getStyleClass().add("message-list");
        messageList.setFocusTraversable(false);
        messageList.setPlaceholder(new Label("No messages yet — say hello!"));
        messageList.setCellFactory(MessageCell::new);
        root.add(messageList, 0, 2);

        root.add(buildInputBar(), 0, 3);

        Label hint = new Label("Commands:  allUsers — list who is online   ·   end / bye — leave the chat");
        hint.getStyleClass().add("input-hint");
        GridPane.setMargin(hint, new Insets(0, 18, 12, 18));
        root.add(hint, 0, 4);
    }

    private GridPane buildHeader() {
        Label title = new Label("Group Chat");
        title.getStyleClass().add("chat-title");
        userLabel.getStyleClass().add("chat-subtitle");

        GridPane status = new GridPane();
        status.getStyleClass().add("status-pill");
        status.setHgap(8);
        status.add(statusDot, 0, 0);
        status.add(statusLabel, 1, 0);
        GridPane.setValignment(statusDot, VPos.CENTER);

        GridPane headerPane = new GridPane();
        headerPane.getStyleClass().add("chat-header");
        ColumnConstraints grow = new ColumnConstraints();
        grow.setHgrow(Priority.ALWAYS);
        headerPane.getColumnConstraints().addAll(grow, new ColumnConstraints());
        headerPane.add(title, 0, 0);
        headerPane.add(userLabel, 0, 1);
        headerPane.add(status, 1, 0, 1, 2);
        GridPane.setValignment(status, VPos.CENTER);
        GridPane.setHalignment(status, HPos.RIGHT);
        return headerPane;
    }

    private GridPane buildInputBar() {
        input.getStyleClass().add("message-input");
        input.setWrapText(true);
        input.setPrefRowCount(2);
        input.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() != KeyCode.ENTER) {
                return;
            }
            event.consume();
            if (event.isShiftDown()) {
                input.insertText(input.getCaretPosition(), "\n");
            } else {
                submit();
            }
        });

        sendButton.getStyleClass().add("primary-button");
        sendButton.setOnAction(event -> submit());
        sendButton.setMaxHeight(Double.MAX_VALUE);
        sendButton.setMinWidth(Region.USE_PREF_SIZE);
        leaveButton.getStyleClass().add("leave-button");
        leaveButton.setOnAction(event -> onLeave.run());
        leaveButton.setMaxHeight(Double.MAX_VALUE);
        leaveButton.setMinWidth(Region.USE_PREF_SIZE);

        GridPane bar = new GridPane();
        bar.getStyleClass().add("input-bar");
        bar.setHgap(10);
        ColumnConstraints grow = new ColumnConstraints();
        grow.setHgrow(Priority.ALWAYS);
        bar.getColumnConstraints().addAll(grow, new ColumnConstraints(), new ColumnConstraints());
        bar.add(input, 0, 0);
        bar.add(sendButton, 1, 0);
        bar.add(leaveButton, 2, 0);
        GridPane.setMargin(bar, new Insets(6, 14, 8, 14));
        return bar;
    }

    private void submit() {
        String text = input.getText();
        if (!text.isBlank()) {
            onSend.accept(text);
        }
        input.requestFocus();
    }
}
