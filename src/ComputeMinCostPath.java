package src;

import java.util.*;

/**
 * @author 邵之航
 * @version 1.1
 * @apiNote 计算最短路
 */
public class ComputeMinCostPath {

    // 表示矩阵上的一个节点
    static class Node implements Comparable<Node> {
        int x, y;
        double distance;//到seed point距离
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
            return Double.POSITIVE_INFINITY;
        }
    }



    /**
     * 在梯度矩阵上计算最短路
     * @param matrix RGB矩阵
     * @param startX 起点横坐标
     * @param startY 起点纵坐标
     * @param endX 终点横坐标
     * @param endY 终点纵坐标
     * @return 最短路径，用PathResult类存储，包含路径长度和路径元素
     */
    public static PathResult findShortestPath(int[][] matrix, int startX, int startY, int endX, int endY) {


        //初始化
        double[][] gMatrix = ProcessMatrix.findGMatrix(matrix);
        double[][] fgMatrix = ProcessMatrix.findFgMatrix(gMatrix);
        double[][] IxMatrix = new double[matrix.length][matrix[0].length];
        double[][] IyMatrix = new double[matrix.length][matrix[0].length];
        boolean[][] zeroCrossing = ProcessMatrix.computeZeroCrossing(matrix);
        for (int y = 0; y < matrix.length; y++) {      // 图像y坐标
            for (int x = 0; x < matrix[0].length; x++) {   // 图像x坐标
                int Ix = ProcessMatrix.findIx(matrix, x, y);      // 传入正确的x,y顺序
                int Iy = ProcessMatrix.findIy(matrix, x, y);
                IxMatrix[y][x] = Ix;
                IyMatrix[y][x] = Iy;
            }
        }

        // 参数校验增强
        if (fgMatrix.length == 0 || fgMatrix[0].length == 0) {
            throw new IllegalArgumentException("Invalid gradient matrix");
        }

        int rows = fgMatrix.length;
        int cols = fgMatrix[0].length;

        // 坐标转换（适配图像坐标系）
        int startRow = startY; // X->Col, Y->Row
        int startCol = startX;
        int endRow = endY;
        int endCol = endX;

        // 边界检查
        if (startRow < 0 || startRow >= rows || startCol < 0 || startCol >= cols ||
                endRow < 0 || endRow >= rows || endCol < 0 || endCol >= cols) {
            System.out.println("[Debug] 边界检查失败");
            return new PathResult(-1, Collections.emptyList());
        }

        // 使用Fibonacci堆优化优先级队列
        PriorityQueue<Node> queue = new PriorityQueue<>();
        double[][] distances = new double[rows][cols];
        boolean[][] visited = new boolean[rows][cols];

        // 初始化
        for (double[] row : distances) Arrays.fill(row, Double.MAX_VALUE);
        distances[startRow][startCol] = 0;
        queue.add(new Node(startRow, startCol, 0, null));

        while (!queue.isEmpty()) {
            System.out.println(queue.size());
            Node current = queue.poll();

            // 跳过已处理节点
            if (visited[current.x][current.y]) continue;
            visited[current.x][current.y] = true;

            // 提前终止
            if (current.x == endRow && current.y == endCol) {
                return buildPathResult(current, distances[endRow][endCol]);
            }

            // 方向遍历
            for (int[] dir : DIRECTIONS) {
                int newRow = current.x + dir[0];
                int newCol = current.y + dir[1];

                if (newRow >= 0 && newRow < rows && newCol >= 0 && newCol < cols) {
                    // 动态调整代价计算
                    double moveCost = calculateDynamicCost(fgMatrix, current, newRow, newCol);
                    //double moveCost = computeCost(gMatrix, fgMatrix, IxMatrix, IyMatrix, zeroCrossing, current, newRow, newCol);
                    System.out.println("Cost: " + moveCost);

                    double newDistance = current.distance + moveCost;

                    if (newDistance < distances[newRow][newCol]) {
                        distances[newRow][newCol] = newDistance;
                        queue.add(new Node(newRow, newCol, newDistance, current));
                    }
                }
            }
        }
        return new PathResult(-1, Collections.emptyList());
    }

    private static double computeCost(double[][] gMatrix, double[][] fgMatrix, double[][] IxMatrix, double[][] IyMatrix, boolean[][] zeroCrossing,
                                      Node current, int newRow, int newCol) {

        int px = current.x;
        int py = current.y;
        int qx = newRow;
        int qy = newCol;

        double res =  0.43 * ProcessMatrix.computeFz(zeroCrossing, qx, qy)
                + 0.43 * ProcessMatrix.computeFd(IxMatrix, IyMatrix, gMatrix, px, py, qx, qy)
                + 0.14 * ProcessMatrix.computeFg(fgMatrix, px, py, qx, qy);
        if(Double.isNaN(res)) return 1;

        return res ;
    }

    private static double calculateDynamicCost(double[][] fgMatrix, Node current, int newRow, int newCol) {
        // 基础边缘代价
        double baseCost = fgMatrix[newRow][newCol] + 0.1;

        // 方向连续性惩罚
        double directionPenalty = 1.0;
        if (current.parent != null) {
            int prevDirX = current.x - current.parent.x;
            int prevDirY = current.y - current.parent.y;
            int currDirX = newRow - current.x;
            int currDirY = newCol - current.y;

            // 计算方向变化角度惩罚
            double dotProduct = prevDirX * currDirX + prevDirY * currDirY;
            double prevMagnitude = Math.hypot(prevDirX, prevDirY);
            double currMagnitude = Math.hypot(currDirX, currDirY);
            double cosTheta = dotProduct / (prevMagnitude * currMagnitude);
            directionPenalty = 1.0 + (1.0 - cosTheta) * 0.5;
        }

        // 距离因子
        double distanceFactor = Math.hypot(newRow - current.x, newCol - current.y);

        return baseCost * directionPenalty * distanceFactor;
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
        PathResult path = findShortestPath(matrix, 0, 0, 1, 4);
        path.print();
    }

}

