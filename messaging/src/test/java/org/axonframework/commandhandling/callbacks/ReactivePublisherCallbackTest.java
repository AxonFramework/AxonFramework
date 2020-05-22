/*
 * Copyright (c) 2010-2020. Axon Framework
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

package org.axonframework.commandhandling.callbacks;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.utils.MockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.axonframework.commandhandling.GenericCommandResultMessage.asCommandResultMessage;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Stefan Dragisic
 */
class ReactivePublisherCallbackTest {

    private static final CommandMessage<Object> COMMAND_MESSAGE = GenericCommandMessage.asCommandMessage("Test");
    private static final CommandResultMessage<String> COMMAND_RESPONSE_MESSAGE =
            asCommandResultMessage("Hello reactive world");
    private volatile ReactivePublisherCallback<Object, Object> testSubject;


    @BeforeEach
    void setUp() {
        testSubject = new ReactivePublisherCallback<>();
    }


    @Test
    public void testOnSuccessCallback() throws Exception {
        testSubject.onResult(COMMAND_MESSAGE, COMMAND_RESPONSE_MESSAGE);

        StepVerifier.create(Mono.from(testSubject))
                .expectSubscription()
                .expectNext(COMMAND_RESPONSE_MESSAGE)
                .expectComplete()
                .verify();
    }

    @Test
    public void testOnErrorCallback(){
        RuntimeException exception = new MockException();
        testSubject.onResult(COMMAND_MESSAGE, asCommandResultMessage(exception));

        StepVerifier.create(Mono.from(testSubject))
                .expectSubscription()
                .expectError(MockException.class)
                .verify();
    }

    @Test
    public void testOnSuccessForLimitedTime_Timeout() throws Exception {
        testSubject.onResult(COMMAND_MESSAGE, COMMAND_RESPONSE_MESSAGE);
        final CountDownLatch successCountDownLatch = new CountDownLatch(1);

        Mono.from(testSubject)
                .delaySubscription(Duration.ofSeconds(2))
                .subscribe(it -> successCountDownLatch.countDown());

        assertTrue(successCountDownLatch.await(10, TimeUnit.SECONDS));
    }

    @Test
    public void testOnErrorForLimitedTime_Timeout() throws Exception {
        testSubject.onResult(COMMAND_MESSAGE, COMMAND_RESPONSE_MESSAGE);
        final CountDownLatch successCountDownLatch = new CountDownLatch(1);

        Mono.from(testSubject)
                .delaySubscription(Duration.ofSeconds(2))
                .subscribe(it -> successCountDownLatch.countDown());

        assertFalse(successCountDownLatch.await(1, TimeUnit.SECONDS));
    }

    @Test
    public void testOnResultReturnsMessageWithTimeoutExceptionOnTimeout(){
        StepVerifier.create(Mono.from(testSubject).timeout(Duration.ofSeconds(1)))
                .expectSubscription()
                .expectError(TimeoutException.class)
                .verify();
    }

}
