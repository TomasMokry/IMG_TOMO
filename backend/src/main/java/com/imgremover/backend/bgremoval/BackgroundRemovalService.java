package com.imgremover.backend.bgremoval;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.FloatBuffer;
import java.util.Collections;

@Service
public class BackgroundRemovalService {

    private static final int MODEL_INPUT_SIZE = 1024;

    private final OrtEnvironment environment;
    private final OrtSession session;

    public BackgroundRemovalService(OrtEnvironment environment, OrtSession session) {
        this.environment = environment;
        this.session = session;
    }

    public byte[] removeBackground(byte[] originalImageBytes) {
        BufferedImage original = readImage(originalImageBytes);
        int originalWidth = original.getWidth();
        int originalHeight = original.getHeight();

        BufferedImage modelInput = resize(toRgb(original), MODEL_INPUT_SIZE, MODEL_INPUT_SIZE);
        float[][] mask = runInference(modelInput);
        BufferedImage alphaMask = maskToImage(mask);
        BufferedImage resizedMask = resize(alphaMask, originalWidth, originalHeight);

        BufferedImage result = applyAlpha(original, resizedMask);
        return encodePng(result);
    }

    private float[][] runInference(BufferedImage modelInput) {
        FloatBuffer inputBuffer = FloatBuffer.allocate(3 * MODEL_INPUT_SIZE * MODEL_INPUT_SIZE);
        for (int channel = 0; channel < 3; channel++) {
            for (int y = 0; y < MODEL_INPUT_SIZE; y++) {
                for (int x = 0; x < MODEL_INPUT_SIZE; x++) {
                    int rgb = modelInput.getRGB(x, y);
                    int component = switch (channel) {
                        case 0 -> (rgb >> 16) & 0xFF;
                        case 1 -> (rgb >> 8) & 0xFF;
                        default -> rgb & 0xFF;
                    };
                    inputBuffer.put((component / 255f - 0.5f) / 1.0f);
                }
            }
        }
        inputBuffer.rewind();

        try (OnnxTensor inputTensor = OnnxTensor.createTensor(environment, inputBuffer,
                new long[]{1, 3, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE})) {
            String inputName = session.getInputNames().iterator().next();
            try (OrtSession.Result result = session.run(Collections.singletonMap(inputName, inputTensor))) {
                String outputName = session.getOutputNames().iterator().next();
                OnnxValue outputValue = result.get(outputName)
                        .orElseThrow(() -> new IllegalStateException("Model produced no output named " + outputName));
                float[][][][] output = (float[][][][]) outputValue.getValue();
                return output[0][0];
            }
        } catch (OrtException e) {
            throw new RuntimeException("Background removal inference failed", e);
        }
    }

    private BufferedImage maskToImage(float[][] mask) {
        int size = mask.length;
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (float[] row : mask) {
            for (float value : row) {
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
        }
        float range = Math.max(max - min, 1e-6f);

        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int gray = Math.round(((mask[y][x] - min) / range) * 255f);
                int clamped = Math.max(0, Math.min(255, gray));
                image.getRaster().setSample(x, y, 0, clamped);
            }
        }
        return image;
    }

    private BufferedImage applyAlpha(BufferedImage original, BufferedImage alphaMask) {
        int width = original.getWidth();
        int height = original.getHeight();
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = original.getRGB(x, y) & 0x00FFFFFF;
                int alpha = alphaMask.getRaster().getSample(x, y, 0);
                result.setRGB(x, y, (alpha << 24) | rgb);
            }
        }
        return result;
    }

    private BufferedImage readImage(byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                throw new IllegalArgumentException("Unsupported or corrupt image data");
            }
            return image;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private BufferedImage toRgb(BufferedImage source) {
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return rgb;
    }

    private BufferedImage resize(BufferedImage source, int width, int height) {
        BufferedImage resized = new BufferedImage(width, height, source.getType());
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(source, 0, 0, width, height, null);
        g.dispose();
        return resized;
    }

    private byte[] encodePng(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
