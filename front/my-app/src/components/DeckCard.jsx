import React from 'react';
import LazyBackground from './LazyBackground';

function DeckCard({
  card,
  index,
  cardRefs,
  overlayRefs,
  onClick,
  onContextMenu,
  onMouseMove,
  onMouseOut,
  onTouchStart,
  onTouchEnd
}) {
  return (
    <div className="deck-card-wrapper">
      <div
        className="card-container"
        onClick={() => onClick(card.name, index)}
        onContextMenu={onContextMenu}
        onMouseMove={e => onMouseMove(e, index)}
        onMouseOut={() => onMouseOut(index)}
        onTouchStart={() => onTouchStart(index)}
        onTouchEnd={() => onTouchEnd()}
        ref={el => {
          cardRefs.current[index] = el;
          overlayRefs.current[index] = el?.querySelector('.overlay');
        }}
      >
        <div className="overlay"></div>
        <LazyBackground src={card.imageUrl} className="card" />
        {card.restrictionType && card.restrictionType !== 'unlimited' && (
          <div className="restriction-label">
            {card.restrictionType === 'forbidden'
              ? 'X'
              : card.restrictionType === 'limited'
              ? '1'
              : '2'}
          </div>
        )}
      </div>
      <p className="deck-card-name">{card.name}</p>
    </div>
  );
}

export default DeckCard;