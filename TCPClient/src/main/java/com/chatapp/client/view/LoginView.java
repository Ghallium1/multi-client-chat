package com.chatapp.client.view;

import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.util.function.Consumer;

/**
 * The first screen: the user picks a username, or leaves it empty to join in READ-ONLY MODE.
 * Pure presentation, with no reference to the model.
 */
public final class LoginView {

    private final GridPane root = new GridPane();
    private final Label serverLabel = new Label();
    private final TextField usernameField = new TextField();
    private final Button joinButton = new Button();
    private final Label errorLabel = new Label();

    private Consumer<String> onJoin = name -> { };
    private boolean busy;

    public LoginView() {
        buildLayout();
        usernameField.textProperty().addListener((obs, old, text) -> {
            updateJoinButton();
            clearError();
        });
        usernameField.setOnAction(event -> join());
        joinButton.setOnAction(event -> join());
        updateJoinButton();
    }

    public Parent getRoot() {
        return root;
    }

    public void setServerAddress(String address) {
        serverLabel.setText("Server  " + address);
    }

    public void setOnJoin(Consumer<String> action) {
        this.onJoin = action;
    }

    public void setBusy(boolean busy) {
        this.busy = busy;
        usernameField.setDisable(busy);
        joinButton.setDisable(busy);
        updateJoinButton();
    }

    public void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    public void focus() {
        usernameField.requestFocus();
    }

    private void clearError() {
        errorLabel.setText("");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    private void join() {
        if (!busy) {
            clearError();
            onJoin.accept(usernameField.getText().strip());
        }
    }

    private void updateJoinButton() {
        if (busy) {
            joinButton.setText("Connecting…");
        } else {
            joinButton.setText(usernameField.getText().isBlank() ? "Join in read-only mode" : "Join chat");
        }
    }

    private void buildLayout() {
        root.getStyleClass().add("login-root");
        ColumnConstraints center = new ColumnConstraints();
        center.setHgrow(Priority.ALWAYS);
        center.setHalignment(HPos.CENTER);
        root.getColumnConstraints().add(center);

        Label logo = new Label("GC");
        logo.getStyleClass().add("login-logo");
        Label title = new Label("Group Chat");
        title.getStyleClass().add("login-title");
        serverLabel.getStyleClass().add("login-server");

        Label usernameLabel = new Label("Username");
        usernameLabel.getStyleClass().add("field-label");
        usernameField.setPromptText("e.g. alice");
        usernameField.getStyleClass().add("login-field");

        joinButton.getStyleClass().add("primary-button");
        joinButton.setDefaultButton(true);
        joinButton.setMaxWidth(Double.MAX_VALUE);

        Label hint = new Label("Leave the username empty to join in READ-ONLY MODE (you can read, but not send).");
        hint.getStyleClass().add("login-hint");
        hint.setWrapText(true);

        errorLabel.getStyleClass().add("login-error");
        errorLabel.setWrapText(true);
        clearError();

        GridPane card = new GridPane();
        card.getStyleClass().add("login-card");
        card.setVgap(10);
        ColumnConstraints fill = new ColumnConstraints();
        fill.setHgrow(Priority.ALWAYS);
        card.getColumnConstraints().add(fill);
        card.add(logo, 0, 0);
        card.add(title, 0, 1);
        card.add(serverLabel, 0, 2);
        card.add(usernameLabel, 0, 3);
        card.add(usernameField, 0, 4);
        card.add(joinButton, 0, 5);
        card.add(errorLabel, 0, 6);
        card.add(hint, 0, 7);
        for (var node : new Region[] {logo, title, serverLabel}) {
            GridPane.setHalignment(node, HPos.CENTER);
        }
        GridPane.setMargin(usernameLabel, new Insets(12, 0, 0, 0));
        card.setMaxWidth(360);

        root.add(card, 0, 0);
    }
}
