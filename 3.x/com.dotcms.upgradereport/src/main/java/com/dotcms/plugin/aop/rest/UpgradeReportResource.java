package com.dotcms.plugin.aop.rest;

import com.dotcms.repackage.javax.ws.rs.GET;
import com.dotcms.repackage.javax.ws.rs.Path;
import com.dotcms.repackage.javax.ws.rs.Produces;
import com.dotcms.repackage.javax.ws.rs.core.Context;
import com.dotcms.repackage.javax.ws.rs.core.MediaType;
import com.dotcms.repackage.javax.ws.rs.core.Response;
import com.dotcms.rest.InitDataObject;
import com.dotcms.rest.WebResource;
import com.dotcms.rest.exception.mapper.ExceptionMapperUtil;
import com.liferay.portal.model.User;

import javax.servlet.http.HttpServletRequest;

@Path("/v1/upgrade/report")
public class UpgradeReportResource {

    private final WebResource webResource = new WebResource();
    private final UpgradeReportService upgradeReportService  =
            new UpgradeReportServiceImpl();

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getReport(@Context final HttpServletRequest request) {

        final InitDataObject initData = this.webResource.init
                (null, false, request, false, null);
        final User user = initData.getUser();
        Response   response = null;

        try {

            response = Response.ok(this.upgradeReportService.createReport()).build();
        } catch (Exception e) {

            response = ExceptionMapperUtil.createResponse
                    (e.getMessage(), e.getMessage());
        }

       return response;
    }
}