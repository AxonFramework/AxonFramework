/*
 * Copyright (c) 2010-2013. Axon Framework
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

package org.axonframework.common.configuration;

import org.axonframework.common.annotation.ParameterResolverFactory;
import org.junit.*;

import java.lang.annotation.Annotation;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class AnnotationConfigurationTest {

    @Test
    public void testConfigurationForSuperClassIsUsed() throws Exception {
        final ParameterResolverFactory mockResolver = mock(ParameterResolverFactory.class);
        AnnotationConfiguration.configure(Object.class).useParameterResolverFactory(mockResolver);

        ParameterResolverFactory factory = AnnotationConfiguration.readFor(String.class).getParameterResolverFactory();

        factory.createInstance(new Annotation[0], Object.class, new Annotation[0]);

        verify(mockResolver).createInstance(eq(new Annotation[0]), eq(Object.class), eq(new Annotation[0]));
    }

    @Test
    public void testConfigurationForExactClassIsUsed() throws Exception {
        final ParameterResolverFactory mockResolver = mock(ParameterResolverFactory.class);
        AnnotationConfiguration.configure(String.class).useParameterResolverFactory(mockResolver);

        ParameterResolverFactory factory = AnnotationConfiguration.readFor(String.class).getParameterResolverFactory();

        factory.createInstance(new Annotation[0], Object.class, new Annotation[0]);

        verify(mockResolver).createInstance(eq(new Annotation[0]), eq(Object.class), eq(new Annotation[0]));
    }

    @Test
    public void testDefaultConfigurationForUndefinedClassIsUsed() throws Exception {
        final ParameterResolverFactory mockResolver = mock(ParameterResolverFactory.class);
        AnnotationConfiguration.defaultConfiguration().useParameterResolverFactory(mockResolver);

        ParameterResolverFactory factory = AnnotationConfiguration.readFor(String.class).getParameterResolverFactory();

        factory.createInstance(new Annotation[0], Object.class, new Annotation[0]);

        verify(mockResolver).createInstance(eq(new Annotation[0]), eq(Object.class), eq(new Annotation[0]));
    }

    @After
    public void tearDown() throws Exception {
        AnnotationConfiguration.reset(String.class);
        AnnotationConfiguration.reset(Object.class);
    }
}
