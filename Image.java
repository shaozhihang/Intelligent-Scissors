import java.awt.image.BufferedImage;

public class Image extends BufferedImage{
    private ImageType typeStr;
    private BufferedImage imgData;



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