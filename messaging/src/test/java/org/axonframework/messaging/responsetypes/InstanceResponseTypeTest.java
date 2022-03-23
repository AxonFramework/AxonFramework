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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test all possible permutations of Query Handler return types through the {@link InstanceResponseType}. To that end,
 * leveraging the  {@link AbstractResponseTypeTest} to cover all usual suspects between the different
 * {@link ResponseType} implementations.
 */
class InstanceResponseTypeTest extends AbstractResponseTypeTest<AbstractResponseTypeTest.QueryResponse> {

    InstanceResponseTypeTest() {
        super(new InstanceResponseType<>(QueryResponse.class));
    }

    @Test
    void testMatchesReturnsMatchIfResponseTypeIsTheSame() throws NoSuchMethodException {
        testMatchPriority("someQuery", MATCHES);
    }

    @Test
    void testMatchesReturnsMatchIfResponseTypeIsSubTypeOfProvidedType() throws NoSuchMethodException {
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
    void testMatchesReturnsMatchIfResponseTypeIsBoundedGenericOfProvidedType() throws NoSuchMethodException {
        testMatchPriority("someBoundedGenericQuery", MATCHES);
    }

    @Test
    void testMatchesReturnsMatchIfResponseTypeIsMultiBoundedGenericOfProvidedType() throws NoSuchMethodException {
        testMatchPriority("someMultiBoundedGenericQuery", MATCHES);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsGenericOfOtherType() throws NoSuchMethodException {
        testMatchPriority("someNonMatchingBoundedGenericQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsArrayOfProvidedType() throws NoSuchMethodException {
        testMatchPriority("someArrayQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsArrayWithSubTypeOfProvidedType() throws NoSuchMethodException {
        testMatchPriority("someSubTypedArrayQuery", DOES_NOT_MATCH);
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
    void testMatchesReturnsNoMatchIfResponseTypeIsBoundedGenericArrayOfProvidedType()
            throws NoSuchMethodException {
        testMatchPriority("someBoundedGenericArrayQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsMultiBoundedGenericArrayOfProvidedType()
            throws NoSuchMethodException {
        testMatchPriority("someMultiBoundedGenericArrayQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsGenericArrayOfOtherType() throws NoSuchMethodException {
        testMatchPriority("someNonMatchingBoundedGenericArrayQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsListOfProvidedType() throws NoSuchMethodException {
        testMatchPriority("someListQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsSubListOfProvidedType() throws NoSuchMethodException {
        testMatchPriority("someSubListQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsSuperListOfProvidedType() throws NoSuchMethodException {
        testMatchPriority("someSuperListQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsBoundedGenericListOfProvidedType() throws NoSuchMethodException {
        testMatchPriority("someBoundedGenericListQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsUnboundedGenericList() throws NoSuchMethodException {
        testMatchPriority("someUnboundedGenericListQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsMultiBoundedGenericListOfProvidedType()
            throws NoSuchMethodException {
        testMatchPriority("someMultiBoundedGenericListQuery", DOES_NOT_MATCH);
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
    void testMatchesReturnsNoMatchIfResponseTypeIsUpperBoundedWildcardListOfProvidedType()
            throws NoSuchMethodException {
        testMatchPriority("someUpperBoundedWildcardListQuery", DOES_NOT_MATCH);
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
    void testMatchesReturnsNoMatchIfResponseTypeIsGenericUpperBoundedWildcardListOfProvidedType()
            throws NoSuchMethodException {
        testMatchPriority("someGenericUpperBoundedWildcardListQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsMultiGenericUpperBoundedWildcardListOfProvidedType()
            throws NoSuchMethodException {
        testMatchPriority("someMultiGenericUpperBoundedWildcardListQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsUnboundedListImplementationOfProvidedType()
            throws NoSuchMethodException {
        testMatchPriority("someUnboundedListImplementationQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsBoundedListImplementationOfProvidedType()
            throws NoSuchMethodException {
        testMatchPriority("someBoundedListImplementationQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsMultiUnboundedListImplementationOfProvidedType()
            throws NoSuchMethodException {
        testMatchPriority("someMultiUnboundedListImplementationQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsMultiBoundedListImplementationOfProvidedType()
            throws NoSuchMethodException {
        testMatchPriority("someMultiBoundedListImplementationQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsSetOfProvidedType() throws NoSuchMethodException {
        testMatchPriority("someSetQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsStreamOfProvidedType() throws NoSuchMethodException {
        testMatchPriority("someStreamQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsMapOfProvidedType() throws NoSuchMethodException {
        testMatchPriority("someMapQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsMatchIfResponseTypeIsFutureOfProvidedType() throws NoSuchMethodException {
        testMatchPriority("someFutureQuery", MATCHES);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsListOfFutureOfProvidedType() throws NoSuchMethodException {
        testMatchPriority("someFutureListQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsMatchIfResponseTypeIsOptionalOfProvidedType() throws NoSuchMethodException {
        testMatchPriority("someOptionalQueryResponse", MATCHES);
    }

    @Test
    void testConvertReturnsSingleResponseAsIs() {
        QueryResponse testResponse = new QueryResponse();

        QueryResponse result = testSubject.convert(testResponse);

        assertEquals(testResponse, result);
    }

    @Test
    void testConvertReturnsSingleResponseAsIsForSubTypedResponse() {
        SubTypedQueryResponse testResponse = new SubTypedQueryResponse();

        QueryResponse result = testSubject.convert(testResponse);

        assertEquals(testResponse, result);
    }

    @SuppressWarnings("unused")
    @Test
    void testConvertThrowsClassCastExceptionForDifferentSingleInstanceResponse() {
        assertThrows(Exception.class, () -> {
            QueryResponse convert = testSubject.convert(new QueryResponseInterface() {
            });
        });
    }

    @SuppressWarnings("unused")
    @Test
    void testConvertThrowsClassCastExceptionForMultipleInstanceResponse() {
        assertThrows(Exception.class, () -> {
            QueryResponse convert = testSubject.convert(new ArrayList<QueryResponse>());
        });
    }
}
