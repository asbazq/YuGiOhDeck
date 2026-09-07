import React, { useCallback, useEffect, useRef, useState } from 'react';
import { trackEvent } from '../utils/analytics';
import { localImagePath } from '../common/imagePath';
import { getOrCreateUserId } from '../utils/userId';
import "../styles/AICardRecognizerModal.css";

const PREDICT_TIMEOUT_MS = 31000;

function abortError() {
  return new DOMException('Request cancelled', 'AbortError');
}

function waitForPoll(signal) {
  return new Promise((resolve, reject) => {
    if (signal.aborted) { reject(abortError()); return; }
    const cancel = () => {
      clearTimeout(timer);
      reject(abortError());
    };
    const timer = setTimeout(() => {
      signal.removeEventListener('abort', cancel);
      resolve();
    }, 1500);
    signal.addEventListener('abort', cancel, { once: true });
  });
}

function normalizeCard(card) {
  if (!card) return null;
  const idOrFile = card.id ?? card.image_id ?? card.imageUrl ?? card.image_url;
  return {
    ...card,
    name: card.kor_name || card.korName || card.name,
    frameType: (card.frame_type || card.frameType || '').toLowerCase(),
    imageUrlLarge: localImagePath(idOrFile, 'large'),
    imageUrlSmall: localImagePath(idOrFile, 'small'),
  };
}

const AICardRecognizerModal = ({ open, onClose, onPick, onTimeout }) => {
  const [file, setFile] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [preview, setPreview] = useState('');
  const [resp, setResp] = useState(null);
  const [waitingPos, setWaitingPos] = useState(null);
  const [closing, setClosing] = useState(false);
  const activeRequest = useRef(null);
  const previousRequest = useRef(Promise.resolve());
  const callbacks = useRef({ onClose, onTimeout });
  callbacks.current = { onClose, onTimeout };

  const cancelActiveRequest = useCallback(() => {
    const request = activeRequest.current;
    activeRequest.current = null;
    if (request) {
      clearTimeout(request.timer);
      request.controller.abort();
    }
  }, []);

  const forceTimeout = useCallback((request) => {
    if (activeRequest.current !== request || request.controller.signal.aborted) return;
    const position = request.position ?? 0;
    cancelActiveRequest();
    setClosing(true);
    setBusy(false);
    setWaitingPos(null);
    const { onTimeout: timeout, onClose: close } = callbacks.current;
    if (typeof timeout === 'function') timeout(position);
    else if (typeof close === 'function') close();
  }, [cancelActiveRequest]);

  const markEntered = useCallback((request, ttlMs) => {
    if (activeRequest.current !== request || request.controller.signal.aborted) return;
    request.entered = true;
    request.position = 0;
    setWaitingPos(0);
    // Start one deadline per prediction; repeated STATUS messages must not extend it.
    if (!request.timer) {
      const timeoutMs = Number.isFinite(ttlMs) && ttlMs > 0 ? ttlMs + 1000 : PREDICT_TIMEOUT_MS;
      request.timer = setTimeout(() => forceTimeout(request), timeoutMs);
    }
  }, [forceTimeout]);

  useEffect(() => {
    setFile(null);
    setBusy(false);
    setError('');
    setPreview('');
    setResp(null);
    setWaitingPos(null);
    setClosing(false);
    return cancelActiveRequest;
  }, [open, cancelActiveRequest]);

  useEffect(() => () => {
    if (preview) URL.revokeObjectURL(preview);
  }, [preview]);

  useEffect(() => {
    if (!open) return;
    const userId = getOrCreateUserId();
    const wsBase = (window.location.protocol === 'https:' ? 'wss://' : 'ws://') + window.location.host;
    let ws;
    try {
      ws = new WebSocket(`${wsBase}/queue-status?group=predict&qid=main&userId=${encodeURIComponent(userId)}`);
    } catch {
      // Position polling and the request deadline also work without WebSocket support.
      return;
    }
    const sendPing = () => {
      if (ws.readyState === WebSocket.OPEN) ws.send('PING');
    };
    let connected = false;
    ws.onopen = () => {
      connected = true;
      sendPing();
    };
    const pingTimer = setInterval(sendPing, 5000);
    ws.onmessage = (event) => {
      const request = activeRequest.current;
      if (!request || request.finished || !request.queueRequested) return;
      try {
        const msg = JSON.parse(event.data);
        if (msg.group && msg.group !== 'predict') return;
        if (msg.qid && msg.qid !== request.qid) return;
        if (msg.type === 'TIMEOUT') {
          forceTimeout(request);
        } else if (msg.type === 'ENTER' || msg.type === 'RUNNING') {
          markEntered(request, msg.ttlMs);
        } else if (msg.type === 'STATUS' && Number.isFinite(msg.pos)) {
          if (msg.pos === 0) markEntered(request, msg.ttlMs);
          else if (msg.pos > 0) {
            request.position = msg.pos;
            setWaitingPos(msg.pos);
          }
        }
      } catch {
        // Ignore malformed or unrelated messages.
      }
    };
    ws.onclose = () => {
      clearInterval(pingTimer);
      // The server releases this user's queue slot when its socket disconnects.
      const request = activeRequest.current;
      if (connected && request?.entered && !request.finished) forceTimeout(request);
    };
    return () => {
      clearInterval(pingTimer);
      ws.onopen = null;
      ws.onmessage = null;
      ws.onclose = null;
      ws.close();
    };
  }, [open, forceTimeout, markEntered]);

  const enqueueCurrent = () => {
    if (!file || activeRequest.current) return;
    const request = {
      controller: new AbortController(),
      qid: 'main',
      userId: getOrCreateUserId(),
      file,
      position: null,
      entered: false,
      finished: false,
      queueRequested: false,
      timer: null,
    };
    const previous = previousRequest.current;
    activeRequest.current = request;
    setBusy(true);
    setError('');
    setResp(null);
    setFile(null);
    setPreview('');

    const run = async () => {
      const { signal } = request.controller;
      const params = `group=predict&qid=${request.qid}&userId=${encodeURIComponent(request.userId)}`;
      const ensureActive = () => {
        if (signal.aborted || activeRequest.current !== request) throw abortError();
      };
      try {
        // A previous close may still be releasing its slot. Finish that before re-entering.
        await previous;
        ensureActive();
        request.queueRequested = true;
        // Let this mutation settle before leave, even when the user closes during entry.
        const enterResponse = await fetch(`/queue/enter?${params}`, { method: 'POST' });
        ensureActive();
        if (!enterResponse.ok) throw new Error((await enterResponse.text()) || '대기열 진입 실패');
        const entry = await enterResponse.json();
        ensureActive();
        if (entry.entered) markEntered(request, entry.ttlMs);
        else {
          request.position = entry.position;
          setWaitingPos(entry.position);
        }

        while (!request.entered) {
          await waitForPoll(signal);
          ensureActive();
          if (request.entered) break;
          const response = await fetch(`/queue/position?${params}`, { signal });
          ensureActive();
          if (!response.ok) throw new Error((await response.text()) || '대기열 확인 실패');
          const status = await response.json();
          ensureActive();
          if (status.pos === 0) markEntered(request, status.ttlMs);
          else if (Number.isFinite(status.pos) && status.pos > 0) {
            request.position = status.pos;
            setWaitingPos(status.pos);
          } else {
            throw new Error('대기열 세션이 종료되었습니다. 다시 시도해 주세요.');
          }
        }

        ensureActive();
        trackEvent('predict_start');
        const body = new FormData();
        body.append('file', request.file);
        const response = await fetch('/predict', { method: 'POST', body, signal });
        ensureActive();
        if (!response.ok) throw new Error((await response.text()) || 'AI 서버 오류');
        const data = await response.json();
        ensureActive();
        const top1 = normalizeCard(data?.top1);
        if (!top1) throw new Error('결과가 없습니다');
        const top4 = Array.isArray(data?.top4) ? data.top4.map(normalizeCard).filter(Boolean).slice(0, 4) : [];
        setResp({ top1, top4 });
        trackEvent('predict_success', { detected_count: data?.detectedCount, elapsed_ms: data?.elapsed });
      } catch (exception) {
        if (!signal.aborted && activeRequest.current === request) {
          setError(exception.message || '오류가 발생했습니다.');
        }
      } finally {
        request.finished = true;
        clearTimeout(request.timer);
        if (request.queueRequested) {
          try { await fetch(`/queue/leave?${params}`, { method: 'POST' }); } catch {}
        }
        if (activeRequest.current === request) {
          activeRequest.current = null;
          setWaitingPos(null);
          setBusy(false);
        }
      }
    };
    previousRequest.current = run();
  };

  const onFile = (event) => {
    const selectedFile = event.target.files?.[0] || null;
    setFile(selectedFile);
    setResp(null);
    setError('');
    setPreview(selectedFile ? URL.createObjectURL(selectedFile) : '');
    // Allow selecting the same image again after an error or completed prediction.
    event.target.value = '';
  };

  const closeAndLeave = () => {
    cancelActiveRequest();
    setClosing(true);
    onClose();
  };

  const pick = (card) => {
    if (!card) return;
    trackEvent('predict_pick', { card_name: card.name });
    onPick({ ...card, imageUrl: card.imageUrlLarge || card.imageUrl });
    closeAndLeave();
  };

  return (open && !closing ? (
    <div className="ai-modal-backdrop">
      <div className="ai-modal">
        <div className="ai-modal-header">
          <strong>AI 카드 판별</strong>
          <button className="ai-close" aria-label="닫기" onClick={closeAndLeave}>×</button>
        </div>

        <div className="ai-modal-body">
          <div className="ai-upload-row">
            <input type="file" accept="image/*" aria-label="카드 이미지" onChange={onFile} disabled={busy} className="ai-file-input" />
            <button className="ai-btn" onClick={enqueueCurrent} disabled={!file || busy}>{busy ? '판별 중…' : 'Predict'}</button>
          </div>

          {error && <div className="ai-error">{error}</div>}

          {Number.isFinite(waitingPos) && waitingPos > 0 && (
            <div className="ai-waiting">대기 중… 현재 {waitingPos}번째 순서입니다</div>
          )}

          <div className="ai-main-row">
            {preview && (
              <div className="ai-overlay">
                <div className="ai-img-title">업로드 이미지</div>
                <img src={preview} alt="preview" />
              </div>
            )}

            {resp?.top1 && (
              <div className="ai-top1">
                <div className="ai-top1-title">가장 유사한 카드</div>
                <img
                  className="ai-top1-img"
                  src={resp.top1.imageUrlLarge}
                  alt={resp.top1.name}
                  onClick={() => pick(resp.top1)}
                />
                <div className="ai-hint">{resp.top1.name}</div>
              </div>
            )}
          </div>

          {resp?.top4?.length > 0 && (
            <div className="ai-top4-row">
              {resp.top4.map((c, i) => (
                <div className="ai-top4-item" key={i} onClick={() => pick(c)}>
                  <div className="ai-top4-thumb">
                    <img className="ai-top4-img" src={c.imageUrlSmall} alt={c.name} />
                  </div>
                  <div className="ai-top4-caption">{c.name}</div>
                </div>
              ))}
            </div>
          )}
        </div>

        <div className="ai-modal-footer">
          <button className="ai-btn-outline" onClick={closeAndLeave}>닫기</button>
        </div>
      </div>
    </div>
  ) : null);
};

export default AICardRecognizerModal;
