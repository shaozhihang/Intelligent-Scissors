package src;

import java.lang.reflect.Array;

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
                if (xIndex >= 0 && xIndex < height && yIndex >= 0 && yIndex < width) {
                    I += matrix[xIndex][yIndex] * S[i + 1][j + 1];
                }
                //若在边界上则用零像素填充，故可以不写
            }
        }
        return I;
    }

    private static int findIx(int[][] matrix, int x, int y) {
        return findIxIy(matrix, x, y, Sx);
    }

    private static int findIy(int[][] matrix, int x, int y) {
        return findIxIy(matrix, x, y, Sy);
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

    /**
     * int矩阵的转置
     * @param matrix 待转置的矩阵
     * @return 转置之后的矩阵
     */
    public static int[][] convertIntMatrix(int[][] matrix) {
        int height = matrix.length;//原矩阵的高度是新矩阵的宽度
        int width = matrix[0].length;//原矩阵的宽度是新矩阵的高度

        int[][] res =  new int[width][height];

        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                res[i][j] = matrix[j][i]; // 行列互换
            }
        }
        return res;
    }

    /**
     * double矩阵的转置
     * @param matrix 待转置的矩阵
     * @return 转置之后的矩阵
     */
    public static double[][] convertDoubleMatrix(double[][] matrix) {
        int height = matrix.length;//原矩阵的高度是新矩阵的宽度
        int width = matrix[0].length;//原矩阵的宽度是新矩阵的高度

        double[][] res =  new double[width][height];

        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                res[i][j] = matrix[j][i]; // 行列互换
            }
        }
        return res;
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

        int[][] mat2 = convertIntMatrix(matrix);
        for(int i = 0; i < mat2.length; i++) {
            for(int j = 0; j < mat2[0].length; j++) {
                System.out.print(mat2[i][j] + " ");
            }
            System.out.println();
        }
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
