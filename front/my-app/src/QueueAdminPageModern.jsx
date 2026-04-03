import React, { useCallback, useEffect, useMemo, useState } from 'react';
import axios from 'axios';
import { useNavigate } from 'react-router-dom';
import './styles/QueueAdminPageModern.css';

function QueueAdminPageModern() {
  const navigate = useNavigate();
  const [throughputSite, setThroughputSite] = useState('');
  const [ttlSite, setTtlSite] = useState('');
  const [throughputPredict, setThroughputPredict] = useState('');
  const [ttlPredict, setTtlPredict] = useState('');
  const [capSite, setCapSite] = useState('');
  const [capPredict, setCapPredict] = useState('');
  const [message, setMessage] = useState('');
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

  useEffect(() => {
    loadConfig();
  }, [loadConfig]);

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
      setMessage('설정을 저장했습니다.');
    } catch {
      setMessage('설정 저장에 실패했습니다.');
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
      setMessage(`API 패치를 시작했습니다. num=${params.num}, offset=${params.offset}, sort=${params.sort}`);
    } catch {
      setMessage('API 패치 요청에 실패했습니다.');
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
      setMessage(`전체 카드 패치를 시작했습니다. ${data?.requestedUrl ?? ''}`.trim());
    } catch {
      setMessage('전체 카드 패치 요청에 실패했습니다.');
    } finally {
      setLoadingAll(false);
    }
  };

  const fetchLimitData = async () => {
    setLoadingLimit(true);
    setMessage('');
    try {
      await axios.post('/api/admin/queue/fetchLimitData');
      setMessage('리미트 레귤레이션 동기화를 시작했습니다.');
    } catch {
      setMessage('리미트 레귤레이션 동기화에 실패했습니다.');
    } finally {
      setLoadingLimit(false);
    }
  };

  const fetchKorData = async () => {
    setLoadingKor(true);
    setMessage('');
    try {
      await axios.post('/api/admin/queue/fetchKorData');
      setMessage('한글 데이터 동기화를 시작했습니다.');
    } catch {
      setMessage('한글 데이터 동기화에 실패했습니다.');
    } finally {
      setLoadingKor(false);
    }
  };

  const summaryCards = useMemo(() => ([
    { label: 'Site Throughput', value: throughputSite || '-' },
    { label: 'Predict Throughput', value: throughputPredict || '-' },
    { label: 'Site Max Running', value: capSite || '-' },
    { label: 'Predict Max Running', value: capPredict || '-' },
  ]), [capPredict, capSite, throughputPredict, throughputSite]);

  return (
    <div className="queue-admin-modern">
      {message && <div className="queue-admin-modern__toast">{message}</div>}

      <div className="queue-admin-modern__shell">
        <aside className="queue-admin-modern__hero">
          <div className="queue-admin-modern__eyebrow">Queue Admin</div>
          <h1 className="queue-admin-modern__title">Operations Console</h1>
          <p className="queue-admin-modern__description">
            큐 처리량, TTL, 동시 실행 상한과 배치 작업을 메인 화면 톤으로 한 곳에 정리했습니다.
          </p>

          <div className="queue-admin-modern__nav">
            <button type="button" className="queue-admin-modern__nav-button" onClick={() => navigate('/')}>
              Deck Builder
            </button>
            <button type="button" className="queue-admin-modern__nav-button" onClick={() => navigate('/limit')}>
              Limit Board
            </button>
            <button type="button" className="queue-admin-modern__nav-button is-active" onClick={() => navigate('/admin/queue')}>
              Admin
            </button>
          </div>

          <div className="queue-admin-modern__summary">
            {summaryCards.map(card => (
              <div key={card.label} className="queue-admin-modern__summary-card">
                <span>{card.label}</span>
                <strong>{card.value}</strong>
              </div>
            ))}
          </div>
        </aside>

        <main className="queue-admin-modern__content">
          <section className="queue-admin-modern__panel">
            <div className="queue-admin-modern__panel-header">
              <div>
                <div className="queue-admin-modern__label">Queue Config</div>
                <h2>Runtime Settings</h2>
              </div>
              <div className="queue-admin-modern__actions">
                <button type="button" className="queue-admin-modern__button" onClick={loadConfig}>
                  Reload
                </button>
                <button type="button" className="queue-admin-modern__button is-primary" onClick={updateConfig}>
                  Save
                </button>
              </div>
            </div>

            <div className="queue-admin-modern__grid">
              <label className="queue-admin-modern__field">
                <span>Site Throughput</span>
                <input type="number" value={throughputSite} onChange={event => setThroughputSite(event.target.value)} />
              </label>
              <label className="queue-admin-modern__field">
                <span>Site Session TTL</span>
                <input type="number" value={ttlSite} onChange={event => setTtlSite(event.target.value)} />
              </label>
              <label className="queue-admin-modern__field">
                <span>Predict Throughput</span>
                <input type="number" value={throughputPredict} onChange={event => setThroughputPredict(event.target.value)} />
              </label>
              <label className="queue-admin-modern__field">
                <span>Predict Session TTL</span>
                <input type="number" value={ttlPredict} onChange={event => setTtlPredict(event.target.value)} />
              </label>
              <label className="queue-admin-modern__field">
                <span>Site Max Running</span>
                <input type="number" value={capSite} onChange={event => setCapSite(event.target.value)} />
              </label>
              <label className="queue-admin-modern__field">
                <span>Predict Max Running</span>
                <input type="number" value={capPredict} onChange={event => setCapPredict(event.target.value)} />
              </label>
            </div>
          </section>

          <section className="queue-admin-modern__panel">
            <div className="queue-admin-modern__panel-header">
              <div>
                <div className="queue-admin-modern__label">Batch Jobs</div>
                <h2>Patch and Sync</h2>
              </div>
            </div>

            <div className="queue-admin-modern__grid">
              <label className="queue-admin-modern__field">
                <span>Card Count</span>
                <input type="number" min="1" value={cardNum} onChange={event => setCardNum(event.target.value)} />
              </label>
              <label className="queue-admin-modern__field">
                <span>Offset</span>
                <input type="number" min="0" value={cardOffset} onChange={event => setCardOffset(event.target.value)} />
              </label>
              <label className="queue-admin-modern__field queue-admin-modern__field--full">
                <span>Sort</span>
                <select value={cardSort} onChange={event => setCardSort(event.target.value)}>
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

            <div className="queue-admin-modern__job-buttons">
              <button type="button" className="queue-admin-modern__button" onClick={fetchAllCards} disabled={loadingAll}>
                {loadingAll ? 'Running...' : 'Fetch All Cards'}
              </button>
              <button type="button" className="queue-admin-modern__button" onClick={fetchApiData} disabled={loadingApi}>
                {loadingApi ? 'Running...' : 'Fetch API Batch'}
              </button>
              <button type="button" className="queue-admin-modern__button" onClick={fetchLimitData} disabled={loadingLimit}>
                {loadingLimit ? 'Running...' : 'Sync Limit Data'}
              </button>
              <button type="button" className="queue-admin-modern__button" onClick={fetchKorData} disabled={loadingKor}>
                {loadingKor ? 'Running...' : 'Sync Korean Data'}
              </button>
            </div>
          </section>
        </main>
      </div>
    </div>
  );
}

export default QueueAdminPageModern;
