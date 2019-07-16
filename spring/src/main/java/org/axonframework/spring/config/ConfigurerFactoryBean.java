/*
 * Copyright (c) 2010-2019. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

public class ConfigurerFactoryBean implements FactoryBean<Configurer>, ApplicationContextAware {

    private final Configurer configurer;

    public ConfigurerFactoryBean(Configurer configurer) {
        this.configurer = configurer;
    }

    @Autowired(required = false)
    public void setConfigurerModules(List<ConfigurerModule> configurerModules) {
        ArrayList<ConfigurerModule> modules = new ArrayList<>(configurerModules);
        modules.sort(Comparator.comparingInt(ConfigurerModule::order));
        modules.forEach(c -> c.configureModule(configurer));
    }

    @Override
    public Configurer getObject() throws Exception {
        return configurer;
    }

    @Override
    public Class<?> getObjectType() {
        return Configurer.class;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        configurer.registerComponent(ApplicationContext.class, c -> applicationContext);
    }

    @Override
    public boolean isSingleton() {
        return true;
    }
}


