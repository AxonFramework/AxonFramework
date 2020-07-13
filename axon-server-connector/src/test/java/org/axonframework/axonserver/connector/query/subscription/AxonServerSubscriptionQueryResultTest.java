/*
 * Copyright (c) 2010-2019. Axon Framework
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

package org.axonframework.axonserver.connector.query.subscription;

import io.axoniq.axonserver.grpc.query.QueryResponse;
import io.axoniq.axonserver.grpc.query.QueryUpdate;
import io.axoniq.axonserver.grpc.query.SubscriptionQueryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Created by Sara Pellegrini on 18/06/2018.
 * sara.pellegrini@gmail.com
 */
class AxonServerSubscriptionQueryResultTest {

    private SubscriptionQueryResponse update;
    private SubscriptionQueryResponse initialResult;


    @BeforeEach
    void setUp(){
        update = SubscriptionQueryResponse.newBuilder().setUpdate(QueryUpdate.newBuilder()).build();
        initialResult = SubscriptionQueryResponse.newBuilder().setInitialResult(QueryResponse.newBuilder()).build();
    }

    @Test
    void toBeImplemented() {
        fail("Tests need to be implemented here");
    }
}
