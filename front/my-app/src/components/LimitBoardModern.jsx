import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import LimitCard from './LimitCard';

const TABS = [
  { key: 'forbidden', label: 'Forbidden', short: 'A' },
  { key: 'limited', label: 'Limited', short: 'B' },
  { key: 'semilimited', label: 'Semi-Limited', short: 'C' },
];

const TAB_KEYS = TABS.map(tab => tab.key);
const KONAMI = [
  'ArrowUp', 'ArrowUp', 'ArrowDown', 'ArrowDown',
  'ArrowLeft', 'ArrowRight', 'ArrowLeft', 'ArrowRight', 'b', 'a',
];

function LimitBoardModern({ showMessage = () => {} }) {
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
  const [isLoading, setIsLoading] = useState(false);
  const [padHidden, setPadHidden] = useState(true);
  const [inlineMessage, setInlineMessage] = useState('');
  const isFetchingRef = useRef({
    forbidden: false,
    limited: false,
    semilimited: false,
  });

  const konamiIndexRef = useRef(0);

  const activeCards = cardsByType[activeTab] || [];
  const totalLoaded = useMemo(
    () => TAB_KEYS.reduce((sum, key) => sum + (cardsByType[key]?.length || 0), 0),
    [cardsByType]
  );

  const fetchCards = useCallback(async (type, page) => {
    if (!TAB_KEYS.includes(type) || isFetchingRef.current[type] || hasMoreByType[type] === false) {
      return;
    }

    isFetchingRef.current[type] = true;
    setIsLoading(true);
    setInlineMessage('Loading cards...');

    try {
      const response = await fetch(`/cards/limit?type=${type}&page=${page}&size=30`);
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }

      const data = await response.json();
      const content = Array.isArray(data?.content) ? data.content : [];
      const nextPage = Number.isFinite(data?.number) ? data.number : page;
      const hasMore = data?.last === false;

      setCardsByType(prev => ({
        ...prev,
        [type]: page === 0 ? content : [...prev[type], ...content],
      }));
      setPageByType(prev => ({ ...prev, [type]: nextPage }));
      setHasMoreByType(prev => ({ ...prev, [type]: hasMore }));
      setInlineMessage('');
    } catch (error) {
      console.error('Failed to fetch limit cards:', error);
      setInlineMessage('');
      showMessage('리미트 카드를 불러오지 못했습니다.');
    } finally {
      setIsLoading(false);
      isFetchingRef.current[type] = false;
    }
  }, [hasMoreByType, showMessage]);

  useEffect(() => {
    if (activeCards.length === 0) {
      fetchCards(activeTab, 0);
    }
  }, [activeCards.length, activeTab, fetchCards]);

  useEffect(() => {
    const handleScroll = () => {
      const nearBottom =
        window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 80;

      if (nearBottom && !isFetchingRef.current[activeTab] && !isLoading && hasMoreByType[activeTab]) {
        fetchCards(activeTab, (pageByType[activeTab] ?? 0) + 1);
      }
    };

    window.addEventListener('scroll', handleScroll, { passive: true });
    return () => window.removeEventListener('scroll', handleScroll);
  }, [activeTab, fetchCards, hasMoreByType, isLoading, pageByType]);

  const handleInput = useCallback((key) => {
    const normalized = (key || '').toLowerCase();
    const expected = (KONAMI[konamiIndexRef.current] || '').toLowerCase();

    if (normalized === expected) {
      konamiIndexRef.current += 1;
      if (konamiIndexRef.current === KONAMI.length) {
        showMessage('Konami code!');
        konamiIndexRef.current = 0;
      }
      return;
    }

    konamiIndexRef.current = normalized === KONAMI[0].toLowerCase() ? 1 : 0;
  }, [showMessage]);

  useEffect(() => {
    const listener = (event) => handleInput(event.key);
    window.addEventListener('keydown', listener);
    return () => window.removeEventListener('keydown', listener);
  }, [handleInput]);

  return (
    <div className="limit-board-modern">
      <section className="limit-board-modern__header">
        <div className="limit-board-modern__intro">
          <div className="limit-board-modern__label">Regulation Board</div>
          <h2>{TABS.find(tab => tab.key === activeTab)?.label}</h2>
          <p>최신 규제 카드를 탭별로 나눠 보고 필요한 만큼 계속 불러옵니다.</p>
        </div>
      </section>

      <section className="limit-board-modern__toolbar">
        <div className="limit-board-modern__tabs">
          {TABS.map(tab => (
            <button
              key={tab.key}
              type="button"
              className={`limit-board-modern__tab ${activeTab === tab.key ? 'is-active' : ''}`}
              onClick={() => {
                setActiveTab(tab.key);
                handleInput(tab.short.toLowerCase());
              }}
            >
              <span>{tab.short}</span>
              {tab.label}
            </button>
          ))}
        </div>

        <div className="limit-board-modern__meta">
          <span>{totalLoaded} cards loaded</span>
          {inlineMessage && <strong>{inlineMessage}</strong>}
          {isLoading && !inlineMessage && <strong>Loading...</strong>}
        </div>
      </section>

      <section className="limit-board-modern__panel">
        {activeCards.length === 0 && !isLoading ? (
          <div className="limit-board-modern__empty">
            현재 표시할 카드가 없습니다.
          </div>
        ) : (
          <div className="limit-board-modern__grid" id="limitBoard">
            {activeCards.map((card, index) => (
              <LimitCard key={card.id ?? `${card.imageUrl}-${index}`} card={card} />
            ))}
          </div>
        )}
      </section>

      <aside className="limit-board-modern__pad-panel">
        <div>
          <div className="limit-board-modern__label">Assist Pad</div>
          <h3>Konami Input</h3>
          <p>키보드 없이도 방향 입력과 B, A 입력을 남겨둘 수 있습니다.</p>
        </div>

        {padHidden ? (
          <button
            type="button"
            className="limit-board-modern__toggle"
            onClick={() => setPadHidden(false)}
          >
            Open Pad
          </button>
        ) : (
          <div className="limit-board-modern__pad-wrap">
            <div className="limit-board-modern__pad">
              <button type="button" className="pad-button up" onClick={() => handleInput('ArrowUp')}>U</button>
              <button type="button" className="pad-button left" onClick={() => handleInput('ArrowLeft')}>L</button>
              <button type="button" className="pad-button center" onClick={() => setPadHidden(true)}>X</button>
              <button type="button" className="pad-button right" onClick={() => handleInput('ArrowRight')}>R</button>
              <button type="button" className="pad-button down" onClick={() => handleInput('ArrowDown')}>D</button>
            </div>
            <div className="limit-board-modern__ab">
              <button type="button" className="limit-board-modern__ab-button" onClick={() => handleInput('b')}>B</button>
              <button type="button" className="limit-board-modern__ab-button" onClick={() => handleInput('a')}>A</button>
            </div>
          </div>
        )}
      </aside>
    </div>
  );
}

export default LimitBoardModern;
