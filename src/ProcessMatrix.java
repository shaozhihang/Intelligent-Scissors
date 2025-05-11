package src;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * @author 邵之航
 * @version 1.1
 * @apiNote 计算矩阵中的f_Z,f_G,f_D,用于计算边权
 */
public class ProcessMatrix {

    //=======================计算拉普拉斯过零点f_Z==============================

    final static double[][] LAPLACIAN_KERNEL = {
            {0, 1, 0},
            {1, -4, 1},
            {0, 1, 0}
    };

    public static double[][] findLaplacian(int[][] matrix) {
        int height = matrix.length;
        int width = matrix[0].length;
        double[][] laplacian = new double[height][width];
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                double sum = 0;
                //3*3领域与核的点积
                for (int i = -1; i <= 1; i++) {
                    for (int j = -1; j <= 1; j++) {
                        sum += matrix[y + j][x + i] * LAPLACIAN_KERNEL[j + 1][i + 1];
                    }
                }
                laplacian[y][x] = sum;
            }
        }
        for (int x = 0; x < width; x++) {
            laplacian[0][x] = 0;
            laplacian[height - 1][x] = 0;
        }
        for (int y = 0; y < height; y++) {
            laplacian[y][0] = 0;
            laplacian[y][width - 1] = 0;
        }
        return laplacian;
    }

    /**
     * 计算拉普拉斯过零点矩阵
     * @param matrix RGB矩阵
     * @return zeroCrossing矩阵
     */
    public static boolean[][] computeZeroCrossing(int[][] matrix) {
        int height = matrix.length;
        int width = matrix[0].length;
        boolean[][] zeroCrossing = new boolean[height][width];//是否为过零点

        double[][] laplacian = findLaplacian(matrix);

        // 检测零交叉点（简化逻辑）
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                boolean isZeroCross = false;
                // 检查周围像素符号变化
                int[][] offsets = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
                for (int[] offset: offsets) {
                    int newY = y + offset[0];
                    int newX = x + offset[1];
                    if(laplacian[y][x] * laplacian[newY][newX] < 0) {
                        if(Math.abs(laplacian[y][x]) < Math.abs(laplacian[newY][newX])) {
                            isZeroCross = true;
                            break;
                        }
                    }
                }
                zeroCrossing[y][x] = isZeroCross;
            }
        }

        return zeroCrossing;
    }

    public static double computeFz(boolean[][] zeroCrossing, int qx, int qy) {
        if(zeroCrossing[qx][qy]) {
            return 0;
        }
        else {
            return 1;
        }
    }

    //=======================计算梯度幅值f_G===================================

    // Sobel Kernels for Ix and Iy
    final static int[][] Sx = {
            {-1, 0, 1},
            {-2, 0, 2},
            {-1, 0, 1}
    };
    final static int[][] Sy = {
            {-1, -2, -1},
            {0, 0, 0},
            {1, 2, 1}
    };


    /**
     * 使用Sx或Sy Kernel计算像素点的Ix或Iy值
     * @param matrix 像素矩阵
     * @param x 像素点横坐标
     * @param y 像素点纵坐标
     * @param S 计算Ix用Sx，计算Iy用Sy
     * @return 像素点的Ix或Iy值
     */
    private static int findIxIy(int[][] matrix, int x, int y, int[][] S) {
        int I = 0;
        int height = matrix.length;
        int width = matrix[0].length;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                int xIndex = x + i;
                int yIndex = y + j;
                if (xIndex >= 0 && xIndex < width && yIndex >= 0 && yIndex < height) {
                    I += matrix[yIndex][xIndex] * S[j + 1][i + 1];
                }
                //若在边界上则用零像素填充，故可以不写
            }
        }
        return I;
    }

    public static int findIx(int[][] matrix, int x, int y) {
        return findIxIy(matrix, x, y, Sx); // 交换x,y参数
    }

    public static int findIy(int[][] matrix, int x, int y) {
        return findIxIy(matrix, x, y, Sy); // 交换x,y参数
    }


    /**
     * 计算G（梯度值）矩阵
     * @param matrix 像素矩阵
     * @return G矩阵
     */
    public static double[][] findGMatrix(int[][] matrix) {
        int imageHeight = matrix.length;
        int imageWidth = matrix[0].length;
        double[][] gMatrix = new double[imageHeight][imageWidth];

        for (int y = 0; y < imageHeight; y++) {      // 图像y坐标
            for (int x = 0; x < imageWidth; x++) {   // 图像x坐标
                int Ix = findIx(matrix, x, y);      // 传入正确的x,y顺序
                int Iy = findIy(matrix, x, y);
                gMatrix[y][x] = Math.sqrt(Ix * Ix + Iy * Iy); // y行x列
            }
        }
        return gMatrix;
    }

    /**
     * 把G矩阵进行归一化，得出归一化处理后的f_G值矩阵，公式为f_G = (G_max - G) / G_max
     * @param gMatrix G矩阵
     * @return 归一化处理后的f_G值矩阵
     */
    public static double[][] findFgMatrix(double[][] gMatrix) {
        int height = gMatrix.length;
        int width = gMatrix[0].length;
        double[][] fgMatrix = new double[height][width];
        double maxG = 0;

        // 寻找maxG
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (gMatrix[y][x] > maxG) {
                    maxG = gMatrix[y][x];
                }
            }
        }

        // 归一化处理
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                fgMatrix[y][x] = 1 - gMatrix[y][x] / maxG;
            }
        }

        return fgMatrix;
    }

    public static double computeFg(double[][] fgMatrix, int px, int py, int qx, int qy) {
        //邻域方向判断
        boolean isDiagonal = (Math.abs(px - qx) == 1 && Math.abs(py - qy) == 1);
        double scale = isDiagonal ? 1.0 / Math.sqrt(2) : 1.0;

        return fgMatrix[py][px] * scale;
    }


    //=======================计算梯度方向f_D===================================

    // 计算梯度方向特征
    public static double computeFd(double[][] Ix, double[][] Iy, double[][] G,
                                    int px, int py, int qx, int qy) {
        // 梯度方向单位向量D(p)和D(q)
        double[] Dp = {Iy[py][px] / G[py][px], -Ix[py][px] / G[py][px]};
        double[] Dq = {Iy[qy][qx] / G[qy][qx], -Ix[qx][qy] / G[qy][qx]};

        // 链接向量L(p,q)
        int dx = qx - px;
        int dy = qy - py;
        double[] L = {dy, dx};

        // 计算方向一致性得分
        double dotP = Dp[0] * L[0] + Dp[1] * L[1];
        double dotQ = L[0] * Dq[0] + L[1] * Dq[1];
        double dp = Math.acos(dotP / (Math.hypot(Dp[0], Dp[1]) * Math.hypot(L[0], L[1])));
        double dq = Math.acos(dotQ / (Math.hypot(L[0], L[1]) * Math.hypot(Dq[0], Dq[1])));

        return (dp + dq) / Math.PI;
    }

    // 在ProcessMatrix类中添加梯度缓存机制
    private static Map<String, double[][]> gradientCache = new HashMap<>();

    public static double[][] getGMatrix(int[][] matrix) {
        String key = Arrays.deepToString(matrix);
        if (!gradientCache.containsKey(key)) {
            gradientCache.put(key, findGMatrix(matrix));
        }
        return gradientCache.get(key);
    }

    public static double[][] calculateGradientDirections(int[][] matrix) {
        int height = matrix.length;
        int width = matrix[0].length;
        double[][] directions = new double[height][width];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int Ix = findIx(matrix, x, y);
                int Iy = findIy(matrix, x, y);
                // 计算梯度方向角度（弧度），范围[-π, π]
                directions[y][x] = Math.atan2(Iy, Ix);
            }
        }
        return directions;
    }

    public static double findMaxGradient(double[][] gMatrix) {
        return Arrays.stream(gMatrix)
                .flatMapToDouble(Arrays::stream)
                .max()
                .orElse(1.0);
    }

    public static void main(String[] args) {
        // 测试代码
        int[][] matrix = {
                {0, 1, 1, 1, 1, 1, 1, 0},
                {0, 1, 1, 1, 1, 1, 1, 0},
                {0, 1, 1, 1, 1, 1, 1, 0},
                {0, 1, 1, 1, 1, 1, 1, 0},
                {0, 1, 1, 1, 2, 1, 1, 0},
                {0, 1, 1, 1, 2, 1, 1, 0},
                {0, 1, 1, 1, 2, 1, 1, 0},
                {0, 1, 1, 1, 2, 1, 1, 0}
        };
        boolean[][] fzMatrix = computeZeroCrossing(matrix);
        for(int y = 0; y < fzMatrix.length; y++) {
            for(int x = 0; x < fzMatrix[0].length; x++) {
                System.out.print(fzMatrix[y][x] + " ");
            }
            System.out.println();
        }

        double[][] gMatrix = findGMatrix(matrix);
        double[][] fgMatrix = findFgMatrix(gMatrix);
        for (int y = 0; y < gMatrix.length; y++) {
            for (int x = 0; x < gMatrix[0].length; x++) {
                System.out.print(gMatrix[y][x] + " ");
            }
            System.out.println();
        }

        System.out.println();
        for (int y = 0; y < fgMatrix.length; y++) {
            for (int x = 0; x < fgMatrix[y].length; x++) {
                System.out.print(fgMatrix[y][x] + " ");
            }
            System.out.println();
        }
    }
}
