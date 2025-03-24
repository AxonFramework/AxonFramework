/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.test.af5;

import org.axonframework.configuration.ApplicationConfigurer;
import org.axonframework.configuration.AxonApplication;
import org.axonframework.configuration.MessagingConfigurer;
import org.axonframework.configuration.Module;
import org.axonframework.configuration.NewConfiguration;

import java.util.function.Function;

// todo: use decorators to decorate commandbus with recorded etc.
public class NewTestFixture {

    private final NewConfiguration configuration;

    public NewTestFixture(Function<ApplicationConfigurer<?>, ApplicationConfigurer<?>> configurerFn) {
        var configurer = MessagingConfigurer.create();
        ApplicationConfigurer<?> c = AxonApplication.create().build();
        this.configuration = configurerFn.apply(c).build();
    }

    public NewTestFixture(ApplicationConfigurer<?> configurer) {
        this.configuration = configurer.build();
    }

    private NewTestFixture(NewConfiguration configuration) {
        this.configuration = configuration;
    }

    public NewTestFixture(Module<?> module) {
        this(module.build(AxonApplication.create().build()));
    }
}
