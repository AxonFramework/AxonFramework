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

package org.axonframework.messaging.core.annotation;

import org.axonframework.messaging.core.annotation.FixedValueParameterResolver;
import org.axonframework.messaging.core.annotation.HierarchicalParameterResolverFactory;
import org.axonframework.messaging.core.annotation.ParameterResolver;
import org.axonframework.messaging.core.annotation.ParameterResolverFactory;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.lang.reflect.Parameter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HierarchicalParameterResolverFactoryTest {

    @Test
    void resolvesComponentFromChildIfExistsInBoth() throws NoSuchMethodException {
        ParameterResolverFactory parent = Mockito.mock(ParameterResolverFactory.class);
        //noinspection rawtypes
        FixedValueParameterResolver resolverParent = new FixedValueParameterResolver<>("parent");
        //noinspection unchecked
        when(parent.createInstance(any(), any(), eq(0))).thenReturn(resolverParent);

        ParameterResolverFactory child = Mockito.mock(ParameterResolverFactory.class);
        //noinspection rawtypes
        FixedValueParameterResolver resolverChild = new FixedValueParameterResolver<>("child");
        //noinspection unchecked
        when(child.createInstance(any(), any(), eq(0))).thenReturn(resolverChild);

        HierarchicalParameterResolverFactory factory = HierarchicalParameterResolverFactory.create(parent, child);

        ParameterResolver<?> result = factory.createInstance(this.getClass().getDeclaredMethod(
                "resolvesComponentFromChildIfExistsInBoth"), new Parameter[]{}, 0);
        assertSame(resolverChild, result);
    }


    @Test
    void resolvesComponentFromParentIfDoesntExistInChild() throws NoSuchMethodException {
        ParameterResolverFactory parent = Mockito.mock(ParameterResolverFactory.class);
        //noinspection rawtypes
        FixedValueParameterResolver resolverParent = new FixedValueParameterResolver<>("parent");
        //noinspection unchecked
        when(parent.createInstance(any(), any(), eq(0))).thenReturn(resolverParent);

        ParameterResolverFactory child = Mockito.mock(ParameterResolverFactory.class);
        when(child.createInstance(any(), any(), eq(0))).thenReturn(null);

        HierarchicalParameterResolverFactory factory = HierarchicalParameterResolverFactory.create(parent, child);

        ParameterResolver<?> result = factory.createInstance(this.getClass().getDeclaredMethod(
                "resolvesComponentFromParentIfDoesntExistInChild"), new Parameter[]{}, 0);
        assertSame(resolverParent, result);
    }
}