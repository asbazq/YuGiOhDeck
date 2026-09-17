import React from 'react';
import { act, cleanup, render, screen } from '@testing-library/react';
import axios from 'axios';
import QueueApp from './QueueApp';

jest.mock('axios', () => ({ get: jest.fn(), post: jest.fn() }));
jest.mock('../utils/userId', () => ({ getOrCreateUserId: () => 'test-user' }));
jest.mock('./QueueModal', () => ({ open, position }) => open ? <div data-testid="queue">{position}</div> : null);

let originalWebSocket;
let socket;
beforeEach(() => {
  jest.useFakeTimers();
  jest.clearAllMocks();
  originalWebSocket = global.WebSocket;
  global.WebSocket = class {
    static OPEN = 1;
    constructor() { socket = this; }
    close = jest.fn();
    send = jest.fn();
  };
  axios.post.mockResolvedValue({ data: { entered: false, position: 3 } });
});

test('slow position requests do not overlap and are aborted on unmount', async () => {
  axios.get.mockImplementation(() => new Promise(() => {}));
  const { unmount } = render(<QueueApp />);
  await act(async () => {});
  await act(async () => { jest.advanceTimersByTime(6000); });
  expect(axios.get).toHaveBeenCalledTimes(1);
  const [url, options] = axios.get.mock.calls[0];
  expect(url).toBe('/queue/position');
  expect(options.signal.aborted).toBe(false);
  unmount();
  expect(options.signal.aborted).toBe(true);
});

test('a late missing-session response cannot undo a WebSocket admission', async () => {
  let resolvePosition;
  axios.get.mockImplementation(() => new Promise((resolve) => { resolvePosition = resolve; }));
  render(<QueueApp />);
  await act(async () => {});
  await act(async () => { jest.advanceTimersByTime(1500); });
  act(() => socket.onmessage({ data: '{"type":"ENTER"}' }));
  expect(screen.queryByTestId('queue')).not.toBeInTheDocument();
  await act(async () => { resolvePosition({ data: { pos: -1 } }); });
  expect(axios.post).toHaveBeenCalledTimes(1);
  expect(screen.queryByTestId('queue')).not.toBeInTheDocument();
});

test('malformed socket messages do not break queue updates', async () => {
  render(<QueueApp />);
  await act(async () => {});
  for (const data of ['{broken', 'null', 'PING']) {
    expect(() => act(() => socket.onmessage({ data }))).not.toThrow();
  }
  expect(screen.getByTestId('queue')).toHaveTextContent('3');
  act(() => socket.onmessage({ data: '{"type":"ENTER"}' }));
  expect(screen.queryByTestId('queue')).not.toBeInTheDocument();
});
afterEach(() => {
  cleanup();
  jest.useRealTimers();
  global.WebSocket = originalWebSocket;
});

test.each([-1, 0])('polling distinguishes missing membership (%s) from admission', async (position) => {
  axios.get.mockImplementation((url) => Promise.resolve({ data: url === '/queue/status' ? { waiting: 3 } : { pos: position } }));
  render(<QueueApp><div>Deck</div></QueueApp>);
  await act(async () => {});
  expect(screen.getByTestId('queue')).toHaveTextContent('3');
  await act(async () => { jest.advanceTimersByTime(1500); });
  if (position === -1) {
    expect(axios.post).toHaveBeenCalledTimes(2);
    expect(screen.getByTestId('queue')).toHaveTextContent('3');
  } else {
    expect(axios.post).toHaveBeenCalledTimes(1);
    expect(screen.queryByTestId('queue')).not.toBeInTheDocument();
  }
});
