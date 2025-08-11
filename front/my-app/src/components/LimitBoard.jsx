// src/components/LimitBoard.jsx
import React, { useState, useEffect, useRef } from 'react';
import LimitCard from './LimitCard';
import alertCard from '../img/black-magician-girl-card-8bit.png';

const TABS = [
  { key: 'forbidden',   label: 'A' },
  { key: 'limited',     label: 'B' },
  { key: 'semilimited', label: 'C' },
];

const TAB_KEYS = TABS.map(t => t.key);

function LimitBoard({ showMessage = () => {} }) {
  // 탭/데이터 상태 (초기 형태를 항상 보장)
  const [activeTab, setActiveTab] = useState('forbidden');
  const [cardsByType, setCardsByType] = useState({
    forbidden: [],
    limited: [],
    semilimited: [],
  });
  const [pageByType, setPageByType] = useState({
    forbidden: 0,
    limited: 0,
    semilimited: 0,
  });
  const [hasMoreByType, setHasMoreByType] = useState({
    forbidden: true,
    limited: true,
    semilimited: true,
  });

  // 로딩/페칭 락
  const [isLoading, setIsLoading] = useState(false);
  const isFetchingRef = useRef({
    forbidden: false,
    limited: false,
    semilimited: false,
  });

  // UI 메시지 / 방향패드
  const [message, setMessage] = useState('');
  const [padHidden, setPadHidden] = useState(true);

  // 현재 탭의 표시 목록
  const filtered = cardsByType?.[activeTab] ?? [];

  // 카드 페치 (탭별-안전)
  const fetchCards = async (type, page) => {
    if (!TAB_KEYS.includes(type)) return;
    if (isFetchingRef.current[type]) return;
    if (hasMoreByType?.[type] === false) return;

    isFetchingRef.current[type] = true;
    setIsLoading(true);
    setMessage('로딩중...');

    try {
      const res = await fetch(`/cards/limit?type=${type}&page=${page}&size=30`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);

      const data = await res.json();
      const content  = Array.isArray(data?.content) ? data.content : [];
      const nextPage = Number.isFinite(data?.number) ? data.number : page;
      const hasMore  = data?.last === false; // last=false면 더 있음

      setCardsByType(prev => ({
        ...prev,
        [type]: page === 0 ? content : [ ...(prev?.[type] ?? []), ...content ],
      }));

      setPageByType(prev => ({ ...prev, [type]: nextPage }));

      // prev가 실수로 boolean이 된 경우에도 복구
      setHasMoreByType(prev => {
        const base = (prev && typeof prev === 'object')
          ? prev
          : { forbidden: true, limited: true, semilimited: true };
        return { ...base, [type]: hasMore };
      });

      setMessage('');
    } catch (e) {
      setMessage('');
      console.error('fetchCards error:', e);
      showMessage('불러오기 실패');
    } finally {
      setIsLoading(false);
      isFetchingRef.current[type] = false;
    }
  };

  // 탭 전환 시 최초 로드
  useEffect(() => {
    if (filtered.length === 0) {
      fetchCards(activeTab, 0);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeTab]);

  // 무한 스크롤
  useEffect(() => {
    const handleScroll = () => {
      const nearBottom =
        window.innerHeight + window.scrollY >=
        document.documentElement.scrollHeight - 5;

      if (
        nearBottom &&
        !isFetchingRef.current[activeTab] &&
        !isLoading &&
        hasMoreByType?.[activeTab]
      ) {
        const nextPage = (pageByType?.[activeTab] ?? 0) + 1;
        fetchCards(activeTab, nextPage);
      }
    };

    window.addEventListener('scroll', handleScroll, { passive: true });
    return () => window.removeEventListener('scroll', handleScroll);
  }, [activeTab, hasMoreByType, isLoading, pageByType]);

  // 코나미 코드
  const konami = [
    'ArrowUp','ArrowUp','ArrowDown','ArrowDown',
    'ArrowLeft','ArrowRight','ArrowLeft','ArrowRight','b','a'
  ];
  const konamiIndex = useRef(0);

  const handleInput = (key) => {
    const expected = konami[konamiIndex.current];
    if ((key ?? '').toLowerCase() === (expected ?? '').toLowerCase()) {
      konamiIndex.current += 1;
      if (konamiIndex.current === konami.length) {
        showMessage('Konami code!');
        konamiIndex.current = 0;
      }
    } else {
      konamiIndex.current =
        (key ?? '').toLowerCase() === (konami[0] ?? '').toLowerCase() ? 1 : 0;
    }
  };

  useEffect(() => {
    const listener = e => handleInput(e.key);
    window.addEventListener('keydown', listener);
    return () => window.removeEventListener('keydown', listener);
    // 빈 의존성으로 최초만 바인딩
  }, []);

  return (
    <div className="limit-container">
      <div className="limit-main">
        <div className="limit-tabs">
          <div id="msgWrap" style={{ display: message ? 'flex' : 'none' }}>
            <div id="msgBubble">{message}</div>
            <img src={alertCard} alt="alert" className="msgImg" />
          </div>

          {TABS.map(tab => (
            <button
              key={tab.key}
              className={`action-button ${activeTab === tab.key ? 'on' : 'off'}`}
              onClick={() => {
                setActiveTab(tab.key);
                if (tab.label === 'A' || tab.label === 'B') {
                  handleInput(tab.label.toLowerCase());
                }
              }}
            >
              {tab.label}
            </button>
          ))}
        </div>

        <div className="cards" id="limitBoard">
          {(filtered ?? []).map((card, index) => (
            <LimitCard key={card.id ?? `${card.imageUrl}-${index}`} card={card} />
          ))}
        </div>

        {padHidden ? (
          <button
            id="showPadButton"
            className="action-button"
            onClick={() => setPadHidden(false)}
          />
        ) : (
          <div id="directionPad">
            <button
              className="hide-button action-button"
              onClick={() => setPadHidden(true)}
            />
            <button
              className="action-button up"
              onClick={() => handleInput('ArrowUp')}
            >
              ↑
            </button>
            <button
              className="action-button left"
              onClick={() => handleInput('ArrowLeft')}
            >
              ←
            </button>
            <button
              className="action-button right"
              onClick={() => handleInput('ArrowRight')}
            >
              →
            </button>
            <button
              className="action-button down"
              onClick={() => handleInput('ArrowDown')}
            >
              ↓
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

export default LimitBoard;
