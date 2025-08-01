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

package org.axonframework.messaging.interceptors;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.unitofwork.StubProcessingContext;
import org.axonframework.messaging.unitofwork.LegacyDefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.LegacyUnitOfWork;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link BeanValidationInterceptor}.
 *
 * @author Allard Buijze
 */
class BeanValidationInterceptorTest {

    private BeanValidationInterceptor<Message<?>> testSubject;

    private InterceptorChain interceptorChain;
    private LegacyUnitOfWork<Message<?>> uow;

    @BeforeEach
    void setUp() {
        testSubject = new BeanValidationInterceptor<>();

        interceptorChain = mock(InterceptorChain.class);
        uow = new LegacyDefaultUnitOfWork<>(null);
    }

    @Test
    void validateSimpleObject() throws Exception {
        uow.transformMessage(m -> new GenericMessage<>(
                new MessageType("message"), "Simple instance"
        ));

        ProcessingContext context = StubProcessingContext.forUnitOfWork(uow);

        testSubject.handle(uow, context, interceptorChain);

        verify(interceptorChain).proceedSync(context);
    }

    @Test
    void validateAnnotatedObject_IllegalNullValue() throws Exception {
        uow.transformMessage(m -> new GenericMessage<>(
                new MessageType("message"), new JSR303AnnotatedInstance(null)
        ));
        ProcessingContext context = StubProcessingContext.forUnitOfWork(uow);
        try {
            testSubject.handle(uow, context, interceptorChain);
            fail("Expected exception");
        } catch (org.axonframework.messaging.interceptors.JSR303ViolationException e) {
            assertFalse(e.getViolations().isEmpty());
        }
        verify(interceptorChain, never()).proceedSync(context);
    }

    @Test
    void validateAnnotatedObject_LegalValue() throws Exception {
        uow.transformMessage(m -> new GenericMessage<>(
                new MessageType("message"), new JSR303AnnotatedInstance("abc")
        ));
        ProcessingContext context = StubProcessingContext.forUnitOfWork(uow);

        testSubject.handle(uow, context, interceptorChain);

        verify(interceptorChain).proceedSync(context);
    }

    @Test
    void validateAnnotatedObject_IllegalValue() throws Exception {
        uow.transformMessage(m -> new GenericMessage<>(
                new MessageType("message"), new JSR303AnnotatedInstance("bea")
        ));
        ProcessingContext context = StubProcessingContext.forUnitOfWork(uow);

        try {
            testSubject.handle(uow, context, interceptorChain);
            fail("Expected exception");
        } catch (JSR303ViolationException e) {
            assertFalse(e.getViolations().isEmpty());
        }

        verify(interceptorChain, never()).proceedSync(context);
    }

    @Test
    void customValidatorFactory() throws Exception {
        uow.transformMessage(m -> new GenericMessage<Object>(new MessageType("message"),
                                                             new JSR303AnnotatedInstance("abc")));
        ProcessingContext context = StubProcessingContext.forUnitOfWork(uow);
        ValidatorFactory mockValidatorFactory = spy(Validation.buildDefaultValidatorFactory());
        testSubject = new BeanValidationInterceptor<>(mockValidatorFactory);
        testSubject.handle(uow, context, interceptorChain);
        verify(mockValidatorFactory).getValidator();
    }

    public static class JSR303AnnotatedInstance {

        @SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal", "unused"})
        @Pattern(regexp = "ab.*")
        @NotNull
        private String notNull;

        public JSR303AnnotatedInstance(String notNull) {
            this.notNull = notNull;
        }
    }
}
