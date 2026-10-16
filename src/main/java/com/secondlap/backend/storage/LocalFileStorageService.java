package com.secondlap.backend.storage;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

@Service
public class LocalFileStorageService implements StorageService {

    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp");

    // Lightbox (object-contain) drives the largest render: ~1600px on the long
    // side at retina. Everything else is a CSS downscale of the same image.
    private static final int MAX_LONG_SIDE = 1600;
    private static final float JPEG_QUALITY = 0.80f;

    private final LocalStorageProperties properties;

    public LocalFileStorageService(LocalStorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public String store(MultipartFile file) {
        validate(file);

        StoredImage stored = process(file);
        String safeName = UUID.randomUUID() + "." + stored.extension();
        Path baseDir = Path.of(properties.basePath()).toAbsolutePath().normalize();
        Path target = resolveWithinBase(baseDir, safeName);

        try {
            Files.createDirectories(baseDir);
            try (InputStream in = new ByteArrayInputStream(stored.bytes())) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store upload", e);
        }

        return properties.publicUrlPrefix() + "/" + safeName;
    }

    /**
     * Resize (long side to {@value #MAX_LONG_SIDE}px, never upscaling) and
     * recompress product images with the JVM's ImageIO. WebP is passed through
     * unchanged because ImageIO cannot decode or encode it without native
     * libraries (deliberately excluded). PNGs with transparency stay PNG; opaque
     * PNGs and JPEGs are written as JPEG at {@value #JPEG_QUALITY} quality.
     */
    private StoredImage process(MultipartFile file) {
        String contentType = file.getContentType();
        byte[] original = readBytes(file);

        if ("image/webp".equals(contentType)) {
            return new StoredImage(original, "webp");
        }

        BufferedImage image = decode(original);
        BufferedImage resized = resizeToMaxLongSide(image);
        boolean keepPng = "image/png".equals(contentType) && resized.getColorModel().hasAlpha();
        return keepPng
                ? new StoredImage(encodePng(resized), "png")
                : new StoredImage(encodeJpeg(flattenAlpha(resized)), "jpg");
    }

    private BufferedImage decode(byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                throw new InvalidUploadException("La imagen no se pudo procesar");
            }
            return image;
        } catch (IOException e) {
            throw new InvalidUploadException("La imagen no se pudo procesar");
        }
    }

    private BufferedImage resizeToMaxLongSide(BufferedImage src) {
        int longSide = Math.max(src.getWidth(), src.getHeight());
        if (longSide <= MAX_LONG_SIDE) {
            return src;
        }
        double scale = (double) MAX_LONG_SIDE / longSide;
        int targetWidth = Math.max(1, (int) Math.round(src.getWidth() * scale));
        int targetHeight = Math.max(1, (int) Math.round(src.getHeight() * scale));

        int type = src.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage dst = new BufferedImage(targetWidth, targetHeight, type);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(src, 0, 0, targetWidth, targetHeight, null);
        g.dispose();
        return dst;
    }

    // JPEG cannot carry an alpha channel; draw onto an opaque white canvas so a
    // transparent source does not turn black.
    private BufferedImage flattenAlpha(BufferedImage src) {
        if (!src.getColorModel().hasAlpha()) {
            return src;
        }
        BufferedImage opaque = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = opaque.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, src.getWidth(), src.getHeight());
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return opaque;
    }

    private byte[] encodeJpeg(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(JPEG_QUALITY);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to encode JPEG", e);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private byte[] encodePng(BufferedImage image) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to encode PNG", e);
        }
        return out.toByteArray();
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read upload", e);
        }
    }

    // Defense in depth: generated UUID names cannot traverse, but any name is
    // verified to resolve inside the base directory before it is used.
    Path resolveWithinBase(Path baseDir, String name) {
        Path target = baseDir.resolve(name).normalize();
        if (!target.startsWith(baseDir)) {
            throw new InvalidUploadException("Resolved path escapes storage directory");
        }
        return target;
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidUploadException("File is required");
        }
        if (file.getSize() > properties.maxFileSizeBytes()) {
            throw new InvalidUploadException(
                    "File exceeds maximum size of " + properties.maxFileSizeBytes() + " bytes");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.containsKey(contentType)) {
            throw new InvalidUploadException("Unsupported content type: " + contentType);
        }
    }

    private record StoredImage(byte[] bytes, String extension) {
    }
}
