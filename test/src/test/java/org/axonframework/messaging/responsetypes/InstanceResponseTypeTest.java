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
    void testMatchesReturnsTrueIfResponseTypeIsTheSame() throws NoSuchMethodException {
        testMatches("someQuery", MATCHES);
    }

    @Test
    void testMatchesReturnsTrueIfResponseTypeIsSubTypeOfProvidedType() throws NoSuchMethodException {
        testMatches("someSubTypedQuery", MATCHES);
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
    void testMatchesReturnsTrueIfResponseTypeIsBoundedGenericOfProvidedType() throws NoSuchMethodException {
        testMatches("someBoundedGenericQuery", MATCHES);
    }

    @Test
    void testMatchesReturnsTrueIfResponseTypeIsMultiBoundedGenericOfProvidedType() throws NoSuchMethodException {
        testMatches("someMultiBoundedGenericQuery", MATCHES);
    }

    @Test
    void testMatchesReturnsFalseIfResponseTypeIsGenericOfOtherType() throws NoSuchMethodException {
        testMatches("someNonMatchingBoundedGenericQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsFalseIfResponseTypeIsArrayOfProvidedType() throws NoSuchMethodException {
        testMatches("someArrayQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsFalseIfResponseTypeIsArrayWithSubTypeOfProvidedType() throws NoSuchMethodException {
        testMatches("someSubTypedArrayQuery", DOES_NOT_MATCH);
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
    void testMatchesReturnsFalseIfResponseTypeIsBoundedGenericArrayOfProvidedType()
            throws NoSuchMethodException {
        testMatches("someBoundedGenericArrayQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsFalseIfResponseTypeIsMultiBoundedGenericArrayOfProvidedType()
            throws NoSuchMethodException {
        testMatches("someMultiBoundedGenericArrayQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsFalseIfResponseTypeIsGenericArrayOfOtherType() throws NoSuchMethodException {
        testMatches("someNonMatchingBoundedGenericArrayQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsFalseIfResponseTypeIsListOfProvidedType() throws NoSuchMethodException {
        testMatches("someListQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsFalseIfResponseTypeIsSubListOfProvidedType() throws NoSuchMethodException {
        testMatches("someSubListQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsFalseIfResponseTypeIsSuperListOfProvidedType() throws NoSuchMethodException {
        testMatches("someSuperListQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsFalseIfResponseTypeIsBoundedGenericListOfProvidedType() throws NoSuchMethodException {
        testMatches("someBoundedGenericListQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsFalseIfResponseTypeIsUnboundedGenericList() throws NoSuchMethodException {
        testMatches("someUnboundedGenericListQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsFalseIfResponseTypeIsMultiBoundedGenericListOfProvidedType()
            throws NoSuchMethodException {
        testMatches("someMultiBoundedGenericListQuery", DOES_NOT_MATCH);
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
    void testMatchesReturnsFalseIfResponseTypeIsUpperBoundedWildcardListOfProvidedType()
            throws NoSuchMethodException {
        testMatches("someUpperBoundedWildcardListQuery", DOES_NOT_MATCH);
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
    void testMatchesReturnsFalseIfResponseTypeIsGenericUpperBoundedWildcardListOfProvidedType()
            throws NoSuchMethodException {
        testMatches("someGenericUpperBoundedWildcardListQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsFalseIfResponseTypeIsMultiGenericUpperBoundedWildcardListOfProvidedType()
            throws NoSuchMethodException {
        testMatches("someMultiGenericUpperBoundedWildcardListQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsFalseIfResponseTypeIsUnboundedListImplementationOfProvidedType()
            throws NoSuchMethodException {
        testMatches("someUnboundedListImplementationQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsFalseIfResponseTypeIsBoundedListImplementationOfProvidedType()
            throws NoSuchMethodException {
        testMatches("someBoundedListImplementationQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsFalseIfResponseTypeIsMultiUnboundedListImplementationOfProvidedType()
            throws NoSuchMethodException {
        testMatches("someMultiUnboundedListImplementationQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsFalseIfResponseTypeIsMultiBoundedListImplementationOfProvidedType()
            throws NoSuchMethodException {
        testMatches("someMultiBoundedListImplementationQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsFalseIfResponseTypeIsSetOfProvidedType() throws NoSuchMethodException {
        testMatches("someSetQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsFalseIfResponseTypeIsStreamOfProvidedType() throws NoSuchMethodException {
        testMatches("someStreamQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsFalseIfResponseTypeIsMapOfProvidedType() throws NoSuchMethodException {
        testMatches("someMapQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsTrueIfResponseTypeIsFutureOfProvidedType() throws NoSuchMethodException {
        testMatches("someFutureQuery", MATCHES);
    }

    @Test
    void testMatchesReturnsFalseIfResponseTypeIsListOfFutureOfProvidedType() throws NoSuchMethodException {
        testMatches("someFutureListQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsTrueIfResponseTypeIsOptionalOfProvidedType() throws NoSuchMethodException {
        testMatches("someOptionalQueryResponse", MATCHES);
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
