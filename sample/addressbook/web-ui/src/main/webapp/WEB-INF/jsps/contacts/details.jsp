<%@ taglib prefix="c" uri="http://java.sun.com/jstl/core_rt" %>
<%--
  ~ Copyright (c) 2010. Axon Framework
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~ http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  --%>

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
            <td><a href="${ctx}/contacts/${identifier}/address/delete/${address.addressType}">Remove</a>
            </td>
        </tr>
    </c:forEach>
    </tbody>
</table>