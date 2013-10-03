/*
 * Copyright (c) 2010-2013. Axon Framework
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

package org.axonframework.contextsupport.spring;

import org.axonframework.common.annotation.ParameterResolverFactory;
import org.axonframework.common.configuration.AnnotationConfiguration;
import org.axonframework.saga.Saga;
import org.axonframework.saga.annotation.AbstractAnnotatedSaga;
import org.axonframework.saga.annotation.AnnotatedSagaManager;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor;

import java.util.Set;

/**
 * BeanPostProcessor that configures a {@link org.axonframework.common.annotation.ParameterResolverFactory}
 * for each of the Sagas. It does so by detecting saga managers in the application context.
 *
 * @author Allard Buijze
 * @since 2.1
 */
public class AnnotatedSagaConfigurationBeanPostProcessor implements DestructionAwareBeanPostProcessor {

    private ParameterResolverFactory parameterResolverFactory;

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof AnnotatedSagaManager) {
            Set<Class<? extends Saga>> types = ((AnnotatedSagaManager) bean).getManagedSagaTypes();
            for (Class<? extends Saga> type : types) {
                if (AbstractAnnotatedSaga.class.isAssignableFrom(type)) {
                    AnnotationConfiguration.configure(type).useParameterResolverFactory(parameterResolverFactory);
                }
            }
        }
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    @Override
    public void postProcessBeforeDestruction(Object bean, String beanName) throws BeansException {
        if (bean instanceof AnnotatedSagaManager) {
            Set<Class<? extends Saga>> types = ((AnnotatedSagaManager) bean).getManagedSagaTypes();
            for (Class<? extends Saga> type : types) {
                if (AbstractAnnotatedSaga.class.isAssignableFrom(type)) {
                    AnnotationConfiguration.reset(type);
                }
            }
        }
    }

    /**
     * Sets the parameter resolver factory to use for each of the Sagas detected.
     *
     * @param parameterResolverFactory the parameter resolver factory to use for each of the Sagas detected
     */
    public void setParameterResolverFactory(ParameterResolverFactory parameterResolverFactory) {
        this.parameterResolverFactory = parameterResolverFactory;
    }
}
