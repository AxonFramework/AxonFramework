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

package org.axonframework.axonserver.connector.util;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.MethodDescriptor;
import org.junit.jupiter.api.*;

import java.io.InputStream;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.*;

class GrpcBufferingInterceptorTest {

    private CallOptions mockCallOptions;
    private Channel mockChannel;
    private ClientCall.Listener<Object> mockResponseListener;
    private ClientCall<Object, Object> mockCall;

    @BeforeEach
    void setUp() throws Exception {
        mockCallOptions = CallOptions.DEFAULT;
        mockChannel = mock(Channel.class);
        mockResponseListener = mock(ClientCall.Listener.class);
        mockCall = mock(ClientCall.class);
        when(mockChannel.newCall(any(), any())).thenReturn(mockCall);

    }

    @Test
    void testInterceptClientCall_BiDiStreaming() {
        MethodDescriptor<Object, Object> method = buildMethod(MethodDescriptor.MethodType.BIDI_STREAMING);

        GrpcBufferingInterceptor testSubject = new GrpcBufferingInterceptor(1000);
        ClientCall<Object, Object> actual = testSubject.interceptCall(method, mockCallOptions, mockChannel);

        verify(mockChannel).newCall(method, mockCallOptions);

        actual.start(mockResponseListener, null);

        verify(mockCall).request(1000);
    }

    @Test
    void testInterceptClientCall_ServerStreaming() {
        MethodDescriptor<Object, Object> method = buildMethod(MethodDescriptor.MethodType.SERVER_STREAMING);

        GrpcBufferingInterceptor testSubject = new GrpcBufferingInterceptor(1000);
        ClientCall<Object, Object> actual = testSubject.interceptCall(method, mockCallOptions, mockChannel);

        verify(mockChannel).newCall(method, mockCallOptions);

        actual.start(mockResponseListener, null);

        verify(mockCall).request(1000);
    }

    @Test
    void testInterceptClientCall_ClientStreaming() {
        MethodDescriptor<Object, Object> method = buildMethod(MethodDescriptor.MethodType.CLIENT_STREAMING);

        GrpcBufferingInterceptor testSubject = new GrpcBufferingInterceptor(1000);
        ClientCall<Object, Object> actual = testSubject.interceptCall(method, mockCallOptions, mockChannel);

        verify(mockChannel).newCall(method, mockCallOptions);

        actual.start(mockResponseListener, null);

        verify(mockCall, never()).request(anyInt());
    }

    @Test
    void testInterceptClientCall_NoStreaming() {
        MethodDescriptor<Object, Object> method = buildMethod(MethodDescriptor.MethodType.UNARY);

        GrpcBufferingInterceptor testSubject = new GrpcBufferingInterceptor(1000);
        ClientCall<Object, Object> actual = testSubject.interceptCall(method, mockCallOptions, mockChannel);

        verify(mockChannel).newCall(method, mockCallOptions);

        actual.start(mockResponseListener, null);

        verify(mockCall, never()).request(anyInt());
    }

    @Test
    void testInterceptClientCall_ZeroBuffer() {
        MethodDescriptor<Object, Object> method = buildMethod(MethodDescriptor.MethodType.BIDI_STREAMING);

        GrpcBufferingInterceptor testSubject = new GrpcBufferingInterceptor(0);
        ClientCall<Object, Object> actual = testSubject.interceptCall(method, mockCallOptions, mockChannel);

        verify(mockChannel).newCall(method, mockCallOptions);

        actual.start(mockResponseListener, null);

        verify(mockCall, never()).request(anyInt());
    }

    private MethodDescriptor<Object, Object> buildMethod(MethodDescriptor.MethodType type) {
        return MethodDescriptor.newBuilder()
                               .setFullMethodName("test")
                               .setType(type)
                               .setRequestMarshaller(new StubMarshaller())
                               .setResponseMarshaller(new StubMarshaller())
                               .build();
    }

    private static class StubMarshaller implements MethodDescriptor.Marshaller<Object> {
        @Override
        public InputStream stream(Object value) {
            return null;
        }

        @Override
        public Object parse(InputStream stream) {
            return null;
        }
    }
}
