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
import * as React from 'react';
import { fireEvent, renderUnwrapped } from 'wrappedTestingLibrary';
import { applyTimeoutMultiplier } from 'jest-preset-graylog/lib/timeouts';
import { createBrowserRouter, createMemoryRouter } from 'react-router-dom';
import DefaultProviders from 'DefaultProviders';
import DefaultQueryClientProvider from 'DefaultQueryClientProvider';

import { StoreMock as MockStore, asMock } from 'helpers/mocking';
import mockAction from 'helpers/mocking/MockAction';
import Routes from 'routing/Routes';
import AppRouter from 'routing/AppRouter';
import CurrentUserProvider from 'contexts/CurrentUserProvider';
import StreamsContext from 'contexts/StreamsContext';
import useViewsPlugin from 'views/test/testViewsPlugin';
import useUserLayoutPreferences from 'components/common/EntityDataTable/hooks/useUserLayoutPreferences';
import { defaultPerspective as mockDefaultPerspective } from 'fixtures/perspectives';
import { layoutPreferences } from 'fixtures/entityListLayoutPreferences';
import type { Stream } from 'logic/streams/types';

jest.mock('stores/users/CurrentUserStore', () => ({
  CurrentUserStore: MockStore(
    'get',
    ['getInitialState', () => ({
      currentUser: {
        id: 'user-betty-id',
        full_name: 'Betty Holberton',
        username: 'betty',
        permissions: ['dashboards:create'],
      },
    })],
  ),
}));

jest.mock('components/common/PaginatedEntityTable/useFetchEntities', () => () => ({
  data: {
    list: [],
    pagination: { total: 0 },
  },
}));

jest.mock('components/common/EntityDataTable/hooks/useUserLayoutPreferences');

declare global {
  namespace NodeJS {
    interface Global {
      api_url: string;
    }
  }
}

jest.mock('util/AppConfig', () => ({
  gl2ServerUrl: jest.fn(() => global.api_url),
  gl2AppPathPrefix: jest.fn(() => ''),
  gl2DevMode: jest.fn(() => false),
  isFeatureEnabled: jest.fn(() => true),
  isCloud: jest.fn(() => false),
}));

jest.mock('stores/sessions/SessionStore', () => ({
  SessionActions: {
    logout: mockAction(),
  },
  SessionStore: {
    isLoggedIn: jest.fn(() => true),
  },
}));

jest.mock('views/components/searchbar/queryinput/QueryInput');

jest.unmock('logic/rest/FetchProvider');

jest.mock('views/hooks/useMinimumRefreshInterval', () => () => ({
  data: 'PT1S',
  isInitialLoading: false,
}));

jest.mock('hooks/useSearchConfiguration', () => () => ({
  config: {
    auto_refresh_timerange_options: {
      PT1S: '1 second',
      PT1M: 'Only a minute',
    },
    default_auto_refresh_option: 'PT5S',
  },
  refresh: jest.fn(),
}));

jest.mock('components/perspectives/hooks/useActivePerspective', () => ({
  __esModule: true,
  default: () => ({
    activePerspective: mockDefaultPerspective,
  }),
}));

jest.mock('react-router-dom', () => ({
  ...jest.requireActual('react-router-dom'),
  createBrowserRouter: jest.fn(),
}));

const finderTimeout = applyTimeoutMultiplier(15000);
const testTimeout = applyTimeoutMultiplier(30000);

const setInitialUrl = (url: string) => asMock(createBrowserRouter).mockImplementation((routes) => createMemoryRouter(routes, { initialEntries: [url] }));

describe('Create a new dashboard', () => {
  useViewsPlugin();

  beforeEach(() => {
    asMock(useUserLayoutPreferences).mockReturnValue({ data: layoutPreferences, isInitialLoading: false });
  });

  const SimpleAppRouter = () => (
    <DefaultProviders>
      <DefaultQueryClientProvider>
        <CurrentUserProvider>
          <StreamsContext.Provider value={[{ id: 'stream-1', title: 'Stream 1' } as Stream]}>
            <AppRouter />
          </StreamsContext.Provider>
        </CurrentUserProvider>
      </DefaultQueryClientProvider>
    </DefaultProviders>
  );

  it('using Dashboards Page', async () => {
    setInitialUrl(Routes.DASHBOARDS);
    const { findByText, findAllByText } = renderUnwrapped(<SimpleAppRouter />);

    const buttons = await findAllByText('Create new dashboard', {}, { timeout: finderTimeout });

    fireEvent.click(buttons[0]);
    await findByText(/This dashboard has no widgets yet/, {}, { timeout: finderTimeout });
  }, testTimeout);

  it('by going to the new dashboards endpoint', async () => {
    setInitialUrl(Routes.pluginRoute('DASHBOARDS_NEW'));
    const { findByText } = renderUnwrapped(<SimpleAppRouter />);

    await findByText(/This dashboard has no widgets yet/, {}, { timeout: finderTimeout });
  }, testTimeout);
});
