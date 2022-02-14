package org.axonframework.spring.config;

import org.axonframework.spring.stereotype.Saga;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;

/**
 * BeanDefinitionRegistryPostProcessor implementation that scans for Saga types and registers a Configurer for each
 * Saga found
 */
public class SpringSagaLookup implements BeanDefinitionRegistryPostProcessor {
    private static final Logger logger = LoggerFactory.getLogger(SpringSagaLookup.class);

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        String[] sagas = beanFactory.getBeanNamesForAnnotation(Saga.class);
        for (String saga : sagas) {
            if (beanFactory.containsBeanDefinition(saga + "$$Registrar")) {
                logger.info("Registrar for {} already available. Skipping configuration", saga);
                break;
            }

            Saga sagaAnnotation = beanFactory.findAnnotationOnBean(saga, Saga.class);
            Class<?> sagaType = beanFactory.getType(saga);

            BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.genericBeanDefinition(SpringSagaConfigurer.class)
                                                                               .addConstructorArgValue(sagaType);

            if (sagaAnnotation != null && !"".equals(sagaAnnotation.sagaStore())) {
                beanDefinitionBuilder.addPropertyValue("sagaStore", sagaAnnotation.sagaStore());
            }
            BeanDefinitionRegistry bdRegistry = (BeanDefinitionRegistry) beanFactory;
            bdRegistry.registerBeanDefinition(saga + "$$Registrar",
                                              beanDefinitionBuilder.getBeanDefinition());
        }
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry beanDefinitionRegistry) throws BeansException {

    }
}
