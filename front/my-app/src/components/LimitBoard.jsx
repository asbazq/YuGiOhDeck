import React from 'react';
import LimitCard from './LimitCard';

function LimitBoard({ cards }) {
  return (
    <div className="cards" id="limitBoard">
      {cards.map((card, index) => (
        <LimitCard key={`${card.imageUrl}-${index}`} card={card} />
      ))}
    </div>
  );
}

export default LimitBoard;