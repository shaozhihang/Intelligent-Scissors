package src;

/**
 * @author 邵之航
 * @version 1.1
 * @apiNote 该类用于处理像素矩阵，计算每个像素点的梯度值和归一化处理后的f_G值矩阵
 */
public class ProcessMatrix {
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
     * 使用Sx Kernel计算像素点的Ix值
     * @param matrix 像素矩阵
     * @param width 水平宽度
     * @param height 垂直高度
     * @param x 像素点横坐标
     * @param y 像素点纵坐标
     * @return 像素点的Ix值
     */
    public static int findIx(int[][] matrix, int width, int height, int x, int y) {
        int Ix = 0;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                int xIndex = x + i;
                int yIndex = y + j;
                if (xIndex >= 0 && xIndex < width && yIndex >= 0 && yIndex < height) {
                    Ix += matrix[yIndex][xIndex] * Sx[i + 1][j + 1];
                }
                //若在边界上则用零像素填充，故可以不写
            }
        }
        return Ix;
    }

    /**
     * 使用Sy Kernel计算像素点的Iy值
     * @param matrix 像素矩阵
     * @param width 水平宽度
     * @param height 垂直高度
     * @param x 像素点横坐标
     * @param y 像素点纵坐标
     * @return 像素点的Ix值
     */
    public static int findIy(int[][] matrix, int width, int height, int x, int y) {
        int Iy = 0;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                int xIndex = x + i;
                int yIndex = y + j;
                if (xIndex >= 0 && xIndex < width && yIndex >= 0 && yIndex < height) {
                    Iy += matrix[yIndex][xIndex] * Sy[i + 1][j + 1];
                }
                //若在边界上则用零像素填充，故可以不写
            }
        }
        return Iy;
    }


    /**
     * 计算G（梯度值）矩阵
     * @param matrix 像素矩阵
     * @param width 水平宽度
     * @param height 垂直高度
     * @return G矩阵
     */
    public static double[][] findGMatrix(int[][] matrix, int width, int height) {
        double[][] gMatrix = new double[width][height];

        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                int Ix = findIx(matrix, width, height, i, j);
                int Iy = findIy(matrix, width, height, i, j);
                gMatrix[i][j] = Math.sqrt(Ix * Ix + Iy * Iy);
            }
        }

        return gMatrix;
    }

    /**
     * 把G矩阵进行归一化，得出归一化处理后的f_G值矩阵，公式为f_G = (G_max - G) / G_max
     * @param gMatrix G矩阵
     * @param width 水平宽度
     * @param height 垂直高度
     * @return 归一化处理后的f_G值矩阵
     */
    public static double[][] findFgMatrix(double[][] gMatrix, int width, int height) {
        double[][] fgMatrix = new double[width][height];
        double maxG = 0;

        // 寻找maxG
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                if (gMatrix[i][j] > maxG) {
                    maxG = gMatrix[i][j];
                }
            }
        }

        // 归一化处理
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                fgMatrix[i][j] = 1 - gMatrix[i][j] / maxG;
            }
        }

        return fgMatrix;
    }
    public static void main(String[] args) {
        // 测试代码
        int[][] matrix = {
                {0, 0, 0, 0, 0},
                {0, 1, 2, 3, 0},
                {0, 4, 5, 6, 0},
                {0, 7, 8, 9, 0},
                {0, 0, 0, 0, 0}
        };
        double[][] gMatrix = findGMatrix(matrix, matrix.length, matrix[0].length);
        double[][] fgMatrix = findFgMatrix(gMatrix, matrix.length, matrix[0].length);
        for (int i = 0; i < gMatrix.length; i++) {
            for (int j = 0; j < gMatrix[i].length; j++) {
                System.out.print(gMatrix[i][j] + " ");
            }
            System.out.println();
        }

        System.out.println();
        for (int i = 0; i < fgMatrix.length; i++) {
            for (int j = 0; j < fgMatrix[i].length; j++) {
                System.out.print(fgMatrix[i][j] + " ");
            }
            System.out.println();
        }
    }
}
