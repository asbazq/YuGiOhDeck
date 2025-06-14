import React, { useState, useEffect, useRef } from 'react';
import LimitCard from './LimitCard';
import alertCard from '../img/black-magician-girl-card-8bit.png';

const TABS = [
  { key: 'forbidden', label: 'A' },
  { key: 'limited', label: 'B' },
  { key: 'semilimited', label: 'C' }
];

function LimitBoard({ showMessage = () => {} }) {
  const [activeTab, setActiveTab] = useState('forbidden');
  const [cardsByType, setCardsByType] = useState({
    forbidden: [],
    limited: [],
    semilimited: []
  });
  const [pageByType, setPageByType] = useState({
    forbidden: 0,
    limited: 0,
    semilimited: 0
  });
  const [hasMoreByType, setHasMoreByType] = useState({
    forbidden: true,
    limited: true,
    semilimited: true
  });

  const [isLoading, setIsLoading] = useState(false);
  const [showKonami, setShowKonami] = useState(false);
  const [message, setMessage] = useState('');
  const [padHidden, setPadHidden] = useState(true);
  const filtered = cardsByType[activeTab];
  const fetchCards = async (type, page) => {
    if (isLoading || !hasMoreByType[type]) return;
    setIsLoading(true);
    try {
      setMessage('로딩중...');
      const res = await fetch(`/cards/limit?type=${type}&page=${page}&size=10`);
      const data = await res.json();
      setCardsByType(prev => ({
        ...prev,
        [type]: page === 0 ? data.content : [...prev[type], ...data.content]
      }));
      setPageByType(prev => ({ ...prev, [type]: data.number }));
      setHasMoreByType(prev => ({ ...prev, [type]: !data.last }));
      setMessage('');
    } catch {
      setMessage('');
      showMessage('불러오기 실패');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    if (filtered.length === 0) {
      fetchCards(activeTab, 0);
    }
  }, [activeTab]);

  useEffect(() => {
    const handleScroll = () => {
      if ( window.innerHeight + window.scrollY >=
          document.documentElement.scrollHeight - 5 &&
         !isLoading
      ) {
        const nextPage = pageByType[activeTab] + 1;
        fetchCards(activeTab, nextPage);
      }
    };
    window.addEventListener('scroll', handleScroll);
    return () => window.removeEventListener('scroll', handleScroll);
  }, [activeTab, hasMoreByType, isLoading, pageByType]);

  const konami = [
    'ArrowUp',
    'ArrowUp',
    'ArrowDown',
    'ArrowDown',
    'ArrowLeft',
    'ArrowRight',
    'ArrowLeft',
    'ArrowRight',
    'b',
    'a'
  ];
  const konamiIndex = useRef(0);

  const handleInput = (key) => {
    const expected = konami[konamiIndex.current];
    if (key.toLowerCase() === expected.toLowerCase()) {
      konamiIndex.current += 1;
      if (konamiIndex.current === konami.length) {
        showMessage('Konami code!');
        konamiIndex.current = 0;
      }
    } else {
      konamiIndex.current =
        key.toLowerCase() === konami[0].toLowerCase() ? 1 : 0;
    }
  };

  useEffect(() => {
    const listener = e => handleInput(e.key);
    window.addEventListener('keydown', listener);
    return () => window.removeEventListener('keydown', listener);
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
          {filtered.map((card, index) => (
            <LimitCard key={`${card.imageUrl}-${index}`} card={card} />
          ))}
        </div>
        {padHidden ? (
          <button
            id="showPadButton"
            className="action-button"
            onClick={() => setPadHidden(false)}
          >
          </button>
            ) : (
          <div id="directionPad">
            <button
              className="hide-button action-button"
              onClick={() => setPadHidden(true)}
            >
            </button>
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