package com.hape.photogallery.service;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageOutputStream;

import com.hape.photogallery.exception.InvalidFileTypeException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ImageProcessingService {

    private static final int THUMBNAIL_WIDTH = 400;

    private final ExifService exifService;
    private final Path uploadDir;

    public ImageProcessingService(ExifService exifService,
                                  @Value("${photo.upload-dir:uploads}") String uploadDir) {
        this.exifService = exifService;
        this.uploadDir = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public void validateImageMagicBytes(InputStream in) throws IOException {
        byte[] header = new byte[12];
        int read = in.read(header);
        if (read <= 0) throw new InvalidFileTypeException("无法识别文件内容，请上传图片文件");
        if (read >= 2 && header[0] == (byte) 0xFF && header[1] == (byte) 0xD8) return;
        if (read >= 4 && header[0] == (byte) 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47) return;
        if (read >= 4 && header[0] == 0x47 && header[1] == 0x49 && header[2] == 0x46 && header[3] == 0x38) return;
        if (read >= 2 && header[0] == 0x42 && header[1] == 0x4D) return;
        if (read >= 12 && header[0] == 0x52 && header[1] == 0x49 && header[2] == 0x46 && header[3] == 0x46
                && header[8] == 0x57 && header[9] == 0x45 && header[10] == 0x42 && header[11] == 0x50) return;
        throw new InvalidFileTypeException("文件格式不支持，请上传常见的图片文件");
    }

    public void autoRotateIfNeeded(Path path) throws IOException {
        int orientation = exifService.getOrientation(path);
        int degrees = switch (orientation) {
            case 3 -> 180;
            case 6 -> 90;
            case 8 -> 270;
            default -> 0;
        };
        if (degrees > 0) {
            BufferedImage img = ImageIO.read(path.toFile());
            if (img == null) return;
            BufferedImage rotated = rotateImage(img, degrees);
            String format = getFormat(path);
            ImageIO.write(rotated, format, path.toFile());
        }
    }

    @Value("${photo.watermark.font:SansSerif}")
    private String watermarkFont;

    @Value("${photo.watermark.font-size-ratio:40}")
    private float watermarkFontSizeRatio;

    @Value("${photo.watermark.color-alpha:180}")
    private int watermarkColorAlpha;

    public void applyWatermark(Path filePath, String text) throws IOException {
        BufferedImage img = ImageIO.read(filePath.toFile());
        if (img == null) return;

        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        float fontSize = Math.max(14f, img.getWidth() / watermarkFontSizeRatio);
        Font font = new Font(watermarkFont, Font.PLAIN, (int) fontSize);
        g.setFont(font);

        FontMetrics fm = g.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        int padding = (int) (fontSize * 0.6);
        int x = img.getWidth() - textWidth - padding;
        int y = fm.getAscent() + padding;

        g.setColor(new Color(255, 255, 255, watermarkColorAlpha));
        g.drawString(text, x, y);
        g.dispose();

        String format = getFormat(filePath);
        ImageIO.write(img, format, filePath.toFile());
    }

    public void generateThumbnail(Path original, String dateDir, String baseName) throws IOException {
        generateThumbnail(original, dateDir, baseName, THUMBNAIL_WIDTH);
    }

    public void generateThumbnail(Path original, String dateDir, String baseName, int width) throws IOException {
        BufferedImage image = ImageIO.read(original.toFile());
        if (image == null) return;

        int h = (int) ((double) image.getHeight() / image.getWidth() * width);
        boolean hasAlpha = image.getColorModel().hasAlpha();
        int type = hasAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage thumb = new BufferedImage(width, h, type);
        Graphics2D g = thumb.createGraphics();
        if (hasAlpha) {
            g.setComposite(java.awt.AlphaComposite.Clear);
            g.fillRect(0, 0, width, h);
            g.setComposite(java.awt.AlphaComposite.SrcOver);
        }
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(image, 0, 0, width, h, null);
        g.dispose();

        Path thumbDir = width == THUMBNAIL_WIDTH
                ? uploadDir.resolve(dateDir).resolve("thumbnails")
                : uploadDir.resolve(dateDir).resolve("thumbnails").resolve(String.valueOf(width));
        Files.createDirectories(thumbDir);
        Path thumbPath = thumbDir.resolve(baseName);

        java.util.Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (writers.hasNext()) {
            ImageWriter writer = writers.next();
            try {
                ImageWriteParam param = writer.getDefaultWriteParam();
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(0.75f);
                writer.setOutput(new FileImageOutputStream(thumbPath.toFile()));
                writer.write(null, new IIOImage(thumb, null, null), param);
            } finally {
                writer.dispose();
            }
        } else {
            ImageIO.write(thumb, "jpeg", thumbPath.toFile());
        }
    }

    public void generateWebp(Path original, String dateDir, String baseName) {
        String lower = baseName.toLowerCase();
        Path webpDir = uploadDir.resolve(dateDir).resolve("webp");
        try {
            Files.createDirectories(webpDir);
        } catch (IOException e) { return; }
        Path webpPath = webpDir.resolve(baseName + ".webp");

        if (lower.endsWith(".webp")) {
            try { Files.copy(original, webpPath, StandardCopyOption.REPLACE_EXISTING); } catch (IOException ignored) {}
            return;
        }

        try {
            BufferedImage img = ImageIO.read(original.toFile());
            if (img == null) return;
            java.util.Iterator<ImageWriter> writers = ImageIO.getImageWritersByMIMEType("image/webp");
            if (!writers.hasNext()) return;
            ImageWriter writer = writers.next();
            try {
                ImageWriteParam param = writer.getDefaultWriteParam();
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                if (param.canWriteCompressed()) {
                    param.setCompressionType("Lossy");
                    param.setCompressionQuality(0.8f);
                }
                writer.setOutput(new FileImageOutputStream(webpPath.toFile()));
                writer.write(null, new IIOImage(img, null, null), param);
            } finally {
                writer.dispose();
            }
        } catch (IOException e) {
            // WebP generation failure is non-fatal
        }
    }

    public BufferedImage rotateImage(BufferedImage src, int degrees) {
        int w = src.getWidth(), h = src.getHeight();
        boolean swap = degrees == 90 || degrees == 270;
        int nw = swap ? h : w, nh = swap ? w : h;
        BufferedImage dst = new BufferedImage(nw, nh, src.getType() == BufferedImage.TYPE_CUSTOM
                ? BufferedImage.TYPE_INT_RGB : src.getType());
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        if (degrees == 90) {
            g.translate(nw, 0);
            g.rotate(Math.PI / 2);
        } else if (degrees == 180) {
            g.translate(w, h);
            g.rotate(Math.PI);
        } else if (degrees == 270) {
            g.translate(0, nh);
            g.rotate(-Math.PI / 2);
        }
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return dst;
    }

    public BufferedImage mirrorImage(BufferedImage src, boolean horizontal) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage dst = new BufferedImage(w, h, src.getType() == BufferedImage.TYPE_CUSTOM
                ? BufferedImage.TYPE_INT_RGB : src.getType());
        Graphics2D g = dst.createGraphics();
        if (horizontal) {
            g.drawImage(src, w, 0, 0, h, 0, 0, w, h, null);
        } else {
            g.drawImage(src, 0, h, w, 0, 0, 0, w, h, null);
        }
        g.dispose();
        return dst;
    }

    public String getFormat(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".png")) return "PNG";
        if (name.endsWith(".gif")) return "GIF";
        if (name.endsWith(".bmp")) return "BMP";
        if (name.endsWith(".webp")) return "JPEG";
        return "JPEG";
    }
}
