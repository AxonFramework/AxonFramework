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

package io.axoniq.axonhub.client.query;

import io.axoniq.axonhub.client.MessageHandlerInterceptorSupport;
import org.axonframework.common.Registration;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryInvocationErrorHandler;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.SimpleQueryBus;

/**
 * Created by Sara Pellegrini on 29/03/2018.
 * sara.pellegrini@gmail.com
 */
public class EnhancedQueryBus  extends SimpleQueryBus implements QueryBus,
        MessageHandlerInterceptorSupport<QueryMessage<?,?>> {

    public EnhancedQueryBus() {
    }

    public EnhancedQueryBus(MessageMonitor<? super QueryMessage<?, ?>> messageMonitor,
                            TransactionManager transactionManager,
                            QueryInvocationErrorHandler errorHandler) {
        super(messageMonitor, transactionManager, errorHandler);
    }

    @Override
    public Registration registerHandlerInterceptor(MessageHandlerInterceptor handlerInterceptor) {
        return super.registerHandlerInterceptor(handlerInterceptor);
    }
}
