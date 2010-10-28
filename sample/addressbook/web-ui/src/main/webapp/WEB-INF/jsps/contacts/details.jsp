<%@ taglib prefix="c" uri="http://java.sun.com/jstl/core_rt" %>

<p>Addresses for : <c:out value='${name}'/></p>

<table class="hor-minimalist-b">
    <thead>
    <tr>
        <th>Type</th>
        <th>Street and number</th>
        <th>Zipcode</th>
        <th>City</th>
    </tr>
    </thead>
    <tbody>
    <c:forEach items="${addresses}" var="address">
        <tr>
            <td><c:out value='${address.addressType}'/></td>
            <td><c:out value='${address.streetAndNumber}'/></td>
            <td><c:out value='${address.zipCode}'/></td>
            <td><c:out value='${address.city}'/></td>
        </tr>
    </c:forEach>
    </tbody>
</table>