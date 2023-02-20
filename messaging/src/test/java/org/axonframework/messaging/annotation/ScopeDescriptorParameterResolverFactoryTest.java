/*
 * Copyright (c) 2010-2023. Axon Framework
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.axonframework.messaging.annotation;

import org.axonframework.messaging.Message;
import org.axonframework.messaging.NoScopeDescriptor;
import org.axonframework.messaging.ScopeDescriptor;
import org.junit.jupiter.api.*;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link ScopeDescriptorParameterResolverFactory}.
 *
 * @author Steven van Beelen
 */
class ScopeDescriptorParameterResolverFactoryTest {

    private final ScopeDescriptorParameterResolverFactory testSubject = new ScopeDescriptorParameterResolverFactory();

    private Method scopeDescriptorLessMethod;
    private Method scopeDescriptorUsingMethod;
    private Message<?> testMessage;

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        scopeDescriptorUsingMethod = getClass().getMethod("someScopeDescriptorUsingMethod", ScopeDescriptor.class);
        scopeDescriptorLessMethod = getClass().getMethod("someScopeDescriptorLessMethod", String.class);
        testMessage = mock(Message.class);
    }

    @Test
    void parameterResolverIsNullForScopeDescriptorLessMethod() {
        assertNull(testSubject.createInstance(scopeDescriptorLessMethod, scopeDescriptorLessMethod.getParameters(), 0));
    }

    @Test
    void resolvesNoScopeDescriptor() {
        ParameterResolver<ScopeDescriptor> resolver =
                testSubject.createInstance(scopeDescriptorUsingMethod, scopeDescriptorUsingMethod.getParameters(), 0);

        assertTrue(resolver.matches(testMessage));
        assertEquals(NoScopeDescriptor.INSTANCE, resolver.resolveParameterValue(testMessage));
    }

    @SuppressWarnings("unused")
    public void someScopeDescriptorLessMethod(String s) {
        // Used for testing
    }

    @SuppressWarnings("unused")
    public void someScopeDescriptorUsingMethod(ScopeDescriptor scopeDescriptor) {
        // Used for testing
    }
}