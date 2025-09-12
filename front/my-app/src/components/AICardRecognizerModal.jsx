import React, { useState, useEffect } from 'react';
import { trackEvent } from '../utils/analytics';
import { localImagePath, filenameOf } from '../common/imagePath';
import { getOrCreateUserId } from '../utils/userId';
import "../styles/AICardRecognizerModal.css";

const AICardRecognizerModal = ({ open, onClose, onPick, onTimeout }) => {
  const [file, setFile] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [preview, setPreview] = useState('');
  const [resp, setResp] = useState(null);
  const [vip, setVip] = useState(false);
  const [queue, setQueue] = useState([]);
  const [waitingPos, setWaitingPos] = useState(null);
  const [waitingQid, setWaitingQid] = useState('main');
  const wsRef = React.useRef(null);
  const pingTimer = React.useRef(null);
  const enteredRef = React.useRef(false);   // running 진입 여부
  const timedOutRef = React.useRef(false);  // TIMEOUT 중복 방지
  const [ttlMs, setTtlMs] = useState(30000); // 서버 TTL 캐시
  const hardTimerRef = React.useRef(null);
  const [closing, setClosing] = useState(false);

  useEffect(() => {
    if (!open) {
      setFile(null);
      setBusy(false);
      setError('');
      setPreview('');
      setResp(null);
      setQueue([]);
      enteredRef.current = false;
      timedOutRef.current = false;
      try { if (hardTimerRef.current) { clearTimeout(hardTimerRef.current); hardTimerRef.current = null; } } catch {}
      setClosing(false);
    }
  }, [open]);

  async function leaveServerOnly(qid, userId) {
    try {
      await fetch(`/queue/leave?group=predict&qid=${qid}&userId=${encodeURIComponent(userId)}`, { method: 'POST' });
    } catch {}
  }

  // 공용 타임아웃 처리 (한 번만)
  const forceTimeout = async (qid) => {

    if (timedOutRef.current) return;
    timedOutRef.current = true;
    enteredRef.current = false;
    // ① UI 먼저 닫기
    setClosing(true);
    setQueue([]); setFile(null); setResp(null); setError(''); setBusy(false);
    const snapshotPos = Number.isFinite(waitingPos) ? waitingPos : 0;
    try {
      if (typeof onTimeout === 'function') onTimeout(snapshotPos);
      else if (typeof onClose === 'function') onClose();
    } catch {}
    // ② 타이머/WS 정리
    try { if (pingTimer.current) { clearInterval(pingTimer.current); pingTimer.current = null; } } catch {}
    try { if (wsRef.current) { wsRef.current.close(); wsRef.current = null; } } catch {}
    try { if (hardTimerRef.current) { clearTimeout(hardTimerRef.current); hardTimerRef.current = null; } } catch {}
    // ③ 네트워크 정리(지연돼도 이미 닫힘)
    try {
      const userId = getOrCreateUserId();
      await leaveServerOnly(qid, userId);
      try {
        const r = await fetch(`/queue/enter?group=predict&qid=${qid}&userId=${encodeURIComponent(userId)}`, { method: 'POST' });
        if (r.ok) await r.json();
      } catch {}
    } catch {}
  };

  useEffect(() => {
    return () => { if (preview) URL.revokeObjectURL(preview); };
  }, [preview]);

  // 서버 대기열 기반 처리 루프
  useEffect(() => {
    const runNext = async () => {
      if (!open) return;  
      if (busy) return;
      const next = queue.find(it => it.status === 'pending');
      if (!next) return;

      setBusy(true); setError('');
      setQueue(q => q.map(it => it.id === next.id ? ({ ...it, status: 'running' }) : it));

      const userId = getOrCreateUserId();
      const qid = vip ? 'vip' : 'main';
      setWaitingQid(qid);

      const normalize = (m) => {
        if (!m) return null;
        const kor = m.kor_name || m.korName;
        const frameType = (m.frame_type || m.frameType || '').toLowerCase();
        const idOrFile =
          m.id ?? m.image_id ?? m.imageUrl?.split('/').pop() ?? m.image_url?.split('/').pop() ?? filenameOf(m.imageUrl || m.image_url || '');
        const imageUrlLarge = localImagePath(idOrFile, 'large');
        const imageUrlSmall = localImagePath(idOrFile, 'small');
        return { ...m, name: kor || m.name, frameType, imageUrlLarge, imageUrlSmall };
      };

      // TTL 워치독용 abort controller (catch에서 쓸 수 있도록 바깥에 둠)
      const ctrl = new AbortController();

      try {
        // 1) 대기열 진입
        const enterRes = await fetch(
          `/queue/enter?group=predict&qid=${qid}&userId=${encodeURIComponent(userId)}`,
          { method: 'POST' }
        );
        if (!enterRes.ok) throw new Error((await enterRes.text()) || '대기열 진입 실패');
        const enterJson = await enterRes.json(); // { entered, position }
        if (!enterJson.entered) {
          setWaitingPos(enterJson.position);
          await waitUntilEntered(qid, userId, setWaitingPos); // pos 0 이하 될 때까지 폴링
        }

        // 2) predict 호출 + TTL watchdog
        trackEvent('predict_start');
        enteredRef.current = true;
        // 하드 컷오프: TTL + 1500ms 후에도 여전히 러닝이면 강제 타임아웃
        try { if (hardTimerRef.current) clearTimeout(hardTimerRef.current); } catch {}
        const ttlSafe = Number.isFinite(ttlMs) && ttlMs > 0 ? ttlMs : 30000; // 30초 기본값(원하는 값으로 조정)
        const deadline = ttlSafe + 1500; // 약간의 버퍼
        hardTimerRef.current = setTimeout(() => {
          if (open && enteredRef.current && !timedOutRef.current) {
            const q = vip ? 'vip' : (waitingQid || 'main');
            // 실패해도 UI는 즉시 닫히도록 보호
            try { forceTimeout(q); } catch {
              if (typeof onTimeout === 'function') onTimeout(waitingPos ?? 0);
              else if (typeof onClose === 'function') onClose();
            }
          }
        }, deadline);

        const fd = new FormData();
        fd.append('file', next.file);

        const url = vip ? '/predict?vip=true' : '/predict';

        const watchdog = new Promise((_, rej) =>
          setTimeout(() => rej(new Error('__TTL_EXPIRED__')), (ttlMs || 30000) + 1000)
        );

        const r = await Promise.race([
          fetch(url, { method: 'POST', body: fd, signal: ctrl.signal }),
          watchdog
        ]);

        if (!r.ok) throw new Error((await r.text()) || 'AI 서버 오류');

        const data = await r.json();
        trackEvent('predict_success', { detected_count: data?.detectedCount, elapsed_ms: data?.elapsed });

        const top1 = normalize(data?.top1);
        const top4 = Array.isArray(data?.top4) ? data.top4.map(normalize) : [];
        if (!top1) throw new Error('결과가 없습니다');
        setResp({ top1, top4: top4.slice(0, 4) });
        setQueue(q => q.map(it => it.id === next.id ? ({ ...it, status: 'done' }) : it));
        enteredRef.current = false; // 정상 종료
      } catch (e) {
        if (e?.message === '__TTL_EXPIRED__') {
          try { ctrl.abort(); } catch {}
          await forceTimeout(qid);
          return;
        }
        setError(e.message || '오류가 발생했습니다.');
        setQueue(q => q.map(it => it.id === next.id ? ({ ...it, status: 'error', message: e.message }) : it));
      } finally {
        // 타임아웃 경로에선 이미 leave 처리됨
        if (!timedOutRef.current) {
          try {
            await fetch(`/queue/leave?group=predict&qid=${waitingQid}&userId=${encodeURIComponent(userId)}`, { method: 'POST' });
          } catch {}
        }
        setWaitingPos(null);
        setBusy(false);
      }
    };
    runNext();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, queue, busy, vip, ttlMs]);

  const onFile = (e) => {
    const f = e.target.files?.[0];
    if (preview) URL.revokeObjectURL(preview);
    setFile(f || null);
    setResp(null);
    setError('');
    if (f) setPreview(URL.createObjectURL(f));
    else setPreview('');
  };

  const enqueueCurrent = () => {
    if (!file) { setError('이미지를 선택하세요'); return; }
    const id = Date.now() + '-' + (file.name || 'upload');
    setQueue(q => [...q, { id, name: file.name || 'upload', file, status: 'pending' }]);
    setFile(null);
    setPreview('');
    setResp(null);
  };

  async function waitUntilEntered(qid, userId, onPos) {
    for (;;) {
      await new Promise(r => setTimeout(r, 1500));
      const res = await fetch(`/queue/position?group=predict&qid=${qid}&userId=${encodeURIComponent(userId)}`);
      if (!res.ok) continue;
      const j = await res.json();
      const pos = typeof j.pos === 'number' ? j.pos : -1;
      if (typeof onPos === 'function') onPos(pos);
      if (pos <= 0) { enteredRef.current = true; break; }
    }
  }

  const pick = (c) => {
    if (!c) return;
    trackEvent('predict_pick', { card_name: c?.name });
    onPick({
      ...c,
      imageUrl: c.imageUrlLarge || c.imageUrl || localImagePath(c.id ?? c.image_url ?? c.imageUrl, 'large'),
    });
    onClose();
  };

  const closeAndLeave = async () => {
    setClosing(true); // 사용자가 직접 닫아도 즉시 숨김
    try {
      const userId = getOrCreateUserId();
      const qid = vip ? 'vip' : (waitingQid || 'main');
      await leaveServerOnly(qid, userId);
    } catch {}
    onClose();
  };

  // WS: TIMEOUT/STATUS 감지
  useEffect(() => {
    if (!open) return;
    // TTL 워치독: RUNNING 진입 순간부터 ttlMs 후 강제 forceTimeout
    const armHardTimerWithTTL = (ttlMs) => {
      const ttlSafe = Number.isFinite(ttlMs) && ttlMs > 0 ? ttlMs : 30000; // 기본 30s
      try { if (hardTimerRef.current) clearTimeout(hardTimerRef.current); } catch {}
      hardTimerRef.current = setTimeout(() => {
        if (open && enteredRef.current && !timedOutRef.current) {
          const q = vip ? 'vip' : (waitingQid || 'main');
          try { forceTimeout(q); } catch {
            if (typeof onTimeout === 'function') onTimeout(waitingPos ?? 0);
            else if (typeof onClose === 'function') onClose();
          }
        }
      }, ttlSafe + 1500);
    };
    const userId = getOrCreateUserId();
    const qid = vip ? 'vip' : (waitingQid || 'main');
    const WS_HOST =
      window.location.hostname === 'no86.xyz'
        ? 'no86.xyz:8082'
        : window.location.host;
    const WS_BASE = (window.location.protocol === 'https:' ? 'wss://' : 'ws://') + WS_HOST;
    const url = `${WS_BASE}/queue-status?group=predict&qid=${encodeURIComponent(qid)}&userId=${encodeURIComponent(userId)}`;
    let ws;
    try {
      ws = new WebSocket(url);
      wsRef.current = ws;
    } catch {
      return;
    }
    const sendPing = () => { try { if (ws && ws.readyState === WebSocket.OPEN) ws.send('PING'); } catch {} };
    ws.onopen = sendPing;
    pingTimer.current = setInterval(sendPing, 5000);
    ws.onmessage = (e) => {
      const s = e?.data || '';
      if (!s || s[0] !== '{') return;
      try {
        const msg = JSON.parse(s);
        // 1) 서버가 RUNNING 타입을 보낼 수 있음 → 이때 진입 및 워치독
        if (msg.type === 'RUNNING') {
          enteredRef.current = true;
          setWaitingPos(0);
          armHardTimerWithTTL(msg.ttlMs);
          return;
        }
        if (msg.type === 'TIMEOUT' && !timedOutRef.current) {
          forceTimeout(qid);
          return;
        }
        if (msg.type === 'ENTER' && (msg.group === 'predict' || !msg.group)) {
          setWaitingPos(0);
        }
        if (msg.type === 'STATUS' && (msg.group === 'predict') && Number.isFinite(msg.pos)) {
          setWaitingPos(msg.pos);
          if (msg.pos <= 0) {
            // 러닝 진입 감지 + 워치독(STATUS로 ttlMs가 올 수도, 안 올 수도 있음)
            if (!enteredRef.current) 
            enteredRef.current = true;
            armHardTimerWithTTL(msg.ttlMs); // 없으면 기본 TTL(30s)
         } else if (enteredRef.current && msg.pos > 0) {
            // running → waiting 으로 밀리면 TTL 만료로 간주
            forceTimeout(qid);
            return;
          }
        }
      } catch {}
    };
    ws.onclose = () => {
      try { if (pingTimer.current) clearInterval(pingTimer.current); } catch {}
      pingTimer.current = null;
      wsRef.current = null;
      try { if (hardTimerRef.current) clearTimeout(hardTimerRef.current); } catch {}
      hardTimerRef.current = null;
      // running 상태에서 WS 끊기면 TTL 만료로 처리
      if (open && enteredRef.current && !timedOutRef.current) {
        forceTimeout(qid);
      }
    };
    return () => {
      try { if (pingTimer.current) clearInterval(pingTimer.current); } catch {}
      pingTimer.current = null;
      try { ws.close(); } catch {}
      wsRef.current = null;
      try { if (hardTimerRef.current) clearTimeout(hardTimerRef.current); } catch {}
      hardTimerRef.current = null;
    };
  }, [open, vip, waitingQid]);

  // footer "닫기"도 leave
  useEffect(() => {
    if (!open) return;
    const handler = (ev) => {
      const t = ev.target;
      try {
        if (t && t.matches && t.matches('.ai-modal-footer .ai-btn-outline')) {
          ev.preventDefault();
          ev.stopPropagation();
          closeAndLeave();
        }
      } catch {}
    };
    document.addEventListener('click', handler, true);
    return () => document.removeEventListener('click', handler, true);
  }, [open, waitingQid, vip]);

  // HTTP 폴백(WS 없어도 TTL 감지)
  useEffect(() => {
    if (!open) return;
    let stop = false;
    let inFlight = false;

    const userId = getOrCreateUserId();

    const tick = async () => {
      if (stop || inFlight) return;
      if (timedOutRef.current) return; // 이건 유지 (이미 타임아웃 처리된 뒤)
      inFlight = true;
      try {
        // vip / waitingQid가 바뀌었을 수 있으니 매 tick 때 계산
        const qidNow = vip ? 'vip' : (waitingQid || 'main');
        const r = await fetch(`/queue/isRunning?group=predict&qid=${qidNow}&userId=${encodeURIComponent(userId)}`);
        if (!r.ok) return;
        const j = await r.json();
        const running = j?.running === true || j?.running === 'true' || j?.running === 1 || j?.running === '1';
        // await 사이 상태가 바뀌었을 수 있으니 재확인 후 강제 종료
        if (!timedOutRef.current && enteredRef.current && !running) {
          forceTimeout(qidNow); // 즉시 닫기 → 네트워크는 내부에서 정리
        }
        // 보너스: 러닝 진입 신호를 WS에서 못 받았을 수도 있으니, 서버가 running=true면 enteredRef를 세움
        if (running && !enteredRef.current) {
          enteredRef.current = true;
        }
      } catch {
        // 네트워크 오류면 다음 tick에 재시도
      } finally {
        inFlight = false;
      }
    };

    // 즉시 1회, 이후 주기적으로
    tick();
    const timer = setInterval(tick, 2000);
    return () => { stop = true; clearInterval(timer); };
  }, [open, vip, waitingQid]);



  return (open && !closing ? (
    <div className="ai-modal-backdrop">
      <div className="ai-modal">
        <div className="ai-modal-header">
          <strong>AI 카드 판별</strong>
          <button className="ai-close" onClick={closeAndLeave}>×</button>
        </div>

        <div className="ai-modal-body">
          <div className="ai-upload-row">
            <input type="file" accept="image/*" onChange={onFile} className="ai-file-input" />
            <button className="ai-btn" onClick={enqueueCurrent} disabled={!file || busy}>Predict (Queue)</button>
          </div>

          {error && <div className="ai-error">{error}</div>}

          {Number.isFinite(waitingPos) && waitingPos > 0 && (
            <div className="ai-waiting">{vip ? 'VIP ' : ''}대기 중… 앞에 {waitingPos}명</div>
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
