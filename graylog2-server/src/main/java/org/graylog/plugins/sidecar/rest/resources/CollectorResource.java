/*
 * Copyright (C) 2020 Graylog, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Server Side Public License, version 1,
 * as published by MongoDB, Inc.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * Server Side Public License for more details.
 *
 * You should have received a copy of the Server Side Public License
 * along with this program. If not, see
 * <http://www.mongodb.com/licensing/server-side-public-license>.
 */
package org.graylog.plugins.sidecar.rest.resources;

import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.graylog.plugins.sidecar.audit.SidecarAuditEventTypes;
import org.graylog.plugins.sidecar.permissions.SidecarRestPermissions;
import org.graylog.plugins.sidecar.rest.models.Collector;
import org.graylog.plugins.sidecar.rest.models.CollectorSummary;
import org.graylog.plugins.sidecar.rest.responses.CollectorListResponse;
import org.graylog.plugins.sidecar.rest.responses.CollectorSummaryResponse;
import org.graylog.plugins.sidecar.services.CollectorService;
import org.graylog.plugins.sidecar.services.ConfigurationService;
import org.graylog.plugins.sidecar.services.EtagService;
import org.graylog2.audit.jersey.AuditEvent;
import org.graylog2.audit.jersey.NoAuditEvent;
import org.graylog2.database.PaginatedList;
import org.graylog2.plugin.rest.PluginRestResource;
import org.graylog2.plugin.rest.ValidationResult;
import org.graylog2.rest.models.SortOrder;
import org.graylog2.search.SearchQuery;
import org.graylog2.search.SearchQueryField;
import org.graylog2.search.SearchQueryParser;
import org.graylog2.shared.rest.resources.RestResource;
import org.graylog2.shared.utilities.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Inject;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.CacheControl;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.graylog2.shared.rest.documentation.generator.Generator.CLOUD_VISIBLE;

@Api(value = "Sidecar/Collectors", description = "Manage collectors", tags = {CLOUD_VISIBLE})
@Path("/sidecar/collectors")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiresAuthentication
public class CollectorResource extends RestResource implements PluginRestResource {
    private static final Logger LOG = LoggerFactory.getLogger(CollectorResource.class);

    private static final Pattern VALID_COLLECTOR_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_.-]+$");
    // exclude special characters from path ; * ? " < > | &
    private static final Pattern VALID_PATH_PATTERN = Pattern.compile("^[^;*?\"<>|&]+$");
    private static final Set<String> VALID_LINUX_SERVICE_TYPES = Collections.singleton("exec");
    private static final Set<String> VALID_WINDOWS_SERVICE_TYPES = ImmutableSet.of("exec", "svc");
    private static final Set<String> VALID_DARWIN_SERVICE_TYPES = Collections.singleton("exec");
    private static final Set<String> VALID_FREEBSD_SERVICE_TYPES = Collections.singleton("exec");
    private static final List<String> VALID_OPERATING_SYSTEMS = Arrays.asList("linux", "windows", "darwin", "freebsd");

    private final CollectorService collectorService;
    private final ConfigurationService configurationService;
    private final EtagService etagService;
    private final SearchQueryParser searchQueryParser;
    private static final ImmutableMap<String, SearchQueryField> SEARCH_FIELD_MAPPING = ImmutableMap.<String, SearchQueryField>builder()
            .put("id", SearchQueryField.create(Collector.FIELD_ID))
            .put("name", SearchQueryField.create(Collector.FIELD_NAME))
            .put("operating_system", SearchQueryField.create(Collector.FIELD_NODE_OPERATING_SYSTEM))
            .build();

    @Inject
    public CollectorResource(CollectorService collectorService,
                             ConfigurationService configurationService,
                             EtagService etagService) {
        this.collectorService = collectorService;
        this.configurationService = configurationService;
        this.etagService = etagService;
        this.searchQueryParser = new SearchQueryParser(Collector.FIELD_NAME, SEARCH_FIELD_MAPPING);
    }

    @GET
    @Path("/{id}")
    @RequiresPermissions(SidecarRestPermissions.COLLECTORS_READ)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Show collector details")
    public Collector getCollector(@ApiParam(name = "id", required = true)
                                  @PathParam("id") String id) {

        final Collector collector = this.collectorService.find(id);
        if (collector == null) {
            throw new NotFoundException("Cound not find collector <" + id + ">.");
        }

        return collector;
    }

    @GET
    @Timed
    @RequiresPermissions(SidecarRestPermissions.COLLECTORS_READ)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "List all collectors")
    public Response listCollectors(@Context HttpHeaders httpHeaders) throws JsonProcessingException {
        String ifNoneMatch = httpHeaders.getHeaderString("If-None-Match");
        Boolean etagCached = false;
        Response.ResponseBuilder builder = Response.noContent();

        // check if client is up-to-date with a known valid etag
        if (ifNoneMatch != null) {
            EntityTag etag = new EntityTag(ifNoneMatch.replaceAll("\"", ""));
            if (etagService.collectorsAreCached(etag.toString())) {
                etagCached = true;
                builder = Response.notModified();
                builder.tag(etag);
            }
        }

        // fetch collector list from database if client is outdated
        if (!etagCached) {
            final List<Collector> result = this.collectorService.all();
            CollectorListResponse collectorListResponse = CollectorListResponse.create(result.size(), result);

            // add new etag to cache
            EntityTag collectorsEtag = etagService.buildEntityTagForResponse(collectorListResponse);
            builder = Response.ok(collectorListResponse);
            builder.tag(collectorsEtag);
            etagService.registerCollector(collectorsEtag.toString());
        }

        // set cache control
        CacheControl cacheControl = new CacheControl();
        cacheControl.setNoTransform(true);
        cacheControl.setPrivate(true);
        builder.cacheControl(cacheControl);

        return builder.build();
    }

    @GET
    @Path("/summary")
    @RequiresPermissions(SidecarRestPermissions.COLLECTORS_READ)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "List a summary of all collectors")
    public CollectorSummaryResponse listSummary(@ApiParam(name = "page") @QueryParam("page") @DefaultValue("1") int page,
                                                @ApiParam(name = "per_page") @QueryParam("per_page") @DefaultValue("50") int perPage,
                                                @ApiParam(name = "query") @QueryParam("query") @DefaultValue("") String query,
                                                @ApiParam(name = "sort",
                                                          value = "The field to sort the result on",
                                                          required = true,
                                                          allowableValues = "name,id,collector_id")
                                                @DefaultValue(Collector.FIELD_NAME) @QueryParam("sort") String sort,
                                                @ApiParam(name = "order", value = "The sort direction", allowableValues = "asc, desc")
                                                @DefaultValue("asc") @QueryParam("order") SortOrder order) {
        final SearchQuery searchQuery = searchQueryParser.parse(query);
        final PaginatedList<Collector> collectors = this.collectorService.findPaginated(searchQuery, page, perPage, sort, order);
        final long total = this.collectorService.count();
        final List<CollectorSummary> summaries = collectors.stream()
                .map(CollectorSummary::create)
                .collect(Collectors.toList());

        return CollectorSummaryResponse.create(query, collectors.pagination(), total, sort, order, summaries);
    }

    @POST
    @RequiresPermissions(SidecarRestPermissions.COLLECTORS_CREATE)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Create a new collector")
    @AuditEvent(type = SidecarAuditEventTypes.COLLECTOR_CREATE)
    public Response createCollector(@ApiParam(name = "JSON body", required = true)
                                    @Valid @NotNull Collector request) throws BadRequestException {
        return saveCollector(collectorService.fromRequest(request));
    }

    @PUT
    @Path("/{id}")
    @RequiresPermissions(SidecarRestPermissions.COLLECTORS_UPDATE)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Update a collector")
    @AuditEvent(type = SidecarAuditEventTypes.COLLECTOR_UPDATE)
    public Response updateCollector(@ApiParam(name = "id", required = true)
                                    @PathParam("id") String id,
                                    @ApiParam(name = "JSON body", required = true)
                                    @Valid @NotNull Collector request) throws BadRequestException {
        return saveCollector(collectorService.fromRequest(id, request));
    }

    private Response saveCollector(Collector collector) {
        final ValidationResult validationResult = validate(collector);
        if (validationResult.failed()) {
            return Response.status(Response.Status.BAD_REQUEST).entity(validationResult).build();
        }

        etagService.invalidateAllCollectors();
        return Response.ok().entity(collectorService.save(collector)).build();
    }

    @POST
    @Path("/{id}/{name}")
    @RequiresPermissions({SidecarRestPermissions.COLLECTORS_READ, SidecarRestPermissions.COLLECTORS_CREATE})
    @ApiOperation(value = "Copy a collector")
    @AuditEvent(type = SidecarAuditEventTypes.COLLECTOR_CLONE)
    public Response copyCollector(@ApiParam(name = "id", required = true)
                                  @PathParam("id") String id,
                                  @ApiParam(name = "name", required = true)
                                  @PathParam("name") String name) throws NotFoundException, BadRequestException {
        final Collector collector = collectorService.copy(id, name);
        final ValidationResult validationResult = validate(collector);
        if (validationResult.failed()) {
            return Response.status(Response.Status.BAD_REQUEST).entity(validationResult).build();
        }
        collectorService.save(collector);
        etagService.invalidateAllCollectors();
        return Response.accepted().build();
    }

    @DELETE
    @Path("/{id}")
    @RequiresPermissions(SidecarRestPermissions.COLLECTORS_DELETE)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Delete a collector")
    @AuditEvent(type = SidecarAuditEventTypes.COLLECTOR_DELETE)
    public Response deleteCollector(@ApiParam(name = "id", required = true)
                                    @PathParam("id") String id) {
        final long configurationsForCollector = configurationService.all().stream()
                .filter(configuration -> configuration.collectorId().equals(id))
                .count();
        if (configurationsForCollector > 0) {
            throw new BadRequestException("Collector still in use, cannot delete.");
        }

        int deleted = collectorService.delete(id);
        if (deleted == 0) {
            return Response.notModified().build();
        }
        etagService.invalidateAllCollectors();
        return Response.accepted().build();
    }

    @POST
    @Path("/validate")
    @NoAuditEvent("Validation only")
    @RequiresPermissions(SidecarRestPermissions.COLLECTORS_READ)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Validates collector parameters")
    public ValidationResult validateCollector(
            @Valid @ApiParam("collector") Collector toValidate) {
        return validate(toValidate);
    }

    private ValidationResult validate(Collector toValidate) {
        final Optional<Collector> collectorOptional;
        final Collector collector;
        final ValidationResult validation = new ValidationResult();

        if (toValidate.name().isEmpty()) {
            validation.addError("name", "Collector name cannot be empty.");
        } else if (!validateCollectorName(toValidate.name())) {
            validation.addError("name", "Collector name can only contain the following characters: A-Z,a-z,0-9,_,-,.");
        }

        if (toValidate.executablePath().isEmpty()) {
            validation.addError("executable_path", "Collector binary path cannot be empty.");
        } else if (!validatePath(toValidate.executablePath())) {
            validation.addError("executable_path", "Collector binary path cannot contain the following characters: ; * ? \" < > | &");
        }

        if (toValidate.nodeOperatingSystem() != null) {
            if (!validateOperatingSystem(toValidate.nodeOperatingSystem())) {
                validation.addError("node_operating_system", StringUtils.f("Operating system can only be one of %s.", VALID_OPERATING_SYSTEMS));
            }
            if (!validateServiceType(toValidate.serviceType(), toValidate.nodeOperatingSystem())) {
                validation.addError("service_type", "Only Windows collectors support 'Windows service'.");
            }
            collectorOptional = Optional.ofNullable(collectorService.findByNameAndOs(toValidate.name(), toValidate.nodeOperatingSystem()));
        } else {
            collectorOptional = Optional.ofNullable(collectorService.findByName(toValidate.name()));
        }
        if (collectorOptional.isPresent()) {
            collector = collectorOptional.get();
            if (!collector.id().equals(toValidate.id())) {
                // a collector exists with a different id, so the name is already in use, fail validation
                validation.addError("name", "Collector \"" + toValidate.name() + "\" already exists for the \"" + collector.nodeOperatingSystem() + "\" operating system.");
            }
        }
        return validation;
    }

    private boolean validateCollectorName(String name) {
        return VALID_COLLECTOR_NAME_PATTERN.matcher(name).matches();
    }

    private boolean validateServiceType(String type, String operatingSystem) {
        switch (operatingSystem) {
            case "linux":
                return VALID_LINUX_SERVICE_TYPES.contains(type);
            case "windows":
                return VALID_WINDOWS_SERVICE_TYPES.contains(type);
            case "darwin":
                return VALID_DARWIN_SERVICE_TYPES.contains(type);
            case "freebsd":
                return VALID_FREEBSD_SERVICE_TYPES.contains(type);
        }
        return false;
    }

    private boolean validateOperatingSystem(String operatingSystem) {
        return VALID_OPERATING_SYSTEMS.contains(operatingSystem);
    }

    private boolean validatePath(String path) {
        return VALID_PATH_PATTERN.matcher(path).matches();
    }

}
