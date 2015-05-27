/*
 * Copyright (c) 2010-2015. Axon Framework
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

package org.axonframework.commandhandling.distributed.jgroups.support.callbacks;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.distributed.jgroups.CommandResponseProcessingFailedException;
import org.axonframework.commandhandling.distributed.jgroups.ReplyMessage;
import org.axonframework.serializer.Serializer;
import org.jgroups.Address;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.hamcrest.core.Is.is;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.refEq;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.isNotNull;

/**
 * @author Srideep Prasad
 */
@RunWith(MockitoJUnitRunner.class)
public class ReplyingCallbackTest {

    private ReplyingCallback replyingCallback;
    @Mock
    private JChannel mockChannel;
    @Mock
    private Message mockMsg;
    @Mock
    private CommandMessage mockCommandMsg;
    @Mock
    private Serializer mockSerializer;
    @Mock
    private Address mockAddr;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private static final String IDENTIFIER = "identifier";

    @Before
    public void setup(){
        replyingCallback = new ReplyingCallback(mockChannel,mockMsg,mockCommandMsg,mockSerializer);
        when(mockCommandMsg.getIdentifier()).thenReturn(IDENTIFIER);
        when(mockMsg.getSrc()).thenReturn(mockAddr);

    }

    @Test
    public void testOnSuccessWithSerializableMsg() throws Exception {
        Object dummyData = new Object();
        ReplyMessage expectedReplyMsg = new ReplyMessage(IDENTIFIER, dummyData, null, mockSerializer);

        replyingCallback.onSuccess(dummyData);

        verify(mockChannel).send(eq(mockAddr),refEq(expectedReplyMsg));
    }

    @Test
    public void testOnSuccessWithExceptionDuringChannelSend() throws Exception {
        Object dummyData = new Object();
        ReplyMessage expectedReplyMsg = new ReplyMessage(IDENTIFIER, dummyData, null, mockSerializer);
        Exception expectedCause = new Exception("Serialization Exception!");

        doThrow(expectedCause).when(mockChannel).send(same(mockAddr),refEq(expectedReplyMsg));
        expectedException.expect(CommandResponseProcessingFailedException.class);
        expectedException.expectCause(is(expectedCause));

        replyingCallback.onSuccess(dummyData);
    }

    @Test
    public void testOnFailureWithSerializableException() throws Exception {
        Exception exception = new Exception();
        ReplyMessage expectedReplyMsg = new ReplyMessage(IDENTIFIER, null, exception, mockSerializer);

        replyingCallback.onFailure(exception);

        verify(mockChannel).send(eq(mockAddr),refEq(expectedReplyMsg));
    }

    @Test
    public void testOnFailureWithExceptionDuringChannelSend() throws Exception {
        Exception exception = new Exception();
        ReplyMessage expectedReplyMsg = new ReplyMessage(IDENTIFIER, null, exception, mockSerializer);

        doThrow(new Exception("Serialization Exception!")).when(mockChannel).send(same(mockAddr), refEq(expectedReplyMsg));
        expectedException.expect(CommandResponseProcessingFailedException.class);
        expectedException.expectCause(is(isNotNull(Exception.class)));

        replyingCallback.onFailure(exception);
    }
}
