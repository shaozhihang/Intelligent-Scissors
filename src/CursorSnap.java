package src;

public class CursorSnap {

    /**
     * 寻找离鼠标点最近的大梯度点
     * @param mousePoint 鼠标所在的坐标
     * @param gMatrix 梯度矩阵
     * @param snapRate 吸附距离半径（图像高度的千分比）
     * @return 附近梯度值最大的点（若没有则为原鼠标点）
     */
    public int[] findSnapPoint(int[] mousePoint, double[][] gMatrix, int snapRate) {
        int snapRadius = (int) (snapRate * gMatrix.length / 1000.0);

        int maxX = mousePoint[0];
        int maxY = mousePoint[1];
        double maxGradient = gMatrix[mousePoint[0]][mousePoint[1]];

        for (int y = Math.max(0, mousePoint[1] - snapRadius); y <= Math.min(gMatrix.length - 1, mousePoint[1] + snapRadius); y++) {
            for (int x = Math.max(0, mousePoint[0] - snapRadius); x <= Math.min(gMatrix[0].length - 1, mousePoint[0] + snapRadius); x++) {
                if (gMatrix[y][x] > maxGradient) {
                    maxGradient = gMatrix[y][x];
                    maxX = x;
                    maxY = y;
                }
            }
        }

        return new int[]{maxX, maxY};
    }
}
