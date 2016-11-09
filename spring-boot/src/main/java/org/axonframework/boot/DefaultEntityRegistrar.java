package org.axonframework.boot;

import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.AnnotationMetadata;

import java.util.Map;

@Order(Ordered.HIGHEST_PRECEDENCE)
public class DefaultEntityRegistrar implements ImportBeanDefinitionRegistrar {

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        Map<String, Object> attributes = importingClassMetadata.getAnnotationAttributes("org.axonframework.boot.RegisterDefaultEntities");
        String[] packages = (String[]) attributes.get("packages");

        AutoConfigurationPackages.register(registry, packages);
    }
}
