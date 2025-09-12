import React, { useEffect, useState, useCallback } from 'react';
import axios from 'axios';
import { useNavigate } from 'react-router-dom';
import './styles/QueueAdminPage.css';

export default function QueueAdminPage() {
  const navigate = useNavigate();

  // site
  const [throughputSite, setThroughputSite] = useState('');
  const [ttlSite, setTtlSite] = useState('');
  // predict
  const [throughputPredict, setThroughputPredict] = useState('');
  const [ttlPredict, setTtlPredict] = useState('');
  // 동시 실행 상한
  const [capSite, setCapSite] = useState('');
  const [capPredict, setCapPredict] = useState('');

  const [message, setMessage] = useState('');
  const [isMenuOpen, setIsMenuOpen] = useState(false);

  // 패치 파라미터
  const [cardNum, setCardNum] = useState(500);
  const [cardOffset, setCardOffset] = useState(20);
  const [cardSort, setCardSort] = useState('new');
  const [loadingApi, setLoadingApi] = useState(false);
  const [loadingAll, setLoadingAll] = useState(false);
  const [loadingLimit, setLoadingLimit] = useState(false);
  const [loadingKor, setLoadingKor] = useState(false);

  const loadConfig = useCallback(async () => {
    try {
      const { data } = await axios.get('/api/admin/queue/all');
      setThroughputSite(data.throughputSite ?? '');
      setTtlSite(data.sessionTtlMillisSite ?? '');
      setThroughputPredict(data.throughputPredict ?? '');
      setTtlPredict(data.sessionTtlMillisPredict ?? '');
      setCapSite(data.maxRunningSite ?? '');
      setCapPredict(data.maxRunningPredict ?? '');
      setMessage('');
    } catch {
      setMessage('설정을 불러오지 못했습니다.');
    }
  }, []);

  const updateConfig = async () => {
    try {
      const params = {};
      if (throughputSite !== '') params.throughputSite = Number(throughputSite);
      if (ttlSite !== '') params.sessionTtlMillisSite = Number(ttlSite);
      if (throughputPredict !== '') params.throughputPredict = Number(throughputPredict);
      if (ttlPredict !== '') params.sessionTtlMillisPredict = Number(ttlPredict);
      if (capSite !== '') params.maxRunningSite = Number(capSite);
      if (capPredict !== '') params.maxRunningPredict = Number(capPredict);

      await axios.post('/api/admin/queue/all', null, { params });
      setMessage('저장되었습니다.');
      // 최신값 다시 반영하고 싶으면 아래 주석 해제
      // await loadConfig();
    } catch {
      setMessage('저장 실패');
    }
  };

  const fetchApiData = async () => {
    setLoadingApi(true);
    setMessage('');
    try {
      const params = {
        num: Math.max(1, Number(cardNum) || 1),
        offset: Math.max(0, Number(cardOffset) || 0),
        sort: cardSort || 'new',
      };
      await axios.post('/api/admin/queue/fetchApiData', null, { params });
      setMessage(`API 데이터 가져오기 시작 (num=${params.num}, offset=${params.offset}, sort=${params.sort})`);
    } catch {
      setMessage('API 데이터 가져오기 실패');
    } finally {
      setLoadingApi(false);
    }
  };

  const fetchAllCards = async () => {
    setLoadingAll(true);
    setMessage('');
    try {
      const { data } = await axios.post('/api/admin/queue/fetchApiData', null, {
        params: { all: true },
      });
      setMessage(`전체 카드 패치 시작 (요청: ${data?.requestedUrl ?? 'all'})`);
    } catch {
      setMessage('전체 카드 패치 실패');
    } finally {
      setLoadingAll(false);
    }
  };

  const fetchLimitData = async () => {
    setLoadingLimit(true);
    setMessage('');
    try {
      await axios.post('/api/admin/queue/fetchLimitData');
      setMessage('리밋 데이터 가져오기 시작');
    } catch {
      setMessage('리밋 데이터 가져오기 실패');
    } finally {
      setLoadingLimit(false);
    }
  };

  const fetchKorData = async () => {
    setLoadingKor(true);
    setMessage('');
    try {
      await axios.post('/api/admin/queue/fetchKorData');
      setMessage('한글 데이터 가져오기 시작');
    } catch {
      setMessage('한글 데이터 가져오기 실패');
    } finally {
      setLoadingKor(false);
    }
  };

  useEffect(() => { loadConfig(); }, [loadConfig]);

  return (
    <>
      <button
        className="menu-button"
        onClick={() => setIsMenuOpen(true)}
        aria-label="메뉴 열기"
      >
        <span></span><span></span><span></span>
      </button>

      <div
        className={`menu-overlay ${isMenuOpen ? 'open' : ''}`}
        onClick={() => setIsMenuOpen(false)}
      />

      <div className={`side-menu ${isMenuOpen ? 'open' : ''}`}>
        <div className="board-switch">
          <button onClick={() => { navigate('/limit'); setIsMenuOpen(false); }}>
            리미트 레귤레이션
          </button>
          <button onClick={() => { navigate('/'); setIsMenuOpen(false); }}>
            덱 빌딩
          </button>
          <button onClick={() => { navigate('/admin/queue'); setIsMenuOpen(false); }}>
            관리자
          </button>
        </div>
      </div>

      <div className="pixel-admin-wrapper">
        <div className="pixel-admin">
          <h1>Queue Admin</h1>

          {/* SITE 설정 */}
          <div className="section-title">site</div>
          <div className="row">
            <label>
              throughput (site):&nbsp;
              <input
                type="number"
                className="pixel-input"
                value={throughputSite}
                onChange={e => setThroughputSite(e.target.value)}
              />
            </label>
          </div>
          <div className="row">
            <label>
              sessionTtlMillis (site):&nbsp;
              <input
                type="number"
                className="pixel-input"
                value={ttlSite}
                onChange={e => setTtlSite(e.target.value)}
              />
            </label>
          </div>

          {/* PREDICT 설정 */}
          <div className="section-title">predict</div>
          <div className="row">
            <label>
              throughput (predict):&nbsp;
              <input
                type="number"
                className="pixel-input"
                value={throughputPredict}
                onChange={e => setThroughputPredict(e.target.value)}
              />
            </label>
          </div>
          <div className="row">
            <label>
              sessionTtlMillis (predict):&nbsp;
              <input
                type="number"
                className="pixel-input"
                value={ttlPredict}
                onChange={e => setTtlPredict(e.target.value)}
              />
            </label>
          </div>

          {/* 동시 실행 상한 */}
          <div className="section-title">동시 실행 상한</div>
          <div className="row">
            <label>
              max (site):&nbsp;
              <input
                type="number"
                className="pixel-input"
                value={capSite}
                onChange={e => setCapSite(e.target.value)}
              />
            </label>
          </div>
          <div className="row">
            <label>
              max (predict):&nbsp;
              <input
                type="number"
                className="pixel-input"
                value={capPredict}
                onChange={e => setCapPredict(e.target.value)}
              />
            </label>
          </div>

          <button className="pixel-btn" onClick={loadConfig}>불러오기</button>
          <button className="pixel-btn" onClick={updateConfig}>저장</button>

          {message && <div className="pixel-message">{message}</div>}
        </div>

        <div className="fetch-button-container">
          <h1>패치</h1>

          <div className="row">
            <label>
              num:&nbsp;
              <input
                type="number"
                className="pixel-input-num"
                min={1}
                value={cardNum}
                onChange={e => setCardNum(e.target.value)}
              />
            </label>
          </div>

          <div className="row">
            <label>
              offset:&nbsp;
              <input
                type="number"
                className="pixel-input-num"
                min={0}
                value={cardOffset}
                onChange={e => setCardOffset(e.target.value)}
              />
            </label>
          </div>

          <div className="row">
            <label>
              sort:&nbsp;
              <select
                className="pixel-select"
                value={cardSort}
                onChange={e => setCardSort(e.target.value)}
              >
                <option value="atk">atk</option>
                <option value="def">def</option>
                <option value="name">name</option>
                <option value="type">type</option>
                <option value="level">level</option>
                <option value="id">id</option>
                <option value="new">new</option>
              </select>
            </label>
          </div>

          <button className="pixel-btn" onClick={fetchAllCards} disabled={loadingAll}>
            {loadingAll ? '요청 중...' : '전체 카드 fetchApiData'}
          </button>
          <button className="pixel-btn" onClick={fetchApiData} disabled={loadingApi}>
            {loadingApi ? '요청 중...' : 'fetchApiData'}
          </button>
          <button className="pixel-btn" onClick={fetchLimitData} disabled={loadingLimit}>
            {loadingLimit ? '요청 중...' : '리미트 레귤레이션'}
          </button>
          <button className="pixel-btn" onClick={fetchKorData} disabled={loadingKor}>
            {loadingKor ? '요청 중...' : '한글'}
          </button>
        </div>
      </div>
    </>
  );
}
