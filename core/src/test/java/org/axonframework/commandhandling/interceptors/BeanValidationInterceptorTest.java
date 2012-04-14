/*
 * Copyright (c) 2010-2011. Axon Framework
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

package org.axonframework.commandhandling.interceptors;

import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.InterceptorChain;
import org.axonframework.unitofwork.UnitOfWork;
import org.junit.*;

import javax.validation.Validation;
import javax.validation.ValidatorFactory;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class BeanValidationInterceptorTest {

    private BeanValidationInterceptor testSubject;
    private InterceptorChain mockInterceptorChain;
    private UnitOfWork uow;

    @Before
    public void setUp() throws Exception {
        testSubject = new BeanValidationInterceptor();
        mockInterceptorChain = mock(InterceptorChain.class);
        uow = mock(UnitOfWork.class);
    }

    @Test
    public void testValidateSimpleObject() throws Throwable {
        testSubject.handle(new GenericCommandMessage<Object>("Simple instance"), uow, mockInterceptorChain);
        verify(mockInterceptorChain).proceed();
    }

    @Test
    public void testValidateAnnotatedObject_IllegalNullValue() throws Throwable {
        try {
            testSubject.handle(new GenericCommandMessage<Object>(new JSR303AnnotatedInstance(null)),
                               uow, mockInterceptorChain);
            fail("Expected exception");
        } catch (JSR303ViolationException e) {
            assertFalse(e.getViolations().isEmpty());
        }
        verify(mockInterceptorChain, never()).proceed();
    }

    @Test
    public void testValidateAnnotatedObject_LegalValue() throws Throwable {
        testSubject.handle(new GenericCommandMessage<Object>(new JSR303AnnotatedInstance("abc")),
                           uow, mockInterceptorChain);

        verify(mockInterceptorChain).proceed();
    }

    @Test
    public void testValidateAnnotatedObject_IllegalValue() throws Throwable {
        try {
            testSubject.handle(new GenericCommandMessage<Object>(new JSR303AnnotatedInstance("bea")),
                               uow, mockInterceptorChain);
            fail("Expected exception");
        } catch (JSR303ViolationException e) {
            assertFalse(e.getViolations().isEmpty());
        }
        verify(mockInterceptorChain, never()).proceed();
    }

    @Test
    public void testCustomValidatorFactory() throws Throwable {
        ValidatorFactory mockValidatorFactory = spy(Validation.buildDefaultValidatorFactory());
        testSubject = new BeanValidationInterceptor(mockValidatorFactory);

        testSubject.handle(new GenericCommandMessage<Object>(new JSR303AnnotatedInstance("abc")),
                           uow, mockInterceptorChain);

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
