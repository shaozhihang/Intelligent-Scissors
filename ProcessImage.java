import java.awt.image.DataBufferInt;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
* 图像转换为图结构
每个像素视为图中的一个节点，每个节点与其8邻域像素通过“链接”连接。

计算梯度：
使用Sobel算子（水平和垂直方向）计算每个像素的梯度分量，通过公式转换为成本函数。

链接成本与梯度成反比：梯度越高（边缘越强），成本越低。
* */
public class ProcessImage {
    /// 把图像转换为像素点矩阵
    /// @param image 原始图片对象
    /// @return 二维int类型数组，表示传入图片的像素点矩阵
    public static int[][] toRGBMatrix(Image image){
        int width = image.getWidth();
        int height = image.getHeight();
        int[][] pixelMatrix = new int[width][height];

        // 获取像素数据的一维数组
        int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();

        // 将一维数组转换为二维矩阵（按行优先存储）
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                pixelMatrix[x][y] = pixels[index];
            }
        }
        return pixelMatrix;
    }

    /**
     * 把图像转换为图结构
     * @param image 要处理的Image类型图像
     * @return 一个包含节点与其所连接的边们的映射列表
     */
    public static Map<Node, List<Edge>> toGraph(Image image) {
        return ImageGraph.buildGraph(image);
    }




}

