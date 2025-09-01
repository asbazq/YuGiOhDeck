import React from 'react';
import LazyBackground from './LazyBackground';
import { localImagePath, filenameOf } from '../common/imagePath';

function DeckCard({
  card,
  index,
  useLarge = false,
  cardRefs,
  overlayRefs,
  onClick,
  onContextMenu,
  onMouseMove,
  onMouseOut,
  onTouchStart,
  onTouchEnd
}) {
  // 안전한 파일명/ID 파싱
  const fileOrId = card?.id ?? filenameOf(card?.imageUrl) ?? filenameOf(card?.imageUrlSmall);
  const smallSrc = localImagePath(fileOrId, 'small');
  const largeSrc = localImagePath(fileOrId, 'large');
  const currentSrc = useLarge ? largeSrc : smallSrc;

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
        <LazyBackground
          src={currentSrc}
          className="card"
          data-small={smallSrc}
          data-large={largeSrc}
        />
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