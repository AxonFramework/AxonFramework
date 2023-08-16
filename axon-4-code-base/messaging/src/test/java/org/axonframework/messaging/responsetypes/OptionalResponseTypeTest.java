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
    void matchesReturnsMatchIfResponseTypeIsTheSame() throws NoSuchMethodException {
        testMatchRanked("someQuery", MATCHES);
    }

    @Test
    void optionalMatchesExpectedType() throws NoSuchMethodException {
        Method methodToTest = methodOf(getClass(), "someOptionalQueryResponse");
        Type methodReturnType = methodToTest.getGenericReturnType();
        assertEquals(Boolean.TRUE, ResponseTypes.instanceOf(QueryResponse.class).matches(methodReturnType));
    }

    @Test
    void matchesReturnsMatchIfResponseTypeIsSubTypeOfProvidedType() throws NoSuchMethodException {
        testMatchRanked("someSubTypedQuery", MATCHES);
    }

    @Test
    void matchesReturnsNoMatchIfResponseTypeIsSuperTypeOfProvidedType() throws NoSuchMethodException {
        testMatchRanked("someSuperTypedQuery", DOES_NOT_MATCH);
    }

    @Test
    void matchesReturnsNoMatchIfResponseTypeIsUnboundedGeneric() throws NoSuchMethodException {
        testMatchRanked("someUnboundedGenericQuery", DOES_NOT_MATCH);
    }

    @Test
    void matchesReturnsMatchIfResponseTypeIsBoundedGenericOfProvidedType() throws NoSuchMethodException {
        testMatchRanked("someBoundedGenericQuery", MATCHES);
    }

    @Test
    void matchesReturnsMatchIfResponseTypeIsMultiBoundedGenericOfProvidedType() throws NoSuchMethodException {
        testMatchRanked("someMultiBoundedGenericQuery", MATCHES);
    }

    @Test
    void matchesReturnsNoMatchIfResponseTypeIsGenericOfOtherType() throws NoSuchMethodException {
        testMatchRanked("someNonMatchingBoundedGenericQuery", DOES_NOT_MATCH);
    }

    @Test
    void matchesReturnsNoMatchIfResponseTypeIsArrayOfProvidedType() throws NoSuchMethodException {
        testMatchRanked("someArrayQuery", DOES_NOT_MATCH);
    }

    @Test
    void matchesReturnsNoMatchIfResponseTypeIsArrayWithSubTypeOfProvidedType() throws NoSuchMethodException {
        testMatchRanked("someSubTypedArrayQuery", DOES_NOT_MATCH);
    }

    @Test
    void matchesReturnsNoMatchIfResponseTypeIsArrayWithSuperTypeOfProvidedType() throws NoSuchMethodException {
        testMatchRanked("someSuperTypedArrayQuery", DOES_NOT_MATCH);
    }

    @Test
    void matchesReturnsNoMatchIfResponseTypeIsUnboundedGenericArray() throws NoSuchMethodException {
        testMatchRanked("someUnboundedGenericArrayQuery", DOES_NOT_MATCH);
    }

    @Test
    void matchesReturnsNoMatchIfResponseTypeIsBoundedGenericArrayOfProvidedType()
            throws NoSuchMethodException {
        testMatchRanked("someBoundedGenericArrayQuery", DOES_NOT_MATCH);
    }

    @Test
    void matchesReturnsNoMatchIfResponseTypeIsMultiBoundedGenericArrayOfProvidedType()
            throws NoSuchMethodException {
        testMatchRanked("someMultiBoundedGenericArrayQuery", DOES_NOT_MATCH);
    }

    @Test
    void matchesReturnsNoMatchIfResponseTypeIsGenericArrayOfOtherType() throws NoSuchMethodException {
        testMatchRanked("someNonMatchingBoundedGenericArrayQuery", DOES_NOT_MATCH);
    }

    @Test
    void matchesReturnsNoMatchIfResponseTypeIsListOfProvidedType() throws NoSuchMethodException {
        testMatchRanked("someListQuery", DOES_NOT_MATCH);
    }

    @Test
    void matchesReturnsNoMatchIfResponseTypeIsSubListOfProvidedType() throws NoSuchMethodException {
        testMatchRanked("someSubListQuery", DOES_NOT_MATCH);
    }

    @Test
    void matchesReturnsNoMatchIfResponseTypeIsSuperListOfProvidedType() throws NoSuchMethodException {
        testMatchRanked("someSuperListQuery", DOES_NOT_MATCH);
    }

    @Test
    void matchesReturnsNoMatchIfResponseTypeIsBoundedGenericListOfProvidedType() throws NoSuchMethodException {
        testMatchRanked("someBoundedGenericListQuery", DOES_NOT_MATCH);
    }

    @Test
    void matchesReturnsNoMatchIfResponseTypeIsUnboundedGenericList() throws NoSuchMethodException {
        testMatchRanked("someUnboundedGenericListQuery", DOES_NOT_MATCH);
    }

    @Test
    void matchesReturnsNoMatchIfResponseTypeIsMultiBoundedGenericListOfProvidedType()
            throws NoSuchMethodException {
        testMatchRanked("someMultiBoundedGenericListQuery", DOES_NOT_MATCH);
    }

    @Test
    void matchesReturnsNoMatchIfResponseTypeIsGenericListOfOtherType() throws NoSuchMethodException {
        testMatchRanked("someNonMatchingBoundedGenericListQuery", DOES_NOT_MATCH);
    }

    @Test
    void matchesReturnsNoMatchIfResponseTypeIsUnboundedWildcardList() throws NoSuchMethodException {
        testMatchRanked("someUnboundedWildcardListQuery", DOES_NOT_MATCH);
    }

    @Test
    void matchesReturnsNoMatchIfResponseTypeIsLowerBoundedWildcardList() throws NoSuchMethodException {
        testMatchRanked("someLowerBoundedWildcardListQuery", DOES_NOT_MATCH);
    }

    @Test
    void matchesReturnsNoMatchIfResponseTypeIsUpperBoundedWildcardListOfProvidedType()
            throws NoSuchMethodException {
        testMatchRanked("someUpperBoundedWildcardListQuery", DOES_NOT_MATCH);
    }

    @Test
    void matchesReturnsNoMatchIfResponseTypeIsWildcardListOfOtherType() throws NoSuchMethodException {
        testMatchRanked("someNonMatchingUpperBoundedWildcardQuery", DOES_NOT_MATCH);
    }

    @Test
    void matchesReturnsNoMatchIfResponseTypeIsGenericUpperBoundedWildcardListOfProvidedType()
            throws NoSuchMethodException {
        testMatchRanked("someGenericUpperBoundedWildcardListQuery", DOES_NOT_MATCH);
    }

    @Test
    void matchesReturnsNoMatchIfResponseTypeIsMultiGenericUpperBoundedWildcardListOfProvidedType()
            throws NoSuchMethodException {
        testMatchRanked("someMultiGenericUpperBoundedWildcardListQuery", DOES_NOT_MATCH);
    }

    @Test
    void matchesReturnsNoMatchIfResponseTypeIsUnboundedGenericUpperBoundedWildcardList()
            throws NoSuchMethodException {
        testMatchRanked("someUnboundedGenericUpperBoundedWildcardListQuery", DOES_NOT_MATCH);
    }

    @Test
    void matchesReturnsNoMatchIfResponseTypeIsSetOfProvidedType() throws NoSuchMethodException {
        testMatchRanked("someSetQuery", DOES_NOT_MATCH);
    }

    @Test
    void matchesReturnsNoMatchIfResponseTypeIsStreamOfProvidedType() throws NoSuchMethodException {
        testMatchRanked("someStreamQuery", DOES_NOT_MATCH);
    }

    @Test
    void matchesReturnsNoMatchIfResponseTypeIsMapOfProvidedType() throws NoSuchMethodException {
        testMatchRanked("someMapQuery", DOES_NOT_MATCH);
    }

    @Test
    void matchesReturnsMatchIfResponseTypeIsFutureOfProvidedType() throws NoSuchMethodException {
        testMatchRanked("someFutureQuery", MATCHES);
    }

    @Test
    void convertReturnsSingleResponseAsProvidedOptional() {
        QueryResponse testResponse = new QueryResponse();

        Optional<QueryResponse> result = testSubject.convert(testResponse);

        assertTrue(result.isPresent());
        assertEquals(testResponse, result.get());
    }

    @Test
    void convertReturnsNullResponseAsEmptyOptional() {
        Optional<QueryResponse> result = testSubject.convert(null);

        assertFalse(result.isPresent());
    }

    @Test
    void convertReturnsSingleResponseAsIsForSubTypedResponse() {
        SubTypedQueryResponse testResponse = new SubTypedQueryResponse();

        Optional<QueryResponse> result = testSubject.convert(testResponse);

        assertTrue(result.isPresent());
        assertEquals(testResponse, result.get());
    }

    @SuppressWarnings("unused")
    @Test
    void convertThrowsClassCastExceptionForDifferentSingleInstanceResponse() {
        assertThrows(Exception.class, () -> testSubject.convert(new QueryResponseInterface() {
        }));
    }

    @SuppressWarnings("unused")
    @Test
    void convertThrowsClassCastExceptionForMultipleInstanceResponse() {
        assertThrows(Exception.class, () -> testSubject.convert(new ArrayList<QueryResponse>()));
    }
}
