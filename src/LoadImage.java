package src;
/*
* 从文件夹读取图片文件，转化为java中的对象
* */
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;


public class LoadImage {
    public static BufferedImage load(String imgPath) throws IOException {
        return ImageIO.read(new File(imgPath));
    }
}
