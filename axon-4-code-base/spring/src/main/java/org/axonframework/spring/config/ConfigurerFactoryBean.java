/*
 * Copyright (c) 2010-2023. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.spring.config;

import org.axonframework.config.Configurer;
import org.axonframework.config.ConfigurerModule;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nonnull;

/**
 * FactoryBean that creates an instance of a {@link Configurer} for use in a Spring Application Context.
 * <p>
 * This bean has a dependency on all {@link ConfigurerModule ConfigurerModules} in the Application Context, which
 * are initialized <em>before</em> the Configurer is made available in the application context. This ensures that
 * any customizations made by autowiring the Configurer will override any defaults set by a ConfigurerModule.
 * <p>
 * The ConfigurerFactoryBean is wired by the {@link SpringAxonAutoConfigurer} as part of Spring Boot Auto-Configuration
 * and should not be wired "manually" in an Application Context.
 *
 * @author Allard Buijze
 * @since 4.2
 * @deprecated Replaced by the {@link SpringConfigurer}, which itself is a {@link Configurer} implementation.
 */
@Deprecated
public class ConfigurerFactoryBean implements FactoryBean<Configurer>, ApplicationContextAware {

    private final Configurer configurer;

    /**
     * Initialize the factory bean, using the given {@code configurer} to make available in the Application Context,
     * once configured by the ConfigurerModules in that context.
     *
     * @param configurer The Configurer to make available in the Application Context
     */
    public ConfigurerFactoryBean(Configurer configurer) {
        this.configurer = configurer;
    }

    /**
     * Registers the {@code configurerModules} that provide context-sensitive default settings for the Configurer.
     *
     * @param configurerModules the modules that provide defaults for the Configurer
     */
    @Autowired(required = false)
    public void setConfigurerModules(List<ConfigurerModule> configurerModules) {
        ArrayList<ConfigurerModule> modules = new ArrayList<>(configurerModules);
        modules.sort(Comparator.comparingInt(ConfigurerModule::order));
        modules.forEach(c -> c.configureModule(configurer));
    }

    @Override
    public Configurer getObject() {
        return configurer;
    }

    @Override
    public Class<?> getObjectType() {
        return Configurer.class;
    }

    @Override
    public void setApplicationContext(@Nonnull ApplicationContext applicationContext) throws BeansException {
        configurer.registerComponent(ApplicationContext.class, c -> applicationContext);
    }

    @Override
    public boolean isSingleton() {
        return true;
    }
}


