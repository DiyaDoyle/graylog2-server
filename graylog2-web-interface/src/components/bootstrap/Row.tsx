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
// eslint-disable-next-line no-restricted-imports
import { Row as BootstrapRow } from 'react-bootstrap';
import styled, { css } from 'styled-components';

export const RowContentStyles = css(
  ({ theme }) => css`
    background-color: ${theme.colors.global.contentBackground};
    margin-bottom: ${theme.spacings.xs};
    border-radius: 6px;
    box-shadow: rgb(0 0 0 / 4%) 0 3px 5px;
  `,
);

type RowProps = React.ComponentProps<typeof BootstrapRow>;
const Row: React.ComponentType<RowProps> = styled(BootstrapRow)`
  &.content {
    ${RowContentStyles}
  }
`;

/** @component */
export default Row;
