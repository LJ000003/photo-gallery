package com.hape.photogallery.service;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageInputStream;

import com.hape.photogallery.exception.InvalidFileTypeException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ImageProcessingService {

    private static final Logger log = LoggerFactory.getLogger(ImageProcessingService.class);

    private static final int THUMBNAIL_WIDTH = 400;

    private final ExifService exifService;
    private final Path uploadDir;
    private final int maxDecodeDim;

    public ImageProcessingService(ExifService exifService,
                                  @Value("${photo.upload-dir:uploads}") String uploadDir,
                                  @Value("${photo.processing.max-decode-dim:4096}") int maxDecodeDim) {
        this.exifService = exifService;
        this.uploadDir = Path.of(uploadDir).toAbsolutePath().normalize();
        this.maxDecodeDim = maxDecodeDim;
    }

    /**
     * 降采样解码：长边超过 maxDecodeDim（默认 4096）时按比例缩小（单系数两轴同用，保长宽比）。
     * 生产容器 -Xmx448m 下全尺寸解码一张超大图（如 8000px+）即可能 OOM——降采样后最坏
     * 4096²×4B ≈ 67MB/张，2-4 并发消费者可承受。
     * 解码失败/文件损坏 → null（对齐 ImageIO.read 语义，调用方按"确定性失败"处理）。
     * 绝不做全尺寸兜底解码——448MB 堆下兜底 = OOM。
     */
    public BufferedImage decodeCapped(Path file) {
        try (ImageInputStream iis = ImageIO.createImageInputStream(file.toFile())) {
            if (iis == null) return null;
            java.util.Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) return null;
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                int w = reader.getWidth(0);
                int h = reader.getHeight(0);
                if (w <= 0 || h <= 0) return null;
                int factor = Math.max(w, h) > maxDecodeDim
                        ? (int) Math.ceil((double) Math.max(w, h) / maxDecodeDim)
                        : 1;
                ImageReadParam param = reader.getDefaultReadParam();
                param.setSourceSubsampling(factor, factor, 0, 0);
                return reader.read(0, param);
            } catch (IOException | RuntimeException e) {
                return null;
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            return null;
        }
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

    @Value("${photo.watermark.font:SansSerif}")
    private String watermarkFont;

    @Value("${photo.watermark.font-size-ratio:40}")
    private float watermarkFontSizeRatio;

    @Value("${photo.watermark.color-alpha:180}")
    private int watermarkColorAlpha;

    // === 降采样（用于 WebP 和缩略图加速） ===

    private static final int DISPLAY_MAX = 2048;

    public BufferedImage downscaleToDisplay(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        if (w <= DISPLAY_MAX && h <= DISPLAY_MAX) return img;
        double ratio = (double) DISPLAY_MAX / Math.max(w, h);
        int nw = (int) (w * ratio);
        int nh = (int) (h * ratio);
        BufferedImage scaled = new BufferedImage(nw, nh, img.getType() == BufferedImage.TYPE_CUSTOM
                ? BufferedImage.TYPE_INT_RGB : img.getType());
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(img, 0, 0, nw, nh, null);
        g.dispose();
        return scaled;
    }

    // === 基于 BufferedImage 的处理（避免重复解码） ===

    /**
     * 返回旋转后的 BufferedImage，不写磁盘
     */
    public BufferedImage autoRotateIfNeeded(BufferedImage img, Path path) throws IOException {
        int orientation = exifService.getOrientation(path);
        int degrees = switch (orientation) {
            case 3 -> 180;
            case 6 -> 90;
            case 8 -> 270;
            default -> 0;
        };
        if (degrees > 0) {
            return rotateImage(img, degrees);
        }
        return img;
    }

    /**
     * 在原图上绘制水印，返回原图引用
     */
    public BufferedImage applyWatermark(BufferedImage img, String text) {
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
        return img;
    }

    public void generateThumbnail(BufferedImage image, String dateDir, String baseName) throws IOException {
        generateThumbnail(image, dateDir, baseName, THUMBNAIL_WIDTH);
    }

    public void generateThumbnail(BufferedImage image, String dateDir, String baseName, int width) throws IOException {
        if (image.getWidth() <= 0 || image.getHeight() <= 0) {
            throw new IOException("图片尺寸无效: " + image.getWidth() + "x" + image.getHeight());
        }
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
        writeJpeg(thumb, thumbPath(dateDir, baseName, width));
    }

    /**
     * 生成全尺寸 WebP（增强产物，失败不阻塞整条处理链——缩略图已成功，照片仍 DONE）。
     * 注意：webp 编码器为 native 库（webp-imageio），初始化失败可能抛 Error 而非
     * IOException——统一捕 Throwable 并留日志，避免照片因 webp 缺失误报 FAILED
     * （曾与「无 writer 静默 return」两种失败表现互相矛盾）。
     */
    public void generateWebp(BufferedImage img, String dateDir, String baseName) {
        Path webpDir = uploadDir.resolve(dateDir).resolve("webp");
        try {
            Files.createDirectories(webpDir);
        } catch (IOException e) {
            log.warn("无法创建 WebP 目录: {}", webpDir, e);
            return;
        }
        Path webpPath = webpDir.resolve(baseName + ".webp");
        // 原子写：tmp + ATOMIC_MOVE（与 writeAtomic 同构），失败只留 tmp、目标完好
        Path tmp = webpPath.resolveSibling(webpPath.getFileName() + "." + UUID.randomUUID() + ".tmp");
        try {
            java.util.Iterator<ImageWriter> writers = ImageIO.getImageWritersByMIMEType("image/webp");
            if (!writers.hasNext()) {
                log.warn("无 WebP 编码器，跳过生成（viewer 大图将回退缩略图）: {}", webpPath);
                return;
            }
            ImageWriter writer = writers.next();
            try {
                ImageWriteParam param = writer.getDefaultWriteParam();
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                if (param.canWriteCompressed()) {
                    param.setCompressionType("Lossy");
                    param.setCompressionQuality(0.8f);
                }
                try (FileImageOutputStream ios = new FileImageOutputStream(tmp.toFile())) {
                    writer.setOutput(ios);
                    writer.write(null, new IIOImage(img, null, null), param);
                }
            } finally {
                writer.dispose();
            }
            moveAtomic(tmp, webpPath);
        } catch (Throwable e) {
            log.warn("WebP 生成失败: {}", webpPath, e);
        } finally {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
                // 清理失败仅留残留文件
            }
        }
    }

    private Path thumbPath(String dateDir, String baseName, int width) throws IOException {
        Path dir = width == THUMBNAIL_WIDTH
                ? uploadDir.resolve(dateDir).resolve("thumbnails")
                : uploadDir.resolve(dateDir).resolve("thumbnails").resolve(String.valueOf(width));
        Files.createDirectories(dir);
        return dir.resolve(baseName);
    }

    private void writeJpeg(BufferedImage image, Path path) throws IOException {
        writeAtomic(image, path, 0.75f);
    }

    /**
     * 原子写 JPEG：先写同目录唯一 .tmp 再 ATOMIC_MOVE 替换——中途失败（磁盘满/IO 错误）
     * 只损坏 .tmp，目标文件完好可重试；曾原地截断重写，失败即毁目标文件 → 永久 FAILED。
     * tmp 用 UUID 唯一命名：同照片并发处理（retry-processing 与在途消息/重扫并存）时
     * 各自写独立临时文件，后到者覆盖先到者，绝不交错写坏（固定 .tmp 名会互踩字节）。
     * 缩略图/原图写回共用本模板（webp 在 generateWebp 内同构处理）。
     */
    private void writeAtomic(BufferedImage image, Path path, float quality) throws IOException {
        Path tmp = path.resolveSibling(path.getFileName() + "." + UUID.randomUUID() + ".tmp");
        try {
            writeJpegTo(image, tmp, quality);
            moveAtomic(tmp, path);
        } catch (IOException e) {
            // 编码失败（磁盘满等）或 move 失败：清理 .tmp，目标文件完好
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
                // 清理失败仅留残留文件，不影响目标文件完好性
            }
            throw e;
        }
    }

    /** ATOMIC_MOVE 替换（不支持时降级普通 move），供 webp 原子写复用 */
    private void moveAtomic(Path tmp, Path target) throws IOException {
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** 实际 JPEG 编码（不涉及原子性，写给定路径；调用方负责 tmp/move 语义） */
    private void writeJpegTo(BufferedImage image, Path path, float quality) throws IOException {
        java.util.Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (writers.hasNext()) {
            ImageWriter writer = writers.next();
            try {
                ImageWriteParam param = writer.getDefaultWriteParam();
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(quality);
                try (FileImageOutputStream ios = new FileImageOutputStream(path.toFile())) {
                    writer.setOutput(ios);
                    writer.write(null, new IIOImage(image, null, null), param);
                }
            } finally {
                writer.dispose();
            }
        } else {
            ImageIO.write(image, "jpeg", path.toFile());
        }
    }

    /** 高质量写回原文件（旋转/水印后覆盖原图），原子写语义见 writeAtomic */
    public void writeOriginalJpeg(BufferedImage image, Path path) throws IOException {
        writeAtomic(image, path, 0.92f);
    }

    // === 原 Path 版重载（供外部调用） ===

    public void autoRotateIfNeeded(Path path) throws IOException {
        int orientation = exifService.getOrientation(path);
        int degrees = switch (orientation) {
            case 3 -> 180;
            case 6 -> 90;
            case 8 -> 270;
            default -> 0;
        };
        if (degrees > 0) {
            BufferedImage img = decodeCapped(path);
            if (img == null) return;
            BufferedImage rotated = rotateImage(img, degrees);
            String format = getFormat(path);
            ImageIO.write(rotated, format, path.toFile());
        }
    }

    public void applyWatermark(Path filePath, String text) throws IOException {
        BufferedImage img = decodeCapped(filePath);
        if (img == null) return;
        applyWatermark(img, text);
        String format = getFormat(filePath);
        ImageIO.write(img, format, filePath.toFile());
    }

    public void generateThumbnail(Path original, String dateDir, String baseName) throws IOException {
        generateThumbnail(original, dateDir, baseName, THUMBNAIL_WIDTH);
    }

    public void generateThumbnail(Path original, String dateDir, String baseName, int width) throws IOException {
        BufferedImage image = decodeCapped(original);
        if (image == null) return;
        generateThumbnail(image, dateDir, baseName, width);
    }

    public void generateWebp(Path original, String dateDir, String baseName) {
        String lower = baseName.toLowerCase(Locale.ROOT);
        Path webpDir = uploadDir.resolve(dateDir).resolve("webp");
        try {
            Files.createDirectories(webpDir);
        } catch (IOException e) {
            log.warn("WebP 目录创建失败: {}", webpDir, e);
            return;
        }
        Path webpPath = webpDir.resolve(baseName + ".webp");
        // 原子写：tmp + ATOMIC_MOVE，失败只留 tmp、目标完好；tmp 清理放 finally（编码器可能抛 Error）
        Path tmp = webpPath.resolveSibling(webpPath.getFileName() + "." + UUID.randomUUID() + ".tmp");
        try {
            if (lower.endsWith(".webp")) {
                Files.copy(original, tmp, StandardCopyOption.REPLACE_EXISTING);
                moveAtomic(tmp, webpPath);
                return;
            }

            // 捕 Throwable（native 编码器初始化失败可能抛 Error 而非 IOException）
            BufferedImage img = decodeCapped(original);
            if (img == null) return;
            java.util.Iterator<ImageWriter> writers = ImageIO.getImageWritersByMIMEType("image/webp");
            if (!writers.hasNext()) {
                log.warn("无 WebP 编码器，跳过生成: {}", webpPath);
                return;
            }
            ImageWriter writer = writers.next();
            try {
                ImageWriteParam param = writer.getDefaultWriteParam();
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                if (param.canWriteCompressed()) {
                    param.setCompressionType("Lossy");
                    param.setCompressionQuality(0.8f);
                }
                try (FileImageOutputStream ios = new FileImageOutputStream(tmp.toFile())) {
                    writer.setOutput(ios);
                    writer.write(null, new IIOImage(img, null, null), param);
                }
            } finally {
                writer.dispose();
            }
            moveAtomic(tmp, webpPath);
        } catch (Throwable e) {
            log.warn("WebP 生成失败: {}", webpPath, e);
        } finally {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
                // 清理失败仅留残留文件
            }
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

    /**
     * 按文件实际字节（魔数）判断格式，而非扩展名。
     * 背景：旋转/水印写回时文件内容可能被转码（如 PNG 原图旋转后写成 JPEG 字节，
     * 但文件名/扩展名不变）——按扩展名判断会写错格式（GIF 场景 ImageIO.write
     * 可能返回 false → transform 400），改按魔数后写回格式与真实字节一致。
     * .webp 返回 "WebP"（webp-imageio/sejda SPI 注册名，与 MIME image/webp 对应）。
     */
    public String getFormat(Path path) {
        byte[] header = new byte[12];
        int read;
        try (InputStream in = Files.newInputStream(path)) {
            // readNBytes 循环读满或 EOF——单次 read 契约不保证填满缓冲区，
            // 短读会导致 WebP 魔数（需 12 字节）判不出而回退 JPEG（格式错位残留窗口）
            read = in.readNBytes(header, 0, header.length);
        } catch (IOException e) {
            throw new UncheckedIOException("无法读取文件格式: " + path, e);
        }
        if (read >= 2 && header[0] == (byte) 0xFF && header[1] == (byte) 0xD8) return "JPEG";
        if (read >= 4 && header[0] == (byte) 0x89 && header[1] == 0x50
                && header[2] == 0x4E && header[3] == 0x47) return "PNG";
        if (read >= 4 && header[0] == 0x47 && header[1] == 0x49
                && header[2] == 0x46 && header[3] == 0x38) return "GIF";
        if (read >= 2 && header[0] == 0x42 && header[1] == 0x4D) return "BMP";
        if (read >= 12 && header[0] == 0x52 && header[1] == 0x49 && header[2] == 0x46
                && header[3] == 0x46 && header[8] == 0x57 && header[9] == 0x45
                && header[10] == 0x42 && header[11] == 0x50) return "WebP";
        return "JPEG";
    }
}
