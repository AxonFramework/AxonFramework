package org.axonframework.queryhandling.responsetypes;

import org.junit.*;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.axonframework.common.ReflectionUtils.methodOf;
import static org.junit.Assert.*;

@SuppressWarnings("unused")
public class InstanceResponseTypeTest<E> {

    private InstanceResponseType<QueryResponse> testSubject = new InstanceResponseType<>(QueryResponse.class);

    @Test
    public void testMatchesReturnsTrueIfResponseTypeIsTheSame() throws NoSuchMethodException {
        Method testMethod = methodOf(getClass(), "someQuery");

        assertTrue(testSubject.matches(testMethod.getGenericReturnType()));
    }

    @SuppressWarnings("unused")
    public QueryResponse someQuery() {
        return new QueryResponse();
    }

    @Test
    public void testMatchesReturnsTrueIfResponseTypeIsSubTypeOfProvidedType() throws NoSuchMethodException {
        Method testMethod = methodOf(getClass(), "someSubTypedQuery");

        assertTrue(testSubject.matches(testMethod.getGenericReturnType()));
    }

    @SuppressWarnings("unused")
    public SubTypedQueryResponse someSubTypedQuery() {
        return new SubTypedQueryResponse();
    }

    @Test
    public void testMatchesReturnsFalseIfResponseTypeIsSuperTypeOfProvidedType() throws NoSuchMethodException {
        Method testMethod = methodOf(getClass(), "someSuperTypedQuery");

        assertFalse(testSubject.matches(testMethod.getGenericReturnType()));
    }

    @SuppressWarnings("unused")
    public Object someSuperTypedQuery() {
        return new Object();
    }

    @Test
    public void testMatchesReturnsFalseIfResponseTypeIsUnboundedGeneric() throws NoSuchMethodException {
        Method testMethod = methodOf(getClass(), "someUnboundedGenericQuery");

        assertFalse(testSubject.matches(testMethod.getGenericReturnType()));
    }

    @SuppressWarnings({"unchecked", "unused"})
    public <E> E someUnboundedGenericQuery() {
        return (E) new SubTypedQueryResponse();
    }

    @Test
    public void testMatchesReturnsTrueIfResponseTypeIsBoundedGenericOfProvidedType() throws NoSuchMethodException {
        Method testMethod = methodOf(getClass(), "someBoundedGenericQuery");

        assertTrue(testSubject.matches(testMethod.getGenericReturnType()));
    }

    @SuppressWarnings({"unchecked", "unused"})
    public <E extends QueryResponse> E someBoundedGenericQuery() {
        return (E) new SubTypedQueryResponse();
    }

    @Test
    public void testMatchesReturnsTrueIfResponseTypeIsMultiBoundedGenericOfProvidedType()
            throws NoSuchMethodException {
        Method testMethod = methodOf(getClass(), "someMultiBoundedGenericQuery");

        assertTrue(testSubject.matches(testMethod.getGenericReturnType()));
    }

    @SuppressWarnings({"unchecked", "unused"})
    public <E extends SubTypedQueryResponse & QueryResponseInterface> E someMultiBoundedGenericQuery() {
        return (E) new ComplexTypedQueryResponse();
    }

    @Test
    public void testMatchesReturnsFalseIfResponseTypeIsGenericOfOtherType() throws NoSuchMethodException {
        Method testMethod = methodOf(getClass(), "someNonMatchingBoundedGenericQuery");

        assertFalse(testSubject.matches(testMethod.getGenericReturnType()));
    }

    @SuppressWarnings({"unchecked", "unused"})
    public <E extends QueryResponseInterface> E someNonMatchingBoundedGenericQuery() {
        return (E) new QueryResponseInterface() {
        };
    }

    @Test
    public void testMatchesReturnsTrueIfResponseTypeIsArrayOfProvidedType() throws NoSuchMethodException {
        Method testMethod = methodOf(getClass(), "someArrayQuery");

        assertTrue(testSubject.matches(testMethod.getGenericReturnType()));
    }

    @SuppressWarnings("unused")
    public QueryResponse[] someArrayQuery() {
        return new QueryResponse[]{};
    }

    @Test
    public void testMatchesReturnsTrueIfResponseTypeIsArrayWithSubTypeOfProvidedType() throws NoSuchMethodException {
        Method testMethod = methodOf(getClass(), "someSubTypedArrayQuery");

        assertTrue(testSubject.matches(testMethod.getGenericReturnType()));
    }

    @SuppressWarnings("unused")
    public SubTypedQueryResponse[] someSubTypedArrayQuery() {
        return new SubTypedQueryResponse[]{};
    }

    @Test
    public void testMatchesReturnsFalseIfResponseTypeIsArrayWithSuperTypeOfProvidedType() throws NoSuchMethodException {
        Method testMethod = methodOf(getClass(), "someSuperTypedArrayQuery");

        assertFalse(testSubject.matches(testMethod.getGenericReturnType()));
    }

    @SuppressWarnings("unused")
    public Object[] someSuperTypedArrayQuery() {
        return new Object[]{};
    }

    @Test
    public void testMatchesReturnsFalseIfResponseTypeIsUnboundedGenericArray() throws NoSuchMethodException {
        Method testMethod = methodOf(getClass(), "someUnboundedGenericArrayQuery");

        assertFalse(testSubject.matches(testMethod.getGenericReturnType()));
    }

    @SuppressWarnings({"unused", "unchecked"})
    public <E> E[] someUnboundedGenericArrayQuery() {
        return (E[]) new SubTypedQueryResponse[]{};
    }

    @Test
    public void testMatchesReturnsTrueIfResponseTypeIsBoundedGenericArrayOfProvidedType() throws NoSuchMethodException {
        Method testMethod = methodOf(getClass(), "someBoundedGenericArrayQuery");

        assertTrue(testSubject.matches(testMethod.getGenericReturnType()));
    }

    @SuppressWarnings({"unused", "unchecked"})
    public <E extends QueryResponse> E[] someBoundedGenericArrayQuery() {
        return (E[]) new SubTypedQueryResponse[]{};
    }

    @Test
    public void testMatchesReturnsTrueIfResponseTypeIsMultiBoundedGenericArrayOfProvidedType()
            throws NoSuchMethodException {
        Method testMethod = methodOf(getClass(), "someMultiBoundedGenericArrayQuery");

        assertTrue(testSubject.matches(testMethod.getGenericReturnType()));
    }

    @SuppressWarnings({"unused", "unchecked"})
    public <E extends SubTypedQueryResponse & QueryResponseInterface> E[] someMultiBoundedGenericArrayQuery() {
        return (E[]) new ComplexTypedQueryResponse[]{};
    }

    @Test
    public void testMatchesReturnsFalseIfResponseTypeIsGenericArrayOfOtherType() throws NoSuchMethodException {
        Method testMethod = methodOf(getClass(), "someNonMatchingBoundedGenericArrayQuery");

        assertFalse(testSubject.matches(testMethod.getGenericReturnType()));
    }

    @SuppressWarnings({"unused", "unchecked"})
    public <E extends QueryResponseInterface> E[] someNonMatchingBoundedGenericArrayQuery() {
        return (E[]) new SubTypedQueryResponse[]{};
    }

    @Test
    public void testMatchesReturnsTrueIfResponseTypeIsListOfProvidedType() throws NoSuchMethodException {
        Method testMethod = methodOf(getClass(), "someListQuery");

        assertTrue(testSubject.matches(testMethod.getGenericReturnType()));
    }

    @SuppressWarnings("unused")
    public List<QueryResponse> someListQuery() {
        return new ArrayList<>();
    }

    @Test
    public void testMatchesReturnsTrueIfResponseTypeIsSubListOfProvidedType() throws NoSuchMethodException {
        Method testMethod = methodOf(getClass(), "someSubListQuery");

        assertTrue(testSubject.matches(testMethod.getGenericReturnType()));
    }

    @SuppressWarnings("unused")
    public List<SubTypedQueryResponse> someSubListQuery() {
        return new ArrayList<>();
    }

    @Test
    public void testMatchesReturnsFalseIfResponseTypeIsSuperListOfProvidedType() throws NoSuchMethodException {
        Method testMethod = methodOf(getClass(), "someSuperListQuery");

        assertFalse(testSubject.matches(testMethod.getGenericReturnType()));
    }

    @SuppressWarnings("unused")
    public List<Object> someSuperListQuery() {
        return new ArrayList<>();
    }

    @Test
    public void testMatchesReturnsTrueIfResponseTypeIsBoundedGenericListOfProvidedType() throws NoSuchMethodException {
        Method testMethod = methodOf(getClass(), "someBoundedGenericListQuery");

        assertTrue(testSubject.matches(testMethod.getGenericReturnType()));
    }

    @SuppressWarnings("unused")
    public <E extends QueryResponse> List<E> someBoundedGenericListQuery() {
        return new ArrayList<>();
    }

    @Test
    public void testMatchesReturnsFalseIfResponseTypeIsUnboundedGenericList() throws NoSuchMethodException {
        Method testMethod = methodOf(getClass(), "someUnboundedGenericListQuery");

        assertFalse(testSubject.matches(testMethod.getGenericReturnType()));
    }

    @SuppressWarnings("unused")
    public <E> List<E> someUnboundedGenericListQuery() {
        return new ArrayList<>();
    }

    @Test
    public void testMatchesReturnsTrueIfResponseTypeIsMultiBoundedGenericListOfProvidedType() throws NoSuchMethodException {
        Method testMethod = methodOf(getClass(), "someMultiBoundedGenericListQuery");

        assertTrue(testSubject.matches(testMethod.getGenericReturnType()));
    }

    @SuppressWarnings("unused")
    public <E extends SubTypedQueryResponse & QueryResponseInterface> List<E> someMultiBoundedGenericListQuery() {
        return new ArrayList<>();
    }

    @Test
    public void testMatchesReturnsFalseIfResponseTypeIsGenericListOfOtherType() throws NoSuchMethodException {
        Method testMethod = methodOf(getClass(), "someNonMatchingBoundedGenericListQuery");

        assertFalse(testSubject.matches(testMethod.getGenericReturnType()));
    }

    @SuppressWarnings("unused")
    public <E extends QueryResponseInterface> List<E> someNonMatchingBoundedGenericListQuery() {
        return new ArrayList<>();
    }

    @Test
    public void testMatchesReturnsFalseIfResponseTypeIsUnboundedWildcardList() throws NoSuchMethodException {
        Method testMethod = methodOf(getClass(), "someUnboundedWildcardListQuery");

        assertFalse(testSubject.matches(testMethod.getGenericReturnType()));
    }

    @SuppressWarnings("unused")
    public List<?> someUnboundedWildcardListQuery() {
        return new ArrayList<>();
    }

    @Test
    public void testMatchesReturnsFalseIfResponseTypeIsLowerBoundedWildcardList() throws NoSuchMethodException {
        Method testMethod = methodOf(getClass(), "someLowerBoundedWildcardListQuery");

        assertFalse(testSubject.matches(testMethod.getGenericReturnType()));
    }

    @SuppressWarnings("unused")
    public List<? super QueryResponse> someLowerBoundedWildcardListQuery() {
        return new ArrayList<>();
    }

    @Test
    public void testMatchesReturnsTrueIfResponseTypeIsUpperBoundedWildcardListOfProvidedType() throws NoSuchMethodException {
        Method testMethod = methodOf(getClass(), "someUpperBoundedWildcardListQuery");

        assertTrue(testSubject.matches(testMethod.getGenericReturnType()));
    }

    @SuppressWarnings("unused")
    public List<? extends SubTypedQueryResponse> someUpperBoundedWildcardListQuery() {
        return new ArrayList<>();
    }

    @Test
    public void testMatchesReturnsFalseIfResponseTypeIsWildcardListOfOtherType() throws NoSuchMethodException {
        Method testMethod = methodOf(getClass(), "someNonMatchingUpperBoundedWildcardQuery");

        assertFalse(testSubject.matches(testMethod.getGenericReturnType()));
    }

    @SuppressWarnings("unused")
    public List<? extends QueryResponseInterface> someNonMatchingUpperBoundedWildcardQuery() {
        return new ArrayList<>();
    }

    @Test
    public void testMatchesReturnsTrueIfResponseTypeIsGenericUpperBoundedWildcardListOfProvidedType() throws NoSuchMethodException {
        Method testMethod = methodOf(getClass(), "someGenericUpperBoundedWildcardListQuery");

        assertTrue(testSubject.matches(testMethod.getGenericReturnType()));
    }

    @SuppressWarnings("unused")
    public <E> List<? extends E> someUnboundedGenericUpperBoundedWildcardListQuery() {
        return new ArrayList<>();
    }

    @Test
    public void testMatchesReturnsTrueIfResponseTypeIsMultiGenericUpperBoundedWildcardListOfProvidedType() throws NoSuchMethodException {
        Method testMethod = methodOf(getClass(), "someMultiGenericUpperBoundedWildcardListQuery");

        assertTrue(testSubject.matches(testMethod.getGenericReturnType()));
    }

    @SuppressWarnings("unused")
    public <E extends SubTypedQueryResponse> List<? extends E> someGenericUpperBoundedWildcardListQuery() {
        return new ArrayList<>();
    }

    @Test
    public void testMatchesReturnsFalseIfResponseTypeIsUnboundedGenericUpperBoundedWildcardList() throws NoSuchMethodException {
        Method testMethod = methodOf(getClass(), "someUnboundedGenericUpperBoundedWildcardListQuery");

        assertFalse(testSubject.matches(testMethod.getGenericReturnType()));
    }

    @SuppressWarnings("unused")
    public <E extends SubTypedQueryResponse & QueryResponseInterface> List<? extends E> someMultiGenericUpperBoundedWildcardListQuery() {
        return new ArrayList<>();
    }

    @Test
    public void testMatchesReturnsTrueIfResponseTypeIsSetOfProvidedType() throws NoSuchMethodException {
        Method testMethod = methodOf(getClass(), "someSetQuery");

        assertTrue(testSubject.matches(testMethod.getGenericReturnType()));
    }

    @SuppressWarnings("unused")
    public Set<QueryResponse> someSetQuery() {
        return new HashSet<>();
    }

    @Test
    public void testMatchesReturnsTrueIfResponseTypeIsStreamOfProvidedType() throws NoSuchMethodException {
        Method testMethod = methodOf(getClass(), "someStreamQuery");

        assertTrue(testSubject.matches(testMethod.getGenericReturnType()));
    }

    @SuppressWarnings("unused")
    public Stream<QueryResponse> someStreamQuery() {
        return Stream.of(new QueryResponse());
    }

    @Test
    public void testMatchesReturnsFalseIfResponseTypeIsMapOfProvidedType() throws NoSuchMethodException {
        Method testMethod = methodOf(getClass(), "someMapQuery");

        assertFalse(testSubject.matches(testMethod.getGenericReturnType()));
    }

    @SuppressWarnings("unused")
    public Map<QueryResponse, QueryResponse> someMapQuery() {
        return new HashMap<>();
    }

    private static class QueryResponse {

    }

    private static class SubTypedQueryResponse extends QueryResponse {

    }

    private interface QueryResponseInterface {

    }

    private static class ComplexTypedQueryResponse extends SubTypedQueryResponse implements QueryResponseInterface {

    }
}
