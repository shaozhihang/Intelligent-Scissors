package src;

public class SeedPoint {
    private Node node;// 坐标属性
    private int x;
    private int y;

    public SeedPoint(Node node) {
        this.node = node;
        x = node.x;
        y = node.y;
    }

    public SeedPoint(int x, int y) {
        this.x = x;
        this.y = y;
        this.node = new Node(x,y);
    }

    public Node getNode() {
        return node;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }
    // 梯度属性
    // ......
}
