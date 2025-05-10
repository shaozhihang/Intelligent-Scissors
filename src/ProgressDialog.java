package src;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class ProgressDialog {
    private final Stage dialogStage;
    private final ProgressBar progressBar;
    private final Label label;

    public ProgressDialog(Stage owner) {
        dialogStage = new Stage();
        dialogStage.initOwner(owner);
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.setTitle("Processing...");

        VBox root = new VBox(10);
        root.setPadding(new Insets(15));
        root.setAlignment(Pos.CENTER);

        progressBar = new ProgressBar();
        progressBar.setPrefWidth(300);

        label = new Label("Generating selection mask...");
        Button cancelBtn = new Button("Cancel");

        root.getChildren().addAll(label, progressBar, cancelBtn);
        Scene scene = new Scene(root);
        dialogStage.setScene(scene);
    }

    public void show() {
        dialogStage.show();
    }

    public void updateProgress(double progress, String message) {
        Platform.runLater(() -> {
            progressBar.setProgress(progress);
            label.setText(message);
        });
    }

    public void close() {
        Platform.runLater(dialogStage::close);
    }

    public void setOnCancel(Runnable action) {
        // 通过查找取消按钮设置事件
        ((Button)((VBox)dialogStage.getScene().getRoot()).getChildren().get(2))
                .setOnAction(e -> action.run());
    }
}