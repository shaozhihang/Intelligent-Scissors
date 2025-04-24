import java.util.*;

/**
 * @author 石一凡
 * @描述: 包含属性x，y
 * <p>{@code x}: 节点的横坐标</p>
 * <p>{@code y}: </p>
 */
class Node {
    int x, y;
    double distance = Double.POSITIVE_INFINITY;
    List<Edge> edges = new ArrayList<>();

    Node(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Node node = (Node) o;
        return x == node.x && y == node.y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }
}

/**
 * @author 石一凡
 * @描述:
 * <p>包含属性target与weight
 * <p>{@code target}: 该边另一头的节点
 * <p>{@code weight}: 该边的权重，由灰度值差计算
 */
class Edge {
    Node target;
    double weight;

    Edge(Node target, double weight) {
        this.target = target;
        this.weight = weight;
    }
}

public class ImageGraph {
    private static final int[][] DIRS = {
            {-1, -1}, {-1, 0}, {-1, 1},
            {0, -1},          {0, 1},
            {1, -1},  {1, 0}, {1, 1}
    };

    protected static Map<Node, List<Edge>> buildGraph(Image image) {
        int width = image.getWidth();
        int height = image.getHeight();
        Map<Node, List<Edge>> graph = new HashMap<>();
        Node[][] nodes = new Node[width][height];

        // 初始化所有节点
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                nodes[x][y] = new Node(x, y);
                graph.put(nodes[x][y], new ArrayList<>());
            }
        }

        // 添加边
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                Node current = nodes[x][y];
                for (int[] dir : DIRS) {
                    int nx = x + dir[0];
                    int ny = y + dir[1];
                    if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                        double weight = calculateWeight(image, x, y, nx, ny);
                        current.edges.add(new Edge(nodes[nx][ny], weight));
                    }
                }
            }
        }
        return graph;
    }

    private static double calculateWeight(Image image, int x1, int y1, int x2, int y2) {
        int rgb1 = image.getRGB(x1, y1);
        int rgb2 = image.getRGB(x2, y2);
        // 示例：使用灰度差作为权重
        double gray1 = (getRed(rgb1) * 0.3 + getGreen(rgb1) * 0.59 + getBlue(rgb1) * 0.11);
        double gray2 = (getRed(rgb2) * 0.3 + getGreen(rgb2) * 0.59 + getBlue(rgb2) * 0.11);
        return Math.abs(gray1 - gray2) + 1e-6; // 避免零权重
    }

    // 提取RGB分量
    private static int getRed(int rgb) { return (rgb >> 16) & 0xFF; }
    private static int getGreen(int rgb) { return (rgb >> 8) & 0xFF; }
    private static int getBlue(int rgb) { return rgb & 0xFF; }
}