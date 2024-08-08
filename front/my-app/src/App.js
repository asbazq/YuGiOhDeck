import React, { useState, useEffect, useCallback, useRef } from 'react';
import './App.css';
import pako from 'pako';

function App() {
  const [mainDeck, setMainDeck] = useState([]);
  const [extraDeck, setExtraDeck] = useState([]);
  const [searchResults, setSearchResults] = useState([]);
  const [searchKeyword, setSearchKeyword] = useState('');
  const [currentPage, setCurrentPage] = useState(0);
  const [isLoading, setIsLoading] = useState(false);
  const [hasMoreResults, setHasMoreResults] = useState(true);
  const [message, setMessage] = useState('');
  const [cardDetail, setCardDetail] = useState(null);
  const [isExpanded, setIsExpanded] = useState(false);
  
  const cardRefs = useRef([]);
  const overlayRefs = useRef([]);
  const expandedOverlayRef = useRef(null);

  const searchCards = useCallback((keyWord, page) => {
    if (!hasMoreResults || isLoading) return;

    setIsLoading(true);
    fetch(`/cards/search?keyWord=${encodeURIComponent(keyWord)}&page=${page}&size=28`)
      .then(response => {
        if (!response.ok) throw new Error('Network response was not ok');
        return response.json();
      })
      .then(data => {
        setSearchResults(prevResults => page === 0 ? data.content : [...prevResults, ...data.content]);
        setHasMoreResults(!data.last);
        setCurrentPage(data.number);
        setIsLoading(false);
      })
      .catch(error => {
        console.error('Error:', error);
        setIsLoading(false);
      });
  }, [hasMoreResults, isLoading]);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const deck = params.get('deck');

    if (deck && /^[a-zA-Z0-9+/=]*$/.test(deck)) {
      try {
        const compressed = atob(deck);
        const compressedData = new Uint8Array(compressed.split('').map(char => char.charCodeAt(0)));
        const dataStr = pako.inflate(compressedData, { to: 'string' });
        const dataObj = JSON.parse(dataStr);
        setMainDeck(sortCards(dataObj.cardsContent || []));
        setExtraDeck(sortCards(dataObj.extraDeckContent || []));
      } catch (error) {
        console.error('Error during deck loading:', error);
      }
    }
  }, []);

  useEffect(() => {
    const handleScroll = () => {
      if (window.innerHeight + document.documentElement.scrollTop >= document.documentElement.offsetHeight - 1 &&
        hasMoreResults && !isLoading) {
        searchCards(searchKeyword, currentPage + 1);
      }
    };

    window.addEventListener('scroll', handleScroll);
    return () => window.removeEventListener('scroll', handleScroll);
  }, [searchCards, searchKeyword, currentPage, hasMoreResults, isLoading]);

  const handleSearch = (event) => {
    if (event.key === 'Enter') {
      if (/^[a-zA-Z0-9 가-힣()]*$/.test(searchKeyword)) {
        setCurrentPage(0);
        setHasMoreResults(true);
        searchCards(searchKeyword, 0);
      } else {
        showMessage('유효하지 않은 입력입니다.');
      }
    }
  };

  const showMessage = (msg) => {
    setMessage(msg);
    setTimeout(() => setMessage(''), 2000);
  };

  const sortCards = (deck) => {
    return deck.slice().sort((a, b) => {
      const imageUrlA = a.imageUrl.split('/').pop();
      const imageUrlB = b.imageUrl.split('/').pop();
      return imageUrlA.localeCompare(imageUrlB);
    });
  };

  const addCardToDeck = (imageUrl, frameType, name) => {
    const cardImageId = imageUrl.split('/').pop();
    const count = [...mainDeck, ...extraDeck].filter(card => card.imageUrl.includes(cardImageId)).length;

    if (count >= 3) {
      showMessage('같은 카드는 3장만 추가 가능합니다');
      return;
    }

    if (['link', 'fusion', 'synchro', 'xyz', 'xyz_pendulum', 'synchro_pendulum', 'fusion_pendulum'].includes(frameType)) {
      if (extraDeck.length >= 15) {
        showMessage('엑스트라 덱은 15장까지만 가능합니다.');
        return;
      }
      const newExtraDeck = sortCards([...extraDeck, { imageUrl, frameType, name }]);
      setExtraDeck(newExtraDeck);
      saveUrl(mainDeck, newExtraDeck);
    } else {
      if (mainDeck.length >= 60) {
        showMessage('메인 덱은 60장까지만 가능합니다.');
        return;
      }
      const newMainDeck = sortCards([...mainDeck, { imageUrl, frameType, name }]);
      setMainDeck(newMainDeck);
      saveUrl(newMainDeck, extraDeck);
    }
  };

  const removeCardFromDeck = useCallback((index, deckType) => {
    if (deckType === 'main') {
      setMainDeck(prevDeck => {
        const newDeck = sortCards([...prevDeck.slice(0, index), ...prevDeck.slice(index + 1)]);
        saveUrl(newDeck, extraDeck);
        return newDeck;
      });
    } else {
      setExtraDeck(prevDeck => {
        const newDeck = sortCards([...prevDeck.slice(0, index), ...prevDeck.slice(index + 1)]);
        saveUrl(mainDeck, newDeck);
        return newDeck;
      });
    }
  }, [mainDeck, extraDeck]);

  const saveUrl = (mainDeck, extraDeck) => {
    const dataObj = { cardsContent: mainDeck, extraDeckContent: extraDeck };
    const dataStr = JSON.stringify(dataObj);
    const compressed = pako.deflate(dataStr);
    const save = btoa(String.fromCharCode(...compressed));
    window.history.pushState({ data: save }, '', `?deck=${encodeURIComponent(save)}`);
  };

  const viewCardDetail = (cardName) => {
    fetch(`/cards/cardinfo?cardName=${encodeURIComponent(cardName)}`)
      .then(response => {
        if (!response.ok) throw new Error('Network response was not ok');
        return response.json();
      })
      .then(data => {
        setCardDetail(data);
        setIsExpanded(true);
      })
      .catch(error => console.error('There has been a problem with your fetch operation:', error));
  };

  const handleClick = (name, index) => {
    setIsExpanded(prev => !prev);

    if (!isExpanded) {
      viewCardDetail(name);
      if (expandedOverlayRef.current && cardRefs.current[index]) {
        expandedOverlayRef.current.style.display = 'block';
        cardRefs.current[index].classList.add('expanded');
        
      }
    } else {
      if (expandedOverlayRef.current && cardRefs.current[index]) {
        expandedOverlayRef.current.style.display = 'none';
        cardRefs.current[index].classList.remove('expanded');
        setIsExpanded(false);
        setCardDetail(null);
      }
    }
  };

  const handleMouseMove = (e, index) => {
    if (cardRefs.current[index]) {
      const x = e.nativeEvent.offsetX;
      const y = e.nativeEvent.offsetY;

      const rotateY = (-40 / 98) * x + 20;
      const rotateX = (40 / 143) * y - 26;

      const bgPosX = (x / cardRefs.current[index].clientWidth) * 100;
      const bgPosY = (y / cardRefs.current[index].clientHeight) * 100;

      overlayRefs.current[index].style.background = `radial-gradient(circle at ${bgPosX}% ${bgPosY}%, rgba(255, 255, 255, 0.8), transparent 70%)`;

      if (isExpanded) {
        cardRefs.current[index].style.transform = `translate(-50%, -50%) scale(4) perspective(350px) rotateX(${rotateX}deg) rotateY(${rotateY}deg)`;
      } else {
        cardRefs.current[index].style.transform = `perspective(350px) rotateX(${rotateX}deg) rotateY(${rotateY}deg)`;
      }
    }
  };

  const handleMouseOut = (index) => {
    if (cardRefs.current[index]) {
      overlayRefs.current[index].style.background = 'none';
      if (isExpanded) {
        cardRefs.current[index].style.transform = 'translate(-50%, -50%) scale(4)';
      } else {
        cardRefs.current[index].style.transform = 'perspective(350px) rotateY(0deg) rotateX(0deg)';
      }
    }
  };

  useEffect(() => {
    const expandedOverlay = document.querySelector('.expanded-overlay');

    const handleOverlayClick = () => {
      setIsExpanded(false);
      if (expandedOverlay) {
        expandedOverlay.style.display = 'none';
      }
      cardRefs.current.forEach(card => {
        if (card) {
          card.classList.remove('expanded');
          card.style.transform = 'perspective(350px) rotateY(0deg) rotateX(0deg)';
        }
      });
    };

    if (expandedOverlay) {
      expandedOverlay.addEventListener('click', handleOverlayClick);
    }

    return () => {
      if (expandedOverlay) {
        expandedOverlay.removeEventListener('click', handleOverlayClick);
      }
    };
  }, []);

  return (
    <div className="container">
      <div ref={expandedOverlayRef} className="expanded-overlay"></div> 
      <div className="left-container">
        <div id="title">YuGiOh Deck</div>
        <div className="description">이 웹사이트는 YuGiOh 덱 빌더입니다. 원하는 카드를 추가하고 덱을 구성해보세요!</div>
        <div className="contact-info">오류 문의 : wjdgns5488@naver.com</div>
        <button onClick={() => { setMainDeck([]); setExtraDeck([]); saveUrl([], []); window.history.pushState({}, '', '/'); }}>Reset</button>
        <div id="mainDeckLabel">메인 덱 <span>{mainDeck.length}</span></div>
        <div className="cards" id="cardsContainer">
          {mainDeck.map((card, index) => (
            <div 
              key={index} 
              className="card-container" 
              onClick={() => handleClick(card.name, index)} 
              onContextMenu={(e) => {
                e.preventDefault();
                removeCardFromDeck(index, 'main');
              }}
              onMouseMove={(e) => handleMouseMove(e, index)}
              onMouseOut={() => handleMouseOut(index)}
              ref={(el) => { cardRefs.current[index] = el; overlayRefs.current[index] = el?.querySelector('.overlay'); }}
            >
              <div className="overlay"></div>
              <div className="card" style={{ backgroundImage: `url(${card.imageUrl})` }}></div>
            </div>
          ))}
        </div>
        <div id="extraDeckLabel">엑스트라 덱 <span>{extraDeck.length}</span></div>
        <div className="cards" id="extraDeck">
          {extraDeck.map((card, index) => (
            <div 
              key={index} 
              className="card-container" 
              onClick={() => handleClick(card.name, mainDeck.length + index)}
              onContextMenu={(e) => {
                e.preventDefault();
                removeCardFromDeck(index, 'extra');
              }}
              onMouseMove={(e) => handleMouseMove(e, mainDeck.length + index)}
              onMouseOut={() => handleMouseOut(mainDeck.length + index)}
              ref={(el) => { cardRefs.current[mainDeck.length + index] = el; overlayRefs.current[mainDeck.length + index] = el?.querySelector('.overlay'); }}
            >
              <div className="overlay"></div>
              <div className="card" style={{ backgroundImage: `url(${card.imageUrl})` }}></div>
            </div>
          ))}
        </div>
      </div>
  
      <div className="right-container">
        <div className="search-container">
          <input
            type="search"
            value={searchKeyword}
            onChange={e => setSearchKeyword(e.target.value)}
            onKeyDown={handleSearch}
            placeholder="eg: Dark Magician⌕"
          />
          {isLoading && <div id="loading">Loading...</div>}
        </div>
        <div className="divider"></div>
        <div className="cards" id="searchResult">
          {searchResults.map((result, index) => (
            <div 
              key={index} 
              className="search-result-item" 
              onClick={() => addCardToDeck(result.imageUrl, result.frameType, result.name)}
            >
              <img src={`/images/${result.imageUrl.split('/').pop()}`} alt={result.name} />
              <p>{result.name}</p>
            </div>
          ))}
        </div>
      </div>
    </div>
  );  
}

export default App;
