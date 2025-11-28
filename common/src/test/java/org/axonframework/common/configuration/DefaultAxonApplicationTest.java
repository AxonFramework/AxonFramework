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

package org.axonframework.common.configuration;

import org.axonframework.common.FutureUtils;
import org.axonframework.common.lifecycle.LifecycleHandlerInvocationException;
import org.axonframework.common.lifecycle.Phase;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test class validating the {@link DefaultAxonApplication}.
 *
 * @author Steven van Beelen
 */
class DefaultAxonApplicationTest extends ApplicationConfigurerTestSuite<DefaultAxonApplication> {

    @Override
    public DefaultAxonApplication createConfigurer() {
        return new DefaultAxonApplication();
    }


    @Test
    void startupFailsWhenLifecycleHandlerThrowsException() {
        var configuration = createConfigurer()
                .onStart(Phase.INSTRUCTION_COMPONENTS, (LifecycleHandler) configuration1 -> {
                    throw new UnsupportedOperationException("Expected exception");
                }).build();

        assertThatThrownBy(configuration::start)
                .isInstanceOf(LifecycleHandlerInvocationException.class)
                .satisfies(th -> {
                    Throwable root = th;
                    Throwable unwrapped;
                    while ((unwrapped = FutureUtils.unwrap(root)) != root) {
                        root = unwrapped;
                    }
                    // Then walk to the deepest root cause
                    while (root.getCause() != null) {
                        root = root.getCause();
                    }

                    assertThat(root)
                            .isInstanceOf(UnsupportedOperationException.class)
                            .hasMessage("Expected exception");
                });
    }
}
