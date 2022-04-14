/*
 * Copyright (c) 2010-2022. Axon Framework
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

import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test all possible permutations of Query Handler return types through the {@link MultipleInstancesResponseType}. To
 * that end, leveraging the  {@link AbstractResponseTypeTest} to cover all usual suspects between the different {@link
 * ResponseType} implementations.
 */
public class MultipleInstancesResponseTypeTest
        extends AbstractResponseTypeTest<List<AbstractResponseTypeTest.QueryResponse>> {

    protected static final Integer MATCHES_LIST = MultipleInstancesResponseType.ITERABLE_MATCH;

    public MultipleInstancesResponseTypeTest() {
        super(new MultipleInstancesResponseType<>(QueryResponse.class));
    }

    @Test
    void testMatchesReturnsMatchSingleIfResponseTypeIsOfTheSame() throws NoSuchMethodException {
        testMatchRanked("someQuery", MATCHES);
    }

    @Test
    void testMatchesReturnsMatchSingleIfResponseTypeIsSubTypeOfProvidedType() throws NoSuchMethodException {
        testMatchRanked("someSubTypedQuery", MATCHES);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsSuperTypeOfProvidedType() throws NoSuchMethodException {
        testMatchRanked("someSuperTypedQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsUnboundedGeneric() throws NoSuchMethodException {
        testMatchRanked("someUnboundedGenericQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsSingleMatchIfResponseTypeIsBoundedGenericOfProvidedType() throws NoSuchMethodException {
        testMatchRanked("someBoundedGenericQuery", MATCHES);
    }

    @Test
    void testMatchesReturnsSingleMatchIfResponseTypeIsMultiBoundedGenericOfProvidedType() throws NoSuchMethodException {
        testMatchRanked("someMultiBoundedGenericQuery", MATCHES);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsGenericOfOtherType() throws NoSuchMethodException {
        testMatchRanked("someNonMatchingBoundedGenericQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsListMatchIfResponseTypeIsArrayOfProvidedType() throws NoSuchMethodException {
        testMatchRanked("someArrayQuery", MATCHES_LIST);
    }

    @Test
    void testMatchesReturnsListMatchIfResponseTypeIsArrayWithSubTypeOfProvidedType() throws NoSuchMethodException {
        testMatchRanked("someSubTypedArrayQuery", MATCHES_LIST);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsArrayWithSuperTypeOfProvidedType() throws NoSuchMethodException {
        testMatchRanked("someSuperTypedArrayQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsUnboundedGenericArray() throws NoSuchMethodException {
        testMatchRanked("someUnboundedGenericArrayQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsListMatchIfResponseTypeIsBoundedGenericArrayOfProvidedType() throws NoSuchMethodException {
        testMatchRanked("someBoundedGenericArrayQuery", MATCHES_LIST);
    }

    @Test
    void testMatchesReturnsListMatchIfResponseTypeIsMultiBoundedGenericArrayOfProvidedType()
            throws NoSuchMethodException {
        testMatchRanked("someMultiBoundedGenericArrayQuery", MATCHES_LIST);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsGenericArrayOfOtherType() throws NoSuchMethodException {
        testMatchRanked("someNonMatchingBoundedGenericArrayQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsListMatchIfResponseTypeIsListOfProvidedType() throws NoSuchMethodException {
        testMatchRanked("someListQuery", MATCHES_LIST);
    }

    @Test
    void testMatchesReturnsListMatchIfResponseTypeIsSubListOfProvidedType() throws NoSuchMethodException {
        testMatchRanked("someSubListQuery", MATCHES_LIST);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsSuperListOfProvidedType() throws NoSuchMethodException {
        testMatchRanked("someSuperListQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsListMatchIfResponseTypeIsBoundedGenericListOfProvidedType() throws NoSuchMethodException {
        testMatchRanked("someBoundedGenericListQuery", MATCHES_LIST);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsUnboundedGenericList() throws NoSuchMethodException {
        testMatchRanked("someUnboundedGenericListQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsListMatchIfResponseTypeIsMultiBoundedGenericListOfProvidedType()
            throws NoSuchMethodException {
        testMatchRanked("someMultiBoundedGenericListQuery", MATCHES_LIST);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsGenericListOfOtherType() throws NoSuchMethodException {
        testMatchRanked("someNonMatchingBoundedGenericListQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsUnboundedWildcardList() throws NoSuchMethodException {
        testMatchRanked("someUnboundedWildcardListQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsLowerBoundedWildcardList() throws NoSuchMethodException {
        testMatchRanked("someLowerBoundedWildcardListQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsListMatchIfResponseTypeIsUpperBoundedWildcardListOfProvidedType()
            throws NoSuchMethodException {
        testMatchRanked("someUpperBoundedWildcardListQuery", MATCHES_LIST);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsWildcardListOfOtherType() throws NoSuchMethodException {
        testMatchRanked("someNonMatchingUpperBoundedWildcardQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsUnboundedGenericUpperBoundedWildcardList()
            throws NoSuchMethodException {
        testMatchRanked("someUnboundedGenericUpperBoundedWildcardListQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsListMatchIfResponseTypeIsGenericUpperBoundedWildcardListOfProvidedType()
            throws NoSuchMethodException {
        testMatchRanked("someGenericUpperBoundedWildcardListQuery", MATCHES_LIST);
    }

    @Test
    void testMatchesReturnsListMatchIfResponseTypeIsMultiGenericUpperBoundedWildcardListOfProvidedType()
            throws NoSuchMethodException {
        testMatchRanked("someMultiGenericUpperBoundedWildcardListQuery", MATCHES_LIST);
    }

    @Test
    void testMatchesReturnsListMatchIfResponseTypeIsListImplementationOfProvidedType() throws NoSuchMethodException {
        testMatchRanked("someListImplementationQuery", MATCHES_LIST);
    }

    /*
     This dummy function (QueryResponseList someListImplementationQuery) and dummy class (QueryResponseList) are
     contained in this test class instead of the AbstractResponseTypeTest class, because the functionality to derive
     whether a response type has a direct super type which we service (an Iterable in this case), checks if the
     enclosing classes contain unresolved generic types. It does this to check whether the type has raw types or not.
     Since the AbstractResponseTypeTest has a generic type R for test implementations, a check by that functionality for
     AbstractResponseTypeTest.QueryResponseList results in the state that it thinks it's unresolved
     (whilst in fact it is). This is however such a slim scenario, that I decided to put the dummy class and test
     function in the actual test class itself instead of in the abstract test class.
     */

    @SuppressWarnings("unused")
    public static QueryResponseList someListImplementationQuery() {
        return new QueryResponseList();
    }

    @SuppressWarnings("WeakerAccess")
    static class QueryResponseList extends ArrayList<QueryResponse> {

    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsUnboundedListImplementationOfProvidedType()
            throws NoSuchMethodException {
        testMatchRanked("someUnboundedListImplementationQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsListMatchIfResponseTypeIsBoundedListImplementationOfProvidedType()
            throws NoSuchMethodException {
        testMatchRanked("someBoundedListImplementationQuery", MATCHES_LIST);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsMultiUnboundedListImplementationOfProvidedType()
            throws NoSuchMethodException {
        testMatchRanked("someMultiUnboundedListImplementationQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsListMatchIfResponseTypeIsMultiBoundedListImplementationOfProvidedType()
            throws NoSuchMethodException {
        testMatchRanked("someMultiBoundedListImplementationQuery", MATCHES_LIST);
    }

    @Test
    void testMatchesReturnsListMatchIfResponseTypeIsSetOfProvidedType() throws NoSuchMethodException {
        testMatchRanked("someSetQuery", MATCHES_LIST);
    }

    @Test
    void testMatchesReturnsListMatchIfResponseTypeIsStreamOfProvidedType() throws NoSuchMethodException {
        testMatchRanked("someStreamQuery", MATCHES_LIST);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsMapOfProvidedType() throws NoSuchMethodException {
        testMatchRanked("someMapQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsSingleMatchIfResponseTypeIsFutureOfProvidedType() throws NoSuchMethodException {
        testMatchRanked("someFutureQuery", MATCHES);
    }

    @Test
    void testMatchesReturnsListMatchIfResponseTypeIsListOfFutureOfProvidedType() throws NoSuchMethodException {
        testMatchRanked("someFutureListQuery", MATCHES_LIST);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsOptionalOfProvidedType() throws NoSuchMethodException {
        testMatchRanked("someOptionalQueryResponse", MATCHES);
    }

    @SuppressWarnings("unused")
    @Test
    void testConvertReturnsListForSingleInstanceResponse() {
        QueryResponse testResponse = new QueryResponse();

        List<QueryResponse> result = testSubject.convert(testResponse);
        assertEquals(1, result.size());
        assertEquals(testResponse, result.get(0));
    }

    @Test
    void testConvertReturnsListOnResponseOfArrayType() {
        QueryResponse[] testResponse = new QueryResponse[]{new QueryResponse()};

        List<QueryResponse> result = testSubject.convert(testResponse);

        assertEquals(testResponse.length, result.size());
        assertEquals(testResponse[0], result.get(0));
    }

    @Test
    void testConvertReturnsListOnResponseOfSubTypedArrayType() {
        SubTypedQueryResponse[] testResponse = new SubTypedQueryResponse[]{new SubTypedQueryResponse()};

        List<QueryResponse> result = testSubject.convert(testResponse);

        assertEquals(testResponse.length, result.size());
        assertEquals(testResponse[0], result.get(0));
    }

    @SuppressWarnings("unused")
    @Test
    void testConvertThrowsExceptionForResponseOfDifferentArrayType() {
        QueryResponseInterface[] testResponse = new QueryResponseInterface[]{new QueryResponseInterface() {
        }};

        assertThrows(Exception.class, () -> testSubject.convert(testResponse));
    }

    @Test
    void testConvertReturnsListOnResponseOfListType() {
        List<QueryResponse> testResponse = new ArrayList<>();
        testResponse.add(new QueryResponse());

        List<QueryResponse> result = testSubject.convert(testResponse);

        assertEquals(testResponse.size(), result.size());
        assertEquals(testResponse.get(0), result.get(0));
    }

    @Test
    void testConvertReturnsListOnResponseOfSubTypedListType() {
        List<SubTypedQueryResponse> testResponse = new ArrayList<>();
        testResponse.add(new SubTypedQueryResponse());

        List<QueryResponse> result = testSubject.convert(testResponse);

        assertEquals(testResponse.size(), result.size());
        assertEquals(testResponse.get(0), result.get(0));
    }

    @Test
    void testConvertReturnsListOnResponseOfSetType() {
        Set<QueryResponse> testResponse = new HashSet<>();
        testResponse.add(new QueryResponse());

        List<QueryResponse> result = testSubject.convert(testResponse);

        assertEquals(testResponse.size(), result.size());
        assertEquals(testResponse.iterator().next(), result.get(0));
    }

    @SuppressWarnings("unused")
    @Test
    void testConvertThrowsExceptionForResponseOfDifferentListType() {
        List<QueryResponseInterface> testResponse = new ArrayList<>();
        testResponse.add(new QueryResponseInterface() {
        });

        assertThrows(Exception.class, () -> testSubject.convert(testResponse));
    }

    @Test
    void testConvertReturnsEmptyListForResponseOfDifferentListTypeIfTheListIsEmpty() {
        List<QueryResponseInterface> testResponse = new ArrayList<>();

        List<QueryResponse> result = testSubject.convert(testResponse);

        assertTrue(result.isEmpty());
    }
}
