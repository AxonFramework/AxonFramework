package org.axonframework.queryhandling.responsetypes;

import org.junit.*;

@SuppressWarnings("unused")
public class InstanceResponseTypeTest<E> extends AbstractResponseTypeTest<AbstractResponseTypeTest.QueryResponse> {

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
    public void testMatchesReturnsTrueIfResponseTypeIsArrayOfProvidedType() throws NoSuchMethodException {
        testMatches("someArrayQuery", MATCHES);
    }

    @Test
    public void testMatchesReturnsTrueIfResponseTypeIsArrayWithSubTypeOfProvidedType() throws NoSuchMethodException {
        testMatches("someSubTypedArrayQuery", MATCHES);
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
    public void testMatchesReturnsTrueIfResponseTypeIsBoundedGenericArrayOfProvidedType() throws NoSuchMethodException {
        testMatches("someBoundedGenericArrayQuery", MATCHES);
    }

    @Test
    public void testMatchesReturnsTrueIfResponseTypeIsMultiBoundedGenericArrayOfProvidedType()
            throws NoSuchMethodException {
        testMatches("someMultiBoundedGenericArrayQuery", MATCHES);
    }

    @Test
    public void testMatchesReturnsFalseIfResponseTypeIsGenericArrayOfOtherType() throws NoSuchMethodException {
        testMatches("someNonMatchingBoundedGenericArrayQuery", DOES_NOT_MATCHES);
    }

    @Test
    public void testMatchesReturnsTrueIfResponseTypeIsListOfProvidedType() throws NoSuchMethodException {
        testMatches("someListQuery", MATCHES);
    }

    @Test
    public void testMatchesReturnsTrueIfResponseTypeIsSubListOfProvidedType() throws NoSuchMethodException {
        testMatches("someSubListQuery", MATCHES);
    }

    @Test
    public void testMatchesReturnsFalseIfResponseTypeIsSuperListOfProvidedType() throws NoSuchMethodException {
        testMatches("someSuperListQuery", DOES_NOT_MATCHES);
    }

    @Test
    public void testMatchesReturnsTrueIfResponseTypeIsBoundedGenericListOfProvidedType() throws NoSuchMethodException {
        testMatches("someBoundedGenericListQuery", MATCHES);
    }

    @Test
    public void testMatchesReturnsFalseIfResponseTypeIsUnboundedGenericList() throws NoSuchMethodException {
        testMatches("someUnboundedGenericListQuery", DOES_NOT_MATCHES);
    }

    @Test
    public void testMatchesReturnsTrueIfResponseTypeIsMultiBoundedGenericListOfProvidedType()
            throws NoSuchMethodException {
        testMatches("someMultiBoundedGenericListQuery", MATCHES);
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
    public void testMatchesReturnsTrueIfResponseTypeIsUpperBoundedWildcardListOfProvidedType()
            throws NoSuchMethodException {
        testMatches("someUpperBoundedWildcardListQuery", MATCHES);
    }

    @Test
    public void testMatchesReturnsFalseIfResponseTypeIsWildcardListOfOtherType() throws NoSuchMethodException {
        testMatches("someNonMatchingUpperBoundedWildcardQuery", DOES_NOT_MATCHES);
    }

    @Test
    public void testMatchesReturnsTrueIfResponseTypeIsGenericUpperBoundedWildcardListOfProvidedType()
            throws NoSuchMethodException {
        testMatches("someGenericUpperBoundedWildcardListQuery", MATCHES);
    }

    @Test
    public void testMatchesReturnsTrueIfResponseTypeIsMultiGenericUpperBoundedWildcardListOfProvidedType()
            throws NoSuchMethodException {
        testMatches("someMultiGenericUpperBoundedWildcardListQuery", MATCHES);
    }

    @Test
    public void testMatchesReturnsFalseIfResponseTypeIsUnboundedGenericUpperBoundedWildcardList()
            throws NoSuchMethodException {
        testMatches("someUnboundedGenericUpperBoundedWildcardListQuery", DOES_NOT_MATCHES);
    }

    @Test
    public void testMatchesReturnsTrueIfResponseTypeIsSetOfProvidedType() throws NoSuchMethodException {
        testMatches("someSetQuery", MATCHES);
    }

    @Test
    public void testMatchesReturnsTrueIfResponseTypeIsStreamOfProvidedType() throws NoSuchMethodException {
        testMatches("someStreamQuery", MATCHES);
    }

    @Test
    public void testMatchesReturnsFalseIfResponseTypeIsMapOfProvidedType() throws NoSuchMethodException {
        testMatches("someMapQuery", DOES_NOT_MATCHES);
    }
}
