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

package org.axonframework.messaging.core.interception;

import org.axonframework.messaging.core.ApplicationContext;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageHandlerInterceptorChain;
import org.axonframework.messaging.core.MessagingTestUtils;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.core.unitofwork.StubProcessingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ApplicationContextHandlerInterceptorTest {

    private static final Message TEST_MESSAGE = MessagingTestUtils.command("testCommand", "payload");

    private ApplicationContext moduleApplicationContext;
    private ApplicationContextHandlerInterceptor testSubject;

    @BeforeEach
    void setUp() {
        moduleApplicationContext = mock(ApplicationContext.class);
        testSubject = new ApplicationContextHandlerInterceptor(moduleApplicationContext);
    }

    @Nested
    class Construction {

        @Test
        void rejectsNullApplicationContext() {
            // when / then
            assertThatThrownBy(() -> new ApplicationContextHandlerInterceptor(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class InterceptOnHandle {

        @SuppressWarnings("unchecked")
        private final MessageHandlerInterceptorChain<Message> chain = mock(MessageHandlerInterceptorChain.class);

        @Test
        void setsApplicationContextResourceOnContext() {
            // given
            ProcessingContext context = new StubProcessingContext();

            // when
            testSubject.interceptOnHandle(TEST_MESSAGE, context, chain);

            // then
            ArgumentCaptor<ProcessingContext> contextCaptor = ArgumentCaptor.forClass(ProcessingContext.class);
            verify(chain).proceed(eq(TEST_MESSAGE), contextCaptor.capture());

            ProcessingContext overriddenContext = contextCaptor.getValue();
            assertThat(overriddenContext.getResource(ProcessingContext.APPLICATION_CONTEXT))
                    .isSameAs(moduleApplicationContext);
        }

        @Test
        void proceedsWithOriginalMessage() {
            // given
            ProcessingContext context = new StubProcessingContext();

            // when
            testSubject.interceptOnHandle(TEST_MESSAGE, context, chain);

            // then
            verify(chain).proceed(eq(TEST_MESSAGE), any(ProcessingContext.class));
        }
    }
}
