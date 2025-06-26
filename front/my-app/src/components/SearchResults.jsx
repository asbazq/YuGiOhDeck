import React, { useState, useCallback, useRef } from 'react';
import { createPortal } from 'react-dom';
import SearchResultItem from './SearchResultItem';
import '../styles/HoverEffect.css';

function SearchResults({ results, addCardToDeck }) {
  const [hoverInfo, setHoverInfo] = useState(null);
  const cacheRef = useRef({});
  const containerRef = useRef(null);

  const handleHover = useCallback(async (name, el, pointerType) => {
    if (pointerType === 'touch' || window.matchMedia('(hover: none)').matches) {
      setHoverInfo(null);
      return;
    }

    if (!name || !el) {
      setHoverInfo(null);
      return;
    }

    const rect = el.getBoundingClientRect();
    const infoPos = {
      top: rect.top + window.scrollY,
      left: rect.left + window.scrollX - 230,
    };

    const cached = cacheRef.current[name];
    if (cached) {
      setHoverInfo({ ...cached, ...infoPos });
      return;
    }

    try {
      const res = await fetch(`/cards/cardinfo?cardName=${encodeURIComponent(name)}`);
      if (!res.ok) throw new Error('info');
      const data = await res.json();
      const info = { name: data.name, desc: data.korDesc || data.desc };
      cacheRef.current[name] = info;
      setHoverInfo({ ...info, ...infoPos });
    } catch (e) {
      setHoverInfo(null);
    }
  }, []);

  return (
     <div className="search-results-wrapper" ref={containerRef}>
      {hoverInfo &&
        createPortal(
          <div
            className="hover-info"
            style={{ top: hoverInfo.top, left: hoverInfo.left }}
          >
            <div className="hover-info-name">{hoverInfo.name}</div>
            <div className="hover-info-desc">{hoverInfo.desc}</div>
          </div>,
          document.body
        )}
      <div className="cards" id="searchResult">
        {results.map((result, index) => (
          <SearchResultItem
            key={`${result.imageUrl.split('/').pop()}-${index}`}
            result={result}
            onClick={addCardToDeck}
            onHover={handleHover}
          />
        ))}
      </div>
    </div>
  );
}

export default SearchResults;