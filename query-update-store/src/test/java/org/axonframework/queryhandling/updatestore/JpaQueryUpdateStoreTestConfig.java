package org.axonframework.queryhandling.updatestore;

import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.json.JacksonSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JpaQueryUpdateStoreTestConfig {

    @Bean
    public Serializer messageSerializer() {
        return JacksonSerializer.defaultSerializer();
    }
}
