package src;
// =============================import===========================
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.*;
import java.awt.Image;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// =============================import===========================


public class Frame extends Application {
    // ===========================场景中的对象===========================
    private BorderPane root; // 根布局
    private HBox toolBar;    // 顶部工具栏
    private Button openBtn, exportBtn, lassoBtn, selectBtn; // 功能按钮
    private ScrollPane imageScrollPane; // 图片滚动容器
    private ImageView imageView; // 图片显示组件
    private FileChooser fileChooser; // 文件选择器
    private Stage primaryStage; // 主窗口引用（用于文件对话框）
    private Cursor currentCursor = Cursor.DEFAULT; // 当前光标状态
    private Image customImage;
    private List<Point2D> userPathPoints = new ArrayList<>(); // 用户绘制的路径点
    private boolean isLassoActive = false; // 套索工具是否激活
    private Canvas overlayCanvas = new Canvas(); // 用于绘制路径的透明画布
    private Map<Node, List<Edge>> graph; // 存储图结构的成员变量
    private BufferedImage bufferedImage; // 当前加载的图像
    private List<Point2D> optimizedPathScreen = new ArrayList<>();
    private ImageType imageType = ImageType.PNG;

    // ===========================场景中的对象===========================

//    private double xOffset = 0;
//    private double yOffset = 0;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;

        // 1. 初始化根布局和基础设置
        root = new BorderPane();
        Scene scene = new Scene(root, 800, 600);

        // 2. 创建顶部工具栏
        initToolBar(scene,this.optimizedPathScreen);

        // 3. 初始化图片显示区域
        initImageView();

        // 4. 设置事件监听（如快捷键缩放）
        setupEventHandlers(scene);

        // 5. 显示窗口
        primaryStage.setTitle("Photoshop-Like App");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void initToolBar(Scene scene, List<Point2D> optimizedPathScreen) {
        toolBar = new HBox(10);
        toolBar.setPadding(new Insets(10));

        // 创建按钮
        openBtn = new Button("打开文件");
        exportBtn = new Button("导出图片");
        lassoBtn = new Button("智能套索");
        selectBtn = new Button("选择工具");

        // 按钮事件绑定
        openBtn.setOnAction(e -> openImage());
        exportBtn.setOnAction(e -> exportImage());
        selectBtn.setOnAction(e -> scene.setCursor(Cursor.DEFAULT));

        // 智能套索按钮事件（合并到此处）
        lassoBtn.setOnAction(e -> {
            isLassoActive = true;
            scene.setCursor(Cursor.CROSSHAIR);
            userPathPoints.clear();
            drawOverlay(optimizedPathScreen);
        });

        // 只添加一次按钮到工具栏
        toolBar.getChildren().addAll(openBtn, exportBtn, lassoBtn, selectBtn);
        root.setTop(toolBar);

        // 鼠标事件监听
        overlayCanvas.setOnMousePressed(this::handleLassoPress);
        overlayCanvas.setOnMouseDragged(this::handleLassoDrag);
        overlayCanvas.setOnMouseReleased(e -> handleLassoRelease(e, scene));
    }

    private void handleLassoPress(MouseEvent e) {
        if (isLassoActive) {
            userPathPoints.add(new Point2D(e.getX(), e.getY()));
        }
    }

    private void handleLassoDrag(MouseEvent e) {
        if (isLassoActive) {
            userPathPoints.add(new Point2D(e.getX(), e.getY()));
            drawOverlay(optimizedPathScreen); // 实时绘制路径，此方法暂未实现
        }
    }

    private void handleLassoRelease(MouseEvent e, Scene scene) {
        if (isLassoActive) {
            optimizePathUsingGraph(); // 调用图结构优化路径
            isLassoActive = false;
            scene.setCursor(Cursor.DEFAULT);
        }
    }

    private void optimizePathUsingGraph() {
        if (userPathPoints.size() < 2) return;

        // 转换为像素坐标
        List<Node> pixelNodes = convertScreenPointsToNodes(userPathPoints);

        // 使用Dijkstra连接路径点（假设已实现）
        List<Node> optimizedPath = new ArrayList<>();
        for (int i = 0; i < pixelNodes.size() - 1; i++) {
            Node start = pixelNodes.get(i);
            Node end = pixelNodes.get(i + 1);
            List<Node> segment = Dijkstra.findShortestPath(graph, start, end);
            optimizedPath.addAll(segment);
        }

        // 转换为屏幕坐标（用于绘制）
        List<Point2D> screenPath = convertNodesToScreenPoints(optimizedPath);
        optimizedPathScreen = screenPath;

        // 重绘覆盖层
        drawOverlay(optimizedPathScreen);
    }

    private List<Node> convertScreenPointsToNodes(List<Point2D> screenPoints) {
        double scaleX = imageView.getImage().getWidth() / imageView.getBoundsInLocal().getWidth();
        double scaleY = imageView.getImage().getHeight() / imageView.getBoundsInLocal().getHeight();

        return screenPoints.stream()
                .map(p -> new Node(
                        (int)(p.getX() * scaleX),
                        (int)(p.getY() * scaleY)
                ))
                .collect(Collectors.toList());
    }

    private List<Point2D> convertNodesToScreenPoints(List<Node> nodes) {
        double scaleX = imageView.getBoundsInLocal().getWidth() / imageView.getImage().getWidth();
        double scaleY = imageView.getBoundsInLocal().getHeight() / imageView.getImage().getHeight();

        return nodes.stream()
                .map(node -> new Point2D(node.x * scaleX, node.y * scaleY))
                .collect(Collectors.toList());
    }

    private void setupEventHandlers(Scene scene) {
        // Ctrl + 滚轮缩放图片
        imageScrollPane.setOnScroll(e -> {
            if (e.isControlDown()) {
                double zoomFactor = e.getDeltaY() > 0 ? 1.1 : 0.9;
                imageView.setScaleX(imageView.getScaleX() * zoomFactor);
                imageView.setScaleY(imageView.getScaleY() * zoomFactor);
                e.consume(); // 阻止事件继续传递
            }
        });

        // 全局光标状态同步
        scene.setCursor(currentCursor);
    }

    private void drawOverlay(List<Point2D> optimizedPath) {
        GraphicsContext gc = overlayCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, overlayCanvas.getWidth(), overlayCanvas.getHeight()); // 清空画布

        // 绘制用户临时路径（红色）
        if (!userPathPoints.isEmpty()) {
            gc.setStroke(Color.RED);
            gc.setLineWidth(2);
            gc.beginPath();
            gc.moveTo(userPathPoints.get(0).getX(), userPathPoints.get(0).getY());
            for (int i = 1; i < userPathPoints.size(); i++) {
                gc.lineTo(userPathPoints.get(i).getX(), userPathPoints.get(i).getY());
            }
            gc.stroke();
        }

        // 绘制优化后的路径（绿色）
        if (optimizedPath != null && !optimizedPath.isEmpty()) {
            gc.setStroke(Color.GREEN);
            gc.setLineWidth(3);
            gc.beginPath();
            gc.moveTo(optimizedPath.get(0).getX(), optimizedPath.get(0).getY());
            for (int i = 1; i < optimizedPath.size(); i++) {
                gc.lineTo(optimizedPath.get(i).getX(), optimizedPath.get(i).getY());
            }
            gc.stroke();
        }
    }

    private void initImageView() {
        imageView = new ImageView();
        overlayCanvas = new Canvas();
        imageScrollPane = new ScrollPane(); // 初始化 ScrollPane

        // 绑定画布尺寸到 ImageView
        overlayCanvas.widthProperty().bind(imageView.fitWidthProperty());
        overlayCanvas.heightProperty().bind(imageView.fitHeightProperty());

        // 将 ImageView 和 Canvas 叠加到 StackPane
        StackPane stackPane = new StackPane(imageView, overlayCanvas);
        imageScrollPane.setContent(stackPane); // 正确设置内容

        // 将 ScrollPane 添加到根布局
        root.setCenter(imageScrollPane);
    }

    private void openImage() {
        FileChooser fileChooser = new FileChooser();
        File file = fileChooser.showOpenDialog(this.primaryStage);
        if (file != null) {
            try {
                // 加载图像
                this.bufferedImage = ImageIO.read(file);
                javafx.scene.image.Image fxImage = SwingFXUtils.toFXImage(this.bufferedImage, null);
                this.imageView.setImage(fxImage);
                String[] p = file.getPath().split("\\.");
                String typeStr = p[p.length-1];
                this.imageType = switch (typeStr.toLowerCase()) {
                    case "png"  -> ImageType.PNG;
                    case "jpg"  -> ImageType.JPG;
                    case "jpeg" -> ImageType.JPEG;
                    case "gif"  -> ImageType.GIF;
                    case "tiff" -> ImageType.TIFF;
                    case "svg"  -> ImageType.SVG;
                    case "webp" -> ImageType.WebP;
                    case "heif", "heic" -> ImageType.HEIF; // HEIF 可能对应两种扩展名
                    case "raw"  -> ImageType.RAW;
                    case "psd"  -> ImageType.PSD;
                    case "eps"  -> ImageType.EPS;
                    default      -> throw new IllegalArgumentException("不支持的图像类型: " + typeStr);
                };

                // 异步构建图结构
                new Thread(() -> {
                    Map<Node, List<Edge>> newGraph = ProcessImage.toGraph(bufferedImage);
                    Platform.runLater(() -> {
                        this.graph = newGraph; // 直接存储到成员变量
                    });
                }).start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void exportImage() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("保存图片");

        // 定义所有支持的格式列表（描述 + 扩展名 + ImageIO格式名）
        List<FileChooser.ExtensionFilter> filters = new ArrayList<>();
        Map<FileChooser.ExtensionFilter, String> formatMap = new HashMap<>();

        // 添加所有支持的格式
        addFormatFilter(filters, formatMap, "PNG 图像", ImageType.PNG, "*.png", "png");
        addFormatFilter(filters, formatMap, "JPEG 图像", ImageType.JPG, "*.jpg;*.jpeg", "jpg");
        addFormatFilter(filters, formatMap, "GIF 图像", ImageType.GIF, "*.gif", "gif");
        addFormatFilter(filters, formatMap, "TIFF 图像", ImageType.TIFF, "*.tiff;*.tif", "tiff");
        addFormatFilter(filters, formatMap, "WebP 图像", ImageType.WebP, "*.webp", "webp");
        // 其他格式...

        // 设置默认过滤器（基于当前 imageType）
        FileChooser.ExtensionFilter defaultFilter = filters.stream()
                .filter(f -> f.getDescription().contains(this.imageType.name()))
                .findFirst()
                .orElse(filters.get(0));
        fileChooser.setSelectedExtensionFilter(defaultFilter);

        // 添加所有过滤器到对话框
        fileChooser.getExtensionFilters().addAll(filters);

        File file = fileChooser.showSaveDialog(primaryStage);

        if (file != null) {
            try {
                BufferedImage bImage = SwingFXUtils.fromFXImage(imageView.getImage(), null);

                // 获取用户选择的格式
                FileChooser.ExtensionFilter selectedFilter = fileChooser.getSelectedExtensionFilter();
                String formatName = formatMap.get(selectedFilter);

                // 自动补全文件扩展名
                String ext = selectedFilter.getExtensions().get(0).replace("*.", "");
                if (!file.getName().toLowerCase().endsWith("." + ext)) {
                    file = new File(file.getAbsolutePath() + "." + ext);
                }

                ImageIO.write(bImage, formatName, file);
            } catch (IOException e) {
                e.printStackTrace();
                new Alert(Alert.AlertType.ERROR, "保存失败: " + e.getMessage()).show();
            }
        }
    }

    // 辅助方法：添加格式过滤器
    private void addFormatFilter(List<FileChooser.ExtensionFilter> filters,
                                 Map<FileChooser.ExtensionFilter, String> formatMap,
                                 String description,
                                 ImageType type,
                                 String extensions,
                                 String imageIOFormat) {
        FileChooser.ExtensionFilter filter = new FileChooser.ExtensionFilter(description, extensions);
        filters.add(filter);
        formatMap.put(filter, imageIOFormat.toUpperCase());
    }
}