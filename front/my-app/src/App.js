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
  const [expandedIndex, setExpandedIndex] = useState(null);
  const [effectsEnabled, setEffectsEnabled] = useState(true);
  
  
  const cardRefs = useRef([]);
  const overlayRefs = useRef([]);
  const expandedOverlayRef = useRef(null);
  const expandedIndexRef = useRef(null);
  const isAnimatingRef = useRef(false);

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
    if (isAnimatingRef.current) return;
    const card = cardRefs.current[index];
    if (!card) return;

    if (expandedIndex === null) {
      viewCardDetail(name);
       if (expandedOverlayRef.current) {
        expandedOverlayRef.current.style.display = 'block';
      }

      const rect = card.getBoundingClientRect();
      card.dataset.origTop = rect.top;
      card.dataset.origLeft = rect.left;
      card.dataset.origScrollY = window.scrollY;

      card.style.position = 'fixed';
      card.style.top = rect.top + 'px';
      card.style.left = rect.left + 'px';

      isAnimatingRef.current = true;
      requestAnimationFrame(() => {
        card.classList.add('expanded');
        card.style.top = '';
        card.style.left = '';
        card.style.transform = 'translate(-50%, -50%) scale(4)';
      });

      card.addEventListener('transitionend', function handler() {
        isAnimatingRef.current = false;
        card.removeEventListener('transitionend', handler);
      });

      expandedIndexRef.current = index;
      setExpandedIndex(index);
      setIsExpanded(true);
    } else {
      if (expandedIndexRef.current !== index) return;
      if (expandedOverlayRef.current) {
        expandedOverlayRef.current.style.display = 'none';
        
      }

      const origTop = parseFloat(card.dataset.origTop || 0);
      const origLeft = parseFloat(card.dataset.origLeft || 0);
      const origScrollY = parseFloat(card.dataset.origScrollY || 0);
      const finalTop = origTop + (origScrollY - window.scrollY);

      isAnimatingRef.current = true;
      card.classList.remove('expanded');
      card.style.zIndex = '10'
      card.style.top = finalTop + 'px';
      card.style.left = origLeft + 'px';
      card.style.transform = 'translate(0, 0) scale(1)';

      card.addEventListener('transitionend', function handler() {
        card.style.position = 'relative';
        card.style.top = '';
        card.style.left = '';
        card.style.transform = '';
        card.style.zIndex = '';
        expandedIndexRef.current = null;
        setExpandedIndex(null);
        setIsExpanded(false);
        setCardDetail(null);
        isAnimatingRef.current = false;
        card.removeEventListener('transitionend', handler);
      });
    }
  };

  const handleMouseMove = (e, index) => {
    if (isAnimatingRef.current) return;
    if (cardRefs.current[index]) {
      const x = e.nativeEvent.offsetX;
      const y = e.nativeEvent.offsetY;

      const bgPosX = (x / cardRefs.current[index].clientWidth) * 100;
      const bgPosY = (y / cardRefs.current[index].clientHeight) * 100;

      if (effectsEnabled) {
        overlayRefs.current[index].style.background = `radial-gradient(circle at ${bgPosX}% ${bgPosY}%, rgba(255, 255, 255, 0.8), transparent 70%)`;
      } else {
        overlayRefs.current[index].style.background = 'none';
      }

      if (effectsEnabled) {
        const rotateY = (-40 / 98) * x + 20;
        const rotateX = (40 / 143) * y - 26;

        if (isExpanded) {
          cardRefs.current[index].style.transform = `translate(-50%, -50%) scale(4) perspective(350px) rotateX(${rotateX}deg) rotateY(${rotateY}deg)`;
        } else {
          cardRefs.current[index].style.transform = `perspective(350px) rotateX(${rotateX}deg) rotateY(${rotateY}deg)`;
        }
      } else {
        if (isExpanded) {
          cardRefs.current[index].style.transform = 'translate(-50%, -50%) scale(4)';
        } else {
          cardRefs.current[index].style.transform = '';
        }
      } 
    }
  };

  const handleMouseOut = (index) => {
     if (isAnimatingRef.current) return;
    if (cardRefs.current[index]) {
      overlayRefs.current[index].style.background = 'none';
      if (isExpanded) {
        cardRefs.current[index].style.transform = 'translate(-50%, -50%) scale(4)';
      } else {
        cardRefs.current[index].style.transform = effectsEnabled ? 'perspective(350px) rotateY(0deg) rotateX(0deg)' : '';
      }
    }
  };

  useEffect(() => {
    const expandedOverlay = document.querySelector('.expanded-overlay');

    const handleOverlayClick = () => {
      if (isAnimatingRef.current || expandedIndexRef.current === null) return;
      const card = cardRefs.current[expandedIndexRef.current];
      if (!card) return;

      expandedOverlay.style.display = 'none';

      const origTop = parseFloat(card.dataset.origTop || 0);
      const origLeft = parseFloat(card.dataset.origLeft || 0);
      const origScrollY = parseFloat(card.dataset.origScrollY || 0);
      const finalTop = origTop + (origScrollY - window.scrollY);

      isAnimatingRef.current = true;
      card.classList.remove('expanded');
      card.style.zIndex = '10';
      card.style.top = finalTop + 'px';
      card.style.left = origLeft + 'px';
      card.style.transform = 'translate(0, 0) scale(1)';

      card.addEventListener('transitionend', function handler() {
        card.style.position = 'relative';
        card.style.top = '';
        card.style.left = '';
        card.style.transform = '';
        card.style.zIndex = '';
        expandedIndexRef.current = null;
        setExpandedIndex(null);
        setIsExpanded(false);
        setCardDetail(null);
        isAnimatingRef.current = false;
        card.removeEventListener('transitionend', handler);
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

  useEffect(() => {
    let scrollTimeout;
    let lastScrollY = window.scrollY;

    const handleScrollFollow = () => {
      if (expandedIndexRef.current !== null) {
        const card = cardRefs.current[expandedIndexRef.current];
        if (card) {
          const currentScrollY = window.scrollY;
          const scrollDiff = currentScrollY - lastScrollY;

          const matrix = new DOMMatrix(window.getComputedStyle(card).transform);
          const currentY = matrix.m42;
          const direction = scrollDiff < 0 ? 'down' : 'up';
          let newY;
          if (direction === 'up') {
            newY = currentY - Math.abs(scrollDiff) * 10;
          } else {
            newY = currentY + Math.abs(scrollDiff) * 10;
          }

          card.style.transform = `translate(-50%, ${newY}px) scale(4)`;

          clearTimeout(scrollTimeout);
          scrollTimeout = setTimeout(() => {
            if (expandedIndexRef.current !== null) {
              card.style.transform = 'translate(-50%, -50%) scale(4)';
            }
          }, 200);

          lastScrollY = currentScrollY;
        }
      } else {
        lastScrollY = window.scrollY;
      }
    };

    window.addEventListener('scroll', handleScrollFollow);
    return () => {
      window.removeEventListener('scroll', handleScrollFollow);
      clearTimeout(scrollTimeout);
    };
  }, []);

  return (
    <div className="container">
    <div ref={expandedOverlayRef} className="expanded-overlay"></div>
      {isExpanded && cardDetail && (
        <div className="card-detail-container" style={{ display: 'block' }}>
          <div id="cardDetailContainer">{cardDetail.name}</div>
        </div>
      )}
      {isExpanded && cardDetail && (
        <div className="card-detail-desc-container" style={{ display: 'block' }}>
          {cardDetail.korDesc}
        </div>
      )}
      <div className="left-container">
        <div id="title">YuGiOh Deck</div>
        <div className="description">이 웹사이트는 YuGiOh 덱 빌더입니다. 원하는 카드를 추가하고 덱을 구성해보세요!</div>
        <div className="contact-info">오류 문의 : wjdgns5488@naver.com</div>
       <button id="resetButton" className="action-button" onClick={() => { setMainDeck([]); setExtraDeck([]); saveUrl([], []); window.history.pushState({}, '', '/'); }}>Reset</button>
        <button id="effectButton" className="action-button" onClick={() => setEffectsEnabled(!effectsEnabled)}>
          {effectsEnabled ? '이펙트 OFF' : '이펙트 ON'}
        </button>
        <div id="mainDeckLabel">메인 덱 <span>{mainDeck.length}</span></div>
        <div className="cards" id="cardsContainer">
          {mainDeck.map((card, index) => (
             <React.Fragment key={`${card.imageUrl.split('/').pop()}-${index}`}>
            <div className="deck-card-wrapper">
              <div
                className="card-container"
                onClick={() => handleClick(card.name, index)}
                onContextMenu={(e) => {
                  e.preventDefault();
                  if (expandedIndexRef.current !== null) return;
                  removeCardFromDeck(index, 'main');
                }}
                onMouseMove={(e) => handleMouseMove(e, index)}
                onMouseOut={() => handleMouseOut(index)}
                ref={(el) => { cardRefs.current[index] = el; overlayRefs.current[index] = el?.querySelector('.overlay'); }}
              >
                <div className="overlay"></div>
                <div className="card" style={{ backgroundImage: `url(${card.imageUrl})` }}></div>
              </div>
              <p className="deck-card-name">{card.name}</p>
            </div>
             {expandedIndex === index && (
              <div className="deck-card-wrapper placeholder-wrapper">
                <div className="card-container placeholder"></div>
                <p className="deck-card-name">{card.name}</p>
              </div>
            )}
            </React.Fragment>
          ))}
        </div>
        <div id="extraDeckLabel">엑스트라 덱 <span>{extraDeck.length}</span></div>
        <div className="cards" id="extraDeck">
          {extraDeck.map((card, index) => (
             <React.Fragment key={`${card.imageUrl.split('/').pop()}-extra-${index}`}>
            <div className="deck-card-wrapper">
              <div
                className="card-container"
                onClick={() => handleClick(card.name, mainDeck.length + index)}
                onContextMenu={(e) => {
                  e.preventDefault();
                  if (expandedIndexRef.current !== null) return;
                  removeCardFromDeck(index, 'extra');
                }}
                onMouseMove={(e) => handleMouseMove(e, mainDeck.length + index)}
                onMouseOut={() => handleMouseOut(mainDeck.length + index)}
                ref={(el) => { cardRefs.current[mainDeck.length + index] = el; overlayRefs.current[mainDeck.length + index] = el?.querySelector('.overlay'); }}
              >
                <div className="overlay"></div>
                <div className="card" style={{ backgroundImage: `url(${card.imageUrl})` }}></div>
              </div>
              <p className="deck-card-name">{card.name}</p>
            </div>
              {expandedIndex === mainDeck.length + index && (
              <div className="deck-card-wrapper placeholder-wrapper">
                <div className="card-container placeholder"></div>
                <p className="deck-card-name">{card.name}</p>
              </div>
            )}
            </React.Fragment>
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
              key={`${result.imageUrl.split('/').pop()}-${index}`}
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
