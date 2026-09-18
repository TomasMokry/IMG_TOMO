package com.imgremover.backend.image;

import com.imgremover.backend.bgremoval.BackgroundRemovalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ImageController.class)
class ImageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ImageRepository imageRepository;

    @MockitoBean
    private BackgroundRemovalService backgroundRemovalService;

    @Test
    void upload_validImage_returnsIdAndUrl() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1, 2, 3});
        when(imageRepository.save(any(ImageEntity.class))).thenAnswer(invocation -> {
            ImageEntity entity = invocation.getArgument(0);
            entity.setId(42L);
            return entity;
        });

        mockMvc.perform(multipart("/api/images").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.originalUrl").value("/api/images/42/original"));
    }

    @Test
    void upload_emptyFile_returnsBadRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", new byte[0]);

        mockMvc.perform(multipart("/api/images").file(file))
                .andExpect(status().isBadRequest());

        verify(imageRepository, never()).save(any());
    }

    @Test
    void upload_unsupportedType_returnsBadRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", new byte[]{1});

        mockMvc.perform(multipart("/api/images").file(file))
                .andExpect(status().isBadRequest());

        verify(imageRepository, never()).save(any());
    }

    @Test
    void upload_tooLarge_returnsBadRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", new byte[11 * 1024 * 1024]);

        mockMvc.perform(multipart("/api/images").file(file))
                .andExpect(status().isBadRequest());

        verify(imageRepository, never()).save(any());
    }

    @Test
    void getOriginal_existingId_returnsBytesWithContentType() throws Exception {
        ImageEntity entity = new ImageEntity();
        entity.setContentType("image/png");
        entity.setOriginalBytes(new byte[]{9, 8, 7});
        when(imageRepository.findById(1L)).thenReturn(Optional.of(entity));

        mockMvc.perform(get("/api/images/1/original"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(new byte[]{9, 8, 7}));
    }

    @Test
    void getOriginal_missingId_returnsNotFound() throws Exception {
        when(imageRepository.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/images/99/original"))
                .andExpect(status().isNotFound());
    }

    @Test
    void process_existingId_invokesServiceAndReturnsUrl() throws Exception {
        ImageEntity entity = new ImageEntity();
        entity.setId(5L);
        entity.setOriginalBytes(new byte[]{1, 2, 3});
        when(imageRepository.findById(5L)).thenReturn(Optional.of(entity));
        when(backgroundRemovalService.removeBackground(entity.getOriginalBytes())).thenReturn(new byte[]{9, 9});

        mockMvc.perform(post("/api/images/5/process"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.processedUrl").value("/api/images/5/processed"));

        verify(imageRepository, times(1)).save(entity);
    }

    @Test
    void process_missingId_returnsNotFound() throws Exception {
        when(imageRepository.findById(anyLong())).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/images/404/process"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getProcessed_notYetProcessed_returnsNotFound() throws Exception {
        ImageEntity entity = new ImageEntity();
        when(imageRepository.findById(1L)).thenReturn(Optional.of(entity));

        mockMvc.perform(get("/api/images/1/processed"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getProcessed_afterProcessing_returnsBytes() throws Exception {
        ImageEntity entity = new ImageEntity();
        entity.setProcessedBytes(new byte[]{5, 5, 5});
        when(imageRepository.findById(1L)).thenReturn(Optional.of(entity));

        mockMvc.perform(get("/api/images/1/processed"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(new byte[]{5, 5, 5}));
    }

    @Test
    void delete_existingId_returnsNoContent() throws Exception {
        when(imageRepository.existsById(1L)).thenReturn(true);

        mockMvc.perform(delete("/api/images/1"))
                .andExpect(status().isNoContent());

        verify(imageRepository).deleteById(1L);
    }

    @Test
    void delete_missingId_returnsNotFound() throws Exception {
        when(imageRepository.existsById(404L)).thenReturn(false);

        mockMvc.perform(delete("/api/images/404"))
                .andExpect(status().isNotFound());

        verify(imageRepository, never()).deleteById(any());
    }
}
