import React from 'react';
import { useCallback } from 'react';

function SearchBar({
  searchKeyword,
  onChange,
  onSearch,
  isLoading,
  frameType,
  onFrameChange
}) {
  // Enter 키 눌렀을 때만 실행, 로딩 중엔 무시
  const handleKeyDown = useCallback(
    e => {
      if (e.key === 'Enter' && !isLoading && !e.nativeEvent.isComposing) {
        onSearch(e);
      }
    },
    [isLoading, onSearch]
  );

  return (
    <div className="search-wrapper">
      <div className="search-container">
        <select
          value={frameType}
          onChange={e => onFrameChange(e.target.value)}
          disabled={isLoading}
        >
          <option value="">ALL</option>
          <option value="normal">일반</option>
          <option value="effect">효과</option>
          <option value="ritual">의식</option>
          <option value="fusion">융합</option>
          <option value="synchro">싱크로</option>
          <option value="xyz">엑시즈</option>
          <option value="link">링크</option>
          <option value="spell">마법</option>
          <option value="trap">함정</option>
          <option value="effect_pendulum">효과 펜듈럼</option>
          <option value="xyz_pendulum">엑시즈 펜듈럼</option>
          <option value="synchro_pendulum">싱크로 펜듈럼</option>
          <option value="fusion_pendulum">융합 펜듈럼</option>
          <option value="normal_pendulum">일반 펜듈럼</option>
        </select>
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
    </div>
  );
}

export default SearchBar;