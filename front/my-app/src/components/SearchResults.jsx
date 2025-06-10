import React from 'react';
import SearchResultItem from './SearchResultItem';

function SearchResults({ results, addCardToDeck }) {
  return (
    <div className="cards" id="searchResult">
      {results.map((result, index) => (
        <SearchResultItem
          key={`${result.imageUrl.split('/').pop()}-${index}`}
          result={result}
          onClick={addCardToDeck}
        />
      ))}
    </div>
  );
}

export default SearchResults;