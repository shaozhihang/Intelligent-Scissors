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
     * @param x 像素点纵坐标
     * @param y 像素点横坐标
     * @return 像素点的Ix值
     */
    public static int findIx(int[][] matrix, int x, int y) {
        int Ix = 0;
        int height = matrix.length;
        int width = matrix[0].length;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                int xIndex = x + i;
                int yIndex = y + j;
                if (xIndex >= 0 && xIndex < height && yIndex >= 0 && yIndex < width) {
                    Ix += matrix[xIndex][yIndex] * Sx[i + 1][j + 1];
                }
                //若在边界上则用零像素填充，故可以不写
            }
        }
        return Ix;
    }

    /**
     * 使用Sy Kernel计算像素点的Iy值
     * @param matrix 像素矩阵
     * @param x 像素点纵坐标
     * @param y 像素点横坐标
     * @return 像素点的Iy值
     */
    public static int findIy(int[][] matrix, int x, int y) {
        int Iy = 0;
        int height = matrix.length;
        int width = matrix[0].length;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                int xIndex = x + i;
                int yIndex = y + j;
                if (xIndex >= 0 && xIndex < height && yIndex >= 0 && yIndex < width) {
                    Iy += matrix[xIndex][yIndex] * Sy[i + 1][j + 1];
                }
                //若在边界上则用零像素填充，故可以不写
            }
        }
        return Iy;
    }


    /**
     * 计算G（梯度值）矩阵
     * @param matrix 像素矩阵
     * @return G矩阵
     */
    public static double[][] findGMatrix(int[][] matrix) {
        int height = matrix.length;
        int width = matrix[0].length;
        double[][] gMatrix = new double[height][width];

        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                int Ix = findIx(matrix, i, j);
                int Iy = findIy(matrix, i, j);
                gMatrix[i][j] = Math.sqrt(Ix * Ix + Iy * Iy);
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
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                if (gMatrix[i][j] > maxG) {
                    maxG = gMatrix[i][j];
                }
            }
        }

        // 归一化处理
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
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
                {0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0}
        };
        double[][] gMatrix = findGMatrix(matrix);
        double[][] fgMatrix = findFgMatrix(gMatrix);
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
