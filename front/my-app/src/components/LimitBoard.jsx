import React, { useState, useEffect, useRef } from 'react';
import LimitCard from './LimitCard';
import alertCard from '../img/1749718043.gif';

const TABS = [
  { key: 'forbidden', label: 'A' },
  { key: 'limited', label: 'B' },
  { key: 'semilimited', label: 'C' }
];

function LimitBoard({ cards, showMessage = () => {} }) {
  const [activeTab, setActiveTab] = useState('forbidden');
  const [showKonami, setShowKonami] = useState(false);
  const [message, setMessage] = useState('');
  const [padHidden, setPadHidden] = useState(true);
  const filtered = cards.filter(card => card.restrictionType === activeTab);
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

  const handleInput = key => {
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
    <>
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
    </>
  );
}

export default LimitBoard;