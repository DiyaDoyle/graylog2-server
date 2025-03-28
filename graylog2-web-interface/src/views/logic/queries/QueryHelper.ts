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
import trim from 'lodash/trim';
import moment from 'moment-timezone';

import { DATE_TIME_FORMATS } from 'util/DateTime';
import { MISSING_BUCKET_NAME } from 'views/Constants';

export const isPhrase = (searchTerm: string | undefined | null) => String(searchTerm).indexOf(' ') !== -1;

export const escape = (searchTerm: string | number | undefined | null) => {
  let escapedTerm = String(searchTerm);

  // Replace newlines.
  escapedTerm = escapedTerm.replace(/\r\n/g, ' ');
  escapedTerm = escapedTerm.replace(/\n/g, ' ');
  escapedTerm = escapedTerm.replace(/<br>/g, ' ');

  if (isPhrase(escapedTerm)) {
    escapedTerm = String(escapedTerm).replace(/(["\\])/g, '\\$&');
    escapedTerm = `"${escapedTerm}"`;
  } else {
    // Escape all lucene special characters from the source: && || : \ / + - ! ( ) { } [ ] ^ " ~ * ?
    escapedTerm = String(escapedTerm).replace(/(&&|\|\||[:\\/+\-!(){}[\]^"~*?$])/g, '\\$&');
  }

  return escapedTerm;
};

export const addToQuery = (oldQuery: string, newTerm: string, operator: string = 'AND') => {
  if (trim(oldQuery) === '*' || trim(oldQuery) === '') {
    return newTerm;
  }

  if (trim(newTerm) === '*' || trim(newTerm) === '') {
    return oldQuery;
  }

  return `${oldQuery} ${operator} ${newTerm}`;
};

export const concatQueryStrings = (
  queryStrings: Array<string>,
  { operator = 'AND', withBrackets = true } = {},
): string => {
  const withRemovedEmpty = queryStrings.filter((s: string) => !!s?.trim());
  const showBracketsForChild = withBrackets && withRemovedEmpty.length > 1;

  return withRemovedEmpty.map((s) => (showBracketsForChild ? `(${s})` : s)).join(` ${operator} `);
};

export const formatTimestamp = (value: string | number) => {
  const utc = moment(value).tz('UTC');

  return `"${utc.format(DATE_TIME_FORMATS.internalIndexer)}"`;
};

export const predicate = (field: string, value: string | number) =>
  value === MISSING_BUCKET_NAME || value === escape(MISSING_BUCKET_NAME)
    ? `NOT _exists_:${field}`
    : `${field}:${value}`;

export const not = (query: string) => `NOT ${query}`.replace(/^NOT NOT /, '');
