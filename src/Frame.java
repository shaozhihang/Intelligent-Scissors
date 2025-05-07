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
//    private Map<Pair<Node, Node>, List<int[]>> pathCache = new HashMap<>(); // 路径缓存
    private List<List<int[]>> pathSegments = new ArrayList<>(); // 分段存储路径
    private static final int MAX_CACHE_SIZE = 100;
    private final LinkedHashMap<Pair<Node, Node>, List<int[]>> pathCache =
            new LinkedHashMap<>(MAX_CACHE_SIZE, 0.75f, true) { // LRU顺序
                @Override
                protected boolean removeEldestEntry(Map.Entry eldest) {
                    return size() > MAX_CACHE_SIZE;
                }
            };

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
        toolBar.getChildren().addAll(openBtn, exportBtn, lassoBtn, selectBtn);
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
            int[] snappedPoint = new CursorSnap().findSnapPoint(
                    mousePoint,
                    gMatrix,
                    10 // snapRate（示例值，图像高度的1%）
            );

            // 将吸附后的图像坐标转换为屏幕坐标
            Point2D snappedScreenPoint = new Point2D(
                    snappedPoint[1] / scaleX, // 根据实际比例因子调整
                    snappedPoint[0] / scaleY
            );

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
                gMatrix,
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
        if (isMagneticLassoActive) {
            userPathPoints.add(new Point2D(e.getX(), e.getY()));
            drawOverlay(optimizedPathScreen); // 实时绘制路径，此方法暂未实现
        }
    }

    private void handleMagneticLassoRelease(MouseEvent e, Scene scene) {
    }

    private void optimizePath(Node startPoint, Node endPoint) {
        if (userPathPoints.size() < 2) return;

        int[][] RGBMatrix = ProcessImage.toRGBMatrix(this.bufferedImage);
        double[][] gMatrix = ProcessMatrix.findGMatrix(RGBMatrix);
        List<int[]> path = ComputeMinCostPath.findShortestPath(gMatrix,startPoint.y,startPoint.x,endPoint.y,endPoint.x).getPath();
        // 转换为屏幕坐标（用于绘制）
        optimizedPathScreen = convertPathToScreenPoints(path);

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

    private List<Node> PathToNodes(List<int[]> path) {
        List<Node> nodes = new ArrayList<>();
        for (int[] point :path){
            Node cur = new Node(point[0], point[1]);
            nodes.add(cur);
        }
        return nodes;
    }

    private List<int[]> NodesToPath(List<Node> nodes) {
        List<int[]> path = new ArrayList<>();
        for (Node node :nodes) {
            int[] cur = new int[2];
            cur[0] = node.x;
            cur[1] = node.y;
            path.add(cur);
        }
        return path;
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

        // 键盘事件
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DELETE && isMagneticLassoActive) {
                cancelMagneticLasso();
            }
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
        for (SeedPoint seed : seedPoints) {
            drawSeedMarker(gc, seed);
            System.out.println("[Debug] 绘制一个");
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
            gc.stroke();
        }

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

                gMatrix = ProcessMatrix.findGMatrix(ProcessImage.toRGBMatrix(bufferedImage));
                // 刷新布局
                Platform.runLater(() -> {
                    overlayCanvas.setWidth(imageView.getLayoutBounds().getWidth());
                    overlayCanvas.setHeight(imageView.getLayoutBounds().getHeight());
                    drawOverlay(optimizedPathScreen);
                });
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
            recalculateAllPaths();
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

}