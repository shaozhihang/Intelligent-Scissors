package src;

import javafx.geometry.Point2D;

public class CursorSnap {
    // 动态吸附参数
    private static final int BASE_SNAP_RADIUS = 15;  // 基础吸附半径（像素）
    private static final double EDGE_THRESHOLD = 0.2; // 梯度阈值（归一化值）

    /**
     * 实时计算吸附位置（屏幕坐标 -> 图像坐标 -> 吸附坐标 -> 屏幕坐标）
     * @param screenPoint 当前鼠标屏幕坐标
     * @param gMatrix     预计算的梯度矩阵
     * @param scaleX      水平缩放比例
     * @param scaleY      垂直缩放比例
     * @return 吸附后的屏幕坐标
     */
    public Point2D realtimeSnap(Point2D screenPoint, double[][] gMatrix, double scaleX, double scaleY) {
        // 1. 屏幕坐标转图像坐标
        int imgX = (int) (screenPoint.getX() / scaleX);
        int imgY = (int) (screenPoint.getY() / scaleY);

        // 2. 动态计算吸附半径（基于局部梯度强度）
        int dynamicRadius = calculateDynamicRadius(gMatrix, imgX, imgY);

        // 3. 查找最大梯度点
        int[] snapped = findMaxGradient(gMatrix, imgX, imgY, dynamicRadius);

        // 4. 图像坐标转回屏幕坐标
        return new Point2D(
                snapped[0] * scaleX,
                snapped[1] * scaleY
        );
    }

    // 动态调整吸附半径（根据局部梯度变化率）
    private int calculateDynamicRadius(double[][] gMatrix, int x, int y) {
        double currentGradient = gMatrix[y][x];
        if (currentGradient > EDGE_THRESHOLD) {
            return BASE_SNAP_RADIUS / 2; // 强边缘区域使用小半径
        }
        return BASE_SNAP_RADIUS;
    }

    // 查找局部最大梯度点
    private int[] findMaxGradient(double[][] gMatrix, int centerX, int centerY, int radius) {
        int maxX = centerX;
        int maxY = centerY;
        double maxVal = gMatrix[centerY][centerX];

        for (int y = Math.max(0, centerY - radius);
             y < Math.min(gMatrix.length, centerY + radius);
             y++) {
            for (int x = Math.max(0, centerX - radius);
                 x < Math.min(gMatrix[0].length, centerX + radius);
                 x++) {
                if (gMatrix[y][x] > maxVal) {
                    maxVal = gMatrix[y][x];
                    maxX = x;
                    maxY = y;
                }
            }
        }
        return new int[]{maxX, maxY};
    }
}