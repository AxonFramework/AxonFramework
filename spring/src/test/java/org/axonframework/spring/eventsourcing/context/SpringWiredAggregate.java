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

package org.axonframework.spring.eventsourcing.context;

import org.axonframework.spring.stereotype.Aggregate;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import jakarta.annotation.Nonnull;

/**
 * Plain aggregate wired through Spring with the {@link Aggregate} annotation.
 *
 * @author Allard Buijze
 */
@Aggregate
public class SpringWiredAggregate implements ApplicationContextAware {

    private transient ApplicationContext context;

    public SpringWiredAggregate() {
    }

    public ApplicationContext getContext() {
        return context;
    }

    @Override
    public void setApplicationContext(@Nonnull ApplicationContext applicationContext) throws BeansException {
        this.context = applicationContext;
    }
}
