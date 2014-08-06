<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<jsp:useBean id="image" type="jetbrains.buildServer.clouds.openstack.OpenstackCloudImage" scope="request"/>
<b>image:</b> <c:out value="${image.getOpenstackImageName()}"/> <b>flavor:</b> <c:out value="${image.getOpenstackFalvorName()}"/>
