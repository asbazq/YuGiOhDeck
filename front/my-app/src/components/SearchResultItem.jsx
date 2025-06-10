import React from 'react';
import LazyImage from './LazyImage';

function SearchResultItem({ result, onClick }) {
  const { imageUrl, name, frameType, restrictionType } = result;
  return (
    <div
      className="search-result-item"
      onClick={() => onClick(imageUrl, frameType, name)}
    >
      <LazyImage src={`/images/${imageUrl.split('/').pop()}`} alt={name} />
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

export default SearchResultItem;