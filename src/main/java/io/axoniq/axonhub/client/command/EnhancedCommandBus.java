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

package io.axoniq.axonhub.client.command;

import io.axoniq.axonhub.client.MessageHandlerInterceptorSupport;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.monitoring.MessageMonitor;

/**
 * Created by Sara Pellegrini on 29/03/2018.
 * sara.pellegrini@gmail.com
 */
public class EnhancedCommandBus extends SimpleCommandBus implements CommandBus,
        MessageHandlerInterceptorSupport<CommandMessage<?>> {

    public EnhancedCommandBus() {
    }

    public EnhancedCommandBus(TransactionManager transactionManager,
                              MessageMonitor<? super CommandMessage<?>> messageMonitor) {
        super(transactionManager, messageMonitor);
    }

}
