package org.axonframework.spring.config;

import org.axonframework.config.Configurer;
import org.axonframework.config.ConfigurerModule;
import org.axonframework.modelling.saga.repository.SagaStore;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * A Configurer Module implementation that configures a Saga based on configuration found in the Application Context.
 */
public class SpringSagaConfigurer implements ConfigurerModule, ApplicationContextAware {

    private final Class<?> sagaType;
    private String sagaStore;
    private ApplicationContext applicationContext;

    /**
     * Initialize the Saga for given {@code sagaType}
     *
     * @param sagaType The type of Saga to configure
     */
    public SpringSagaConfigurer(Class<?> sagaType) {
        this.sagaType = sagaType;
    }

    /**
     * Sets the bean name of the Saga Store to configure
     *
     * @param sagaStore the bean name of the Saga Store to configure
     */
    public void setSagaStore(String sagaStore) {
        this.sagaStore = sagaStore;
    }

    @Override
    public void configureModule(Configurer configurer) {
        configurer.eventProcessing().registerSaga(sagaType,
                                                  sagaConfigurer -> {
                                                      if (sagaStore != null && !"".equals(sagaStore)) {
                                                          sagaConfigurer.configureSagaStore(c -> applicationContext.getBean(sagaStore, SagaStore.class));
                                                      }
                                                  });
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
