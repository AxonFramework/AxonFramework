/*
 * Copyright (c) 2010-2018. Axon Framework
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

import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.FluxSink;

/**
 * @author Stefan Dragisic
 */
public class ReactiveCallback<C, R> implements Publisher<CommandResultMessage<? extends R>>
        , CommandCallback<C, R> {

    EmitterProcessor<CommandResultMessage<? extends R>> commandResultMessageEmitter = EmitterProcessor.create(1);
    FluxSink<CommandResultMessage<? extends R>> sink = commandResultMessageEmitter.sink();

    @Override
    public void onResult(CommandMessage<? extends C> commandMessage,
                         CommandResultMessage<? extends R> commandResultMessage) {
        if (commandResultMessage.isExceptional()) {
            sink.error(commandResultMessage.exceptionResult());
        } else {
            sink.next(commandResultMessage);
        }
        sink.complete();
    }


    @Override
    public void subscribe(Subscriber<? super CommandResultMessage<? extends R>> s) {
        commandResultMessageEmitter.subscribe(s);
    }
}
