/*
 * Copyright (c) 2010-2024. Axon Framework
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

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

class FluxMessageStreamTest extends MessageStreamTest<String> {

    @Override
    MessageStream<Message<String>> createTestSubject(List<Message<String>> values) {
        return new FluxMessageStream<>(Flux.fromIterable(values));
    }

    @Override
    MessageStream<Message<String>> createTestSubject(List<Message<String>> values, Exception failure) {
        Flux<Message<String>> stringFlux = Flux.fromIterable(values).concatWith(Mono.error(failure));
        return new FluxMessageStream<>(stringFlux);
    }

    @Override
    String createRandomValidStreamEntry() {
        return "RandomValue" + ThreadLocalRandom.current().nextInt(10000);
    }
}