<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<jsp:useBean id="image" type="jetbrains.buildServer.clouds.openstack.OpenstackCloudImage" scope="request"/>
Image location: <c:out value="${image.agentHomeDir.absolutePath}"/>

<c:if test="${image.reusable}">
 <br/>
 Instances will be reused. Agent will be un-authorized after stop
</c:if>
