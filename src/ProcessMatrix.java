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

    private static int findIx(int[][] matrix, int x, int y) {
        return findIxIy(matrix, x, y, Sx); // 交换x,y参数
    }

    private static int findIy(int[][] matrix, int x, int y) {
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
