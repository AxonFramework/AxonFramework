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

package org.axonframework.messaging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FallbackMessageTypeResolverTest {

    @Test
    void shouldUseFallbackWhenDelegateThrowsException() {
        // given
        MessageTypeResolver delegate = mock(MessageTypeResolver.class);
        MessageTypeResolver fallback = mock(MessageTypeResolver.class);
        FallbackMessageTypeResolver resolver = new FallbackMessageTypeResolver(delegate, fallback);
        var expectedType = new MessageType("test.type", "1.0.0");
        when(delegate.resolve(String.class)).thenThrow(new MessageTypeNotResolvedException("Test exception"));
        when(fallback.resolve(String.class)).thenReturn(expectedType);
        
        // when
        var result = resolver.resolve(String.class);
        
        // then
        assertEquals(expectedType, result);
        verify(delegate).resolve(String.class);
        verify(fallback).resolve(String.class);
    }
    
    @Test
    void shouldUseDelegateWhenItSucceeds() {
        // given
        var delegate = mock(MessageTypeResolver.class);
        var fallback = mock(MessageTypeResolver.class);
        var resolver = new FallbackMessageTypeResolver(delegate, fallback);
        var expectedType = new MessageType("test.type", "1.0.0");
        when(delegate.resolve(String.class)).thenReturn(expectedType);
        
        // when
        var result = resolver.resolve(String.class);
        
        // then
        assertEquals(expectedType, result);
        verify(delegate).resolve(String.class);
        verify(fallback, never()).resolve(any());
    }
    
    @Test
    void shouldCacheFailuresAndSkipDelegateOnSubsequentCalls() {
        // given
        var delegate = mock(MessageTypeResolver.class);
        var fallback = mock(MessageTypeResolver.class);
        var resolver = new FallbackMessageTypeResolver(delegate, fallback);
        var expectedType = new MessageType("test.type", "1.0.0");
        when(delegate.resolve(String.class)).thenThrow(new MessageTypeNotResolvedException("Test exception"));
        when(fallback.resolve(String.class)).thenReturn(expectedType);
        
        // when
        var result1 = resolver.resolve(String.class);
        
        // then
        assertEquals(expectedType, result1);
        verify(delegate).resolve(String.class);
        verify(fallback).resolve(String.class);
        
        // when
        var result2 = resolver.resolve(String.class);
        
        // then
        assertEquals(expectedType, result2);
        verify(delegate).resolve(String.class);
        verify(fallback, times(2)).resolve(String.class);
    }
}