/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.javax.messaging.interceptors;

import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.junit.jupiter.api.*;

import javax.validation.Validation;
import javax.validation.ValidatorFactory;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link org.axonframework.messaging.interceptors.BeanValidationInterceptor}.
 *
 * @author Allard Buijze
 */
class BeanValidationInterceptorTest {

    private BeanValidationInterceptor<Message<?>> testSubject;

    private InterceptorChain interceptorChain;
    private UnitOfWork<Message<?>> uow;

    @BeforeEach
    void setUp() {
        testSubject = new BeanValidationInterceptor<>();

        interceptorChain = mock(InterceptorChain.class);
        uow = new DefaultUnitOfWork<>(null);
    }

    @Test
    void validateSimpleObject() throws Exception {
        uow.transformMessage(m -> new GenericMessage<>("Simple instance"));

        testSubject.handle(uow, interceptorChain);

        verify(interceptorChain).proceed();
    }

    @Test
    void validateAnnotatedObject_IllegalNullValue() throws Exception {
        uow.transformMessage(m -> new GenericMessage<Object>(new JSR303AnnotatedInstance(null)));
        try {
            testSubject.handle(uow, interceptorChain);
            fail("Expected exception");
        } catch (JSR303ViolationException e) {
            assertFalse(e.getViolations().isEmpty());
        }
        verify(interceptorChain, never()).proceed();
    }

    @Test
    void validateAnnotatedObject_LegalValue() throws Exception {
        uow.transformMessage(m -> new GenericMessage<>(new JSR303AnnotatedInstance("abc")));

        testSubject.handle(uow, interceptorChain);

        verify(interceptorChain).proceed();
    }

    @Test
    void validateAnnotatedObject_IllegalValue() throws Exception {
        uow.transformMessage(m -> new GenericMessage<Object>(new JSR303AnnotatedInstance("bea")));

        try {
            testSubject.handle(uow, interceptorChain);
            fail("Expected exception");
        } catch (JSR303ViolationException e) {
            assertFalse(e.getViolations().isEmpty());
        }

        verify(interceptorChain, never()).proceed();
    }

    @Test
    void customValidatorFactory() throws Exception {
        uow.transformMessage(m -> new GenericMessage<Object>(new JSR303AnnotatedInstance("abc")));
        ValidatorFactory mockValidatorFactory = spy(Validation.buildDefaultValidatorFactory());
        testSubject = new BeanValidationInterceptor<>(mockValidatorFactory);
        testSubject.handle(uow, interceptorChain);
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
