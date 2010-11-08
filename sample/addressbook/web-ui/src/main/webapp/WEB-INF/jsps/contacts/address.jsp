<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jstl/core_rt" %>

<p>Add address for contact : <c:out value='${address.name}'/></p>

<form:form commandName="address">
    <form:errors path="*" cssClass="errorBox"/>
    <form:hidden path="identifier"/>
    <table>
        <tr>
            <td>Address type</td>
            <td>
                <form:select path="addressType">
                    <form:options/>
                </form:select>
            </td>
        </tr>
        <tr>
            <td>Street and number :</td>
            <td><form:input path="streetAndNumber"/></td>
        </tr>
        <tr>
            <td>Zip code :</td>
            <td><form:input path="zipCode"/></td>
        </tr>
        <tr>
            <td>City :</td>
            <td><form:input path="city"/></td>
        </tr>
        <tr>
            <td colspan="2">
                <input type="submit" name="submit" value="Submit"/>
            </td>
        </tr>
    </table>
</form:form>