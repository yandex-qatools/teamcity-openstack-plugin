package ru.yandex.teamcity.plugins.button;

import jetbrains.buildServer.web.openapi.PagePlaces;
import jetbrains.buildServer.web.openapi.PlaceId;
import jetbrains.buildServer.web.openapi.SimplePageExtension;
import jetbrains.buildServer.web.util.WebUtil;
import org.jetbrains.annotations.NotNull;

import javax.servlet.http.HttpServletRequest;

public class PageExtension extends SimplePageExtension {

    public PageExtension(PagePlaces pagePlaces) {
        super(pagePlaces, PlaceId.ALL_PAGES_FOOTER, "cloud-openstack", "input.jsp");
        register();
    }

    @Override
    public boolean isAvailable(@NotNull final HttpServletRequest request) {
        return WebUtil.getPathWithoutAuthenticationType(request).startsWith("/overview.html");
    }
}