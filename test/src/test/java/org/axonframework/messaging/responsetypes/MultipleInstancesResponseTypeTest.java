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
    void testMatchesReturnsFalseIfResponseTypeIsTheSame() throws NoSuchMethodException {
        testMatches("someQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsFalseIfResponseTypeIsSubTypeOfProvidedType() throws NoSuchMethodException {
        testMatches("someSubTypedQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsFalseIfResponseTypeIsSuperTypeOfProvidedType() throws NoSuchMethodException {
        testMatches("someSuperTypedQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsFalseIfResponseTypeIsUnboundedGeneric() throws NoSuchMethodException {
        testMatches("someUnboundedGenericQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsFalseIfResponseTypeIsBoundedGenericOfProvidedType() throws NoSuchMethodException {
        testMatches("someBoundedGenericQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsFalseIfResponseTypeIsMultiBoundedGenericOfProvidedType()
            throws NoSuchMethodException {
        testMatches("someMultiBoundedGenericQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsFalseIfResponseTypeIsGenericOfOtherType() throws NoSuchMethodException {
        testMatches("someNonMatchingBoundedGenericQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsTrueIfResponseTypeIsArrayOfProvidedType() throws NoSuchMethodException {
        testMatches("someArrayQuery", MATCHES);
    }

    @Test
    void testMatchesReturnsTrueIfResponseTypeIsArrayWithSubTypeOfProvidedType() throws NoSuchMethodException {
        testMatches("someSubTypedArrayQuery", MATCHES);
    }

    @Test
    void testMatchesReturnsFalseIfResponseTypeIsArrayWithSuperTypeOfProvidedType() throws NoSuchMethodException {
        testMatches("someSuperTypedArrayQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsFalseIfResponseTypeIsUnboundedGenericArray() throws NoSuchMethodException {
        testMatches("someUnboundedGenericArrayQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsTrueIfResponseTypeIsBoundedGenericArrayOfProvidedType() throws NoSuchMethodException {
        testMatches("someBoundedGenericArrayQuery", MATCHES);
    }

    @Test
    void testMatchesReturnsTrueIfResponseTypeIsMultiBoundedGenericArrayOfProvidedType()
            throws NoSuchMethodException {
        testMatches("someMultiBoundedGenericArrayQuery", MATCHES);
    }

    @Test
    void testMatchesReturnsFalseIfResponseTypeIsGenericArrayOfOtherType() throws NoSuchMethodException {
        testMatches("someNonMatchingBoundedGenericArrayQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsTrueIfResponseTypeIsListOfProvidedType() throws NoSuchMethodException {
        testMatches("someListQuery", MATCHES);
    }

    @Test
    void testMatchesReturnsTrueIfResponseTypeIsSubListOfProvidedType() throws NoSuchMethodException {
        testMatches("someSubListQuery", MATCHES);
    }

    @Test
    void testMatchesReturnsFalseIfResponseTypeIsSuperListOfProvidedType() throws NoSuchMethodException {
        testMatches("someSuperListQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsTrueIfResponseTypeIsBoundedGenericListOfProvidedType() throws NoSuchMethodException {
        testMatches("someBoundedGenericListQuery", MATCHES);
    }

    @Test
    void testMatchesReturnsFalseIfResponseTypeIsUnboundedGenericList() throws NoSuchMethodException {
        testMatches("someUnboundedGenericListQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsTrueIfResponseTypeIsMultiBoundedGenericListOfProvidedType()
            throws NoSuchMethodException {
        testMatches("someMultiBoundedGenericListQuery", MATCHES);
    }

    @Test
    void testMatchesReturnsFalseIfResponseTypeIsGenericListOfOtherType() throws NoSuchMethodException {
        testMatches("someNonMatchingBoundedGenericListQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsFalseIfResponseTypeIsUnboundedWildcardList() throws NoSuchMethodException {
        testMatches("someUnboundedWildcardListQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsFalseIfResponseTypeIsLowerBoundedWildcardList() throws NoSuchMethodException {
        testMatches("someLowerBoundedWildcardListQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsTrueIfResponseTypeIsUpperBoundedWildcardListOfProvidedType()
            throws NoSuchMethodException {
        testMatches("someUpperBoundedWildcardListQuery", MATCHES);
    }

    @Test
    void testMatchesReturnsFalseIfResponseTypeIsWildcardListOfOtherType() throws NoSuchMethodException {
        testMatches("someNonMatchingUpperBoundedWildcardQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsFalseIfResponseTypeIsUnboundedGenericUpperBoundedWildcardList()
            throws NoSuchMethodException {
        testMatches("someUnboundedGenericUpperBoundedWildcardListQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsTrueIfResponseTypeIsGenericUpperBoundedWildcardListOfProvidedType()
            throws NoSuchMethodException {
        testMatches("someGenericUpperBoundedWildcardListQuery", MATCHES);
    }

    @Test
    void testMatchesReturnsTrueIfResponseTypeIsMultiGenericUpperBoundedWildcardListOfProvidedType()
            throws NoSuchMethodException {
        testMatches("someMultiGenericUpperBoundedWildcardListQuery", MATCHES);
    }

    @Test
    void testMatchesReturnsTrueIfResponseTypeIsListImplementationOfProvidedType() throws NoSuchMethodException {
        testMatches("someListImplementationQuery", MATCHES);
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
    void testMatchesReturnsFalseIfResponseTypeIsUnboundedListImplementationOfProvidedType()
            throws NoSuchMethodException {
        testMatches("someUnboundedListImplementationQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsTrueIfResponseTypeIsBoundedListImplementationOfProvidedType()
            throws NoSuchMethodException {
        testMatches("someBoundedListImplementationQuery", MATCHES);
    }

    @Test
    void testMatchesReturnsFalseIfResponseTypeIsMultiUnboundedListImplementationOfProvidedType()
            throws NoSuchMethodException {
        testMatches("someMultiUnboundedListImplementationQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsTrueIfResponseTypeIsMultiBoundedListImplementationOfProvidedType()
            throws NoSuchMethodException {
        testMatches("someMultiBoundedListImplementationQuery", MATCHES);
    }

    @Test
    void testMatchesReturnsTrueIfResponseTypeIsSetOfProvidedType() throws NoSuchMethodException {
        testMatches("someSetQuery", MATCHES);
    }

    @Test
    void testMatchesReturnsTrueIfResponseTypeIsStreamOfProvidedType() throws NoSuchMethodException {
        testMatches("someStreamQuery", MATCHES);
    }

    @Test
    void testMatchesReturnsFalseIfResponseTypeIsMapOfProvidedType() throws NoSuchMethodException {
        testMatches("someMapQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsFalseIfResponseTypeIsFutureOfProvidedType() throws NoSuchMethodException {
        testMatches("someFutureQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsTrueIfResponseTypeIsListOfFutureOfProvidedType() throws NoSuchMethodException {
        testMatches("someFutureListQuery", MATCHES);
    }

    @Test
    void testMatchesReturnsFalseIfResponseTypeIsOptionalOfProvidedType() throws NoSuchMethodException {
        testMatches("someOptionalQueryResponse", DOES_NOT_MATCH);
    }

    @SuppressWarnings("unused")
    @Test
    void testConvertThrowsExceptionForSingleInstanceResponse() {
        assertThrows(Exception.class, () -> testSubject.convert(new QueryResponse()));
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
