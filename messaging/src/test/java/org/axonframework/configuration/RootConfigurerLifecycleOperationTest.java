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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.*;
import org.mockito.*;

/**
 * Test suite implementation validating the lifecycle operation registration of the {@link RootConfigurer}.
 *
 * @author Steven van Beelen
 */
class RootConfigurerLifecycleOperationTest extends ConfigurerLifecycleOperationTestSuite<RootConfigurer> {

    @Override
    public RootConfigurer buildConfigurer() {
        return RootConfigurer.defaultConfigurer();
    }

    @Test
    void lifecycleHandlersProceedToFollowingPhaseForNeverEndingPhases() {
        AtomicBoolean invoked = new AtomicBoolean(false);

        LifecycleManagedInstance phaseZeroHandler = spy(new LifecycleManagedInstance());
        LifecycleManagedInstance extremelySlowPhaseOneHandler = spy(new LifecycleManagedInstance(invoked));
        LifecycleManagedInstance phaseTwoHandler = spy(new LifecycleManagedInstance());

        testSubject.onStart(0, phaseZeroHandler::start);
        testSubject.onStart(1, extremelySlowPhaseOneHandler::uncompletableStart);
        testSubject.onStart(2, phaseTwoHandler::start);

        testSubject.registerLifecyclePhaseTimeout(100, TimeUnit.MILLISECONDS)
                   .start();

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
                () -> testSubject.registerLifecyclePhaseTimeout(0, TimeUnit.SECONDS)
        );
    }

    @Test
    void registerLifecyclePhaseTimeoutWithNegativeTimeoutThrowsIllegalArgumentException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> testSubject.registerLifecyclePhaseTimeout(-1, TimeUnit.SECONDS)
        );
    }

    @Test
    void registerLifecyclePhaseTimeoutWithNullTimeUnitThrowsNullPointerException() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> testSubject.registerLifecyclePhaseTimeout(1, null));
    }
}