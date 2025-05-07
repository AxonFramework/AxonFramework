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
        when(delegate.resolveOrThrow(String.class)).thenThrow(new MessageTypeNotResolvedException("Test exception"));
        when(fallback.resolveOrThrow(String.class)).thenReturn(expectedType);
        
        // when
        var result = resolver.resolveOrThrow(String.class);
        
        // then
        assertEquals(expectedType, result);
        verify(delegate).resolveOrThrow(String.class);
        verify(fallback).resolveOrThrow(String.class);
    }
    
    @Test
    void shouldUseDelegateWhenItSucceeds() {
        // given
        var delegate = mock(MessageTypeResolver.class);
        var fallback = mock(MessageTypeResolver.class);
        var resolver = new FallbackMessageTypeResolver(delegate, fallback);
        var expectedType = new MessageType("test.type", "1.0.0");
        when(delegate.resolveOrThrow(String.class)).thenReturn(expectedType);
        
        // when
        var result = resolver.resolveOrThrow(String.class);
        
        // then
        assertEquals(expectedType, result);
        verify(delegate).resolveOrThrow(String.class);
        verify(fallback, never()).resolveOrThrow(any());
    }
    
    @Test
    void shouldCacheFailuresAndSkipDelegateOnSubsequentCalls() {
        // given
        var delegate = mock(MessageTypeResolver.class);
        var fallback = mock(MessageTypeResolver.class);
        var resolver = new FallbackMessageTypeResolver(delegate, fallback);
        var expectedType = new MessageType("test.type", "1.0.0");
        when(delegate.resolveOrThrow(String.class)).thenThrow(new MessageTypeNotResolvedException("Test exception"));
        when(fallback.resolveOrThrow(String.class)).thenReturn(expectedType);
        
        // when
        var result1 = resolver.resolveOrThrow(String.class);
        
        // then
        assertEquals(expectedType, result1);
        verify(delegate).resolveOrThrow(String.class);
        verify(fallback).resolveOrThrow(String.class);
        
        // when
        var result2 = resolver.resolveOrThrow(String.class);
        
        // then
        assertEquals(expectedType, result2);
        verify(delegate).resolveOrThrow(String.class);
        verify(fallback, times(2)).resolveOrThrow(String.class);
    }
}