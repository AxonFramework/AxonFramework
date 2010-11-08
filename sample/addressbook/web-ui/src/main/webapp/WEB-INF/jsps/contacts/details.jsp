<%@ taglib prefix="c" uri="http://java.sun.com/jstl/core_rt" %>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>

<p>Addresses for : <c:out value='${name}'/></p>

<p><a href="${ctx}/contacts/${identifier}/address/new">Create a new address</a></p>

<table class="hor-minimalist-b">
    <thead>
    <tr>
        <th>Type</th>
        <th>Street and number</th>
        <th>Zipcode</th>
        <th>City</th>
        <th>&nbsp;</th>
    </tr>
    </thead>
    <tbody>
    <c:forEach items="${addresses}" var="address">
        <tr>
            <td><c:out value='${address.addressType}'/></td>
            <td><c:out value='${address.streetAndNumber}'/></td>
            <td><c:out value='${address.zipCode}'/></td>
            <td><c:out value='${address.city}'/></td>
            <td><a href="${ctx}/contacts/${identifier}/address/delete&addressType=${address.addressType}">Remove</a>
            </td>
        </tr>
    </c:forEach>
    </tbody>
</table>