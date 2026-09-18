package com.imgremover.backend.bgremoval;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

class BackgroundRemovalServiceManualTest {

    @Test
    void generatesTransparentPngForSampleImage() throws Exception {
        String testImagePath = System.getenv("TEST_IMAGE_PATH");
        org.junit.jupiter.api.Assumptions.assumeTrue(testImagePath != null, "TEST_IMAGE_PATH not set, skipping manual test");

        OrtEnvironment env = OrtEnvironment.getEnvironment();
        byte[] modelBytes;
        try (InputStream in = new ClassPathResource("model/model_quantized.onnx").getInputStream()) {
            modelBytes = in.readAllBytes();
        }
        OrtSession session = env.createSession(modelBytes, new OrtSession.SessionOptions());
        BackgroundRemovalService service = new BackgroundRemovalService(env, session);

        byte[] input = Files.readAllBytes(Path.of(testImagePath));
        byte[] output = service.removeBackground(input);

        Path outPath = Path.of("target/test-output.png");
        Files.createDirectories(outPath.getParent());
        Files.write(outPath, output);

        session.close();
    }
}
