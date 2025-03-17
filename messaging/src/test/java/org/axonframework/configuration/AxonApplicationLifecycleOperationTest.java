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

package org.axonframework.configuration;

import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test suite implementation validating the lifecycle operation registration of the {@link AxonApplication}.
 *
 * @author Steven van Beelen
 */
class AxonApplicationLifecycleOperationTest extends ConfigurerLifecycleOperationTestSuite<AxonApplication> {

    @Override
    public AxonApplication createConfigurer() {
        return AxonApplication.create();
    }

    @Test
    void lifecycleHandlersProceedToFollowingPhaseForNeverEndingPhases() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        AxonConfiguration testConfig = configurer.registerLifecyclePhaseTimeout(100, TimeUnit.MILLISECONDS)
                                                 .build();

        LifecycleManagedInstance phaseZeroHandler = spy(new LifecycleManagedInstance());
        LifecycleManagedInstance extremelySlowPhaseOneHandler = spy(new LifecycleManagedInstance(invoked));
        LifecycleManagedInstance phaseTwoHandler = spy(new LifecycleManagedInstance());

        testConfig.onStart(0, phaseZeroHandler::start);
        testConfig.onStart(1, extremelySlowPhaseOneHandler::uncompletableStart);
        testConfig.onStart(2, phaseTwoHandler::start);

        testConfig.start();

        InOrder lifecycleOrder = inOrder(phaseZeroHandler, extremelySlowPhaseOneHandler, phaseTwoHandler);
        lifecycleOrder.verify(phaseZeroHandler).start();
        lifecycleOrder.verify(extremelySlowPhaseOneHandler).uncompletableStart();
        lifecycleOrder.verify(phaseTwoHandler).start();
        assertFalse(invoked.get());
    }

    @Test
    void registerLifecyclePhaseTimeoutWithZeroTimeoutThrowsIllegalArgumentException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> configurer.registerLifecyclePhaseTimeout(0, TimeUnit.SECONDS)
        );
    }

    @Test
    void registerLifecyclePhaseTimeoutWithNegativeTimeoutThrowsIllegalArgumentException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> configurer.registerLifecyclePhaseTimeout(-1, TimeUnit.SECONDS)
        );
    }

    @Test
    void registerLifecyclePhaseTimeoutWithNullTimeUnitThrowsNullPointerException() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> configurer.registerLifecyclePhaseTimeout(1, null));
    }
}