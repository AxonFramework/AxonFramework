<%@ taglib prefix="decorator" uri="http://www.opensymphony.com/sitemesh/decorator" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jstl/core_rt" %>
<%--
  ~ Copyright (c) 2010. Gridshore
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~     http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  --%>

<c:set var="ctx" value="${pageContext.request.contextPath}"/>

<html>
<head>
    <title><decorator:title/></title>
    <link rel="stylesheet" href="${ctx}/style/main.css"/>
    <link rel="stylesheet" href="${ctx}/style/redmond/jquery-ui-1.8.4.custom.css"/>
    <script type="text/javascript" src="${ctx}/js/jquery-1.4.2.min.js"></script>
    <script type="text/javascript" src="${ctx}/js/jquery-ui-1.8.4.custom.min.js"></script>
    <script type="text/javascript">
        $(function() {
            $('#tabs').tabs({
                        select: function(event, ui) {
                            var url = $.data(ui.tab, 'load.tabs');
                            if (url) {
                                location.href = url;
                                return false;
                            }
                            return true;
                        }
                    });

            $('#primaryNavigation ul li.current').removeClass('current');
            var loc = window.location.pathname;
            if (loc.indexOf('/contacts') > -1) {
                $('#primaryNavigation ul li.contacts').addClass('current');
            } else if (loc.indexOf('/db') > -1) {
                $('#primaryNavigation ul li.db').addClass('current');
            } else {
                $('#primaryNavigation ul li.home').addClass('current');
            }

        });
    </script>
</head>
<body>
<div id="header">
    <div id="primaryNavigation">
        <span id="usermenu">Welcome</span>
        <ul>
            <li class="home"><a href="${ctx}/"><span>Home</span></a></li>
            <li class="contacts"><a href="${ctx}/contacts"><span>Contacts</span></a></li>
            <li class="db"><a href="${ctx}/db"><span>Database</span></a></li>
        </ul>
    </div>
</div>
<div id="main">
    <decorator:body/>
</div>
</body>
</html>
