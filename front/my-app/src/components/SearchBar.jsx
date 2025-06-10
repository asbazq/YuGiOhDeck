import React from 'react';

function SearchBar({ searchKeyword, onChange, onSearch, isLoading }) {
  return (
    <div className="search-container">
      <input
        type="search"
        value={searchKeyword}
        onChange={e => onChange(e.target.value)}
        onKeyDown={onSearch}
        placeholder="eg: Dark Magician⌕"
      />
      {isLoading && <div id="loading">Loading...</div>}
    </div>
  );
}

export default SearchBar;