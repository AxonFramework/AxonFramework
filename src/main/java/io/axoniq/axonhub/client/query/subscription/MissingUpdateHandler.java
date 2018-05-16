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

package io.axoniq.axonhub.client.query.subscription;

import org.axonframework.queryhandling.UpdateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by Sara Pellegrini on 11/05/2018.
 * sara.pellegrini@gmail.com
 */

public class MissingUpdateHandler implements UpdateHandler {

    private final Logger logger = LoggerFactory.getLogger(MissingUpdateHandler.class);

    private final String subscriptionId;

    public MissingUpdateHandler(String subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    @Override
    public void onInitialResult(Object initial) {
        logger.warn("Cannot handle initial result for subscription {} because of missing Update Handler", subscriptionId);
    }

    @Override
    public void onUpdate(Object update) {
        logger.warn("Cannot handle update for subscription {} because of missing Update Handler", subscriptionId);
    }

    @Override
    public void onCompleted() {
        logger.warn("Cannot complete subscription {} because of missing Update Handler", subscriptionId);
    }

    @Override
    public void onCompletedExceptionally(Throwable error) {
        logger.warn("Cannot complete exceptionally subscription {} because of missing Update Handler", subscriptionId);
    }
}
