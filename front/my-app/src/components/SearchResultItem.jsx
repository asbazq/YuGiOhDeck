import React from 'react';
import LazyImage from './LazyImage';
import { localImagePath, filenameOf } from '../common/imagePath';

function SearchResultItem({ result, onClick, onHover }) {
  const { id, imageUrl, imageUrlSmall, rawImageUrl, rawImageUrlSmall, name, frameType, restrictionType } = result;

  const idOrFile = id ?? filenameOf(imageUrl) ?? filenameOf(imageUrlSmall);
  const smallSrc = localImagePath(idOrFile, 'small'); // 표시용
  const largeSrc = localImagePath(idOrFile, 'large'); // 필요 시 전환용

  return (
    <div
      className="search-result-item"
      // 덱 추가 시 부모로 “원본 large” 전달 (rawImageUrl 우선)
      onClick={() => onClick(smallSrc, frameType, name)}
      onPointerEnter={e => onHover(name, e.currentTarget, e.pointerType)}
      onMouseLeave={() => onHover(null)}
    >
     {/* 썸네일은 small(로컬), 없으면 LazyImage가 fallback으로 large 사용 */}
     <LazyImage src={smallSrc} fallbackSrc={largeSrc} alt={name} />
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