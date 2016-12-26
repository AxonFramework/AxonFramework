package org.axonframework.spring.config;

import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation that enables Axon Configuration API for Spring. The Configuration is created and automatically configured
 * based on beans present in the Application Context.
 * <p>
 * Note that it is recommended to use the {@code axon-spring-boot-autoconfigure} module instead. Support for this
 * annotation is likely to be removed in future releases in favor of Spring's auto-configuration mechanism.
 * <p>
 * This annotation will also make a Bean of type {@link AxonConfiguration} available, which can be used for more
 * fine-grained configuration.
 */
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Import(SpringAxonAutoConfigurer.ImportSelector.class)
@AnnotationDriven
public @interface EnableAxon {
}
