import React from 'react';
import LazyImage from './LazyImage';
import { localImagePath, filenameOf } from '../common/imagePath';

function LimitCard({ card }) {
  const { id, imageUrl, imageUrlSmall, name, restrictionType } = card;
  return (
    <div className="search-result-item">
      <LazyImage
        src={imageUrlSmall ?? localImagePath(id ?? imageUrl, 'small')}
        alt={name}
      />
      {restrictionType && restrictionType !== 'unlimited' && (
        <div
          className={`restriction-label ${
            restrictionType === 'forbidden'
              ? 'forbidden'
              : restrictionType === 'limited'
              ? 'limited'
              : 'semi-limited'
          }`}
        >
          {restrictionType === 'forbidden'
            ? null
            : restrictionType === 'limited'
            ? '1'
            : '2'}
        </div>
      )}
      <p>{name}</p>
    </div>
  );
}

export default LimitCard;