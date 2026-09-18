package com.imgremover.backend.bgremoval;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class BackgroundRemovalServiceTest {

    private static final String MODEL_RESOURCE_PATH = "model/model_quantized.onnx";

    private static OrtEnvironment environment;
    private static OrtSession session;
    private static BackgroundRemovalService service;

    @BeforeAll
    static void loadModel() throws Exception {
        Assumptions.assumeTrue(new ClassPathResource(MODEL_RESOURCE_PATH).exists(),
                "model_quantized.onnx not present on the classpath, skipping (see tech-stack.md to download it)");

        environment = OrtEnvironment.getEnvironment();
        byte[] modelBytes;
        try (InputStream in = new ClassPathResource(MODEL_RESOURCE_PATH).getInputStream()) {
            modelBytes = in.readAllBytes();
        }
        session = environment.createSession(modelBytes, new OrtSession.SessionOptions());
        service = new BackgroundRemovalService(environment, session);
    }

    @AfterAll
    static void closeSession() throws Exception {
        if (session != null) {
            session.close();
        }
    }

    @Test
    void removeBackground_solidColorBackgroundWithCenteredShape_producesTransparentBackground() throws Exception {
        byte[] input = buildTestImage();

        byte[] output = service.removeBackground(input);

        BufferedImage result = ImageIO.read(new ByteArrayInputStream(output));
        assertThat(result.getColorModel().hasAlpha()).isTrue();

        int corner = result.getRGB(5, 5);
        int cornerAlpha = (corner >>> 24) & 0xFF;
        assertThat(cornerAlpha).isLessThan(20);

        int center = result.getRGB(result.getWidth() / 2, result.getHeight() / 2);
        int centerAlpha = (center >>> 24) & 0xFF;
        assertThat(centerAlpha).isGreaterThan(200);

        int centerRgb = center & 0x00FFFFFF;
        int expectedRgb = Color.decode("#4682B4").getRGB() & 0x00FFFFFF;
        assertThat(centerRgb).isEqualTo(expectedRgb);
    }

    @Test
    void removeBackground_preservesOriginalImageDimensions() throws Exception {
        byte[] input = buildTestImage();

        byte[] output = service.removeBackground(input);

        BufferedImage result = ImageIO.read(new ByteArrayInputStream(output));
        assertThat(result.getWidth()).isEqualTo(200);
        assertThat(result.getHeight()).isEqualTo(200);
    }

    private static byte[] buildTestImage() throws Exception {
        BufferedImage image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 200, 200);
        g.setColor(Color.decode("#4682B4"));
        g.fillOval(40, 40, 120, 120);
        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
