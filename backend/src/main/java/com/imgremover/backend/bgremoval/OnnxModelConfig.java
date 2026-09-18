package com.imgremover.backend.bgremoval;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;

@Configuration
public class OnnxModelConfig {

    private static final String MODEL_RESOURCE_PATH = "model/model_quantized.onnx";

    @Bean(destroyMethod = "close")
    public OrtEnvironment ortEnvironment() {
        return OrtEnvironment.getEnvironment();
    }

    @Bean(destroyMethod = "close")
    public OrtSession ortSession(OrtEnvironment environment) throws OrtException, IOException {
        byte[] modelBytes;
        try (InputStream in = new ClassPathResource(MODEL_RESOURCE_PATH).getInputStream()) {
            modelBytes = in.readAllBytes();
        }
        return environment.createSession(modelBytes, new OrtSession.SessionOptions());
    }
}
