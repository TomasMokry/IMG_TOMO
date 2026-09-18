package com.imgremover.backend.image;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ImageRepositoryTest {

    @Autowired
    private ImageRepository imageRepository;

    @Test
    void savingAnImage_populatesIdAndTimestamps() {
        ImageEntity entity = new ImageEntity();
        entity.setOriginalFilename("product.png");
        entity.setContentType("image/png");
        entity.setOriginalBytes(new byte[]{1, 2, 3, 4});

        ImageEntity saved = imageRepository.save(entity);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getProcessedBytes()).isNull();
    }

    @Test
    void findById_returnsPersistedOriginalBytes() {
        ImageEntity entity = new ImageEntity();
        entity.setOriginalFilename("product.png");
        entity.setContentType("image/png");
        entity.setOriginalBytes(new byte[]{9, 8, 7});
        Long id = imageRepository.save(entity).getId();

        Optional<ImageEntity> found = imageRepository.findById(id);

        assertThat(found).isPresent();
        assertThat(found.get().getOriginalBytes()).containsExactly(9, 8, 7);
    }

    @Test
    void updatingProcessedBytes_bumpsUpdatedAt() {
        ImageEntity entity = new ImageEntity();
        entity.setOriginalFilename("product.png");
        entity.setContentType("image/png");
        entity.setOriginalBytes(new byte[]{1});
        ImageEntity saved = imageRepository.saveAndFlush(entity);
        var initialUpdatedAt = saved.getUpdatedAt();

        saved.setProcessedBytes(new byte[]{2, 3});
        ImageEntity updated = imageRepository.saveAndFlush(saved);

        assertThat(updated.getProcessedBytes()).containsExactly(2, 3);
        assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(initialUpdatedAt);
    }

    @Test
    void deleteById_removesTheRow() {
        ImageEntity entity = new ImageEntity();
        entity.setOriginalFilename("product.png");
        entity.setContentType("image/png");
        entity.setOriginalBytes(new byte[]{1});
        Long id = imageRepository.save(entity).getId();

        imageRepository.deleteById(id);

        assertThat(imageRepository.existsById(id)).isFalse();
    }
}
