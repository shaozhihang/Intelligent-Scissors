package src;

/**
 * @author 邵之航
 * @version 1.0
 * @apiNote 计算边权，计算最短路
 */
public class ComputeMinCostPath {

     /**
     * 计算(x1,y1)->(x2,y2)的边权，要求两点相邻或对角线
     * @param matrix RGB矩阵
     * @param x1 起点横坐标
     * @param y1 起点纵坐标
     * @param x2 终点横坐标
     * @param y2 终点纵坐标
     * @return 边权权值
     */
    public static double costByG(int[][] matrix, int x1, int y1, int x2, int y2) {
        double[][] gMatrix = ProcessMatrix.findGMatrix(matrix, matrix.length, matrix[0].length);
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



}

