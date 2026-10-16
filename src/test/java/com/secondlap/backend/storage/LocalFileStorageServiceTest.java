package com.secondlap.backend.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileStorageServiceTest {

    private LocalStorageProperties propertiesFor(Path baseDir) {
        return new LocalStorageProperties(baseDir.toString(), "/uploads", 10_000_000L);
    }

    private byte[] image(int width, int height, String format, boolean withAlpha) throws Exception {
        int type = withAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage img = new BufferedImage(width, height, type);
        Graphics2D g = img.createGraphics();
        // a non-uniform drawing so JPEG compression has real content to work on
        g.setColor(Color.RED);
        g.fillRect(0, 0, width, height);
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, width / 2, height / 2);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    private BufferedImage readStored(Path baseDir, String url) throws Exception {
        String name = url.substring("/uploads/".length());
        return ImageIO.read(baseDir.resolve(name).toFile());
    }

    @Test
    void writesImageToLocalDiskAndReturnsPublicUrl(@TempDir Path baseDir) throws Exception {
        LocalFileStorageService service = new LocalFileStorageService(propertiesFor(baseDir));
        MockMultipartFile file = new MockMultipartFile(
                "files", "photo.jpg", "image/jpeg", image(800, 600, "jpg", false));

        String url = service.store(file);

        assertThat(url).startsWith("/uploads/").endsWith(".jpg");
        String storedName = url.substring("/uploads/".length());
        assertThat(Files.exists(baseDir.resolve(storedName))).isTrue();
    }

    @Test
    void resizesLargeImageToMaxLongSidePreservingAspectRatio(@TempDir Path baseDir) throws Exception {
        LocalFileStorageService service = new LocalFileStorageService(propertiesFor(baseDir));
        byte[] original = image(3000, 2000, "jpg", false);
        MockMultipartFile file = new MockMultipartFile("files", "big.jpg", "image/jpeg", original);

        String url = service.store(file);
        BufferedImage stored = readStored(baseDir, url);

        // long side clamped to 1600, aspect ratio (3:2) preserved
        assertThat(Math.max(stored.getWidth(), stored.getHeight())).isEqualTo(1600);
        assertThat(stored.getWidth()).isEqualTo(1600);
        assertThat(stored.getHeight()).isEqualTo(1067); // round(2000 * 1600/3000)
        // recompressed result weighs less than the original
        long storedBytes = Files.size(baseDir.resolve(url.substring("/uploads/".length())));
        assertThat(storedBytes).isLessThan(original.length);
    }

    @Test
    void doesNotUpscaleSmallImage(@TempDir Path baseDir) throws Exception {
        LocalFileStorageService service = new LocalFileStorageService(propertiesFor(baseDir));
        MockMultipartFile file = new MockMultipartFile(
                "files", "small.jpg", "image/jpeg", image(800, 500, "jpg", false));

        String url = service.store(file);
        BufferedImage stored = readStored(baseDir, url);

        assertThat(stored.getWidth()).isEqualTo(800);
        assertThat(stored.getHeight()).isEqualTo(500);
    }

    @Test
    void convertsOpaquePngToJpeg(@TempDir Path baseDir) throws Exception {
        LocalFileStorageService service = new LocalFileStorageService(propertiesFor(baseDir));
        MockMultipartFile file = new MockMultipartFile(
                "files", "opaque.png", "image/png", image(1000, 800, "png", false));

        String url = service.store(file);

        assertThat(url).endsWith(".jpg");
    }

    @Test
    void keepsTransparentPngAsPng(@TempDir Path baseDir) throws Exception {
        LocalFileStorageService service = new LocalFileStorageService(propertiesFor(baseDir));
        MockMultipartFile file = new MockMultipartFile(
                "files", "alpha.png", "image/png", image(1000, 800, "png", true));

        String url = service.store(file);
        BufferedImage stored = readStored(baseDir, url);

        assertThat(url).endsWith(".png");
        assertThat(stored.getColorModel().hasAlpha()).isTrue();
    }

    @Test
    void passesWebpThroughUnchanged(@TempDir Path baseDir) throws Exception {
        LocalFileStorageService service = new LocalFileStorageService(propertiesFor(baseDir));
        byte[] fakeWebp = new byte[]{'R', 'I', 'F', 'F', 1, 2, 3, 4};
        MockMultipartFile file = new MockMultipartFile("files", "x.webp", "image/webp", fakeWebp);

        String url = service.store(file);
        byte[] stored = Files.readAllBytes(baseDir.resolve(url.substring("/uploads/".length())));

        assertThat(url).endsWith(".webp");
        assertThat(stored).isEqualTo(fakeWebp); // stored verbatim, no processing
    }

    @Test
    void rejectsCorruptImageAsBadRequest(@TempDir Path baseDir) {
        LocalFileStorageService service = new LocalFileStorageService(propertiesFor(baseDir));
        MockMultipartFile corrupt = new MockMultipartFile(
                "files", "broken.jpg", "image/jpeg", new byte[]{1, 2, 3, 4, 5, 6});

        assertThatThrownBy(() -> service.store(corrupt))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessageContaining("no se pudo procesar");
    }

    @Test
    void rejectsUnsupportedContentType(@TempDir Path baseDir) {
        LocalFileStorageService service = new LocalFileStorageService(propertiesFor(baseDir));
        MockMultipartFile pdf = new MockMultipartFile(
                "files", "doc.pdf", "application/pdf", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> service.store(pdf))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessageContaining("Unsupported content type");
    }

    @Test
    void rejectsFileExceedingMaxSizeBeforeProcessing(@TempDir Path baseDir) throws Exception {
        LocalStorageProperties tinyLimit =
                new LocalStorageProperties(baseDir.toString(), "/uploads", 100L);
        LocalFileStorageService service = new LocalFileStorageService(tinyLimit);
        MockMultipartFile tooBig = new MockMultipartFile(
                "files", "big.jpg", "image/jpeg", image(800, 600, "jpg", false));

        assertThatThrownBy(() -> service.store(tooBig))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessageContaining("exceeds maximum size");
    }

    @Test
    void rejectsEmptyFile(@TempDir Path baseDir) {
        LocalFileStorageService service = new LocalFileStorageService(propertiesFor(baseDir));
        MockMultipartFile empty = new MockMultipartFile(
                "files", "empty.png", "image/png", new byte[]{});

        assertThatThrownBy(() -> service.store(empty))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessageContaining("required");
    }

    @Test
    void rejectsNullFile(@TempDir Path baseDir) {
        LocalFileStorageService service = new LocalFileStorageService(propertiesFor(baseDir));

        assertThatThrownBy(() -> service.store(null))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessageContaining("required");
    }

    @Test
    void rejectsMissingContentType(@TempDir Path baseDir) {
        LocalFileStorageService service = new LocalFileStorageService(propertiesFor(baseDir));
        MockMultipartFile noType = new MockMultipartFile(
                "files", "file.bin", null, new byte[]{1, 2, 3});

        assertThatThrownBy(() -> service.store(noType))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessageContaining("Unsupported content type");
    }

    @Test
    void rejectsNameThatEscapesBaseDirectory(@TempDir Path baseDir) {
        LocalFileStorageService service = new LocalFileStorageService(propertiesFor(baseDir));

        assertThatThrownBy(() -> service.resolveWithinBase(baseDir, "../escape.png"))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessageContaining("escapes storage directory");
    }
}
