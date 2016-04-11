/*
 * Copyright (c) 2010-2012. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.messaging.interceptors;

import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.junit.Before;
import org.junit.Test;

import javax.validation.Validation;
import javax.validation.ValidatorFactory;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class BeanValidationInterceptorTest {

    private BeanValidationInterceptor<Message<?>> testSubject;
    private InterceptorChain mockInterceptorChain;
    private UnitOfWork<Message<?>> uow;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        testSubject = new BeanValidationInterceptor<>();
        mockInterceptorChain = mock(InterceptorChain.class);
        uow = new DefaultUnitOfWork<>(null);
    }

    @Test
    public void testValidateSimpleObject() throws Exception {
        uow.transformMessage(m -> new GenericMessage<>("Simple instance"));
        testSubject.handle(uow, mockInterceptorChain);
        verify(mockInterceptorChain).proceed();
    }

    @Test
    public void testValidateAnnotatedObject_IllegalNullValue() throws Exception {
        uow.transformMessage(m -> new GenericMessage<Object>(new JSR303AnnotatedInstance(null)));
        try {
            testSubject.handle(uow, mockInterceptorChain);
            fail("Expected exception");
        } catch (JSR303ViolationException e) {
            assertFalse(e.getViolations().isEmpty());
        }
        verify(mockInterceptorChain, never()).proceed();
    }

    @Test
    public void testValidateAnnotatedObject_LegalValue() throws Exception {
        uow.transformMessage(m -> new GenericMessage<>(new JSR303AnnotatedInstance("abc")));
        testSubject.handle(uow, mockInterceptorChain);
        verify(mockInterceptorChain).proceed();
    }

    @Test
    public void testValidateAnnotatedObject_IllegalValue() throws Exception {
        uow.transformMessage(m -> new GenericMessage<Object>(new JSR303AnnotatedInstance("bea")));
        try {
            testSubject.handle(
                    uow, mockInterceptorChain);
            fail("Expected exception");
        } catch (JSR303ViolationException e) {
            assertFalse(e.getViolations().isEmpty());
        }
        verify(mockInterceptorChain, never()).proceed();
    }

    @Test
    public void testCustomValidatorFactory() throws Exception {
        uow.transformMessage(m -> new GenericMessage<Object>(new JSR303AnnotatedInstance("abc")));
        ValidatorFactory mockValidatorFactory = spy(Validation.buildDefaultValidatorFactory());
        testSubject = new BeanValidationInterceptor<>(mockValidatorFactory);
        testSubject.handle(uow, mockInterceptorChain);
        verify(mockValidatorFactory).getValidator();
    }

    public static class JSR303AnnotatedInstance {

        @Pattern(regexp = "ab.*")
        @NotNull
        private String notNull;

        public JSR303AnnotatedInstance(String notNull) {
            this.notNull = notNull;
        }
    }
}
