package org.axonframework.spring.config;

import org.axonframework.config.EventHandlingConfiguration;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Bean to be used to specify partial configuration for the EventHandlingConfiguration of this ApplicationContext. This
 * class allows several modules to specify the settings for Event Handling that are important for each module.
 * <p>
 * Implement {@link #configure(EventHandlingConfiguration)} to specify the actual configuration.
 */
public abstract class EventHandlingConfigurer implements InitializingBean {

    private EventHandlingConfiguration eventHandlingConfiguration;

    /**
     * Sets the EventHandlingConfiguration found in this ApplicationContext. This method is used by Spring Autowiring
     * and should not be used directly.
     *
     * @param eventHandlingConfiguration The EventHandlingConfiguration to configure
     */
    @Autowired
    public void setEventHandlingConfiguration(EventHandlingConfiguration eventHandlingConfiguration) {
        this.eventHandlingConfiguration = eventHandlingConfiguration;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        configure(eventHandlingConfiguration);
    }

    /**
     * Configures the relevant settings on the given {@code eventHandlingConfiguration}.
     *
     * @param eventHandlingConfiguration The EventHandlingConfiguration instance describing how Event Handlers are
     *                                   organized in Processing groups
     */
    protected abstract void configure(EventHandlingConfiguration eventHandlingConfiguration);
}
