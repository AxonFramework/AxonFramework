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
 * that end, leveraging the  {@link AbstractResponseTypeTest} to cover all usual suspects between the different
 * {@link ResponseType} implementations.
 */
public class MultipleInstancesResponseTypeTest
        extends AbstractResponseTypeTest<List<AbstractResponseTypeTest.QueryResponse>> {

    public MultipleInstancesResponseTypeTest() {
        super(new MultipleInstancesResponseType<>(QueryResponse.class));
    }

    @Test
    void testMatchesReturnsMatchSingleIfResponseTypeIsOfTheSame() throws NoSuchMethodException {
        testMatchPriority("someQuery", MATCHES);
    }

    @Test
    void testMatchesReturnsMatchSingleIfResponseTypeIsSubTypeOfProvidedType() throws NoSuchMethodException {
        testMatchPriority("someSubTypedQuery", MATCHES);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsSuperTypeOfProvidedType() throws NoSuchMethodException {
        testMatchPriority("someSuperTypedQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsUnboundedGeneric() throws NoSuchMethodException {
        testMatchPriority("someUnboundedGenericQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsSingleMatchIfResponseTypeIsBoundedGenericOfProvidedType() throws NoSuchMethodException {
        testMatchPriority("someBoundedGenericQuery", MATCHES);
    }

    @Test
    void testMatchesReturnsSingleMatchIfResponseTypeIsMultiBoundedGenericOfProvidedType() throws NoSuchMethodException {
        testMatchPriority("someMultiBoundedGenericQuery", MATCHES);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsGenericOfOtherType() throws NoSuchMethodException {
        testMatchPriority("someNonMatchingBoundedGenericQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsListMatchIfResponseTypeIsArrayOfProvidedType() throws NoSuchMethodException {
        testMatchPriority("someArrayQuery", MATCHES_LIST);
    }

    @Test
    void testMatchesReturnsListMatchIfResponseTypeIsArrayWithSubTypeOfProvidedType() throws NoSuchMethodException {
        testMatchPriority("someSubTypedArrayQuery", MATCHES_LIST);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsArrayWithSuperTypeOfProvidedType() throws NoSuchMethodException {
        testMatchPriority("someSuperTypedArrayQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsUnboundedGenericArray() throws NoSuchMethodException {
        testMatchPriority("someUnboundedGenericArrayQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsListMatchIfResponseTypeIsBoundedGenericArrayOfProvidedType() throws NoSuchMethodException {
        testMatchPriority("someBoundedGenericArrayQuery", MATCHES_LIST);
    }

    @Test
    void testMatchesReturnsListMatchIfResponseTypeIsMultiBoundedGenericArrayOfProvidedType()
            throws NoSuchMethodException {
        testMatchPriority("someMultiBoundedGenericArrayQuery", MATCHES_LIST);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsGenericArrayOfOtherType() throws NoSuchMethodException {
        testMatchPriority("someNonMatchingBoundedGenericArrayQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsListMatchIfResponseTypeIsListOfProvidedType() throws NoSuchMethodException {
        testMatchPriority("someListQuery", MATCHES_LIST);
    }

    @Test
    void testMatchesReturnsListMatchIfResponseTypeIsSubListOfProvidedType() throws NoSuchMethodException {
        testMatchPriority("someSubListQuery", MATCHES_LIST);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsSuperListOfProvidedType() throws NoSuchMethodException {
        testMatchPriority("someSuperListQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsListMatchIfResponseTypeIsBoundedGenericListOfProvidedType() throws NoSuchMethodException {
        testMatchPriority("someBoundedGenericListQuery", MATCHES_LIST);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsUnboundedGenericList() throws NoSuchMethodException {
        testMatchPriority("someUnboundedGenericListQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsListMatchIfResponseTypeIsMultiBoundedGenericListOfProvidedType()
            throws NoSuchMethodException {
        testMatchPriority("someMultiBoundedGenericListQuery", MATCHES_LIST);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsGenericListOfOtherType() throws NoSuchMethodException {
        testMatchPriority("someNonMatchingBoundedGenericListQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsUnboundedWildcardList() throws NoSuchMethodException {
        testMatchPriority("someUnboundedWildcardListQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsLowerBoundedWildcardList() throws NoSuchMethodException {
        testMatchPriority("someLowerBoundedWildcardListQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsListMatchIfResponseTypeIsUpperBoundedWildcardListOfProvidedType()
            throws NoSuchMethodException {
        testMatchPriority("someUpperBoundedWildcardListQuery", MATCHES_LIST);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsWildcardListOfOtherType() throws NoSuchMethodException {
        testMatchPriority("someNonMatchingUpperBoundedWildcardQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsUnboundedGenericUpperBoundedWildcardList()
            throws NoSuchMethodException {
        testMatchPriority("someUnboundedGenericUpperBoundedWildcardListQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsListMatchIfResponseTypeIsGenericUpperBoundedWildcardListOfProvidedType()
            throws NoSuchMethodException {
        testMatchPriority("someGenericUpperBoundedWildcardListQuery", MATCHES_LIST);
    }

    @Test
    void testMatchesReturnsListMatchIfResponseTypeIsMultiGenericUpperBoundedWildcardListOfProvidedType()
            throws NoSuchMethodException {
        testMatchPriority("someMultiGenericUpperBoundedWildcardListQuery", MATCHES_LIST);
    }

    @Test
    void testMatchesReturnsListMatchIfResponseTypeIsListImplementationOfProvidedType() throws NoSuchMethodException {
        testMatchPriority("someListImplementationQuery", MATCHES_LIST);
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
        testMatchPriority("someUnboundedListImplementationQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsListMatchIfResponseTypeIsBoundedListImplementationOfProvidedType()
            throws NoSuchMethodException {
        testMatchPriority("someBoundedListImplementationQuery", MATCHES_LIST);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsMultiUnboundedListImplementationOfProvidedType()
            throws NoSuchMethodException {
        testMatchPriority("someMultiUnboundedListImplementationQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsListMatchIfResponseTypeIsMultiBoundedListImplementationOfProvidedType()
            throws NoSuchMethodException {
        testMatchPriority("someMultiBoundedListImplementationQuery", MATCHES_LIST);
    }

    @Test
    void testMatchesReturnsListMatchIfResponseTypeIsSetOfProvidedType() throws NoSuchMethodException {
        testMatchPriority("someSetQuery", MATCHES_LIST);
    }

    @Test
    void testMatchesReturnsListMatchIfResponseTypeIsStreamOfProvidedType() throws NoSuchMethodException {
        testMatchPriority("someStreamQuery", MATCHES_LIST);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsMapOfProvidedType() throws NoSuchMethodException {
        testMatchPriority("someMapQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsSingleMatchIfResponseTypeIsFutureOfProvidedType() throws NoSuchMethodException {
        testMatchPriority("someFutureQuery", MATCHES);
    }

    @Test
    void testMatchesReturnsListMatchIfResponseTypeIsListOfFutureOfProvidedType() throws NoSuchMethodException {
        testMatchPriority("someFutureListQuery", MATCHES_LIST);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsOptionalOfProvidedType() throws NoSuchMethodException {
        testMatchPriority("someOptionalQueryResponse", MATCHES);
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
