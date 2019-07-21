package org.axonframework.queryhandling.repository;

import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.json.JacksonSerializer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class JacksonSerializerTestConfig {

    @Bean
    public Serializer serializer() {
        return JacksonSerializer.defaultSerializer();
    }
}
