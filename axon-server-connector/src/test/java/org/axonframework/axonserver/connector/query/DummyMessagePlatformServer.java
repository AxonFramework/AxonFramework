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

package org.axonframework.axonserver.connector.query;

import com.google.protobuf.ByteString;
import io.axoniq.axonserver.grpc.ErrorMessage;
import io.axoniq.axonserver.grpc.MetaDataValue;
import io.axoniq.axonserver.grpc.SerializedObject;
import io.axoniq.axonserver.grpc.query.QueryProviderInbound;
import io.axoniq.axonserver.grpc.query.QueryProviderOutbound;
import io.axoniq.axonserver.grpc.query.QueryRequest;
import io.axoniq.axonserver.grpc.query.QueryResponse;
import io.axoniq.axonserver.grpc.query.QueryServiceGrpc;
import io.axoniq.axonserver.grpc.query.QuerySubscription;
import io.axoniq.axonserver.grpc.query.SubscriptionQueryRequest;
import io.axoniq.axonserver.grpc.query.SubscriptionQueryResponse;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.axonframework.axonserver.connector.PlatformService;
import org.axonframework.axonserver.connector.util.TcpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Minimal dummy implementation of gRPC connection to spoof a connection with for example Axon Server when testing Axon
 * Server connector components.
 *
 * @author Marc Gathier
 */
public class DummyMessagePlatformServer {

    private final static Logger logger = LoggerFactory.getLogger(DummyMessagePlatformServer.class);

    private final int port;
    private Server server;

    private Map<QueryDefinition, StreamObserver<?>> subscriptions = new HashMap<>();
    private Set<QueryDefinition> unsubscribedQueries = new CopyOnWriteArraySet<>();

    public DummyMessagePlatformServer() {
        this(TcpUtil.findFreePort());
    }

    public DummyMessagePlatformServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        server = ServerBuilder.forPort(port)
                              .addService(new QueryHandler())
                              .addService(new PlatformService(port))
                              .build();
        server.start();
    }

    public void stop() {
        try {
            server.shutdownNow().awaitTermination();
        } catch (InterruptedException ignore) {
        }
    }

    public String getAddress() {
        return "localhost:" + port;
    }

    public StreamObserver<?> subscriptions(String query, String response) {
        return subscriptions.get(new QueryDefinition(query, response));
    }

    public void onError(String query, String response) {
        StreamObserver<?> subscription = this.subscriptions(query, response);
        subscription.onError(new RuntimeException());
        subscriptions.remove(subscription);
    }

    /**
     * Verify whether the given {@code queryName} and {@code responseType} is unsubscribed from the platform server.
     *
     * @param queryName    the name of the query to validate whether it's unsubscribed
     * @param responseType the {@link Type} of the query to validate whether it's unsubscribed
     * @return {@code true} if the {@code queryDefinition} was unsubscribed, {@code false} if it is not
     */
    public boolean isUnsubscribed(String queryName, Type responseType) {
        return unsubscribedQueries.contains(new QueryDefinition(queryName, responseType.getTypeName()));
    }

    class QueryHandler extends QueryServiceGrpc.QueryServiceImplBase {

        @Override
        public StreamObserver<QueryProviderOutbound> openStream(StreamObserver<QueryProviderInbound> responseObserver) {
            return new StreamObserver<QueryProviderOutbound>() {

                @Override
                public void onNext(QueryProviderOutbound queryProviderOutbound) {
                    switch (queryProviderOutbound.getRequestCase()) {
                        case SUBSCRIBE:
                            QueryDefinition queryDefinition = new QueryDefinition(queryProviderOutbound.getSubscribe());
                            subscriptions.put(queryDefinition, responseObserver);
                            break;
                        case UNSUBSCRIBE:
                            QueryDefinition queryToUnsubscribe =
                                    new QueryDefinition(queryProviderOutbound.getUnsubscribe());
                            subscriptions.remove(queryToUnsubscribe);
                            unsubscribedQueries.add(queryToUnsubscribe);
                            break;
                        case FLOW_CONTROL:
                        case QUERY_RESPONSE:
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
            String errorCode = retrieveErrorCode(request);

            if (!"".equals(errorCode)) {
                responseObserver.onNext(QueryResponse.newBuilder()
                                                     .setMessageIdentifier(request.getMessageIdentifier())
                                                     .setErrorCode(errorCode)
                                                     .setErrorMessage(ErrorMessage.newBuilder()
                                                                                  .addDetails("You wanted trouble")
                                                                                  .build())
                                                     .build());
            } else {
                long repeat = retrieveRepeat(request);
                for (long r = 0; r < repeat; r++) {
                    SerializedObject serializedObject =
                            SerializedObject.newBuilder()
                                            .setData(ByteString.copyFromUtf8("<string>test</string>"))
                                            .setType(String.class.getName())
                                            .build();
                    responseObserver.onNext(QueryResponse.newBuilder()
                                                         .setMessageIdentifier(request.getMessageIdentifier())
                                                         .setPayload(serializedObject)
                                                         .build());

                    long interval = retrieveInterval(request);
                    if (interval > 0) {
                        try {
                            Thread.sleep(interval);
                        } catch (InterruptedException e) {
                            logger.debug("Sleep interrupted");
                        }
                    }
                }
            }
            responseObserver.onCompleted();
        }

        private long retrieveInterval(QueryRequest request) {
            return request.getMetaDataOrDefault("interval", MetaDataValue.newBuilder().setNumberValue(0).build())
                          .getNumberValue();
        }

        private long retrieveRepeat(QueryRequest request) {
            return request.getMetaDataOrDefault("repeat", MetaDataValue.newBuilder().setNumberValue(1).build())
                          .getNumberValue();
        }

        private String retrieveErrorCode(QueryRequest request) {
            return request.getMetaDataOrDefault("errorCode", MetaDataValue.getDefaultInstance())
                          .getTextValueBytes().toStringUtf8();
        }

        @Override
        public StreamObserver<SubscriptionQueryRequest> subscription(
                StreamObserver<SubscriptionQueryResponse> responseObserver) {
            return new StreamObserver<SubscriptionQueryRequest>() {
                @Override
                public void onNext(SubscriptionQueryRequest subscriptionQueryRequest) {
                    // Not implemented, as for #1013 this wasn't mandatory during tests
                }

                @Override
                public void onError(Throwable throwable) {
                    // Not implemented, as for #1013 this wasn't mandatory during tests
                }

                @Override
                public void onCompleted() {
                    // Not implemented, as for #1013 this wasn't mandatory during tests
                }
            };
        }
    }

    private static class QueryDefinition {

        private final String queryName;
        private final String responseName;

        QueryDefinition(QuerySubscription subscription) {
            this.queryName = subscription.getQuery();
            this.responseName = subscription.getResultName();
        }

        QueryDefinition(String queryName, String responseName) {
            this.queryName = queryName;
            this.responseName = responseName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            QueryDefinition that = (QueryDefinition) o;

            if (!queryName.equals(that.queryName)) {
                return false;
            }
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
