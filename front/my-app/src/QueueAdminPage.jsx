import React, { useEffect, useState } from 'react';
import axios from 'axios';
import './styles/QueueAdminPage.css'

export default function QueueAdminPage() {
  const initialQid = (() => {
    const m = window.location.pathname.match(/\/admin\/queue\/(.+)$/);
    return m ? decodeURIComponent(m[1]) : 'main';
  })();

  const [qid, setQid] = useState(initialQid);
  const [throughput, setThroughput] = useState('');
  const [ttl, setTtl] = useState('');
  const [maxRunning, setMaxRunning] = useState('');
  const [message, setMessage] = useState('');

  const loadConfig = async () => {
    try {
      const { data } = await axios.get(`/api/admin/queue/${encodeURIComponent(qid)}`);
      setThroughput(data.throughput ?? '');
      setTtl(data.sessionTtlMillis ?? '');
      setMaxRunning(data.maxRunning ?? '');
      setMessage('');
    } catch {
      setMessage('설정을 불러오지 못했습니다.');
    }
  };

  const updateConfig = async () => {
    try {
      await axios.post(
        `/api/admin/queue/${encodeURIComponent(qid)}`,
        null,
        {
          params: {
            throughput,
            sessionTtlMillis: ttl,
            maxRunning
          }
        }
      );
      setMessage('저장되었습니다.');
    } catch {
      setMessage('저장 실패');
    }
  };

  const fetchApiData = async () => {
    try {
      await axios.post('/api/admin/queue/fetchApiData');
      setMessage('API 데이터 가져오기 시작');
    } catch {
      setMessage('API 데이터 가져오기 실패');
    }
  };

  const fetchLimitData = async () => {
    try {
      await axios.post('/api/admin/queue/fetchLimitData');
      setMessage('리밋 데이터 가져오기 시작');
    } catch {
      setMessage('리밋 데이터 가져오기 실패');
    }
  };

  const fetchKorData = async () => {
    try {
      await axios.post('/api/admin/queue/fetchKorData');
      setMessage('한글 데이터 가져오기 시작');
    } catch {
      setMessage('한글 데이터 가져오기 실패');
    }
  };

useEffect(() => { loadConfig(); }, [qid]);

return (
  <div className="pixel-admin-wrapper">
    <div className="pixel-admin">
      <h1>Queue Admin</h1>

      <div className="row">
        <label>
          큐 ID:&nbsp;
          <input
            className="pixel-input"
            value={qid}
            onChange={e => setQid(e.target.value)}
          />
        </label>
        
      </div>

      <div className="row">
        <label>
          throughput:&nbsp;
          <input
            type="number"
            className="pixel-input"
            value={throughput}
            onChange={e => setThroughput(e.target.value)}
          />
        </label>
      </div>

      <div className="row">
        <label>
          sessionTtlMillis:&nbsp;
          <input
            type="number"
            className="pixel-input"
            value={ttl}
            onChange={e => setTtl(e.target.value)}
          />
        </label>
      </div>

      <div className="row">
        <label>
          maxRunning:&nbsp;
          <input
            type="number"
            className="pixel-input"
            value={maxRunning}
            onChange={e => setMaxRunning(e.target.value)}
          />
        </label>
      </div>
      <button className="pixel-btn" onClick={loadConfig}>
          불러오기
        </button>
      <button className="pixel-btn" onClick={updateConfig}>
        저장
      </button>
      {message && (
        <div className="pixel-message">
          {message}
        </div>
      )}
    </div>
         <div className="fetch-button-container">
          <h1>패치</h1>
        <button className="pixel-btn" onClick={fetchApiData}>
          Api
        </button>
        <button className="pixel-btn" onClick={fetchLimitData}>
          리미트 레귤레이션
        </button>
        <button className="pixel-btn" onClick={fetchKorData}>
          한글
        </button>
      </div>
    </div>
  );
}