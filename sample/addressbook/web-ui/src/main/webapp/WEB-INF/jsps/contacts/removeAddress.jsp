<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jstl/core_rt" %>

<p>Delete address of type : <c:out value='${address.addressType}'/></p>

<form:form commandName="address">
    <form:errors path="*" cssClass="errorBox"/>
    <form:hidden path="identifier"/>
    <form:hidden path="addressType"/>
    <input type="submit" name="submit" value="Delete"/>
</form:form>