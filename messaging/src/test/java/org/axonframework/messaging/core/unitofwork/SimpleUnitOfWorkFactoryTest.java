/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.core.unitofwork;

import org.axonframework.messaging.core.EmptyApplicationContext;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for {@link SimpleUnitOfWorkFactory}.
 *
 * @author John Hendrikx
 */
class SimpleUnitOfWorkFactoryTest {

    @Test
    void shouldEnhanceUnitOfWorkProcessingLifecycle() {
        AtomicBoolean enhancerCalled = new AtomicBoolean();
        AtomicBoolean preInvocationCalled = new AtomicBoolean();

        SimpleUnitOfWorkFactory factory = new SimpleUnitOfWorkFactory(
            EmptyApplicationContext.INSTANCE,
            c -> c.registerProcessingLifecycleEnhancer(pl -> {
                enhancerCalled.set(true);
                pl.runOnPreInvocation(pc -> preInvocationCalled.set(true));
            })
        );

        UnitOfWork unitOfWork = factory.create();

        assertThat(enhancerCalled).isTrue();
        assertThat(preInvocationCalled).isFalse();

        unitOfWork.execute();

        assertThat(preInvocationCalled).isTrue();
    }

}
