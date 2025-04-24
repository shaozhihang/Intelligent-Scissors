/*
* 从文件夹读取图片文件，转化为java中的对象
* */
import javax.imageio.ImageIO;
import java.io.FileInputStream;
import java.io.IOException;


public class LoadImage {
    public Image load(String imgPathStr) throws IOException {
        String[] pPath = imgPathStr.split("\\.");
        String imgType = pPath[pPath.length-1];
        return new Image(imgType, ImageIO.read(new FileInputStream(imgPathStr)));
    }
}
