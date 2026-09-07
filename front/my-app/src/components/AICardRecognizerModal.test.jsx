import React from 'react';
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import AICardRecognizerModal from './AICardRecognizerModal';

jest.mock('../utils/analytics', () => ({ trackEvent: jest.fn() }));
jest.mock('../utils/userId', () => ({ getOrCreateUserId: () => 'test-user' }));

const reply = (data) => Promise.resolve({ ok: true, json: async () => data });
const flush = async () => act(async () => {
  for (let i = 0; i < 20; i++) await Promise.resolve();
});
const tick = async (ms) => {
  await act(async () => { jest.advanceTimersByTime(ms); });
  await flush();
};
const upload = () => {
  fireEvent.change(screen.getByLabelText('카드 이미지'), { target: { files: [new File(['image'], 'card.jpg', { type: 'image/jpeg' })] } });
  fireEvent.click(screen.getByText('Predict'));
};
const calls = (prefix) => global.fetch.mock.calls.filter(([url]) => url.startsWith(prefix));
let socket;
let originalFetch;
let originalWebSocket;

beforeEach(() => {
  jest.useFakeTimers();
  originalFetch = global.fetch;
  originalWebSocket = global.WebSocket;
  URL.createObjectURL = jest.fn(() => 'blob:preview');
  URL.revokeObjectURL = jest.fn();
  global.WebSocket = class {
    static OPEN = 1;
    readyState = 0;
    constructor() { socket = this; }
    send = jest.fn();
    close = jest.fn();
  };
  global.fetch = jest.fn((url) => {
    if (url.startsWith('/queue/leave')) return reply({});
    throw new Error(`Unexpected fetch: ${url}`);
  });
});

afterEach(async () => {
  cleanup();
  await flush();
  jest.useRealTimers();
  global.fetch = originalFetch;
  global.WebSocket = originalWebSocket;
});

test('closing during polling cancels prediction and releases the queue once', async () => {
  global.fetch.mockImplementation((url) => reply(url.startsWith('/queue/enter') ? { entered: false, position: 2 } : {}));
  render(<AICardRecognizerModal open onClose={jest.fn()} />);
  upload();
  await flush();
  expect(screen.getByText(/현재 2번째/)).toBeInTheDocument();
  fireEvent.click(screen.getByLabelText('닫기'));
  await flush();
  await tick(10000);
  expect(calls('/predict')).toHaveLength(0);
  expect(calls('/queue/position')).toHaveLength(0);
  expect(calls('/queue/leave')).toHaveLength(1);
});

test('closing during entry waits for the mutation before releasing the slot', async () => {
  let finishEntry;
  global.fetch.mockImplementation((url) => url.startsWith('/queue/enter')
    ? new Promise((resolve) => { finishEntry = resolve; }) : reply({}));
  render(<AICardRecognizerModal open onClose={jest.fn()} />);
  upload();
  await flush();
  fireEvent.click(screen.getByLabelText('닫기'));
  expect(calls('/queue/leave')).toHaveLength(0);
  finishEntry({ ok: true, json: async () => ({ entered: true }) });
  await flush();
  expect(calls('/queue/leave')).toHaveLength(1);
  expect(calls('/predict')).toHaveLength(0);
});

test('missing queue membership does not grant permission to predict', async () => {
  global.fetch.mockImplementation((url) => {
    if (url.startsWith('/queue/enter')) return reply({ entered: false, position: 1 });
    if (url.startsWith('/queue/position')) return reply({ pos: -1 });
    return reply({});
  });
  render(<AICardRecognizerModal open onClose={jest.fn()} />);
  upload();
  await flush();
  await tick(1500);
  expect(screen.getByText(/대기열 세션이 종료/)).toBeInTheDocument();
  expect(calls('/predict')).toHaveLength(0);
  expect(calls('/queue/leave')).toHaveLength(1);
});

test('successful prediction keeps its result without a later timeout', async () => {
  const onTimeout = jest.fn();
  global.fetch.mockImplementation((url) => {
    if (url.startsWith('/queue/enter')) return reply({ entered: true });
    if (url === '/predict') return reply({ top1: { id: 123, korName: '테스트 카드' }, top4: [] });
    return reply({});
  });
  render(<AICardRecognizerModal open onClose={jest.fn()} onTimeout={onTimeout} />);
  upload();
  await flush();
  expect(screen.getByAltText('테스트 카드')).toHaveAttribute('src', '/images/123.jpg');
  await tick(60000);
  expect(onTimeout).not.toHaveBeenCalled();
  expect(calls('/queue/leave')).toHaveLength(1);
  expect(calls('/queue/position')).toHaveLength(0);
});

test('failed WebSocket connection does not interrupt HTTP prediction; closing aborts it', async () => {
  let predictionSignal;
  const onTimeout = jest.fn();
  global.fetch.mockImplementation((url, options) => {
    if (url.startsWith('/queue/enter')) return reply({ entered: true });
    if (url === '/predict') {
      predictionSignal = options.signal;
      return new Promise((resolve, reject) => options.signal.addEventListener('abort', () => reject(new DOMException('cancelled', 'AbortError'))));
    }
    return reply({});
  });
  render(<AICardRecognizerModal open onClose={jest.fn()} onTimeout={onTimeout} />);
  upload();
  await flush();
  act(() => socket.onclose());
  expect(onTimeout).not.toHaveBeenCalled();
  expect(predictionSignal.aborted).toBe(false);
  fireEvent.click(screen.getByLabelText('닫기'));
  await flush();
  expect(predictionSignal.aborted).toBe(true);
  expect(calls('/queue/leave')).toHaveLength(1);
});

test('repeated RUNNING messages do not extend the prediction deadline', async () => {
  const onTimeout = jest.fn();
  global.fetch.mockImplementation((url, options) => {
    if (url.startsWith('/queue/enter')) return reply({ entered: true, ttlMs: 1000 });
    if (url === '/predict') return new Promise((resolve, reject) => options.signal.addEventListener('abort', () => reject(new DOMException('cancelled', 'AbortError'))));
    return reply({});
  });
  render(<AICardRecognizerModal open onClose={jest.fn()} onTimeout={onTimeout} />);
  upload();
  await flush();
  await tick(1500);
  act(() => socket.onmessage({ data: JSON.stringify({ type: 'RUNNING', ttlMs: 1000 }) }));
  await tick(500);
  expect(onTimeout).toHaveBeenCalledTimes(1);
  expect(calls('/queue/leave')).toHaveLength(1);
});
