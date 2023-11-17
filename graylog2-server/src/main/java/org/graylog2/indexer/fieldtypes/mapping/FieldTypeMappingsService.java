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
package org.graylog2.indexer.fieldtypes.mapping;

import org.graylog2.indexer.MongoIndexSet;
import org.graylog2.indexer.indexset.CustomFieldMapping;
import org.graylog2.indexer.indexset.CustomFieldMappings;
import org.graylog2.indexer.indexset.IndexSetConfig;
import org.graylog2.indexer.indexset.IndexSetService;
import org.graylog2.indexer.indexset.MongoIndexSetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.graylog2.plugin.Message.RESERVED_SETTABLE_FIELDS;

public class FieldTypeMappingsService {

    private static final Logger LOG = LoggerFactory.getLogger(FieldTypeMappingsService.class);

    public static final Set<String> BLACKLISTED_FIELDS = RESERVED_SETTABLE_FIELDS;

    private final IndexSetService indexSetService;
    private final MongoIndexSet.Factory mongoIndexSetFactory;
    private final MongoIndexSetService mongoIndexSetService;

    @Inject
    public FieldTypeMappingsService(final IndexSetService indexSetService,
                                    final MongoIndexSet.Factory mongoIndexSetFactory,
                                    final MongoIndexSetService mongoIndexSetService) {
        this.indexSetService = indexSetService;
        this.mongoIndexSetFactory = mongoIndexSetFactory;
        this.mongoIndexSetService = mongoIndexSetService;
    }

    public void changeFieldType(final CustomFieldMapping customMapping,
                                final Set<String> indexSetsIds,
                                final boolean rotateImmediately) {
        if (BLACKLISTED_FIELDS.contains(customMapping.fieldName())) {
            throw new IllegalArgumentException("Changing field type of " + customMapping.fieldName() + " is not allowed.");
        }
        for (String indexSetId : indexSetsIds) {
            try {
                indexSetService.get(indexSetId).ifPresent(indexSetConfig -> {
                    var updatedIndexSetConfig = storeMapping(customMapping, indexSetConfig);
                    if (rotateImmediately) {
                        updatedIndexSetConfig.ifPresent(this::cycleIndexSet);
                    }
                });
            } catch (Exception ex) {
                LOG.error("Failed to update field type in index set : " + indexSetId, ex);
                throw ex;
            }
        }
    }

    public void removeCustomMappingForFields(final List<String> fieldNames,
                                             final Set<String> indexSetsIds,
                                             final boolean rotateImmediately) {
        for (String indexSetId : indexSetsIds) {
            try {
                indexSetService.get(indexSetId).ifPresent(indexSetConfig -> {
                    var updatedIndexSetConfig = removeMappings(fieldNames, indexSetConfig);
                    if (rotateImmediately) {
                        updatedIndexSetConfig.ifPresent(this::cycleIndexSet);
                    }
                });
                LOG.debug("Removed custom mappings for fields " + fieldNames.toString() + " in index set : " + indexSetId);
            } catch (Exception ex) {
                LOG.error("Failed to remove custom mappings for fields " + fieldNames.toString() + " in index set : " + indexSetId, ex);
                throw ex;
            }
        }
    }

    private Optional<IndexSetConfig> removeMappings(final List<String> fieldNames, final IndexSetConfig indexSetConfig) {
        final CustomFieldMappings previousCustomFieldMappings = indexSetConfig.customFieldMappings();
        final boolean removedSmth = previousCustomFieldMappings.removeIf(customFieldMapping -> fieldNames.stream().anyMatch(fieldName -> customFieldMapping.fieldName().equals(fieldName)));
        if (removedSmth) {
            return Optional.of(mongoIndexSetService.save(
                    indexSetConfig.toBuilder()
                            .customFieldMappings(previousCustomFieldMappings)
                            .build()
            ));
        } else {
            return Optional.empty();
        }
    }

    private Optional<IndexSetConfig> storeMapping(final CustomFieldMapping customMapping,
                                                  final IndexSetConfig indexSetConfig) {
        final CustomFieldMappings previousCustomFieldMappings = indexSetConfig.customFieldMappings();
        if (previousCustomFieldMappings.contains(customMapping)) {
            return Optional.empty();
        }
        return Optional.of(mongoIndexSetService.save(
                indexSetConfig.toBuilder()
                        .customFieldMappings(previousCustomFieldMappings.mergeWith(customMapping))
                        .build()
        ));
    }

    private void cycleIndexSet(final IndexSetConfig indexSetConfig) {
        final MongoIndexSet mongoIndexSet = mongoIndexSetFactory.create(indexSetConfig);
        mongoIndexSet.cycle();
    }
}
