import React, { useState } from 'react';
import LimitCard from './LimitCard';

const TABS = [
  { key: 'forbidden', label: 'A' },
  { key: 'limited', label: 'B' },
  { key: 'semilimited', label: 'C' }
];

function LimitBoard({ cards }) {
  const [activeTab, setActiveTab] = useState('forbidden');
  const filtered = cards.filter(card => card.restrictionType === activeTab);
  
  
  return (
<>
      <div className="limit-tabs">
        {TABS.map(tab => (
          <button
            key={tab.key}
            className={`action-button ${activeTab === tab.key ? 'on' : 'off'}`}
            onClick={() => setActiveTab(tab.key)}
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
    </>
  );
}

export default LimitBoard;