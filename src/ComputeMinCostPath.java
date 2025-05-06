package src;

import java.util.*;

/**
 * @author 邵之航
 * @version 1.0
 * @apiNote 计算最短路
 */
public class ComputeMinCostPath {

    // 表示矩阵上的一个节点
    static class Node implements Comparable<Node> {
        int x, y;
        double distance;
        Node parent;

        public Node(int x, int y, double distance, Node parent) {
            this.x = x;
            this.y = y;
            this.distance = distance;
            this.parent = parent;
        }

        @Override
        public int compareTo(Node other) {
            return Double.compare(this.distance, other.distance);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            Node node = (Node) obj;
            return x == node.x && y == node.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }
    }

    // 8个可能的移动方向
    private static final int[][] DIRECTIONS = {
            {-1, -1}, {-1, 0}, {-1, 1},
            {0, -1},          {0, 1},
            {1, -1},  {1, 0}, {1, 1}
    };

     /**
     * 计算(x1,y1)->(x2,y2)的边权，要求两点相邻或对角线
     * @param gMatrix 梯度值矩阵
     * @param x1 起点纵坐标
     * @param y1 起点横坐标
     * @param x2 终点纵坐标
     * @param y2 终点横坐标
     * @return 边权权值
     */
    public static double costByG(double[][] gMatrix, int x1, int y1, int x2, int y2) {
        if(Math.abs( x1 - x2 ) + Math.abs( y1 - y2 ) == 1) { // 两点相邻
            return 1 / ( gMatrix[x2][y2] + 1 );
        }
        else if(Math.abs( x1 - x2 ) == 1 && Math.abs( y1 - y2 ) == 1) { //两点对角线
            return 1 / ( gMatrix[x2][y2] + 1 ) / Math.sqrt(2);
        }
        else { //两点不相连
            return 1e6;
        }
    }


    /**
     * 在梯度矩阵上计算最短路
     * @param gMatrix 梯度矩阵
     * @param startX 起点横坐标
     * @param startY 起点纵坐标
     * @param endX 终点横坐标
     * @param endY 终点纵坐标
     * @return 最短路径，用PathResult类存储，包含路径长度和路径元素
     */
    public static PathResult findShortestPath(double[][] gMatrix, int startX, int startY, int endX, int endY) {
        int height = gMatrix.length;
        int width = gMatrix[0].length;

        // 检查边界
        if (startX < 0 || startX >= height || startY < 0 || startY >= width ||
                endX < 0 || endX >= height || endY < 0 || endY >= width) {
            return new PathResult(-1, Collections.emptyList());
        }

        // 优先队列用于Dijkstra算法
        PriorityQueue<Node> queue = new PriorityQueue<>();
        // 距离矩阵
        double[][] distances = new double[height][width];
        // 初始化距离为无穷大
        for (double[] row : distances) {
            Arrays.fill(row, Double.MAX_VALUE);
        }

        // 起点
        Node startNode = new Node(startX, startY, 0, null);
        queue.add(startNode);
        distances[startX][startY] = 0;

        while (!queue.isEmpty()) {
            Node current = queue.poll();//取出队头并移除

            // 如果到达终点
            if (current.x == endX && current.y == endY) {
                return buildPathResult(current, distances[endX][endY]);
            }

            // 检查所有8个方向
            for (int[] dir : DIRECTIONS) {
                int newX = current.x + dir[0];
                int newY = current.y + dir[1];

                // 检查边界
                if (newX >= 0 && newX < height && newY >= 0 && newY < width) {
                    // 计算新距离
                    double edgeWeight = costByG(gMatrix, current.x, current.y, newX, newY); // 可以根据需要调整
                    double newDistance = current.distance + edgeWeight;

                    // 如果找到更短路径
                    if (newDistance < distances[newX][newY]) {
                        distances[newX][newY] = newDistance;
                        Node neighbor = new Node(newX, newY, newDistance, current);
                        queue.add(neighbor);
                    }
                }
            }
        }

        // 如果没有找到路径
        return new PathResult(-1, Collections.emptyList());
    }

    private static PathResult buildPathResult(Node endNode, double distance) {
        List<int[]> path = new ArrayList<>();
        Node current = endNode;

        // 反向构建路径
        while (current != null) {
            path.add(new int[]{current.x, current.y});
            current = current.parent;
        }

        // 反转路径使其从起点到终点
        Collections.reverse(path);

        return new PathResult(distance, path);
    }

    // 用于返回结果
    static class PathResult {
        double distance;
        List<int[]> path;

        public PathResult(double distance, List<int[]> path) {
            this.distance = distance;
            this.path = path;
        }

        public double getDistance() {
            return distance;
        }

        public List<int[]> getPath() {
            return path;
        }

        public void print() {
            System.out.println("Distance: " + distance);
            for(int i = 0; i < path.size(); i++) {
                System.out.println(path.get(i)[0] + " " + path.get(i)[1]);
            }
        }
    }

    public static void main(String[] args) {
        int[][] matrix = {
                {3, 3, 3, 3, 3},
                {0, 0, 0, 3, 3},
                {0, 0, 0, 0, 3},
                {0, 0, 0, 0, 3},
                {0, 3, 3, 3, 3}
        };
        double[][] gMatrix = ProcessMatrix.findGMatrix(matrix);
        for (int i = 0; i < gMatrix.length; i++) {
            for (int j = 0; j < gMatrix[0].length; j++) {
                System.out.print(gMatrix[i][j] + " ");
            }
            System.out.println();
        }
        PathResult path = findShortestPath(gMatrix, 0, 0, 4, 1);
        path.print();
    }

}

