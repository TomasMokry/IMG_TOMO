package com.imgremover.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ActiveProfiles("test")
class ImageApiEndToEndTest {

    @Autowired
    private RestTestClient restTestClient;

    @Test
    void fullLifecycle_uploadProcessReRunDelete() throws Exception {
        byte[] originalPng = buildTestImage();

        Map uploadResponse = upload(originalPng, "product.png", "image/png");
        Number id = (Number) uploadResponse.get("id");
        assertThat(uploadResponse.get("originalUrl")).isEqualTo("/api/images/" + id + "/original");

        byte[] fetchedOriginal = restTestClient.get().uri("/api/images/{id}/original", id)
                .exchange()
                .expectStatus().isOk()
                .expectBody(byte[].class)
                .returnResult()
                .getResponseBody();
        assertThat(fetchedOriginal).isEqualTo(originalPng);

        Map processResponse = restTestClient.post().uri("/api/images/{id}/process", id)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
        assertThat(processResponse.get("processedUrl")).isEqualTo("/api/images/" + id + "/processed");

        byte[] processedBytes = restTestClient.get().uri("/api/images/{id}/processed", id)
                .exchange()
                .expectStatus().isOk()
                .expectBody(byte[].class)
                .returnResult()
                .getResponseBody();
        BufferedImage processedImage = ImageIO.read(new ByteArrayInputStream(processedBytes));
        assertThat((processedImage.getRGB(5, 5) >>> 24) & 0xFF).isLessThan(20);
        assertThat((processedImage.getRGB(100, 100) >>> 24) & 0xFF).isGreaterThan(200);

        restTestClient.post().uri("/api/images/{id}/process", id).exchange().expectStatus().isOk();
        byte[] secondProcessedBytes = restTestClient.get().uri("/api/images/{id}/processed", id)
                .exchange()
                .expectStatus().isOk()
                .expectBody(byte[].class)
                .returnResult()
                .getResponseBody();
        assertThat(secondProcessedBytes).isEqualTo(processedBytes);

        restTestClient.delete().uri("/api/images/{id}", id).exchange().expectStatus().isNoContent();
        restTestClient.get().uri("/api/images/{id}/original", id).exchange().expectStatus().isNotFound();
    }

    @Test
    void processingNonExistentImage_returns404() {
        restTestClient.post().uri("/api/images/999999/process").exchange().expectStatus().isNotFound();
    }

    @Test
    void fetchingProcessedBeforeProcessing_returns404() throws Exception {
        Map uploadResponse = upload(buildTestImage(), "product.png", "image/png");
        Number id = (Number) uploadResponse.get("id");

        restTestClient.get().uri("/api/images/{id}/processed", id).exchange().expectStatus().isNotFound();
    }

    @Test
    void uploadingDisallowedFileType_returns400() {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", namedResource("not an image".getBytes(), "document.pdf"));

        restTestClient.post().uri("/api/images")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts)
                .exchange()
                .expectStatus().isBadRequest();
    }

    private Map upload(byte[] bytes, String filename, String contentType) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", namedResource(bytes, filename));

        return restTestClient.post().uri("/api/images")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
    }

    private static ByteArrayResource namedResource(byte[] bytes, String filename) {
        return new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
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
