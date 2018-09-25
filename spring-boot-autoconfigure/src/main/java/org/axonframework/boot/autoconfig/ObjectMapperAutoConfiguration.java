package org.axonframework.boot.autoconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.axonframework.boot.SerializerProperties;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@AutoConfigureBefore(AxonAutoConfiguration.class)
@AutoConfigureAfter(name = "org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration")
@ConditionalOnClass(name = "com.fasterxml.jackson.databind.ObjectMapper")
@EnableConfigurationProperties(value = SerializerProperties.class)
public class ObjectMapperAutoConfiguration {

    @Bean("defaultAxonObjectMapper")
    @ConditionalOnMissingBean
    @ConditionalOnExpression("'${axon.serializer.general}' == 'jackson' || '${axon.serializer.events}' == 'jackson' || '${axon.serializer.messages}' == 'jackson'")
    public ObjectMapper defaultAxonObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
