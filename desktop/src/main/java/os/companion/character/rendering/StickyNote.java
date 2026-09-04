package os.companion.character.rendering;

import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Popup;
import javafx.stage.Window;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class StickyNote {

    private static final int MAX_NOTES = 5;
    private static final double DURATION_S = 22;

    private final Random rnd = new Random();
    private final List<Popup> active = new ArrayList<>();

    public void drop(Window owner, double x, double y, String text) {
        if (owner == null || text == null || text.isBlank()) {
            return;
        }
        Label label = new Label(text.strip());
        label.setWrapText(true);
        label.setMaxWidth(190);
        label.setPadding(new Insets(12));
        label.setStyle("-fx-font-size:14px; -fx-text-fill:#4a3242;");

        StackPane pane = new StackPane(label);
        pane.setStyle("-fx-background-color:#fff2a8; -fx-background-radius:4;"
                + " -fx-border-color:#e6c85a; -fx-border-width:1; -fx-border-radius:4;"
                + " -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 8, 0.2, 1, 2);");
        pane.setRotate(-5 + rnd.nextDouble() * 10);

        Popup popup = new Popup();
        popup.setAutoFix(false);
        popup.setAutoHide(false);
        popup.getContent().add(pane);
        pane.setOnMouseClicked(e -> dismiss(popup));

        popup.show(owner, x, y);
        active.add(popup);

        PauseTransition life = new PauseTransition(Duration.seconds(DURATION_S));
        life.setOnFinished(e -> dismiss(popup));
        life.play();

        while (active.size() > MAX_NOTES) {
            dismiss(active.get(0));
        }
    }

    private void dismiss(Popup popup) {
        popup.hide();
        active.remove(popup);
    }

    public void clearAll() {
        for (Popup p : new ArrayList<>(active)) {
            p.hide();
        }
        active.clear();
    }
}
