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
import { render, waitFor, screen } from 'wrappedTestingLibrary';
import { PluginManifest, PluginStore } from 'graylog-web-plugin/plugin';

import { Messages } from '@graylog/server-api';

import { StoreMock as MockStore, asMock } from 'helpers/mocking';
import useFieldTypes from 'views/logic/fieldtypes/useFieldTypes';
import useViewsPlugin from 'views/test/testViewsPlugin';
import type { Stream } from 'views/stores/StreamsStore';
import { InputsActions } from 'stores/inputs/InputsStore';
import StreamsContext from 'contexts/StreamsContext';
import FetchError from 'logic/errors/FetchError';
import suppressConsole from 'helpers/suppressConsole';

import ShowMessagePage from './ShowMessagePage';
import { message, event, input } from './ShowMessagePage.fixtures';

jest.mock('views/components/messagelist/MessageDetail', () => (props) => <span>{JSON.stringify(props, null, 2)}</span>);

const mockGetInput = jest.fn();
const mockListNodes = jest.fn();

jest.mock('stores/nodes/NodesStore', () => ({
  NodesActions: { list: (...args) => mockListNodes(...args) },
  NodesStore: MockStore(['getInitialState', () => ({ nodes: {} })]),
}));

jest.mock('@graylog/server-api', () => ({
  Messages: { search: jest.fn() },
}));

jest.mock('stores/inputs/InputsStore', () => ({
  InputsActions: {
    get: jest.fn(),
  },
  InputsStore: MockStore(),
}));

jest.mock('views/logic/fieldtypes/useFieldTypes', () => jest.fn());
jest.mock('routing/withParams', () => (x) => x);

type SimpleShowMessagePageProps = {
  index: string;
  messageId: string;
  streams?: Array<{ id: string }>;
};

const SimpleShowMessagePage = ({ index, messageId, streams = [] }: SimpleShowMessagePageProps) => (
  <StreamsContext.Provider value={streams as Array<Stream>}>
    {/* @ts-expect-error */}
    <ShowMessagePage params={{ index, messageId }} />
  </StreamsContext.Provider>
);

describe('ShowMessagePage', () => {
  const isLocalNode = jest.fn();

  beforeEach(() => {
    jest.clearAllMocks();
    asMock(useFieldTypes).mockReturnValue({ data: [], refetch: () => {} });
    asMock(isLocalNode).mockResolvedValue(true);
    asMock(Messages.search).mockResolvedValue(message);
  });

  const testForwarderPlugin = new PluginManifest(
    {},
    {
      // @ts-expect-error
      forwarder: [{ isLocalNode }],
    },
  );

  useViewsPlugin();

  beforeAll(() => PluginStore.register(testForwarderPlugin));

  afterAll(() => PluginStore.unregister(testForwarderPlugin));

  it('triggers a node list refresh on mount', async () => {
    mockGetInput.mockImplementation(() => Promise.resolve(input));

    render(<SimpleShowMessagePage index="graylog_5" messageId="20f683d2-a874-11e9-8a11-0242ac130004" />);

    await waitFor(() => expect(mockListNodes).toHaveBeenCalled());
  });

  it('renders for generic message', async () => {
    asMock(InputsActions.get).mockResolvedValue(input);

    render(<SimpleShowMessagePage index="graylog_5" messageId="20f683d2-a874-11e9-8a11-0242ac130004" />);

    await screen.findByText(/Deprecated field/);
    await screen.findByText(/"id": "20f683d2-a874-11e9-8a11-0242ac130004"/);
    await screen.findByText(/"index": "graylog_5"/);
  });

  it('retrieves field types only for user-accessible streams', async () => {
    const messageWithMultipleStreams = {
      ...message,
      message: {
        ...message.message,
        fields: {
          ...message.message.fields,
          streams: ['000000000000000000000001', 'deadbeef'],
        },
      },
    };
    asMock(Messages.search).mockResolvedValue(messageWithMultipleStreams);
    mockGetInput.mockImplementation(() => Promise.resolve(input));

    render(
      <SimpleShowMessagePage
        index="graylog_5"
        messageId="20f683d2-a874-11e9-8a11-0242ac130004"
        streams={[{ id: 'deadbeef' }]}
      />,
    );

    await screen.findByText(/Deprecated field/);

    expect(useFieldTypes).toHaveBeenCalledWith(['deadbeef'], {
      from: '2019-07-17T11:20:33.000Z',
      to: '2019-07-17T11:20:33.000Z',
      type: 'absolute',
    });
  });

  it('renders for generic event', async () => {
    asMock(Messages.search).mockResolvedValue(event);
    mockGetInput.mockImplementation(() => Promise.resolve());

    render(<SimpleShowMessagePage index="gl-events_0" messageId="01DFZQ64CMGV30NT7DW2P7HQX2" />);

    await screen.findByText(/SSH Brute Force/);
    await screen.findByText(/"id": "01DFZQ64CMGV30NT7DW2P7HQX2"/);
    await screen.findByText(/"index": "gl-events_0"/);
  });

  it('does not fetch input when opening message from forwarder', async () => {
    mockGetInput.mockImplementation(() => Promise.resolve());
    asMock(isLocalNode).mockResolvedValue(false);

    render(<SimpleShowMessagePage index="graylog_5" messageId="20f683d2-a874-11e9-8a11-0242ac130004" />);
    await screen.findByText(/Deprecated field/);

    expect(InputsActions.get).not.toHaveBeenCalled();
  });

  it('renders explanation when fetching of message fails because index does not exist', async () => {
    asMock(Messages.search).mockRejectedValue(
      new FetchError('Not found.', 404, 'Index not found for query: graylog_5. Try recalculating your index ranges.'),
    );

    await suppressConsole(async () => {
      render(<SimpleShowMessagePage index="graylog_5" messageId="20f683d2-a874-11e9-8a11-0242ac130004" />);

      await screen.findByText(/The index or message specified was not found./i);
      await screen.findAllByText(/Try recalculating your index ranges./i);
    });
  });
});
