import React from 'react';
import { useCallback } from 'react';

function SearchBar({ searchKeyword, onChange, onSearch, isLoading }) {
  // Enter 키 눌렀을 때만 실행, 로딩 중엔 무시
  const handleKeyDown = useCallback(
    e => {
      if (e.key === 'Enter' && !isLoading) {
        onSearch(e);
      }
    },
    [isLoading, onSearch]
  );

  return (
    <div className="search-container" style={{ position: 'relative' }}>
      <input
        type="search"
        value={searchKeyword}
        onChange={e => onChange(e.target.value)}
        onKeyDown={handleKeyDown}
        placeholder={isLoading ? '검색 중...' : 'eg: Dark Magician⌕'}
        disabled={isLoading}
        style={{
          opacity: isLoading ? 0.6 : 1,
          cursor: isLoading ? 'wait' : 'auto'
        }}
      />
    </div>
  );
}

export default SearchBar;