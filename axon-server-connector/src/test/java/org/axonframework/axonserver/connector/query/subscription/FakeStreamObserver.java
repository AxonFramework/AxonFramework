/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.axonserver.connector.query.subscription;

import io.grpc.stub.StreamObserver;

import java.util.LinkedList;
import java.util.List;

/**
 * Created by Sara Pellegrini on 18/06/2018.
 * sara.pellegrini@gmail.com
 */
public class FakeStreamObserver<M> implements StreamObserver<M> {

    private List<M> values = new LinkedList<>();
    private List<Throwable> errors = new LinkedList<>();
    private int completedCount = 0;

    @Override
    public void onNext(M value) {
        values.add(value);
    }

    @Override
    public void onError(Throwable t) {
        errors.add(t);
    }

    @Override
    public void onCompleted() {
        completedCount++;
    }

    public List<M> values() {
        return values;
    }

    public List<Throwable> errors() {
        return errors;
    }

    public int completedCount() {
        return completedCount;
    }
}
