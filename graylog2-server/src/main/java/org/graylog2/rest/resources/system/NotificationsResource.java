/**
 * Copyright 2013 Lennart Koopmann <lennart@torch.sh>
 *
 * This file is part of Graylog2.
 *
 * Graylog2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Graylog2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Graylog2.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package org.graylog2.rest.resources.system;

import com.codahale.metrics.annotation.Timed;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mongodb.BasicDBObject;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.graylog2.notifications.Notification;
import org.graylog2.plugin.Tools;
import org.graylog2.rest.documentation.annotations.*;
import org.graylog2.rest.resources.RestResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

/**
 * @author Lennart Koopmann <lennart@torch.sh>
 */
@RequiresAuthentication
@Api(value = "System/Notifications", description = "Notifications generated by the system")
@Path("/system/notifications")
public class NotificationsResource extends RestResource {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationsResource.class);

    @GET @Timed
    @ApiOperation(value = "Get all active notifications")
    @Produces(MediaType.APPLICATION_JSON)
    public String listNotifications() {
        List<Map<String, Object>> notifications = Lists.newArrayList();

        for (Notification n : Notification.all(core)) {
            Map<String, Object> notification = Maps.newHashMap();
            notification.put("timestamp", Tools.getISO8601String(n.getTimestamp()));
            notification.put("severity", n.getSeverity().toString().toLowerCase());
            notification.put("type", n.getType().toString().toLowerCase());

            try {
                notifications.add(notification);
            } catch(IllegalArgumentException e) {
                LOG.warn("There is a notification type we can't handle: [" + n.getType() + "]");
                continue;
            }
        }

        Map<String, Object> result = Maps.newHashMap();
        result.put("total", notifications.size());
        result.put("notifications", notifications);

        return json(result);
    }

    @DELETE @Timed
    @Path("/{notificationType}")
    @ApiOperation(value = "Delete a notification")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "No such notification type.")
    })
    public Response deleteNotification(@ApiParam(title = "notificationType") @PathParam("notificationType") String notificationType) {
        Notification.Type type;

        try {
            type = Notification.Type.valueOf(notificationType.toUpperCase());
        } catch (IllegalArgumentException e) {
            LOG.warn("No such notification type: [" + notificationType + "]");
            return Response.status(400).build();
        }

        Notification.destroy(new BasicDBObject("type", type.toString().toLowerCase()), core, Notification.COLLECTION);

        return Response.status(Response.Status.NO_CONTENT).build();
    }
}
