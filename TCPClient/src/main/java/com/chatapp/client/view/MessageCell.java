package com.chatapp.client.view;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

/** Draws a {@link ChatEntry} as a chat bubble, a system notice, or the active-users card. */
final class MessageCell extends ListCell<ChatEntry> {

    private static final double BUBBLE_WIDTH_RATIO = 0.68;

    private final ListView<ChatEntry> list;

    MessageCell(ListView<ChatEntry> list) {
        this.list = list;
        getStyleClass().add("message-cell");
        // Keep cells as wide as the list so text wraps instead of scrolling sideways.
        prefWidthProperty().bind(list.widthProperty().subtract(24));
        setMaxWidth(Region.USE_PREF_SIZE);
    }

    @Override
    protected void updateItem(ChatEntry entry, boolean empty) {
        super.updateItem(entry, empty);
        setText(null);
        if (empty || entry == null) {
            setGraphic(null);
            return;
        }
        setGraphic(switch (entry.kind()) {
            case OWN -> bubble(entry, "bubble-own", Pos.CENTER_RIGHT);
            case OTHER -> bubble(entry, "bubble-other", Pos.CENTER_LEFT);
            case SYSTEM -> notice(entry, "notice-system");
            case ERROR -> notice(entry, "notice-error");
            case USERS -> usersCard(entry);
        });
    }

    private GridPane bubble(ChatEntry entry, String styleClass, Pos alignment) {
        Label sender = new Label(entry.sender());
        sender.getStyleClass().add("bubble-sender");
        if (entry.kind() == ChatEntry.Kind.OTHER) {
            sender.setTextFill(colorFor(entry.sender()));
        }
        Label time = new Label(entry.time());
        time.getStyleClass().add("bubble-time");

        Label text = new Label(entry.text());
        text.getStyleClass().add("bubble-text");
        text.setWrapText(true);
        text.maxWidthProperty().bind(list.widthProperty().multiply(BUBBLE_WIDTH_RATIO));

        GridPane bubble = new GridPane();
        bubble.getStyleClass().addAll("bubble", styleClass);
        bubble.setHgap(10);
        bubble.setVgap(3);
        bubble.add(sender, 0, 0);
        bubble.add(time, 1, 0);
        bubble.add(text, 0, 1, 2, 1);
        bubble.setMaxWidth(Region.USE_PREF_SIZE);

        return row(bubble, alignment);
    }

    private GridPane notice(ChatEntry entry, String styleClass) {
        Label label = new Label(entry.time() + "  ·  " + entry.text());
        label.getStyleClass().add("notice-text");
        label.setWrapText(true);
        label.maxWidthProperty().bind(list.widthProperty().multiply(0.8));

        GridPane pill = new GridPane();
        pill.getStyleClass().addAll("notice", styleClass);
        pill.add(label, 0, 0);
        pill.setMaxWidth(Region.USE_PREF_SIZE);
        return row(pill, Pos.CENTER);
    }

    private GridPane usersCard(ChatEntry entry) {
        Label title = new Label("Active users (" + entry.users().size() + ")");
        title.getStyleClass().add("users-title");

        GridPane card = new GridPane();
        card.getStyleClass().add("users-card");
        card.setVgap(4);
        card.add(title, 0, 0);
        for (int i = 0; i < entry.users().size(); i++) {
            String user = entry.users().get(i);
            Label name = new Label("●  " + user);
            name.setTextFill(colorFor(user));
            name.getStyleClass().add("users-name");
            card.add(name, 0, i + 1);
        }
        card.setMaxWidth(Region.USE_PREF_SIZE);
        return row(card, Pos.CENTER);
    }

    /** Wraps a node in a full-width row so it can be pushed left, right or centered. */
    private static GridPane row(Region content, Pos alignment) {
        GridPane row = new GridPane();
        ColumnConstraints column = new ColumnConstraints();
        column.setHgrow(Priority.ALWAYS);
        column.setHalignment(alignment.getHpos());
        row.getColumnConstraints().add(column);
        row.add(content, 0, 0);
        return row;
    }

    /** The same user always gets the same, readable color. */
    static Color colorFor(String username) {
        double hue = Math.floorMod(username.hashCode(), 360);
        return Color.hsb(hue, 0.65, 0.62);
    }
}
