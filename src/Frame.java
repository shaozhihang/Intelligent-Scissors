package src;

// =============================import===========================
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import javafx.stage.*;
import javafx.util.Pair;

import java.awt.Image;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;
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
    private boolean isMagneticLassoActive = false; // 套索工具是否激活
    private boolean isCursorSnap = false;
    private Canvas overlayCanvas = new Canvas(); // 用于绘制路径的透明画布
    private Map<Node, List<Edge>> graph; // 存储图结构的成员变量
    private BufferedImage bufferedImage; // 当前加载的图像
    private List<Point2D> optimizedPathScreen = new ArrayList<>();
    private ImageType imageType = ImageType.PNG;
    private SeedPoint currentSeed = null;
    private List<SeedPoint> seedPoints = new ArrayList<>();
    private Scene scene;
    private double imageWidth; // 图像的宽度
    private double imageHeight; // 图像的高度
    private double viewWidth; // 操作界面的宽度
    private double viewHeight; // 操作界面的高度
    private double scaleX;
    private double scaleY;
    private double[][] gMatrix;
    private int[][] matrix;
    private double[][] fgMatrix;
    //    private Map<Pair<Node, Node>, List<int[]>> pathCache = new HashMap<>(); // 路径缓存
    private final CursorSnap cursorSnap = new CursorSnap(); // 实例化吸附工具
    private List<List<int[]>> pathSegments = new ArrayList<>(); // 分段存储路径
    private static final int MAX_CACHE_SIZE = 100;
    private final LinkedHashMap<Pair<Node, Node>, List<int[]>> pathCache =
            new LinkedHashMap<>(MAX_CACHE_SIZE, 0.75f, true) { // LRU顺序
                @Override
                protected boolean removeEldestEntry(Map.Entry eldest) {
                    return size() > MAX_CACHE_SIZE;
                }
            };
    private CheckBox snapToggle = new CheckBox("启用吸附");
    private TextField radiusInput = new TextField("8");
    private Button applyRadiusBtn = new Button("<--应用吸附半径");

    private DoubleProperty snapRadius = new SimpleDoubleProperty(8.0);
    private BooleanProperty snapEnabled = new SimpleBooleanProperty(true);
    private double autoAnchorThreshold = 100;
    private boolean enableAutoAnchor = true;
    private double maxGradient;
    private ObservableList<Point2D> autoAnchors = FXCollections.observableArrayList();
    private double imageComplexity;
    private List<int[]> currentPath;
    private double lastInsertDistance = 0.0;
    private static final double DISTANCE_INTERVAL = 20.0;
    private double currentPathDistance = 0.0;
    private CompletableFuture<Void> currentPathCalculation = null;
    private CompletableFuture<Void> pathCalculationFuture = null;
    private final Object calculationLock = new Object();
    private volatile Point2D latestMousePosition = null;
    private long lastMoveTime = System.currentTimeMillis();
    private Point2D lastMovePosition = null;
    private double currentSpeed = 0;


    // ===========================场景中的对象===========================


    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;

        // 初始化根布局和基础设置
        root = new BorderPane();
        Scene scene = new Scene(root, 800, 600);
        this.scene = scene;

        // 初始化图片显示区域
        initImageView();

        // 创建顶部工具栏
        initToolBar(this.scene,this.optimizedPathScreen);

        // 设置事件监听（如快捷键缩放）
        setupEventHandlers(this.scene);

        // 设置名称
        primaryStage.setTitle("智能套索");

        // 设置图标
        // 相对路径转绝对路径
        URL iconUrl = getClass().getResource("/icons/lasso.png");

        javafx.scene.image.Image icon = new javafx.scene.image.Image(iconUrl.toExternalForm());// 使用绝对路径
        // 监听加载错误
        icon.errorProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                System.err.println("图标加载失败: " + icon.getException());
            }
        });
        primaryStage.getIcons().add(icon);
        // 添加场景
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
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
        addSnapControls();

        // 按钮事件绑定
        openBtn.setOnAction(e -> openImage());
        exportBtn.setOnAction(e -> exportImage());
        selectBtn.setOnAction(e -> {
            isMagneticLassoActive = false;
            scene.setCursor(Cursor.DEFAULT);});

        // 智能套索按钮事件
        lassoBtn.setOnAction(e -> {
            isMagneticLassoActive = true;
            scene.setCursor(Cursor.CROSSHAIR);
            seedPoints.clear();            // 清空旧种子点
            optimizedPathScreen.clear();   // 清空旧路径
            drawOverlay(optimizedPathScreen); // 立即刷新画布
        });

        // 只添加一次按钮到工具栏
        toolBar.getChildren().addAll(openBtn, exportBtn, lassoBtn, selectBtn,
                snapToggle, radiusInput, applyRadiusBtn);
        root.setTop(toolBar);

        // 鼠标事件监听
        overlayCanvas.setOnMousePressed(this::handleMagneticLassoPress);
        overlayCanvas.setOnMouseDragged(this::handleMagneticLassoDrag);
        overlayCanvas.setOnMouseReleased(e -> handleMagneticLassoRelease(e, scene));
        imageView.scaleXProperty().addListener((obs, oldVal, newVal) -> {
            // 获取当前视图尺寸（需动态更新）
            double currentViewWidth = imageView.getBoundsInLocal().getWidth();
            double currentViewHeight = imageView.getBoundsInLocal().getHeight();

            // 更新比例因子
            this.scaleX = imageWidth / (currentViewWidth * newVal.doubleValue());
            this.scaleY = imageHeight / (currentViewHeight * imageView.getScaleY());
        });

        // 同理监听 scaleY 变化
        imageView.scaleYProperty().addListener((obs, oldVal, newVal) -> {
            double currentViewWidth = imageView.getBoundsInLocal().getWidth();
            double currentViewHeight = imageView.getBoundsInLocal().getHeight();
            this.scaleX = imageWidth / (currentViewWidth * imageView.getScaleX());
            this.scaleY = imageHeight / (currentViewHeight * newVal.doubleValue());
        });

        // 禁用 ImageView 的事件拦截
        imageView.setPickOnBounds(false);
    }

    private void addSnapControls() {
        // 初始化吸附组件
        snapToggle.setSelected(true);
        radiusInput.setPrefWidth(80);
        applyRadiusBtn.setPrefWidth(120);

        // 输入验证：仅允许数字输入
        radiusInput.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.matches("\\d+")) {
                double val = Math.min(100, Math.max(1, Double.parseDouble(newVal)));
                snapRadius.set(val);
            }
        });

        // 应用按钮点击事件
        applyRadiusBtn.setOnAction(e -> {
            try {
                double newRadius = Double.parseDouble(radiusInput.getText());
                if (newRadius > 0 && newRadius <= 100) {
                    snapRadius.set(newRadius);
                } else {
                    showAlert("请输入1-100之间的数值");
                }
            } catch (NumberFormatException ex) {
                showAlert("请输入有效数字");
            }
        });

        // 绑定状态
        radiusInput.disableProperty().bind(snapEnabled.not());
        applyRadiusBtn.disableProperty().bind(snapEnabled.not());
        snapEnabled.bind(snapToggle.selectedProperty());
    }

    private void handleMagneticLassoPress(MouseEvent e) {
        System.out.printf("[EVENT] Press at (%.1f, %.1f) CanvasSize: %.0fx%.0f%n",
                e.getX(), e.getY(),
                overlayCanvas.getWidth(),
                overlayCanvas.getHeight());
        if (isMagneticLassoActive && e.getButton() == MouseButton.PRIMARY) {
            System.out.println("[Debug] 条件满足：模式已激活 + 左键点击");
            Point2D rawScreenPoint = new Point2D(e.getX(), e.getY());

            // 转换为图像坐标（行列索引）
            SeedPoint rawSeed = convertToSeedPoint(rawScreenPoint);
            int[] mousePoint = new int[]{rawSeed.getY(), rawSeed.getX()}; // 注意坐标顺序

            // 调用CursorSnap吸附逻辑
            /*int[] snappedPoint = new CursorSnap().findSnapPoint(
                    mousePoint,
                    gMatrix,
                    10 // snapRate（示例值，图像高度的1%）
            );*/

//            int[] snappedPoint = CursorSnap.realtimeSnap(rawScreenPoint, gMatrix, scaleX, scaleY);

            // 将吸附后的图像坐标转换为屏幕坐标
            Point2D snappedScreenPoint = CursorSnap.realtimeSnap(rawScreenPoint, gMatrix, scaleX, scaleY, snapRadius.intValue());

            // 检测是否闭合路径
            if (!seedPoints.isEmpty() && isNearFirstSeed(snappedScreenPoint)) {
                completeLasso();
                return;
            }

            // 添加新种子点
            SeedPoint newSeed = convertToSeedPoint(snappedScreenPoint);
            seedPoints.add(newSeed);
            currentSeed = newSeed;

            // 添加新种子点后
            if (seedPoints.size() >= 2) {
                SeedPoint prev = seedPoints.get(seedPoints.size()-2);
                SeedPoint current = seedPoints.getLast();

                // 替换为异步计算
                computeAndCachePathAsync(prev, current);
            }
            drawOverlay(optimizedPathScreen);
            System.out.println("种子点已添加: " + newSeed.getX() + ", " + newSeed.getY());
        }
    }

    private List<int[]> computeAndCachePath(SeedPoint start, SeedPoint end) {
        // 检查缓存
        Pair<Node, Node> key = new Pair<>(start.getNode(), end.getNode());
        synchronized (pathCache) {
            if (pathCache.containsKey(key)) {
                return pathCache.get(key);
            }
        }

        // 计算新路径
        List<int[]> path = ComputeMinCostPath.findShortestPath(
                fgMatrix,
                start.getY(),
                start.getX(),
                end.getY(),
                end.getX()
        ).getPath();

        // 更新缓存
        addToCache(key, path);

        if (path == null || path.isEmpty()) {
            throw new IllegalStateException("无法计算 " + start + " 到 " + end + " 的路径");
        }

        return path;
    }

    private List<Point2D> mergeAllSegments() {
        List<Point2D> merged = new ArrayList<>();

        for (int i = 0; i < pathSegments.size(); i++) {
            List<int[]> segment = pathSegments.get(i);
            List<Point2D> screenPoints = convertPathToScreenPoints(segment);

            // 防御性检查：确保路径段有效
            if (screenPoints.isEmpty()) {
                System.err.println("警告：第 " + i + " 段路径为空，已跳过");
                continue;
            }

            // 处理子列表范围
            if (i != pathSegments.size() - 1) {
                int endIndex = screenPoints.size() - 1;
                if (endIndex <= 0) { // 单点路径段无法分割
                    merged.addAll(screenPoints);
                } else {
                    merged.addAll(screenPoints.subList(0, endIndex));
                }
            } else {
                merged.addAll(screenPoints);
            }
        }

        return merged;
    }

    private void handleMagneticLassoDrag(MouseEvent e) {
        System.out.println("[DEBUG] 鼠标拖动事件触发");

    }

    private void handleMagneticLassoRelease(MouseEvent e, Scene scene) {
    }

    //    private void optimizePath(Node startPoint, Node endPoint) {
//        if (userPathPoints.size() < 2) return;
//
//        List<int[]> path = ComputeMinCostPath.findShortestPath(fgMatrix,startPoint.y,startPoint.x,endPoint.y,endPoint.x).getPath();
//        // 转换为屏幕坐标（用于绘制）
//        optimizedPathScreen = convertPathToScreenPoints(path);
//
//        // 重绘覆盖层
//        drawOverlay(optimizedPathScreen);
//    }
    private void optimizePath(Node startPoint, Node endPoint) {
        List<int[]> path;
        if (enableAutoAnchor) { // 新增配置开关
            path = computePathWithAutoAnchor(NodesToSeed(startPoint), NodesToSeed(endPoint));
        } else {
            path = ComputeMinCostPath.findShortestPath(fgMatrix,startPoint.y,startPoint.x,endPoint.y,endPoint.x).getPath();
        }
        optimizedPathScreen = convertPathToScreenPoints(path);
        drawOverlay(optimizedPathScreen);
    }

    private void optimizePath2(Point2D start, Point2D end) {
        Node startPoint = new Node(convertToSeedPoint(start));
        Node endPoint = new Node(convertToSeedPoint(end));
        if (userPathPoints.size() < 2) return;

        List<int[]> path = ComputeMinCostPath.findShortestPath(fgMatrix,startPoint.y,startPoint.x,endPoint.y,endPoint.x).getPath();
        // 转换为屏幕坐标（用于绘制）
        optimizedPathScreen = convertPathToScreenPoints(path);

        // 重绘覆盖层
        drawOverlay(optimizedPathScreen);
    }

    // ======================================converting methods=========================================
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
        // 获取当前视图的实际显示比例
        double viewportScaleX = imageView.getScaleX();
        double viewportScaleY = imageView.getScaleY();

        // 计算基础比例（适应图像原始尺寸与视图初始尺寸的比例）
        double baseScaleX = imageView.getBoundsInLocal().getWidth() / imageWidth;
        double baseScaleY = imageView.getBoundsInLocal().getHeight() / imageHeight;

        return nodes.stream()
                .map(node -> new Point2D(
                        (node.x * baseScaleX) * viewportScaleX,
                        (node.y * baseScaleY) * viewportScaleY
                ))
                .collect(Collectors.toList());
    }

    private List<Point2D> convertPathToScreenPoints(List<int[]> path){
        return convertNodesToScreenPoints(PathToNodes(path));
    }

    private static List<Node> PathToNodes(List<int[]> path) {
        List<Node> nodes = new ArrayList<>();
        for (int[] point :path){
            Node cur = new Node(point[0], point[1]);
            nodes.add(cur);
        }
        return nodes;
    }

    private static List<int[]> NodesToPath(List<Node> nodes) {
        List<int[]> path = new ArrayList<>();
        for (Node node :nodes) {
            int[] cur = new int[2];
            cur[0] = node.x;
            cur[1] = node.y;
            path.add(cur);
        }
        return path;
    }

    private static SeedPoint NodesToSeed(Node node) {
        return new SeedPoint(node);
    }

    private static Node SeedToNode(SeedPoint seed) {
        return new Node(seed.getX(), seed.getY());
    }
    // ======================================converting methods=========================================

    private void setupEventHandlers(Scene scene) {
//        // Ctrl + 滚轮缩放图片
//        imageScrollPane.setOnScroll(e -> {
//            if (e.isControlDown()) {
//                double zoomFactor = e.getDeltaY() > 0 ? 1.1 : 0.9;
//                imageView.setScaleX(imageView.getScaleX() * zoomFactor);
//                imageView.setScaleY(imageView.getScaleY() * zoomFactor);
//                e.consume(); // 阻止事件继续传递
//            }
//        });

        // 键盘事件
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DELETE && isMagneticLassoActive) {
                cancelMagneticLasso();
            }
//            if (e.getCode() == KeyCode.SPACE)
        });

        // 右键事件
        overlayCanvas.setOnContextMenuRequested(e -> {
            System.out.println("[Debug] 按下右键！");
            if (isMagneticLassoActive && !seedPoints.isEmpty()) {
                System.out.println("[Debug] 按下右键");
                undoLastSeed();
            }
        });

        // 左键事件
        overlayCanvas.setOnMousePressed(e -> {
            if (isMagneticLassoActive) {
                System.out.println("[Debug] 按下左键");
                handleMagneticLassoPress(e);
            }
        });

        // 全局光标状态同步
        scene.setCursor(currentCursor);
    }

    private void drawOverlay(List<Point2D> optimizedPath) {
        System.out.println("[Debug] 画图启动"+"套索模式："+isMagneticLassoActive);
        GraphicsContext gc = overlayCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, overlayCanvas.getWidth(), overlayCanvas.getHeight());

        // 仅绘制磁性套索相关元素
        // 1. 绘制所有种子点
        for (SeedPoint p : seedPoints) {
            if (p.isAutoGenerated()) {
                // 自动点用橙色绘制
                gc.setStroke(Color.ORANGE);
                gc.strokeOval(p.getX() - 3, p.getY() - 3, 6, 6);
            } else {
                // 用户点用蓝色绘制
                gc.setStroke(Color.BLUE);
                gc.strokeRect(p.getX() - 4, p.getY() - 4, 8, 8);
            }
        }
        System.out.println("Debug 绘制完成");

        // 2. 绘制优化后的路径
        if (optimizedPath != null && !optimizedPath.isEmpty()) {
            gc.setStroke(Color.GREEN);
            gc.setLineWidth(1);
            gc.beginPath();
            gc.moveTo(optimizedPath.getFirst().getX(), optimizedPath.getFirst().getY());
            for (int i = 1; i < optimizedPath.size(); i++) {
                gc.lineTo(optimizedPath.get(i).getX(), optimizedPath.get(i).getY());
            }
            gc.setFill(Color.rgb(255, 165, 0, 0.7)); // 橙色半透明
            autoAnchors.forEach(anchor ->
                    gc.fillOval(anchor.getX() -3, anchor.getY() -3, 6, 6)
            );
            gc.stroke();
        }

        // 绘制自动锚点
        gc.setFill(Color.ORANGE.deriveColor(0, 1, 1, 0.7));
        autoAnchors.forEach(p -> {
            gc.fillOval(p.getX() - 4, p.getY() - 4, 8, 8);
            gc.strokeOval(p.getX() - 5, p.getY() - 5, 10, 10);
        });


        // 3. 绘制闭合提示（如果靠近起点）
        if (!seedPoints.isEmpty() && isMagneticLassoActive) {
            SeedPoint first = seedPoints.getFirst();
            double x = first.getNode().x * scaleX;
            double y = first.getNode().y * scaleY;
            gc.setFill(Color.rgb(0, 255, 0, 0.3));
            gc.fillOval(x-8, y-8, 16, 16); // 半透明绿色提示圈
        }
    }

    private void initImageView() {
        imageView = new ImageView();
        overlayCanvas = new Canvas();
        imageScrollPane = new ScrollPane();

        imageView.scaleXProperty().addListener((obs, old, newVal) -> onImageModified());
        imageView.scaleYProperty().addListener((obs, old, newVal) -> onImageModified());
        imageView.translateXProperty().addListener((obs, old, newVal) -> onImageModified());
        imageView.translateYProperty().addListener((obs, old, newVal) -> onImageModified());

        // 确保画布可穿透鼠标事件
        overlayCanvas.setMouseTransparent(false);

        // 使用Group容器保证绝对坐标对齐
        Group layerGroup = new Group();
        layerGroup.getChildren().addAll(imageView, overlayCanvas);

        // 禁用StackPane的事件拦截
        StackPane stackPane = new StackPane();
        stackPane.setPickOnBounds(false);
        stackPane.getChildren().add(layerGroup);

        imageScrollPane.setContent(stackPane);
        root.setCenter(imageScrollPane);

        // 动态绑定画布尺寸（需在图像加载后）
        imageView.imageProperty().addListener((obs, oldImg, newImg) -> {
            if (newImg != null) {
                overlayCanvas.setWidth(newImg.getWidth());
                overlayCanvas.setHeight(newImg.getHeight());
            }
        });

        overlayCanvas.setOnMouseMoved(this::handleMouseMove);
    }

    // 实时鼠标移动处理
    private void handleMouseMove(MouseEvent e) {
        if (!isMagneticLassoActive || seedPoints.isEmpty()) return;

        Point2D rawPoint = new Point2D(e.getX(), e.getY());
        Point2D finalPoint = rawPoint;

        // 根据吸附状态处理
        if (snapEnabled.get()) {
            finalPoint = cursorSnap.realtimeSnap(
                    rawPoint,
                    gMatrix,
                    scaleX,
                    scaleY,
                    snapRadius.intValue() // 添加半径参数
            );
        }


        SeedPoint pos = convertToSeedPoint(finalPoint,true);
        ComputeMinCostPath.PathResult path = ComputeMinCostPath.findShortestPath(fgMatrix,currentSeed, pos);
        this.currentPath = path.getPath();
        this.currentPathDistance = calculateTotalPathDistance();
        double currentDistance = currentPathDistance;
        drawSnapFeedback(finalPoint, e);
        // 触发自动插入
        if (currentDistance - lastInsertDistance >= DISTANCE_INTERVAL) {
            autoAddSeedAlongPath(finalPoint);
            lastInsertDistance = currentDistance;
        }
        int[] currentPos = {(int)finalPoint.getX(), (int)finalPoint.getY()};
        currentPath.add(currentPos);
    }


    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // 绘制吸附反馈（例如圆圈标记）
    private void drawSnapFeedback(Point2D snappedPoint, MouseEvent e) {
        GraphicsContext gc = overlayCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, overlayCanvas.getWidth(), overlayCanvas.getHeight());

        // 绘制吸附点标记
        gc.setStroke(Color.RED);
        gc.setLineWidth(1);
        gc.strokeOval(
                snappedPoint.getX() - 3,
                snappedPoint.getY() - 3,
                6,
                6
        );
        // 绘制所有种子点
        for (SeedPoint p : seedPoints) {
            if (p.isAutoGenerated()) {
                // 自动点用橙色绘制
                gc.setFill(Color.ORANGE);
                gc.fillOval(p.getX() - 3, p.getY() - 3, 6, 6);
            } else {
                // 用户点用蓝色绘制
                gc.setFill(Color.BLUE);
                gc.fillRect(p.getX() - 4, p.getY() - 4, 8, 8);
            }
        }
        // 绘制先前路径
        if (!pathSegments.isEmpty()) {
            for (List<int[]> pathSegment : pathSegments) {
                drawPathSegment(gc, pathSegment); // 专用路径段绘制方法
            }
        }

        if (!seedPoints.isEmpty() && isMagneticLassoActive) {
            SeedPoint first = seedPoints.getFirst();
            double x = first.getNode().x * scaleX;
            double y = first.getNode().y * scaleY;
            gc.setFill(Color.rgb(0, 255, 0, 0.3));
            gc.fillOval(x-8, y-8, 16, 16); // 半透明绿色提示圈
        }

        if (snapEnabled.get()) {
            gc.setStroke(Color.rgb(255, 0, 0, 0.3));
            gc.strokeOval(e.getX() - snapRadius.get(),
                    e.getY() - snapRadius.get(),
                    snapRadius.get() * 2,
                    snapRadius.get() * 2);
        }

        // 绘制临时路径（如果正在绘制）
        if (!seedPoints.isEmpty()) {
            drawCurrentPath(snappedPoint, e);
        }
    }

    private void drawPathSegment(GraphicsContext gc, List<int[]> pathSegment) {
        gc.setStroke(Color.GREEN);
        gc.setLineWidth(1);

        List<Point2D> screenPoints = convertPathToScreenPoints(pathSegment);
        if (screenPoints.isEmpty()) return;

        gc.beginPath();
        gc.moveTo(screenPoints.getFirst().getX(), screenPoints.getFirst().getY());
        for (int i = 1; i < screenPoints.size(); i++) {
            gc.lineTo(screenPoints.get(i).getX(), screenPoints.get(i).getY());
        }
        gc.stroke();
    }

    // 绘制当前路径段
    private void drawCurrentPath(Point2D cursorPos, MouseEvent e) {

        GraphicsContext gc = overlayCanvas.getGraphicsContext2D();

        gc.clearRect(0, 0, overlayCanvas.getWidth(), overlayCanvas.getHeight());

        if (currentSpeed > 100) { // 高速移动时启用预测
            List<Point2D> predictedPath = predictPath(
                    new Point2D(currentSeed.getX(), currentSeed.getY()),
                    cursorPos
            );

            // 绘制预测路径
            gc.setStroke(Color.LIGHTGRAY);
            gc.setLineDashes(5);
            drawPolyline(gc, predictedPath, cursorPos, e);
            gc.setLineDashes(null);
        } else {
            // 正常绘制实际路径
            drawPolyline(gc, convertPathToScreenPoints(currentPath), cursorPos, e);
        }


//        // 1. 绘制所有历史路径段（已确认的路径）
//        for (List<int[]> segment : pathSegments) {
//            drawPathSegment(gc, segment);
//        }
//
//        // 2. 绘制当前活跃段（最后一个种子点到光标位置）
//        if (!currentPath.isEmpty()) {
//            // 转换为屏幕坐标
//            List<Point2D> screenPoints = convertPathToScreenPoints(currentPath);
//            // 添加当前光标位置作为临时终点
//            screenPoints.add(cursorPos);
//            // 绘制路径
//            gc.setStroke(Color.GREEN);
//            gc.beginPath();
//            gc.moveTo(screenPoints.getFirst().getX(), screenPoints.getFirst().getY());
//            for (int i = 1; i < screenPoints.size(); i++) {
//                gc.lineTo(screenPoints.get(i).getX(), screenPoints.get(i).getY());
//            }
//            gc.stroke();
//        }
//
//        gc.setStroke(Color.RED);
//        gc.setLineWidth(1);
//        gc.strokeOval(
//                cursorPos.getX() - 3,
//                cursorPos.getY() - 3,
//                6,
//                6
//        );
//        if (snapEnabled.get()) {
//            gc.setStroke(Color.rgb(255, 0, 0, 0.3));
//            gc.strokeOval(e.getX() - snapRadius.get(),
//                    e.getY() - snapRadius.get(),
//                    snapRadius.get() * 2,
//                    snapRadius.get() * 2);
//        }
//
//        if (!seedPoints.isEmpty() && isMagneticLassoActive) {
//            SeedPoint first = seedPoints.getFirst();
//            double x = first.getNode().x * scaleX;
//            double y = first.getNode().y * scaleY;
//            gc.setFill(Color.rgb(0, 255, 0, 0.3));
//            gc.fillOval(x-8, y-8, 16, 16); // 半透明绿色提示圈
//        }
//
//        // 3. 绘制所有种子点（用户点和自动点）
//        drawSeedPoints(gc);
    }

    private void drawPolyline(GraphicsContext gc, List<Point2D> path, Point2D cursorPos, MouseEvent e) {
        // 1. 绘制所有历史路径段（已确认的路径）
        for (List<int[]> segment : pathSegments) {
            drawPathSegment(gc, segment);
        }

        // 2. 绘制当前活跃段（最后一个种子点到光标位置）
        if (!currentPath.isEmpty()) {
            // 转换为屏幕坐标
            List<Point2D> screenPoints = convertPathToScreenPoints(currentPath);
            // 添加当前光标位置作为临时终点
            screenPoints.add(cursorPos);
            // 绘制路径
            gc.setStroke(Color.GREEN);
            gc.beginPath();
            gc.moveTo(screenPoints.getFirst().getX(), screenPoints.getFirst().getY());
            for (int i = 1; i < screenPoints.size(); i++) {
                gc.lineTo(screenPoints.get(i).getX(), screenPoints.get(i).getY());
            }
            gc.stroke();
        }

        gc.setStroke(Color.RED);
        gc.setLineWidth(1);
        gc.strokeOval(
                cursorPos.getX() - 3,
                cursorPos.getY() - 3,
                6,
                6
        );
        if (snapEnabled.get()) {
            gc.setStroke(Color.rgb(255, 0, 0, 0.3));
            gc.strokeOval(e.getX() - snapRadius.get(),
                    e.getY() - snapRadius.get(),
                    snapRadius.get() * 2,
                    snapRadius.get() * 2);
        }

        if (!seedPoints.isEmpty() && isMagneticLassoActive) {
            SeedPoint first = seedPoints.getFirst();
            double x = first.getNode().x * scaleX;
            double y = first.getNode().y * scaleY;
            gc.setFill(Color.rgb(0, 255, 0, 0.3));
            gc.fillOval(x-8, y-8, 16, 16); // 半透明绿色提示圈
        }

        // 3. 绘制所有种子点（用户点和自动点）
        drawSeedPoints(gc);
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
//                this.RGBMatrix = ProcessImage.toRGBMatrix(bufferedImage);

                // 更新图像尺寸和比例因子
                this.imageWidth = imageView.getImage().getWidth();
                this.imageHeight = imageView.getImage().getHeight();
                this.viewWidth = imageView.getBoundsInLocal().getWidth();
                this.viewHeight = imageView.getBoundsInLocal().getHeight();
                this.scaleX = imageWidth / viewWidth;
                this.scaleY = imageHeight / viewHeight;

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

                matrix = ProcessImage.toRGBMatrix(bufferedImage);
                gMatrix = ProcessMatrix.findGMatrix(matrix);
                fgMatrix = ProcessMatrix.findFgMatrix(gMatrix);
                // 刷新布局
                Platform.runLater(() -> {
                    overlayCanvas.setWidth(imageView.getLayoutBounds().getWidth());
                    overlayCanvas.setHeight(imageView.getLayoutBounds().getHeight());
                    drawOverlay(optimizedPathScreen);
                });
                this.maxGradient = ProcessMatrix.findMaxGradient(this.gMatrix);
                this.imageComplexity = calculateImageComplexity();
                this.autoAnchorThreshold = 100 * (1 + imageComplexity / 255.0);
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
                .orElse(filters.getFirst());
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
                String ext = selectedFilter.getExtensions().getFirst().replace("*.", "");
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

    private SeedPoint convertToSeedPoint(Point2D screenPoint) {
        // 动态获取当前比例因子（每次点击时计算最新值）
        double currentScaleX = imageView.getImage().getWidth() / imageView.getBoundsInLocal().getWidth();
        double currentScaleY = imageView.getImage().getHeight() / imageView.getBoundsInLocal().getHeight();

        int x = (int)(screenPoint.getX() * currentScaleX);
        int y = (int)(screenPoint.getY() * currentScaleY);
        return new SeedPoint(x, y);
    }

    private SeedPoint convertToSeedPoint(Point2D screenPoint, boolean antoGenerated) {
        // 动态获取当前比例因子（每次点击时计算最新值）
        double currentScaleX = imageView.getImage().getWidth() / imageView.getBoundsInLocal().getWidth();
        double currentScaleY = imageView.getImage().getHeight() / imageView.getBoundsInLocal().getHeight();

        int x = (int)(screenPoint.getX() * currentScaleX);
        int y = (int)(screenPoint.getY() * currentScaleY);
        return new SeedPoint(x, y, antoGenerated);
    }

    // 绘制种子标记（方框+十字）
    private void drawSeedMarker(GraphicsContext gc, SeedPoint seed) {
        // 获取实际渲染尺寸
        double renderWidth = imageView.getBoundsInLocal().getWidth();
        double renderHeight = imageView.getBoundsInLocal().getHeight();

        // 计算实际显示比例
        double scaleX = renderWidth / imageView.getImage().getWidth();
        double scaleY = renderHeight / imageView.getImage().getHeight();

        // 转换到画布坐标
        double screenX = seed.getX() * scaleX;
        double screenY = seed.getY() * scaleY;

        // 绘制标记（增加可见性）
        gc.setStroke(Color.BLUE);
        gc.setLineWidth(1);
        gc.strokeRect(screenX - 4, screenY - 4, 8, 8); // 放大标记
    }

    // 取消操作
    private void cancelMagneticLasso() {
        seedPoints.clear();
        currentSeed = null;
        optimizedPathScreen.clear();
        isMagneticLassoActive = false; // 显式退出模式
        scene.setCursor(Cursor.DEFAULT);
        pathCache.clear();
        pathSegments.clear();
        drawOverlay(optimizedPathScreen);
    }

    // 撤销上一个种子点
    private void undoLastSeed() {
        if (!seedPoints.isEmpty()) {
            seedPoints.removeLast();
            currentSeed = seedPoints.isEmpty() ? null : seedPoints.getLast();
            deleteCurrentPath();
            drawOverlay(optimizedPathScreen);
        }
    }

    // 闭合路径
    private void completeLasso() {
        if (seedPoints.size() < 3) return;

        // 连接首尾
        optimizePath(seedPoints.getLast().getNode(), seedPoints.getFirst().getNode());

        // 生成选区蒙版
        generateSelectionMask();

        // 重置状态
        isMagneticLassoActive = false;
        scene.setCursor(Cursor.DEFAULT);
    }

    // 检测是否靠近第一个种子点
    private boolean isNearFirstSeed(Point2D point) {
        if (seedPoints.isEmpty()) return false;
        SeedPoint first = seedPoints.getFirst();
        return point.distance(first.getNode().x, first.getNode().y) < 10; // 10像素范围内
    }

    private void recalculateAllPaths() {
        pathSegments.clear();
        pathCache.clear(); // 清空缓存

        // 重新计算所有段
        for (int i=1; i<seedPoints.size(); i++) {
            SeedPoint start = seedPoints.get(i-1);
            SeedPoint end = seedPoints.get(i);
            List<int[]> segment = computeAndCachePath(start, end);
            pathSegments.add(segment);
        }

        optimizedPathScreen = mergeAllSegments();
        drawOverlay(optimizedPathScreen);
    }

    private void deleteCurrentPath() {
        pathSegments.removeLast();
        optimizedPathScreen.removeLast();
    }

    // =========================================output image mask====================================
    private void generateSelectionMask() {
        if (seedPoints.size() < 3) {
            new Alert(Alert.AlertType.WARNING, "至少需要3个点才能生成选区").show();
            return;
        }

        // 获取原始图像尺寸
        int imgWidth = (int) imageView.getImage().getWidth();
        int imgHeight = (int) imageView.getImage().getHeight();

        // 转换路径到原始图像坐标（考虑缩放和滚动）
        List<Point2D> imageSpacePath = optimizedPathScreen.stream()
                .map(p -> convertToImageSpace(p))
                .collect(Collectors.toList());

        // 创建蒙版图像
        WritableImage mask = new WritableImage(imgWidth, imgHeight);
        PixelWriter writer = mask.getPixelWriter();

        // 扫描线填充算法
        scanLineFill(imageSpacePath, writer);

        // 应用蒙版
        imageView.setImage(combineImageWithMask(imageView.getImage(), mask));
    }

    // 坐标转换：屏幕坐标 -> 原始图像坐标
    private Point2D convertToImageSpace(Point2D screenPoint) {
        double scaleX = imageView.getImage().getWidth() / imageView.getBoundsInLocal().getWidth();
        double scaleY = imageView.getImage().getHeight() / imageView.getBoundsInLocal().getHeight();

        return new Point2D(
                screenPoint.getX() * scaleX / imageView.getScaleX(),
                screenPoint.getY() * scaleY / imageView.getScaleY()
        );
    }

    // 扫描线填充算法实现
    private void scanLineFill(List<Point2D> polygon, PixelWriter writer) {
        // 数据结构准备
        Map<Integer, List<Double>> edgeTable = new TreeMap<>();
        List<Point2D> closedPolygon = new ArrayList<>(polygon);
        closedPolygon.add(polygon.getFirst()); // 闭合路径
        WritableImage mask = new WritableImage(
                (int)imageView.getImage().getWidth(),
                (int)imageView.getImage().getHeight()
        );

        // 1. 构建边表(Edge Table)
        for (int i = 0; i < closedPolygon.size() - 1; i++) {
            Point2D p1 = closedPolygon.get(i);
            Point2D p2 = closedPolygon.get(i + 1);

            // 忽略水平线
            if (p1.getY() == p2.getY()) continue;

            // 确保p1在上方
            Point2D lower = p1.getY() < p2.getY() ? p2 : p1;
            Point2D upper = p1.getY() < p2.getY() ? p1 : p2;

            double dx = lower.getX() - upper.getX();
            double dy = lower.getY() - upper.getY();
            double inverseSlope = dx / dy;

            // 将边插入边表
            int yMin = (int) Math.ceil(upper.getY());
            int yMax = (int) Math.ceil(lower.getY());
            for (int y = yMin; y < yMax; y++) {
                if (!edgeTable.containsKey(y)) {
                    edgeTable.put(y, new ArrayList<>());
                }
                double x = upper.getX() + inverseSlope * (y - upper.getY());
                edgeTable.get(y).add(x);
            }
        }

        // 2. 扫描处理
        List<Double> activeEdges = new ArrayList<>();
        for (int y : edgeTable.keySet()) {
            // 添加新边
            activeEdges.addAll(edgeTable.get(y));

            // 删除失效边
            activeEdges.removeIf(x -> y >= getYMax(x, activeEdges, edgeTable));

            // 排序交点
            activeEdges.sort(Double::compare);

            // 填充扫描线
            for (int i = 0; i < activeEdges.size(); i += 2) {
                if (i + 1 >= activeEdges.size()) break;

                int startX = (int) Math.ceil(activeEdges.get(i));
                int endX = (int) Math.floor(activeEdges.get(i + 1));

                for (int x = startX; x <= endX; x++) {
                    if (x >= 0 && x < mask.getWidth() && y >= 0 && y < mask.getHeight()) {
                        writer.setColor(x, y, Color.WHITE); // 选区设为不透明
                    }
                }
            }
        }
    }

    // 获取边的最大Y值
    private int getYMax(double x, List<Double> activeEdges, Map<Integer, List<Double>> edgeTable) {
        for (Map.Entry<Integer, List<Double>> entry : edgeTable.entrySet()) {
            if (entry.getValue().contains(x)) {
                return entry.getKey();
            }
        }
        return Integer.MAX_VALUE;
    }

    // 合并图像与蒙版


    private javafx.scene.image.Image combineImageWithMask(
            javafx.scene.image.Image originalImage,
            WritableImage mask
    ) {
        int width = (int) originalImage.getWidth();
        int height = (int) originalImage.getHeight();

        // 创建结果图像（ARGB格式）
        WritableImage result = new WritableImage(width, height);
        PixelWriter writer = result.getPixelWriter();

        // 获取像素读取器
        PixelReader originalReader = originalImage.getPixelReader();
        PixelReader maskReader = mask.getPixelReader();

        // 遍历每个像素
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // 读取原始图像颜色
                Color originalColor = originalReader.getColor(x, y);

                // 读取蒙版颜色（假设蒙版为灰度图，白色表示保留，黑色表示透明）
                Color maskColor = maskReader.getColor(x, y);

                // 计算透明度：蒙版亮度（白色=1.0表示不透明，黑色=0.0表示透明）
                double alpha = maskColor.getRed(); // 使用红色通道作为亮度

                // 创建新颜色（保留原始RGB，应用蒙版透明度）
                Color newColor = new Color(
                        originalColor.getRed(),
                        originalColor.getGreen(),
                        originalColor.getBlue(),
                        alpha
                );

                // 写入结果图像
                writer.setColor(x, y, newColor);
            }
        }

        return result;
    }
    //===================================output image mask=======================================


    //===================================handle huge image=======================================
    private void addToCache(Pair<Node, Node> key, List<int[]> path) {
        if (pathCache.size() >= MAX_CACHE_SIZE) {
            // LRU淘汰策略
            pathCache.remove(pathCache.keySet().iterator().next());
        }
        pathCache.put(key, path);
    }

    public void onImageModified() {
        pathCache.clear(); // 使缓存失效
        optimizedPathScreen = mergeAllSegments(); // 重新生成路径
    }


    private void computeAndCachePathAsync(SeedPoint start, SeedPoint end) {
        // 显示加载状态
        ProgressIndicator progress = new ProgressIndicator(-1);
        StackPane overlay = new StackPane(progress);
        overlay.setStyle("-fx-background-color: rgba(0,0,0,0.5)");
        root.getChildren().add(overlay);

        CompletableFuture.supplyAsync(() -> {
            return computeAndCachePath(start, end);
        }).thenAcceptBoth(CompletableFuture.runAsync(this::mergeAllSegments), (segment, _) -> {
            Platform.runLater(() -> {
                pathSegments.add(segment);
                optimizedPathScreen = mergeAllSegments();
                drawOverlay(optimizedPathScreen);
                root.getChildren().remove(overlay); // 移除加载提示
            });
        }).exceptionally(ex -> {
            Platform.runLater(() -> {
                new Alert(Alert.AlertType.ERROR, "路径计算失败: " + ex.getMessage()).show();
                root.getChildren().remove(overlay);
            });
            return null;
        });
    }
    //===============================================handle huge image==========================================

    //===============================================path cooling===============================================
    public class AnchorPoint {
        private final int x;
        private final int y;
        private final boolean autoGenerated;

        public AnchorPoint(int x, int y, boolean autoGenerated) {
            this.x = x;
            this.y = y;
            this.autoGenerated = autoGenerated;
        }



        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public boolean isAutoGenerated() {
            return autoGenerated;
        }
        // getters & constructor...
    }

    class CandidateScore {
        final int index;     // 路径点索引
        final double score;  // 综合评分
        final double grad;   // 梯度强度
        final double curve;  // 曲率变化

        CandidateScore(int index, double grad, double curve) {
            this.index = index;
            this.grad = grad;
            this.curve = curve;
            this.score = 0.6 * grad + 0.4 * curve; // 权重可调
        }
    }

    private boolean needAutoAnchor(SeedPoint start, SeedPoint end) {
        double distance = Math.hypot(end.getX() - start.getX(), end.getX() - start.getY());
        return distance > autoAnchorThreshold; // 可配置阈值
    }

    private List<SeedPoint> findMidCandidates(SeedPoint start, SeedPoint end) {
        List<SeedPoint> candidates = new ArrayList<>();
        double minGrad = 0.2 * maxGradient;

        // 计算路径方向单位向量
        double dx = end.getX() - start.getX();
        double dy = end.getY() - start.getY();
        double length = Math.hypot(dx, dy);
        double ux = dx / length;
        double uy = dy / length;

        // 沿路径方向采样
        for (double t = 0.2; t < 0.8; t += 0.1) {
            int x = (int)(start.getX() + t * dx);
            int y = (int)(start.getY() + t * dy);

            if (gMatrix[y][x] < minGrad) continue;

            // 检测局部梯度极大值
            if (isLocalGradientMax(x, y, ux, uy)) {
                candidates.add(new SeedPoint(x, y));
            }
        }
        return candidates;
    }

    // 判断是否为局部梯度极大值
    private boolean isLocalGradientMax(int x, int y, double dirX, double dirY) {
        double currentGrad = gMatrix[y][x];

        // 沿法线方向检查两侧像素
        double orthogonalX = -dirY; // 正交方向向量
        double orthogonalY = dirX;

        for (int k = -1; k <= 1; k++) {
            if (k == 0) continue;
            int nx = x + (int)(k * orthogonalX);
            int ny = y + (int)(k * orthogonalY);
            if (ny >=0 && ny < gMatrix.length && nx >=0 && nx < gMatrix[0].length) {
                if (gMatrix[ny][nx] >= currentGrad) {
                    return false;
                }
            }
        }
        return true;
    }

    private List<int[]> computePathWithAutoAnchor(SeedPoint start, SeedPoint end) {
        List<int[]> fullPath = new ArrayList<>();

        if (!needAutoAnchor(start, end)) {
            return currentPath;
        }

        // 查找中间候选点
        List<SeedPoint> candidates = findMidCandidates(start, end);
        List<SeedPoint> topCandidates = selectTopCandidates(candidates, 3); // 取前3个
        if (candidates.isEmpty()) {
            return currentPath;
        }

        SeedPoint mid = candidates.stream()
                .max(Comparator.comparingDouble(p -> gMatrix[p.getY()][p.getX()]))
                .orElse(null);
        if (mid == null) return Collections.emptyList();

        if (mid != null) {
            // 记录自动生成的锚点（屏幕坐标）
            Point2D screenPoint = convertToScreenCoordinates(mid.getX(), mid.getY());
            Platform.runLater(() -> {
                autoAnchors.add(screenPoint);
            });
        }

        // 4. 记录自动生成的锚点（添加到成员变量集合）
        Point2D screenMid = convertToScreenCoordinates(mid.getX(), mid.getY());
        Platform.runLater(() -> autoAnchors.add(screenMid));

        // 5. 递归计算子路径
        List<int[]> path1 = computePathWithAutoAnchor(start, mid);
        List<int[]> path2 = computePathWithAutoAnchor(mid, end);

        // 6. 合并路径（去重中点）
        return mergePaths(path1, path2);
    }


    private double calculatePathCost(List<int[]> path) {
        return path.stream()
                .mapToDouble(p -> gMatrix[p[1]][p[0]])
                .sum();
    }

    private List<SeedPoint> selectTopCandidates(List<SeedPoint> candidates, int maxCount) {
        // 按梯度值降序排序
        candidates.sort((a, b) -> Double.compare(
                gMatrix[b.getY()][b.getX()],
                gMatrix[a.getY()][a.getX()]
        ));

        // 选择梯度最大的前N个点
        return candidates.stream()
                .limit(maxCount)
                .collect(Collectors.toList());
    }

    private Point2D convertToScreenCoordinates(int imgX, int imgY) {
        double screenX = imgX * scaleX + imageView.getBoundsInParent().getMinX();
        double screenY = imgY * scaleY + imageView.getBoundsInParent().getMinY();
        return new Point2D(screenX, screenY);
    }

    private static List<int[]> mergePaths(List<int[]> p1, List<int[]> p2) {
        List<int[]> mergedPath = new ArrayList<>();
        mergedPath.addAll(p1);
        mergedPath.addAll(p2);

        return mergedPath;
    }

    private void autoInsertSeed(SeedPoint start, Point2D cursorPos) {
        // 转换光标位置为图像坐标
        SeedPoint end = convertToSeedPoint(cursorPos);

        // 计算两点间距离
        double distance = Math.hypot(end.getX() - start.getX(), end.getY() - start.getY());

        double autoInsertThreshold = calculateDynamicThreshold();
        if (distance > autoInsertThreshold) {
            // 查找中间候选点
            SeedPoint mid = findBestMidPoint(start, end);

            if (mid != null) {
                // 递归处理前半段路径
                autoInsertSeed(start, convertToScreenCoordinates(mid.getX(),mid.getY()));

                // 添加自动生成的中间点
                mid = new SeedPoint(mid.getX(), mid.getY(), true);
                seedPoints.add(mid);
                currentSeed = mid;

                // 递归处理后半段路径
                autoInsertSeed(mid, cursorPos);
            }
        }
    }

    private SeedPoint findBestMidPoint(SeedPoint start, SeedPoint end) {
        List<SeedPoint> candidates = findMidCandidates(start, end);
        return candidates.stream()
                .max(Comparator.comparing(p -> gMatrix[p.getY()][p.getX()]))
                .orElse(null);
    }

    private double calculateImageComplexity() {
        // 计算梯度平均值作为复杂度指标
        double total = 0;
        for (double[] row : gMatrix) {
            for (double val : row) {
                total += val;
            }
        }
        return total / (gMatrix.length * gMatrix[0].length);
    }

    private double calculateDynamicThreshold() {
        double complexity = calculateImageComplexity();
        return 100 + (1 - complexity) * 200; // 复杂度低时阈值更大
    }

    private List<CandidateScore> analyzePath(List<int[]> path) {
        List<CandidateScore> scores = new ArrayList<>();

        for (int i = 1; i < path.size() - 1; i++) {
            int[] prev = path.get(i-1);
            int[] curr = path.get(i);
            int[] next = path.get(i+1);

            // 计算梯度强度
            double grad = gMatrix[curr[1]][curr[0]];

            // 计算曲率变化
            double curvature = calculateCurvature(prev, curr, next);

            scores.add(new CandidateScore(i, grad, curvature));
        }
        return scores;
    }

    private double calculateCurvature(int[] p1, int[] p2, int[] p3) {
        // 向量1：p1->p2
        double dx1 = p2[0] - p1[0];
        double dy1 = p2[1] - p1[1];

        // 向量2：p2->p3
        double dx2 = p3[0] - p2[0];
        double dy2 = p3[1] - p2[1];

        // 计算方向变化角度
        double angle1 = Math.atan2(dy1, dx1);
        double angle2 = Math.atan2(dy2, dx2);
        return Math.abs(angle2 - angle1); // 角度差绝对值
    }

    private int selectBestCandidate(List<CandidateScore> scores) {
        // 筛选梯度前20%的点
        double gradThreshold = scores.stream()
                .mapToDouble(s -> s.grad)
                .sorted()
                .skip((int)(scores.size() * 0.8))
                .findFirst()
                .orElse(0);

        // 非极大值抑制（NMS）
        List<CandidateScore> filtered = new ArrayList<>();
        for (CandidateScore s : scores) {
            if (s.grad < gradThreshold) continue;

            // 检查邻近区域是否已有更高分点
            boolean isLocalMax = scores.stream()
                    .filter(os -> Math.abs(os.index - s.index) < 5) // 邻近窗口
                    .noneMatch(os -> os.score > s.score);

            if (isLocalMax) filtered.add(s);
        }

        // 选择综合得分最高的点
        return filtered.stream()
                .max(Comparator.comparingDouble(s -> s.score))
                .map(s -> s.index)
                .orElse(-1);
    }

    private void drawSeedPoints(GraphicsContext gc) {
        for (SeedPoint seed : seedPoints) {
            // 自动生成的种子点用橙色标记，用户设置的用蓝色
            if (seed.isAutoGenerated()) {
                gc.setStroke(Color.ORANGE);
                gc.strokeOval(seed.getX() - 3, seed.getY() - 3, 6, 6);
            } else {
                gc.setStroke(Color.BLUE);
                gc.strokeRect(seed.getX() - 4, seed.getY() - 4, 8, 8);
            }
        }
    }

    public void autoAddSeedAlongPath(Point2D snappedPoint) {
        // 获取当前路径段
        List<int[]> currentSegment = this.currentPath;
        if (currentSegment.isEmpty()) return;

        // 分析路径并选择最佳插入点
        int bestIndex = selectBestCandidate2(currentSegment);
        if (bestIndex < 0 || bestIndex >= currentSegment.size()) return;

        // 分割路径段：历史部分（到插入点）和新部分（从插入点开始）
        List<int[]> existingPart = new ArrayList<>(currentSegment.subList(0, bestIndex + 1));
        List<int[]> newPart = new ArrayList<>(currentSegment.subList(bestIndex, currentSegment.size()));

        // 保存历史部分
        pathSegments.add(existingPart);

        // 更新当前路径段为新部分
        this.currentPath = newPart;

        // 添加新种子点（使用插入点的坐标）
        int[] bestPoint = existingPart.getLast();
        SeedPoint newSeed = new SeedPoint(bestPoint[0], bestPoint[1], true);
        seedPoints.add(newSeed);
        currentSeed = newSeed;

        // 更新插入距离跟踪
        lastInsertDistance = calculateTotalPathDistance();
    }

    private int getAutoInsertInterval(List<int[]> path) {
        int baseInterval = 50; // 基础间隔
        double complexity = calculatePathComplexity(path);
        return (int)(baseInterval * (1 + complexity)); // 复杂度高时增大间隔
    }

    private int selectBestCandidate2(List<int[]> pathSegment) {
        if (pathSegment == null || pathSegment.size() < 3) {
            return -1; // 路径段过短，无法分析
        }

        // 1. 分析路径段的特征
        List<CandidateScore> scores = analyzePath(pathSegment);

        // 2. 选择最佳候选点
        int bestIndex = -1;
        double maxScore = -1;
        for (CandidateScore score : scores) {
            if (score.score > maxScore) {
                maxScore = score.score;
                bestIndex = score.index;
            }
        }

        return bestIndex;
    }

    private double calculatePathComplexity(List<int[]> path) {
        // 计算路径平均曲率
        return analyzePath(path).stream()
                .mapToDouble(s -> s.curve)
                .average()
                .orElse(0);
    }

    private double calculatePathDistance(List<int[]> path) {
        double dist = 0;
        for (int i = 1; i < path.size(); i++) {
            int[] p1 = path.get(i-1);
            int[] p2 = path.get(i);
            dist += Math.hypot(p2[0]-p1[0], p2[1]-p1[1]);
        }
        return dist;
    }

    private double calculateTotalPathDistance() {
        // 历史段总距离
        double total = pathSegments.stream()
                .mapToDouble(this::calculateSegmentDistance)
                .sum();

        // 加上当前段距离
        total += calculateSegmentDistance(currentPath);
        return total;
    }

    private double calculateSegmentDistance(List<int[]> segment) {
        double dist = 0.0;
        for (int i = 1; i < segment.size(); i++) {
            int[] p1 = segment.get(i-1);
            int[] p2 = segment.get(i);
            dist += Math.hypot(p2[0]-p1[0], p2[1]-p1[1]);
        }
        return dist;
    }

    private void updateMovementState(Point2D newPosition) {
        long currentTime = System.currentTimeMillis();
        if (lastMovePosition != null) {
            // 计算瞬时速度（像素/秒）
            double distance = newPosition.distance(lastMovePosition);
            long timeDelta = currentTime - lastMoveTime;
            currentSpeed = (timeDelta > 0) ? (distance / timeDelta * 1000) : 0;
        }
        lastMovePosition = newPosition;
        lastMoveTime = currentTime;
    }

    private boolean shouldAddSeed() {
        // 动态间隔公式：基础20px + 速度系数
        double dynamicThreshold = 20 + (currentSpeed * 0.5);
        return (currentPathDistance - lastInsertDistance) > dynamicThreshold;
    }

    //===============================================path cooling===============================================

    // =============================================path predicting=============================================
    private List<Point2D> predictPath(Point2D start, Point2D end) {
        // 贝塞尔曲线预测（二次贝塞尔）
        Point2D controlPoint = calculateControlPoint(start, end);
        return generateBezierPoints(start, controlPoint, end, 10);
    }

    private Point2D calculateControlPoint(Point2D start, Point2D end) {
        // 根据移动方向计算控制点
        double dx = end.getX() - start.getX();
        double dy = end.getY() - start.getY();
        return new Point2D(
                start.getX() + dx * 0.7 + dy * 0.2,
                start.getY() + dy * 0.7 - dx * 0.2
        );
    }

    private List<Point2D> generateBezierPoints(Point2D p0, Point2D p1, Point2D p2, int segments) {
        List<Point2D> points = new ArrayList<>();
        for (int i = 0; i <= segments; i++) {
            double t = (double)i / segments;
            double x = (1-t)*(1-t)*p0.getX() + 2*(1-t)*t*p1.getX() + t*t*p2.getX();
            double y = (1-t)*(1-t)*p0.getY() + 2*(1-t)*t*p1.getY() + t*t*p2.getY();
            points.add(new Point2D(x, y));
        }
        return points;
    }
    // =============================================path predicting=============================================

}
