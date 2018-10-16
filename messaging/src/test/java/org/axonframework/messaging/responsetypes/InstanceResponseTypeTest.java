/*
 * Copyright (c) 2010-2018. Axon Framework
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

import org.junit.*;

import java.util.ArrayList;

import static org.junit.Assert.*;

public class InstanceResponseTypeTest extends AbstractResponseTypeTest<AbstractResponseTypeTest.QueryResponse> {

    public InstanceResponseTypeTest() {
        super(new InstanceResponseType<>(QueryResponse.class));
    }

    @Test
    public void testMatchesReturnsTrueIfResponseTypeIsTheSame() throws NoSuchMethodException {
        testMatches("someQuery", MATCHES);
    }

    @Test
    public void testMatchesReturnsTrueIfResponseTypeIsSubTypeOfProvidedType() throws NoSuchMethodException {
        testMatches("someSubTypedQuery", MATCHES);
    }

    @Test
    public void testMatchesReturnsFalseIfResponseTypeIsSuperTypeOfProvidedType() throws NoSuchMethodException {
        testMatches("someSuperTypedQuery", DOES_NOT_MATCHES);
    }

    @Test
    public void testMatchesReturnsFalseIfResponseTypeIsUnboundedGeneric() throws NoSuchMethodException {
        testMatches("someUnboundedGenericQuery", DOES_NOT_MATCHES);
    }

    @Test
    public void testMatchesReturnsTrueIfResponseTypeIsBoundedGenericOfProvidedType() throws NoSuchMethodException {
        testMatches("someBoundedGenericQuery", MATCHES);
    }

    @Test
    public void testMatchesReturnsTrueIfResponseTypeIsMultiBoundedGenericOfProvidedType() throws NoSuchMethodException {
        testMatches("someMultiBoundedGenericQuery", MATCHES);
    }

    @Test
    public void testMatchesReturnsFalseIfResponseTypeIsGenericOfOtherType() throws NoSuchMethodException {
        testMatches("someNonMatchingBoundedGenericQuery", DOES_NOT_MATCHES);
    }

    @Test
    public void testMatchesReturnsFalseIfResponseTypeIsArrayOfProvidedType() throws NoSuchMethodException {
        testMatches("someArrayQuery", DOES_NOT_MATCHES);
    }

    @Test
    public void testMatchesReturnsFalseIfResponseTypeIsArrayWithSubTypeOfProvidedType() throws NoSuchMethodException {
        testMatches("someSubTypedArrayQuery", DOES_NOT_MATCHES);
    }

    @Test
    public void testMatchesReturnsFalseIfResponseTypeIsArrayWithSuperTypeOfProvidedType() throws NoSuchMethodException {
        testMatches("someSuperTypedArrayQuery", DOES_NOT_MATCHES);
    }

    @Test
    public void testMatchesReturnsFalseIfResponseTypeIsUnboundedGenericArray() throws NoSuchMethodException {
        testMatches("someUnboundedGenericArrayQuery", DOES_NOT_MATCHES);
    }

    @Test
    public void testMatchesReturnsFalseIfResponseTypeIsBoundedGenericArrayOfProvidedType()
            throws NoSuchMethodException {
        testMatches("someBoundedGenericArrayQuery", DOES_NOT_MATCHES);
    }

    @Test
    public void testMatchesReturnsFalseIfResponseTypeIsMultiBoundedGenericArrayOfProvidedType()
            throws NoSuchMethodException {
        testMatches("someMultiBoundedGenericArrayQuery", DOES_NOT_MATCHES);
    }

    @Test
    public void testMatchesReturnsFalseIfResponseTypeIsGenericArrayOfOtherType() throws NoSuchMethodException {
        testMatches("someNonMatchingBoundedGenericArrayQuery", DOES_NOT_MATCHES);
    }

    @Test
    public void testMatchesReturnsFalseIfResponseTypeIsListOfProvidedType() throws NoSuchMethodException {
        testMatches("someListQuery", DOES_NOT_MATCHES);
    }

    @Test
    public void testMatchesReturnsFalseIfResponseTypeIsSubListOfProvidedType() throws NoSuchMethodException {
        testMatches("someSubListQuery", DOES_NOT_MATCHES);
    }

    @Test
    public void testMatchesReturnsFalseIfResponseTypeIsSuperListOfProvidedType() throws NoSuchMethodException {
        testMatches("someSuperListQuery", DOES_NOT_MATCHES);
    }

    @Test
    public void testMatchesReturnsFalseIfResponseTypeIsBoundedGenericListOfProvidedType() throws NoSuchMethodException {
        testMatches("someBoundedGenericListQuery", DOES_NOT_MATCHES);
    }

    @Test
    public void testMatchesReturnsFalseIfResponseTypeIsUnboundedGenericList() throws NoSuchMethodException {
        testMatches("someUnboundedGenericListQuery", DOES_NOT_MATCHES);
    }

    @Test
    public void testMatchesReturnsFalseIfResponseTypeIsMultiBoundedGenericListOfProvidedType()
            throws NoSuchMethodException {
        testMatches("someMultiBoundedGenericListQuery", DOES_NOT_MATCHES);
    }

    @Test
    public void testMatchesReturnsFalseIfResponseTypeIsGenericListOfOtherType() throws NoSuchMethodException {
        testMatches("someNonMatchingBoundedGenericListQuery", DOES_NOT_MATCHES);
    }

    @Test
    public void testMatchesReturnsFalseIfResponseTypeIsUnboundedWildcardList() throws NoSuchMethodException {
        testMatches("someUnboundedWildcardListQuery", DOES_NOT_MATCHES);
    }

    @Test
    public void testMatchesReturnsFalseIfResponseTypeIsLowerBoundedWildcardList() throws NoSuchMethodException {
        testMatches("someLowerBoundedWildcardListQuery", DOES_NOT_MATCHES);
    }

    @Test
    public void testMatchesReturnsFalseIfResponseTypeIsUpperBoundedWildcardListOfProvidedType()
            throws NoSuchMethodException {
        testMatches("someUpperBoundedWildcardListQuery", DOES_NOT_MATCHES);
    }

    @Test
    public void testMatchesReturnsFalseIfResponseTypeIsWildcardListOfOtherType() throws NoSuchMethodException {
        testMatches("someNonMatchingUpperBoundedWildcardQuery", DOES_NOT_MATCHES);
    }

    @Test
    public void testMatchesReturnsFalseIfResponseTypeIsGenericUpperBoundedWildcardListOfProvidedType()
            throws NoSuchMethodException {
        testMatches("someGenericUpperBoundedWildcardListQuery", DOES_NOT_MATCHES);
    }

    @Test
    public void testMatchesReturnsFalseIfResponseTypeIsMultiGenericUpperBoundedWildcardListOfProvidedType()
            throws NoSuchMethodException {
        testMatches("someMultiGenericUpperBoundedWildcardListQuery", DOES_NOT_MATCHES);
    }

    @Test
    public void testMatchesReturnsFalseIfResponseTypeIsUnboundedGenericUpperBoundedWildcardList()
            throws NoSuchMethodException {
        testMatches("someUnboundedGenericUpperBoundedWildcardListQuery", DOES_NOT_MATCHES);
    }

    @Test
    public void testMatchesReturnsFalseIfResponseTypeIsSetOfProvidedType() throws NoSuchMethodException {
        testMatches("someSetQuery", DOES_NOT_MATCHES);
    }

    @Test
    public void testMatchesReturnsFalseIfResponseTypeIsStreamOfProvidedType() throws NoSuchMethodException {
        testMatches("someStreamQuery", DOES_NOT_MATCHES);
    }

    @Test
    public void testMatchesReturnsFalseIfResponseTypeIsMapOfProvidedType() throws NoSuchMethodException {
        testMatches("someMapQuery", DOES_NOT_MATCHES);
    }

    @Test
    public void testMatchesReturnsTrueIfResponseTypeIsFutureOfProvidedType() throws NoSuchMethodException {
        testMatches("someFutureQuery", MATCHES);
    }

    @Test
    public void testConvertReturnsSingleResponseAsIs() {
        QueryResponse testResponse = new QueryResponse();

        QueryResponse result = testSubject.convert(testResponse);

        assertEquals(testResponse, result);
    }

    @Test
    public void testConvertReturnsSingleResponseAsIsForSubTypedResponse() {
        SubTypedQueryResponse testResponse = new SubTypedQueryResponse();

        QueryResponse result = testSubject.convert(testResponse);

        assertEquals(testResponse, result);
    }

    @SuppressWarnings("unused")
    @Test(expected = Exception.class)
    public void testConvertThrowsClassCastExceptionForDifferentSingleInstanceResponse() {
        QueryResponse result = testSubject.convert(new QueryResponseInterface() {});
    }

    @SuppressWarnings("unused")
    @Test(expected = Exception.class)
    public void testConvertThrowsClassCastExceptionForMultipleInstanceResponse() {
        QueryResponse result = testSubject.convert(new ArrayList<QueryResponse>());
    }
}
