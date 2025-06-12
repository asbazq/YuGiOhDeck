import React, { useState, useEffect, useCallback, useRef } from 'react';
import './styles/App.css';
import './styles/DeckCard.css';
import './styles/SearchBar.css';
import './styles/SearchResultItem.css';
import './styles/Message.css'
import './styles/LimitBoard.css'
import './styles/Menu.css'
import './styles/Button.css'

import pako from 'pako';
import SearchBar from './components/SearchBar';
import SearchResults from './components/SearchResults';
import DeckCard from './components/DeckCard';
import LimitBoard from './components/LimitBoard';
import Card from './classes/Card';
import { sortCards, saveUrl } from './common/deckUtils';
import alertCard from './img/black-magician-girl-card-8bit.png';
import konamiGif from './img/1749718043.gif';




function App() {
  const [mainDeck, setMainDeck] = useState([]);
  const [extraDeck, setExtraDeck] = useState([]);
  const [searchResults, setSearchResults] = useState([]);
  const [searchKeyword, setSearchKeyword] = useState('');
  const [frameType, setFrameType] = useState('');
  const [currentPage, setCurrentPage] = useState(0);
  const [isLoading, setIsLoading] = useState(false);
  const [hasMoreResults, setHasMoreResults] = useState(true);
  const [message, setMessage] = useState('');
  const [cardDetail, setCardDetail] = useState(null);
  const [isExpanded, setIsExpanded] = useState(false);
  const [expandedIndex, setExpandedIndex] = useState(null);
  const [effectsEnabled, setEffectsEnabled] = useState(true);
  const [activeBoard, setActiveBoard] = useState('deck');
  const [limitCards, setLimitCards] = useState([]);
  
 useEffect(() => {
    if (typeof window.ChannelIO === 'function') {
      window.ChannelIO('boot', {
        pluginKey: 'c04dce9d-99b7-47bb-9f9a-470519116888'
      });
    }
  }, []);


   useEffect(() => {
    const script = document.createElement("script");
    script.async = true;
    script.src = "https://www.googletagmanager.com/gtag/js?id=G-HL4HGTHXLN";
    document.head.appendChild(script);

    const inlineScript = document.createElement("script");
    inlineScript.innerHTML = `
      window.dataLayer = window.dataLayer || [];
      function gtag(){dataLayer.push(arguments);}
      gtag('js', new Date());
      gtag('config', 'G-HL4HGTHXLN');
    `;
    document.head.appendChild(inlineScript);
  }, []);

   useEffect(() => {
    if (activeBoard === 'limit' && limitCards.length === 0) {
      setMessage('로딩중...');
      fetch('/cards/limit')
        .then(res => res.json())
        .then(data => {
          setLimitCards(data);
          setMessage('');
        })
        .catch(err => {
          console.error('limit fetch error', err);
          showMessage('불러오기 실패');
        });
    }
  }, [activeBoard, limitCards.length]);
  
  const cardRefs = useRef([]);
  const overlayRefs = useRef([]);
  const expandedOverlayRef = useRef(null);
  const expandedIndexRef = useRef(null);
  const isAnimatingRef = useRef(false);

  const searchCards = useCallback(async (keyWord, frame, page) => {
    if (!hasMoreResults || isLoading) return;

    setIsLoading(true);
    try {
      const response = await fetch(`/cards/search?keyWord=${encodeURIComponent(keyWord)}&frameType=${encodeURIComponent(frame)}&page=${page}&size=24`);
      if (!response.ok) throw new Error('Network response was not ok');
      const data = await response.json();

      const resultsWithInfo = await Promise.all(
        data.content.map(async (result) => {
          try {
            const infoRes = await fetch(`/cards/cardinfo?cardName=${encodeURIComponent(result.name)}`);
            if (!infoRes.ok) throw new Error('info error');
            const info = await infoRes.json();
            return { ...result, restrictionType: (info.restrictionType || 'unlimited').toLowerCase() };
          } catch {
            return { ...result, restrictionType: 'unlimited' };
          }
        })
      );

      setSearchResults(prevResults => page === 0 ? resultsWithInfo : [...prevResults, ...resultsWithInfo]);
      setHasMoreResults(!data.last);
      setCurrentPage(data.number);
    } catch (error) {
      console.error('Error:', error);
    } finally {
      setIsLoading(false);
    }
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
        searchCards(searchKeyword, frameType, currentPage + 1);
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
        searchCards(searchKeyword, frameType, 0);
      } else {
        showMessage('유효하지 않은 입력입니다.');
      }
    }
  };

  const showMessage = (msg) => {
    setMessage(msg);
    setTimeout(() => setMessage(''), 2300);
  };

  const addCardToDeck = async (imageUrl, frameType, name) => {
    const response = await fetch(`/cards/cardinfo?cardName=${encodeURIComponent(name)}`);
    if (!response.ok) {
      showMessage('카드 정보를 불러오지 못했습니다.');
      return;
    }
    const info = await response.json();
    const restriction = (info.restrictionType || 'unlimited').toLowerCase();

    let limit = 3;
    let resType = '';
    if (restriction === 'forbidden') {
      showMessage('금지 카드입니다.');
      return;
    } else if (restriction === 'limited') {
      limit = 1;
      resType = '제한'
    } else if (restriction === 'semilimited') {
      limit = 2;
      resType = '준제한'
    }
    const cardImageId = imageUrl.split('/').pop();
    const count = [...mainDeck, ...extraDeck].filter(card => card.imageUrl.includes(cardImageId)).length;

    if (count >= limit) {
      showMessage(`같은 ${resType} 카드는 ${limit}장만 추가 가능합니다`);
      return;
    }
    
    const cardData = new Card({
      imageUrl,
      frameType,
      name,
      restrictionType: restriction,
    });


    if (['link', 'fusion', 'synchro', 'xyz', 'xyz_pendulum', 'synchro_pendulum', 'fusion_pendulum'].includes(frameType)) {
      if (extraDeck.length >= 15) {
        showMessage('엑스트라 덱은 15장까지만 가능합니다.');
        return;
      }
      const newExtraDeck = sortCards([...extraDeck, cardData]);
      setExtraDeck(newExtraDeck);
      saveUrl(mainDeck, newExtraDeck);
    } else {
      if (mainDeck.length >= 60) {
        showMessage('메인 덱은 60장까지만 가능합니다.');
        return;
      }
      const newMainDeck = sortCards([...mainDeck, cardData]);
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

  const handleOverlayClick = () => {
    if (isAnimatingRef.current || expandedIndexRef.current === null) return;
    const card = cardRefs.current[expandedIndexRef.current];
    if (!card) return;
  if (expandedOverlayRef.current) {
    expandedOverlayRef.current.style.display = 'none';
  }

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

  const [isMenuOpen, setIsMenuOpen] = useState(false);

  return (
    <>
    <button
      className="menu-button"
      onClick={() => setIsMenuOpen(true)}
      aria-label="메뉴 열기"
    >
      <span></span>
      <span></span>
      <span></span>
    </button>
    <div className={`menu-overlay ${isMenuOpen ? 'open' : ''}`} onClick={() => setIsMenuOpen(false)}></div>
    <div className={`side-menu ${isMenuOpen ? 'open' : ''}`}>
      <div className="board-switch">
        <button onClick={() => { setActiveBoard('limit'); setIsMenuOpen(false); }}>리미트 레귤레이션</button>
        <button onClick={() => { setActiveBoard('deck'); setIsMenuOpen(false); }}>덱 빌딩</button>
      </div>
    </div>
      <div
        id="msgWrap"
        style={{ display: message ? 'flex' : 'none' }}
        onClick={() => {
          if (message === 'Konami code!') {
            window.open(
              'https://gall.dcinside.com/mgallery/board/view/?id=pixelart&no=23715',
              '_blank'
            );
          }
        }}
      >
        <div id="msgBubble" className={message === 'Konami code!' ? 'konami' : ''}>
          {message}
        </div>
        <img
          src={message === 'Konami code!' ? konamiGif : alertCard}
          alt="alert"
          className={`msgImg${message === 'Konami code!' ? ' konami' : ''}`}
        />
      </div>
    {activeBoard === 'deck' && (
    <div className="container">
      <div className="contact-info">
      </div>
      <button
        id="resetButton"
        className="action-button"
        onClick={() => {
          setMainDeck([]); setExtraDeck([]); saveUrl([], []); window.history.pushState({}, '', '/');
          }}
      >
        초기화
      </button>
      <button
        id="effectButton"
        className={`action-button ${effectsEnabled ? 'on' : 'off'}`}
        onClick={() => setEffectsEnabled(prev => !prev)}
      >
        {effectsEnabled ? '이펙트' : '이펙트'}
      </button>
     <div
      ref={expandedOverlayRef}
      className="expanded-overlay"
      onClick={handleOverlayClick}
    ></div>
      {isExpanded && cardDetail && (
        <div className="card-detail-container" style={{ display: 'block' }}>
          <div id="cardDetailContainer">{cardDetail.name}</div>
        </div>
      )}
      {isExpanded && cardDetail && (
        <div className="card-detail-desc-container" style={{ display: 'block' }}>
          <div className="race-text" style={{ marginBottom: '4px', fontWeight: 'bold' }}>
            [{cardDetail.race}]
          </div>
          {/* 2. 설명(description) */}
          <div className="desc-text" style={{ whiteSpace: 'pre-line' }}>
            {cardDetail.korDesc}
          </div>
        </div>
      )}
      <div className="left-container">
        <footer className="app-footer">
          <small>
            © 1996 Kazuki Takahashi.<br/>
            © 2025 Konami Digital Entertainment Co., Ltd. All rights reserved.<br/>
            “YU-GI-OH!” and associated logos are registered trademarks of Konami Digital Entertainment Co., Ltd.<br/>
            Card illustration © Kazuki Takahashi / Konami Digital Entertainment Co., Ltd. All rights reserved.
          </small>
        </footer>
        <div id="title">YuGiOh Deck</div>
        <div className="description">↑↑↓↓←→←→BA</div>
        <div className="contact-info">오류 문의 : 아래 채널톡</div>
        <div id="mainDeckLabel">메인 덱 <span>{mainDeck.length}</span></div>
        <div className="cards" id="cardsContainer">
          {mainDeck.map((card, index) => (
             <DeckCard
              key={`${card.imageUrl.split('/').pop()}-${index}`}
              card={card}
              index={index}
              cardRefs={cardRefs}
              overlayRefs={overlayRefs}
              onClick={handleClick}
              onContextMenu={(e) => {
                e.preventDefault();
                if (expandedIndexRef.current !== null) return;
                removeCardFromDeck(index, 'main');
              }}
              onMouseMove={handleMouseMove}
              onMouseOut={handleMouseOut}
            />
          ))}
        </div>
        <div id="extraDeckLabel">엑스트라 덱 <span>{extraDeck.length}</span></div>
        <div className="cards" id="extraDeck">
          {extraDeck.map((card, index) => (
             <DeckCard
              key={`${card.imageUrl.split('/').pop()}-extra-${index}`}
              card={card}
              index={mainDeck.length + index}
              cardRefs={cardRefs}
              overlayRefs={overlayRefs}
              onClick={handleClick}
              onContextMenu={(e) => {
                e.preventDefault();
                if (expandedIndexRef.current !== null) return;
                removeCardFromDeck(index, 'extra');
              }}
              onMouseMove={handleMouseMove}
              onMouseOut={handleMouseOut}
            />
          ))}
        </div>
      </div>
      <div className="right-container">
        <SearchBar
          searchKeyword={searchKeyword}
          onChange={setSearchKeyword}
          onSearch={handleSearch}
          isLoading={isLoading}
          frameType={frameType}
          onFrameChange={setFrameType}
        />
        <div className="divider"></div>
        <SearchResults results={searchResults} addCardToDeck={addCardToDeck} />
      </div>
    </div>
   )}
    {activeBoard === 'limit' && (
      <div className="container">
       <LimitBoard cards={limitCards} showMessage={showMessage} />
      </div>
    )}
    </>
  );
}

export default App;
