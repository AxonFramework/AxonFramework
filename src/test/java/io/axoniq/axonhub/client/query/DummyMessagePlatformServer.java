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

import com.google.protobuf.ByteString;
import io.axoniq.axonhub.QueryRequest;
import io.axoniq.axonhub.QueryResponse;
import io.axoniq.axonhub.QuerySubscription;
import io.axoniq.axonhub.grpc.QueryProviderInbound;
import io.axoniq.axonhub.grpc.QueryProviderOutbound;
import io.axoniq.axonhub.grpc.QueryServiceGrpc;
import io.axoniq.platform.MetaDataValue;
import io.axoniq.platform.SerializedObject;
import io.axoniq.axonhub.client.PlatformService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Author: marc
 */
public class DummyMessagePlatformServer {
    private final static Logger logger = LoggerFactory.getLogger(DummyMessagePlatformServer.class);
    private final int port;
    private Server server;
    private Map<QueryDefinition, Set<StreamObserver>> subscriptions = new HashMap<>();


    DummyMessagePlatformServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        server = ServerBuilder.forPort(port)
                .addService(new QueryHandler())
                .addService(new PlatformService(port))
                .build();
        server.start();
    }

    public void stop()  {
        try {
            server.shutdownNow().awaitTermination();
        } catch (InterruptedException ignore) {
        }
    }

    public Set subscriptions(String query, String response) {
        return subscriptions.get(new QueryDefinition(query, response));
    }


    class QueryHandler extends QueryServiceGrpc.QueryServiceImplBase {
        @Override
        public StreamObserver<QueryProviderOutbound> openStream(StreamObserver<QueryProviderInbound> responseObserver) {
            return new StreamObserver<QueryProviderOutbound>() {
                @Override
                public void onNext(QueryProviderOutbound queryProviderOutbound) {
                    switch(queryProviderOutbound.getRequestCase()) {
                        case SUBSCRIBE:
                            QueryDefinition queryDefinition = new QueryDefinition(queryProviderOutbound.getSubscribe());
                            subscriptions.computeIfAbsent(queryDefinition, k -> new HashSet<>()).add(responseObserver);
                            break;
                        case UNSUBSCRIBE:
                            Set<StreamObserver> connected = subscriptions.get(new QueryDefinition(queryProviderOutbound.getUnsubscribe()));
                            if( connected != null) connected.remove(responseObserver);
                            break;
                        case FLOWCONTROL:
                            break;
                        case QUERYRESPONSE:
                            break;
                        case REQUEST_NOT_SET:
                            break;
                    }
                }

                @Override
                public void onError(Throwable throwable) {

                }

                @Override
                public void onCompleted() {

                }
            };
        }

        @Override
        public void query(QueryRequest request, StreamObserver<QueryResponse> responseObserver) {
            long repeat = request.getMetaDataOrDefault("repeat", MetaDataValue.newBuilder().setNumberValue(1).build()).getNumberValue();
            long interval = request.getMetaDataOrDefault("interval", MetaDataValue.newBuilder().setNumberValue(0).build()).getNumberValue();
            for( long r = 0 ; r < repeat; r++) {
                responseObserver.onNext(QueryResponse.newBuilder()
                        .setSuccess(true)
                        .setMessageIdentifier(request.getMessageIdentifier())
                        .setPayload(SerializedObject.newBuilder()
                                .setData(ByteString.copyFromUtf8("<string>test</string>"))
                                .setType(String.class.getName())
                                .build())
                        .build());
                if( interval > 0 ) {
                    try {
                        Thread.sleep(interval);
                    } catch (InterruptedException e) {
                        logger.debug("Sleep interrupted");
                    }
                }
            }
            responseObserver.onCompleted();
        }
    }

    class QueryDefinition {
        private final String queryName;
        private final String responseName;

        QueryDefinition(QuerySubscription subscription) {
            this.queryName = subscription.getQuery();
            this.responseName = subscription.getResultName();
        }
        QueryDefinition(String queryName, String responseName)  {
            this.queryName = queryName;
            this.responseName = responseName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            QueryDefinition that = (QueryDefinition) o;

            if (!queryName.equals(that.queryName)) return false;
            return responseName.equals(that.responseName);
        }

        @Override
        public int hashCode() {
            int result = queryName.hashCode();
            result = 31 * result + responseName.hashCode();
            return result;
        }
    }
}
