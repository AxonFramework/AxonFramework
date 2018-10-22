/*
 * Copyright (c) 2018. AxonIQ
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

package org.axonframework.axonserver.connector;

import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.axonserver.connector.DispatchInterceptors;
import org.junit.*;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Created by Sara Pellegrini on 03/04/2018.
 * sara.pellegrini@gmail.com
 */
public class DispatchInterceptorsTest {

    @Test
    public void registerInterceptors() {
        List<String> results = new ArrayList<>();
        DispatchInterceptors<Message<?>> dispatchInterceptors = new DispatchInterceptors<>();
        dispatchInterceptors.registerDispatchInterceptor(messages -> (a, b) -> {
                                                             results.add("Interceptor One");
                                                             return b;
                                                         }
        );

        dispatchInterceptors.registerDispatchInterceptor(messages -> (a, b) -> {
                                                             results.add("Interceptor Two");
                                                             return b;
                                                         }
        );
        dispatchInterceptors.intercept(new GenericMessage<>("payload"));
        assertEquals("Interceptor One", results.get(0));
        assertEquals("Interceptor Two", results.get(1));
        assertEquals(2, results.size());

    }

}