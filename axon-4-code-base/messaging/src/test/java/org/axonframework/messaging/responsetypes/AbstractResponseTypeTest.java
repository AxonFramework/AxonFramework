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

package org.axonframework.messaging.responsetypes;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import static org.axonframework.common.ReflectionUtils.methodOf;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Helper test implementation of {@link ResponseType} tests.
 *
 * @param <R> a generic for the expected response type of the {@link ResponseType} test subject
 */
public abstract class AbstractResponseTypeTest<R> {
    protected static final Integer MATCHES = ResponseType.MATCH;
    protected static final Integer DOES_NOT_MATCH = ResponseType.NO_MATCH;

    protected final ResponseType<R> testSubject;

    protected AbstractResponseTypeTest(ResponseType<R> testSubject) {
        this.testSubject = testSubject;
    }

    /**
     * Helper function to make testing of the {@link ResponseType#matchRank(Type)} function easier. Takes a {@code
     * methodNameToTest} which it uses to pull a {@link java.lang.reflect.Method} from this abstract class. There after
     * it will pull the return {@link java.lang.reflect.Type} from that method, which it will use as input for the test
     * subject's matchRank and matches functions.
     *
     * @param methodNameToTest a {@link java.lang.String} representing the function you want to extract a return type
     *                         from
     * @param expectedResult   a {@link java.lang.Integer} which is the expected result of the matchRank call
     * @throws NoSuchMethodException if no {@link java.lang.reflect.Method} can be found for the given {@code
     *                               methodNameToTest}
     */
    protected void testMatchRanked(String methodNameToTest, Integer expectedResult) throws NoSuchMethodException {
        Method methodToTest = methodOf(getClass(), methodNameToTest);
        Type methodReturnType = methodToTest.getGenericReturnType();
        assertEquals(expectedResult, testSubject.matchRank(methodReturnType));
        assertEquals(expectedResult > 0, testSubject.matches(methodReturnType));
    }

    @SuppressWarnings("unused")
    public QueryResponse someQuery() {
        return new QueryResponse();
    }

    @SuppressWarnings("unused")
    public SubTypedQueryResponse someSubTypedQuery() {
        return new SubTypedQueryResponse();
    }

    @SuppressWarnings("unused")
    public Object someSuperTypedQuery() {
        return new Object();
    }

    @SuppressWarnings({"unchecked", "unused"})
    public <E> E someUnboundedGenericQuery() {
        return (E) new SubTypedQueryResponse();
    }

    @SuppressWarnings({"unchecked", "unused"})
    public <E extends QueryResponse> E someBoundedGenericQuery() {
        return (E) new SubTypedQueryResponse();
    }

    @SuppressWarnings({"unchecked", "unused"})
    public <E extends SubTypedQueryResponse & QueryResponseInterface> E someMultiBoundedGenericQuery() {
        return (E) new ComplexTypedQueryResponse();
    }

    @SuppressWarnings({"unchecked", "unused"})
    public <E extends QueryResponseInterface> E someNonMatchingBoundedGenericQuery() {
        return (E) new QueryResponseInterface() {
        };
    }

    @SuppressWarnings("unused")
    public QueryResponse[] someArrayQuery() {
        return new QueryResponse[]{};
    }

    @SuppressWarnings("unused")
    public SubTypedQueryResponse[] someSubTypedArrayQuery() {
        return new SubTypedQueryResponse[]{};
    }

    @SuppressWarnings("unused")
    public Object[] someSuperTypedArrayQuery() {
        return new Object[]{};
    }

    @SuppressWarnings({"unused", "unchecked"})
    public <E> E[] someUnboundedGenericArrayQuery() {
        return (E[]) new SubTypedQueryResponse[]{};
    }

    @SuppressWarnings({"unused", "unchecked"})
    public <E extends QueryResponse> E[] someBoundedGenericArrayQuery() {
        return (E[]) new SubTypedQueryResponse[]{};
    }

    @SuppressWarnings({"unused", "unchecked"})
    public <E extends SubTypedQueryResponse & QueryResponseInterface> E[] someMultiBoundedGenericArrayQuery() {
        return (E[]) new ComplexTypedQueryResponse[]{};
    }

    @SuppressWarnings({"unused", "unchecked"})
    public <E extends QueryResponseInterface> E[] someNonMatchingBoundedGenericArrayQuery() {
        return (E[]) new SubTypedQueryResponse[]{};
    }

    @SuppressWarnings("unused")
    public List<QueryResponse> someListQuery() {
        return new ArrayList<>();
    }

    @SuppressWarnings("unused")
    public List<SubTypedQueryResponse> someSubListQuery() {
        return new ArrayList<>();
    }

    @SuppressWarnings("unused")
    public List<Object> someSuperListQuery() {
        return new ArrayList<>();
    }

    @SuppressWarnings("unused")
    public <E extends QueryResponse> List<E> someBoundedGenericListQuery() {
        return new ArrayList<>();
    }

    @SuppressWarnings("unused")
    public <E> List<E> someUnboundedGenericListQuery() {
        return new ArrayList<>();
    }

    @SuppressWarnings("unused")
    public <E extends SubTypedQueryResponse & QueryResponseInterface> List<E> someMultiBoundedGenericListQuery() {
        return new ArrayList<>();
    }

    @SuppressWarnings("unused")
    public <E extends QueryResponseInterface> List<E> someNonMatchingBoundedGenericListQuery() {
        return new ArrayList<>();
    }

    @SuppressWarnings("unused")
    public List<?> someUnboundedWildcardListQuery() {
        return new ArrayList<>();
    }

    @SuppressWarnings("unused")
    public List<? super QueryResponse> someLowerBoundedWildcardListQuery() {
        return new ArrayList<>();
    }

    @SuppressWarnings("unused")
    public List<? extends SubTypedQueryResponse> someUpperBoundedWildcardListQuery() {
        return new ArrayList<>();
    }

    @SuppressWarnings("unused")
    public List<? extends QueryResponseInterface> someNonMatchingUpperBoundedWildcardQuery() {
        return new ArrayList<>();
    }

    @SuppressWarnings("unused")
    public <E> List<? extends E> someUnboundedGenericUpperBoundedWildcardListQuery() {
        return new ArrayList<>();
    }

    @SuppressWarnings("unused")
    public <E extends SubTypedQueryResponse> List<? extends E> someGenericUpperBoundedWildcardListQuery() {
        return new ArrayList<>();
    }

    @SuppressWarnings("unused")
    public <E extends SubTypedQueryResponse & QueryResponseInterface> List<? extends E> someMultiGenericUpperBoundedWildcardListQuery
            () {
        return new ArrayList<>();
    }

    @SuppressWarnings("unused")
    public <E> UnboundQueryResponseList<E> someUnboundedListImplementationQuery() {
        return new UnboundQueryResponseList<>();
    }

    @SuppressWarnings("unused")
    public <E extends QueryResponse> BoundQueryResponseList<E> someBoundedListImplementationQuery() {
        return new BoundQueryResponseList<>();
    }

    @SuppressWarnings({"unused", "TypeParameterHidesVisibleType"})
    public <E, R> MultiUnboundQueryResponseList<E, R> someMultiUnboundedListImplementationQuery() {
        return new MultiUnboundQueryResponseList<>();
    }

    @SuppressWarnings({"unused", "TypeParameterHidesVisibleType"})
    public <E extends QueryResponse, R> MultiBoundQueryResponseList<E, R> someMultiBoundedListImplementationQuery() {
        return new MultiBoundQueryResponseList<>();
    }

    @SuppressWarnings("unused")
    public Set<QueryResponse> someSetQuery() {
        return new HashSet<>();
    }

    @SuppressWarnings("unused")
    public Stream<QueryResponse> someStreamQuery() {
        return Stream.of(new QueryResponse());
    }

    @SuppressWarnings("unused")
    public Map<QueryResponse, QueryResponse> someMapQuery() {
        return new HashMap<>();
    }

    @SuppressWarnings("unused")
    public Future<QueryResponse> someFutureQuery() {
        return CompletableFuture.completedFuture(new QueryResponse());
    }

    @SuppressWarnings("unused")
    public Future<List<QueryResponse>> someFutureListQuery() {
        return CompletableFuture.completedFuture(Collections.singletonList(new QueryResponse()));
    }

    @SuppressWarnings("unused")
    public Optional<QueryResponse> someOptionalQueryResponse() {
        return Optional.of(new QueryResponse());
    }

    static class QueryResponse {

    }

    static class SubTypedQueryResponse extends QueryResponse {

    }

    interface QueryResponseInterface {

    }

    private static class ComplexTypedQueryResponse extends SubTypedQueryResponse implements QueryResponseInterface {

    }

    private static class UnboundQueryResponseList<E> extends ArrayList<E> {

    }

    private static class BoundQueryResponseList<E extends QueryResponse> extends ArrayList<E> {

    }

    @SuppressWarnings("unused")
    private static class MultiUnboundQueryResponseList<E, R> extends ArrayList<E> {

    }

    @SuppressWarnings("unused")
    private static class MultiBoundQueryResponseList<E extends QueryResponse, R> extends ArrayList<E> {

    }
}
