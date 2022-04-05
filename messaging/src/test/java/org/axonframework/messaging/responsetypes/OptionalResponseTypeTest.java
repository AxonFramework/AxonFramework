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

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Optional;

import static org.axonframework.common.ReflectionUtils.methodOf;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test all possible permutations of Query Handler return types through the {@link OptionalResponseType}. To that end,
 * leveraging the  {@link AbstractResponseTypeTest} to cover all usual suspects between the different
 * {@link ResponseType} implementations.
 */
class OptionalResponseTypeTest
        extends AbstractResponseTypeTest<Optional<AbstractResponseTypeTest.QueryResponse>> {

    OptionalResponseTypeTest() {
        super(new OptionalResponseType<>(QueryResponse.class));
    }

    @Test
    void testMatchesReturnsMatchIfResponseTypeIsTheSame() throws NoSuchMethodException {
        testMatchRanked("someQuery", MATCHES);
    }

    @Test
    void testOptionalMatchesExpectedType() throws NoSuchMethodException {
        Method methodToTest = methodOf(getClass(), "someOptionalQueryResponse");
        Type methodReturnType = methodToTest.getGenericReturnType();
        assertEquals(Boolean.TRUE, ResponseTypes.instanceOf(QueryResponse.class).matches(methodReturnType));
    }

    @Test
    void testMatchesReturnsMatchIfResponseTypeIsSubTypeOfProvidedType() throws NoSuchMethodException {
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
    void testMatchesReturnsMatchIfResponseTypeIsBoundedGenericOfProvidedType() throws NoSuchMethodException {
        testMatchRanked("someBoundedGenericQuery", MATCHES);
    }

    @Test
    void testMatchesReturnsMatchIfResponseTypeIsMultiBoundedGenericOfProvidedType() throws NoSuchMethodException {
        testMatchRanked("someMultiBoundedGenericQuery", MATCHES);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsGenericOfOtherType() throws NoSuchMethodException {
        testMatchRanked("someNonMatchingBoundedGenericQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsArrayOfProvidedType() throws NoSuchMethodException {
        testMatchRanked("someArrayQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsArrayWithSubTypeOfProvidedType() throws NoSuchMethodException {
        testMatchRanked("someSubTypedArrayQuery", DOES_NOT_MATCH);
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
    void testMatchesReturnsNoMatchIfResponseTypeIsBoundedGenericArrayOfProvidedType()
            throws NoSuchMethodException {
        testMatchRanked("someBoundedGenericArrayQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsMultiBoundedGenericArrayOfProvidedType()
            throws NoSuchMethodException {
        testMatchRanked("someMultiBoundedGenericArrayQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsGenericArrayOfOtherType() throws NoSuchMethodException {
        testMatchRanked("someNonMatchingBoundedGenericArrayQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsListOfProvidedType() throws NoSuchMethodException {
        testMatchRanked("someListQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsSubListOfProvidedType() throws NoSuchMethodException {
        testMatchRanked("someSubListQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsSuperListOfProvidedType() throws NoSuchMethodException {
        testMatchRanked("someSuperListQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsBoundedGenericListOfProvidedType() throws NoSuchMethodException {
        testMatchRanked("someBoundedGenericListQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsUnboundedGenericList() throws NoSuchMethodException {
        testMatchRanked("someUnboundedGenericListQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsMultiBoundedGenericListOfProvidedType()
            throws NoSuchMethodException {
        testMatchRanked("someMultiBoundedGenericListQuery", DOES_NOT_MATCH);
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
    void testMatchesReturnsNoMatchIfResponseTypeIsUpperBoundedWildcardListOfProvidedType()
            throws NoSuchMethodException {
        testMatchRanked("someUpperBoundedWildcardListQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsWildcardListOfOtherType() throws NoSuchMethodException {
        testMatchRanked("someNonMatchingUpperBoundedWildcardQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsGenericUpperBoundedWildcardListOfProvidedType()
            throws NoSuchMethodException {
        testMatchRanked("someGenericUpperBoundedWildcardListQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsMultiGenericUpperBoundedWildcardListOfProvidedType()
            throws NoSuchMethodException {
        testMatchRanked("someMultiGenericUpperBoundedWildcardListQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsUnboundedGenericUpperBoundedWildcardList()
            throws NoSuchMethodException {
        testMatchRanked("someUnboundedGenericUpperBoundedWildcardListQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsSetOfProvidedType() throws NoSuchMethodException {
        testMatchRanked("someSetQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsStreamOfProvidedType() throws NoSuchMethodException {
        testMatchRanked("someStreamQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsNoMatchIfResponseTypeIsMapOfProvidedType() throws NoSuchMethodException {
        testMatchRanked("someMapQuery", DOES_NOT_MATCH);
    }

    @Test
    void testMatchesReturnsMatchIfResponseTypeIsFutureOfProvidedType() throws NoSuchMethodException {
        testMatchRanked("someFutureQuery", MATCHES);
    }

    @Test
    void testConvertReturnsSingleResponseAsProvidedOptional() {
        QueryResponse testResponse = new QueryResponse();

        Optional<QueryResponse> result = testSubject.convert(testResponse);

        assertTrue(result.isPresent());
        assertEquals(testResponse, result.get());
    }

    @Test
    void testConvertReturnsNullResponseAsEmptyOptional() {
        Optional<QueryResponse> result = testSubject.convert(null);

        assertFalse(result.isPresent());
    }

    @Test
    void testConvertReturnsSingleResponseAsIsForSubTypedResponse() {
        SubTypedQueryResponse testResponse = new SubTypedQueryResponse();

        Optional<QueryResponse> result = testSubject.convert(testResponse);

        assertTrue(result.isPresent());
        assertEquals(testResponse, result.get());
    }

    @SuppressWarnings("unused")
    @Test
    void testConvertThrowsClassCastExceptionForDifferentSingleInstanceResponse() {
        assertThrows(Exception.class, () -> testSubject.convert(new QueryResponseInterface() {
        }));
    }

    @SuppressWarnings("unused")
    @Test
    void testConvertThrowsClassCastExceptionForMultipleInstanceResponse() {
        assertThrows(Exception.class, () -> testSubject.convert(new ArrayList<QueryResponse>()));
    }
}
