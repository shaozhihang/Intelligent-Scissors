package src;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

public class Image extends BufferedImage{
    private ImageType typeStr;
    private BufferedImage imgData;


    /**
     * Image对象的构造方法
     * @param imgType 图像文件的类型，如jpg、png等小写的字符串
     * @param imgData 图像的BufferedImage对象，使用
     */
    public Image(String imgType, BufferedImage imgData){
        super(imgData.getWidth(),imgData.getHeight(),imgData.getType());
        typeStr = switch (imgType) {
            case "jpg" -> ImageType.JPG;
            case "png" -> ImageType.PNG;
            case  "jpeg" -> ImageType.JPEG;
            case "gif" -> ImageType.GIF;
            case  "tiff" -> ImageType.TIFF;
            case "svg" -> ImageType.SVG;
            case  "webp" -> ImageType.WebP;
            case  "heif" -> ImageType.HEIF;
            case "raw" -> ImageType.RAW;
            case "psd" -> ImageType.PSD;
            case "eps" -> ImageType.EPS;
            default -> throw new IllegalStateException("Unexpected value: " + imgType);
        };
        this.imgData = imgData;
    }


    public String type() {
        return switch (typeStr) {
            case JPG -> "jpg";
            case PNG -> "png";
            case JPEG -> "jpeg";
            case GIF -> "gif";
            case TIFF -> "tiff";
            case SVG -> "svg";
            case WebP -> "webp";
            case HEIF -> "heif";
            case RAW -> "raw";
            case PSD -> "psd";
            case EPS -> "eps";
        };
    }

    public static void main(String[] args) throws IOException {
        Instant start = Instant.now();
        Image testImg = LoadImage.load("D:\\photo\\校历.jpg");
        File output = new File(("D:\\photo\\测试.png"));
        ImageIO.write(testImg.imgData,"png",output);
        Duration dur = Duration.between(start,Instant.now());
        System.out.println(output.getAbsolutePath()+" haoshi:"+dur.toMillisPart()+"毫秒");
    }

}

enum ImageType {
    JPG,
    PNG,
    JPEG,
    GIF,
    TIFF,
    SVG,
    WebP,
    HEIF,
    RAW,
    PSD,
    EPS
}