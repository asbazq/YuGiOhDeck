import React, { useCallback, useEffect, useRef, useState } from 'react';
import axios from 'axios';
import QueueModal from './QueueModal';
import alertCard from '../img/black-magician-girl-card-8bit.png';
import '../styles/Message.css';

export default function QueueApp({ children }) {
  const qid = 'main';
  const userIdRef = useRef(Math.random().toString(36).slice(2));
  const [entered, setEntered] = useState(false);
  const [pos, setPos] = useState(0);
  const [running, setRunning] = useState(0);
  const [waiting, setWaiting] = useState(0);
  const [show, setShow] = useState(false);
  const waitingRef = useRef(0);
  const wsRef = useRef(null);
  const [message, setMessage] = useState('');
  const pingTimer = useRef(null);

  const leave = useCallback(async () => {
    setShow(false);
    setEntered(false);
    setPos(0);
    try {
      await axios.post('/queue/leave', null, {
        params: { qid, userId: userIdRef.current }
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
  }, [qid]);

    const enter = useCallback(async () => {
    try {
      const { data } = await axios.post('/queue/enter', null, {
        params: { qid, userId: userIdRef.current }
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
  }, [qid]);

  const sendPing = useCallback(() => {
    if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
      wsRef.current.send('PING');
    }
  }, []);

  const handleTimeout = useCallback(async () => {
    await leave();
    setMessage('세션이 만료되었습니다.');
    setTimeout(() => {
      setMessage('');
      enter();
    }, 2300);
  }, [leave, enter]);

  const fetchStatus = useCallback(async () => {
    try {
      const { data } = await axios.get('/queue/status', { params: { qid } });
      const waitCnt = data.waiting ?? 0;
      setRunning(data.running ?? 0);
      setWaiting(waitCnt);
      waitingRef.current = waitCnt;
    } catch {
      // ignore
    }
  }, [qid]);

  useEffect(() => {
    fetchStatus();
    const id = setInterval(fetchStatus, 5000);
    return () => clearInterval(id);
  }, [fetchStatus]);



  useEffect(() => {
    enter();
  }, [enter]);

  useEffect(() => {
    const ws = new WebSocket(
      `ws://localhost:8080/queue-status?qid=${encodeURIComponent(qid)}&userId=${encodeURIComponent(userIdRef.current)}`
    );
    wsRef.current = ws;
    ws.onopen = sendPing;
    pingTimer.current = setInterval(sendPing, 5000);
    window.addEventListener('mousemove', sendPing);
    window.addEventListener('keydown', sendPing);

    ws.onmessage = e => {
      if (!e.data.startsWith('{')) return;
      const msg = JSON.parse(e.data);
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
      if (msg.type === 'STATUS' && msg.qid === qid) {
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
      window.removeEventListener('mousemove', sendPing);
      window.removeEventListener('keydown', sendPing);
      ws.close();
    };
  }, [qid, leave, handleTimeout, sendPing]);

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