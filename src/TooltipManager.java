package src;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Popup;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;

public class TooltipManager {
    private static final Popup popup = new Popup();
    private static final StackPane container = new StackPane();
    private static final Label label = new Label();
    private static PauseTransition hideTimer;

    static {
        // 初始化样式
        container.getStyleClass().add("tooltip-container");
        label.getStyleClass().add("tooltip-text");

        // 容器布局设置
        container.getChildren().add(label);
        popup.getContent().add(container);

        // 自动隐藏定时器
        hideTimer = new PauseTransition(Duration.seconds(3));
        hideTimer.setOnFinished(e -> popup.hide());
    }

    public static void showTooltip(Stage stage, String message, double x, double y) {
        // 确保在JavaFX线程运行
        Platform.runLater(() -> {
            // 取消之前的隐藏计时
            hideTimer.stop();

            // 更新内容
            label.setText(message);

            // 调整位置（避免溢出屏幕）
            double screenWidth = Screen.getPrimary().getBounds().getWidth();
            double screenHeight = Screen.getPrimary().getBounds().getHeight();
            double popupWidth = container.prefWidth(-1);
            double popupHeight = container.prefHeight(-1);

            // 自动调整显示位置
            double finalX = x + 15; // 偏移避免遮挡光标
            double finalY = y + 15;

            if (finalX + popupWidth > screenWidth) {
                finalX = x - popupWidth - 5;
            }
            if (finalY + popupHeight > screenHeight) {
                finalY = y - popupHeight - 5;
            }

            // 显示提示
            popup.show(stage, finalX, finalY);

            // 启动自动隐藏计时
            hideTimer.playFromStart();
        });
    }

    public static void hideTooltip() {
        Platform.runLater(popup::hide);
    }
}