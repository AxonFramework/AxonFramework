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

import org.axonframework.commandhandling.CommandCallback;
import org.jgroups.Address;
import org.jgroups.View;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

/**
 * @author Srideep Prasad
 */
@RunWith(MockitoJUnitRunner.class)
public class MemberAwareCommandCallbackTest {

    private MemberAwareCommandCallback<Object> memberAwareCommandCallback;
    @Mock
    private Address mockDest;
    @Mock
    private CommandCallback<Object> mockCallback;

    @Before
    public void setup(){
        memberAwareCommandCallback = new MemberAwareCommandCallback<Object>(mockDest, mockCallback);
    }


    @Test
    public void testIsMemberAliveTrue(){
        View mockView = mock(View.class);
        when(mockView.containsMember(mockDest)).thenReturn(true);

        assertTrue(memberAwareCommandCallback.isMemberLive(mockView));
        verify(mockView).containsMember(mockDest);
    }

    @Test
    public void testIsMemberAliveFalse(){
        View mockView = mock(View.class);
        when(mockView.containsMember(mockDest)).thenReturn(false);

        assertFalse(memberAwareCommandCallback.isMemberLive(mockView));
        verify(mockView).containsMember(mockDest);
    }

    @Test
    public void testOnSuccess(){
        Object dummyVal = new Object();

        memberAwareCommandCallback.onSuccess(dummyVal);

        verify(mockCallback).onSuccess(dummyVal);
    }

    @Test
    public void testOnFailure(){
        Exception dummyException = new Exception();

        memberAwareCommandCallback.onFailure(dummyException);

        verify(mockCallback).onFailure(dummyException);
    }



}
