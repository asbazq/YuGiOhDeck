import React, { useCallback, useEffect, useRef, useState } from 'react';
import axios from 'axios';
import QueueModal from './QueueModal';
import { getOrCreateUserId } from '../utils/userId';
import alertCard from '../img/black-magician-girl-card-8bit.png';
import '../styles/Message.css';

export default function QueueApp({ children, group = 'site', qid = 'main' }) {
  const userIdRef = useRef(getOrCreateUserId());
  const [entered, setEntered] = useState(false);
  const [pos, setPos] = useState(0);
  const [running, setRunning] = useState(0);
  const [waiting, setWaiting] = useState(0);
  const [show, setShow] = useState(false);
  const waitingRef = useRef(0);
  const wsRef = useRef(null);
  const [message, setMessage] = useState('');
  const pingTimer = useRef(null);
  const ensureTimer = useRef(null);
  const posPollTimer = useRef(null);



  const leave = useCallback(async () => {
    setShow(false);
    setEntered(false);
    setPos(0);
    try {
      await axios.post('/queue/leave', null, {
        params: { group, qid, userId: userIdRef.current }
      });
    } catch {
      // ignore
    }
    if (wsRef.current) {
      wsRef.current.close();
      wsRef.current = null;
    }
    if (pingTimer.current) {
      clearInterval(pingTimer.current);
      pingTimer.current = null;
    }
  }, [group, qid]);

    const enter = useCallback(async () => {
    try {
      const { data } = await axios.post('/queue/enter', null, {
        params: { group, qid, userId: userIdRef.current }
      });
      if (data.entered) {
        setEntered(true);
        setShow(false);
        setPos(0);
      } else {
        setEntered(false);
        setShow(true);
        setPos(data.position);
        setWaiting(data.position);
        waitingRef.current = data.position;
      }
    } catch {
      // ignore
    }
  }, [group, qid]);

  const sendPing = useCallback(() => {
    if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
      wsRef.current.send('PING');
    }
  }, []);

  const handleTimeout = useCallback(async () => {
    try {
        await axios.post('/queue/leave', null, {
          params: { group, qid, userId: userIdRef.current }
        });
      } catch {}

      // 2) UI: 모달 띄우고 실행 상태 해제
      setEntered(false);
      setShow(true);
      setPos(0);
      setMessage('세션이 만료되었습니다. 대기열로 이동합니다.');

      // 3) 선택: 곧바로 대기열 재진입해서 position 보여주기
      try {
        await enter();
      } catch {}

      // 잠깐 뜨는 토스트는 자동 제거
      setTimeout(() => setMessage(''), 2000);
    }, [group, qid, enter]);

  const fetchStatus = useCallback(async () => {
    try {
      const { data } = await axios.get('/queue/status', { params: { qid, group } });
      const waitCnt = data.waiting ?? 0;
      setRunning(data.running ?? 0);
      setWaiting(waitCnt);
      waitingRef.current = waitCnt;
    } catch {
      // ignore
    }
  }, [qid, group]);

  useEffect(() => {
    fetchStatus();
    const id = setInterval(fetchStatus, 5000);
    return () => clearInterval(id);
  }, [fetchStatus]);



  useEffect(() => {
    enter();
  }, [enter]);

  // Ensure we are still entered: periodically re-call enter when in entered state.
  // If TTL has expired silently and server removed us, this will enqueue and open QueueModal.
  useEffect(() => {
    if (entered && !show) {
      if (ensureTimer.current) clearInterval(ensureTimer.current);
      ensureTimer.current = setInterval(() => {
        enter();
      }, 10000);
      return () => {
        clearInterval(ensureTimer.current);
        ensureTimer.current = null;
      };
    } else {
      if (ensureTimer.current) {
        clearInterval(ensureTimer.current);
        ensureTimer.current = null;
      }
    }
  }, [entered, show, enter]);

  // Fallback position polling: if queued and no ENTER yet, poll /queue/position
  useEffect(() => {
    if (show && !entered) {
      if (posPollTimer.current) clearInterval(posPollTimer.current);
      posPollTimer.current = setInterval(async () => {
        try {
          const { data } = await axios.get('/queue/position', {
            params: { group, qid, userId: userIdRef.current }
          });
          const p = typeof data.pos === 'number' ? data.pos : -1;
          setPos(p);
          if (p <= 0) {
            setEntered(true);
            setShow(false);
            clearInterval(posPollTimer.current);
            posPollTimer.current = null;
          }
        } catch {
          // ignore
        }
      }, 1500);
      return () => {
        if (posPollTimer.current) {
          clearInterval(posPollTimer.current);
          posPollTimer.current = null;
        }
      };
    } else {
      if (posPollTimer.current) {
        clearInterval(posPollTimer.current);
        posPollTimer.current = null;
      }
    }
  }, [show, entered, group, qid]);

  useEffect(() => {
    const WS_BASE = (window.location.protocol === 'https:' ? 'wss://' : 'ws://') + window.location.host;
    const ws = new WebSocket(
      `${WS_BASE}/queue-status?group=${encodeURIComponent(group)}&qid=${encodeURIComponent(qid)}&userId=${encodeURIComponent(userIdRef.current)}`
    );
    wsRef.current = ws;
    ws.onopen = sendPing;
    pingTimer.current = setInterval(sendPing, 5000);
    window.addEventListener('mousemove', sendPing);
    window.addEventListener('keydown', sendPing);

    ws.onmessage = e => {
      if (!e.data.startsWith('{')) return;
      const msg = JSON.parse(e.data);

      // 그룹 필터
    if (msg.group && msg.group !== group) return;
      if (msg.type === 'ENTER') {
        setEntered(true);
        setShow(false);
        setPos(0);
        return;
      }
      if (msg.type === 'TIMEOUT') {
        handleTimeout();
        return;
      }
      if (msg.type === 'STATUS' && msg.group === group && msg.qid === qid) {
        setRunning(msg.running ?? 0);
        setWaiting(msg.waiting ?? 0);
        setPos(msg.pos ?? 0);
      }
    };

    ws.onclose = () => {
      setEntered(false);
      setShow(false);
      setPos(0);
      wsRef.current = null;
    };

    return () => {
      clearInterval(pingTimer.current);
      if (ensureTimer.current) clearInterval(ensureTimer.current);
      if (posPollTimer.current) clearInterval(posPollTimer.current);
      window.removeEventListener('mousemove', sendPing);
      window.removeEventListener('keydown', sendPing);
      ws.close();
    };
  }, [group, qid, leave, handleTimeout, sendPing]);

  return (
    <>
      {children}
      <div id="msgWrap" style={{ display: message ? 'flex' : 'none' }}>
        <div id="msgBubble">{message}</div>
        <img src={alertCard} alt="alert" className="msgImg" />
      </div>
      <QueueModal
        key={pos}
        open={show && !entered}
        position={pos}
      />
    </>
  );
}
