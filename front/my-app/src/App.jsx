import React, { useState, useEffect, useCallback, useRef } from 'react';
import { trackEvent, sendPageView } from './utils/analytics';
import pako from 'pako';
import { useNavigate } from 'react-router-dom';

import './styles/App.css';
import './styles/DeckCard.css';
import './styles/SearchBar.css';
import './styles/SearchResultItem.css';
import './styles/Message.css'
// import './styles/LimitBoard.css'
import './styles/Menu.css'
import './styles/Button.css'
import './styles/MobileSearch.css';

import SearchBar from './components/SearchBar';
import AICardRecognizerModal from "./components/AICardRecognizerModal";
import SearchResults from './components/SearchResults';
import DeckCard from './components/DeckCard';
// import LimitBoard from './components/LimitBoard';
import OrientationModal from './components/OrientationModal';
import BanlistNoticeModal from './components/BanlistNoticeModal';

import Card from './classes/Card';

import { sortCards, saveUrl } from './common/deckUtils';
import { localImagePath, filenameOf } from './common/imagePath';

import alertCard from './img/black-magician-girl-card-8bit.png';
import konamiGif from './img/1750263964.gif';

const EXTRA_FRAMES = new Set([
  'link','fusion','synchro','xyz',
  'xyz_pendulum','synchro_pendulum','fusion_pendulum'
]);

const isExtraFrame = (f) => EXTRA_FRAMES.has(f);
const getDeckType = (f) => (isExtraFrame(f) ? 'extra' : 'main');
const getScaleFactor = () => (window.innerWidth <= 768 ? 3 : 4);


function App() {
  // 덱에 들어간 카드 상태 (메인/엑스트라)
  const [mainDeck, setMainDeck] = useState([]);
  const [extraDeck, setExtraDeck] = useState([]);
  // 검색결과, 검색어, 프레임 타입
  const [searchResults, setSearchResults] = useState([]);
  const [searchKeyword, setSearchKeyword] = useState('');
  const [frameType, setFrameType] = useState('');
  // 검색 페이지네이션 및 로딩 상태
  const [currentPage, setCurrentPage] = useState(0);
  const [isLoading, setIsLoading] = useState(false);
  const [hasMoreResults, setHasMoreResults] = useState(true);
  // 메시지
  const [message, setMessage] = useState('');
  // 카드 상세보기 및 확대 상태
  const [cardDetail, setCardDetail] = useState(null);
  const [isExpanded, setIsExpanded] = useState(false);
  const [expandedIndex, setExpandedIndex] = useState(null);
  // 카드 회전/광택 효과 on/off
  const [effectsEnabled, setEffectsEnabled] = useState(true);
  // 'deck' or 'limit'
  // const [activeBoard, setActiveBoard] = useState('deck');
  // 모바일 여부 및 모바일 검색창 오픈 여부
  const [isMobile, setIsMobile] = useState(window.innerWidth < 768);
  const [isSearchOpen, setIsSearchOpen] = useState(false);
  const [searchButtonTop, setSearchButtonTop] = useState(190);
  const orientationRef = useRef({ beta: 0, gamma: 0 });
  const activeTouchIndexRef = useRef(null);
  const longPressTimeoutRef = useRef(null);
  const [orientationPermissionGranted, setOrientationPermissionGranted] = useState(false);
  const [isOrientationModalOpen, setIsOrientationModalOpen] = useState(false);
  const orientationRequestRef = useRef(false);
  const searchPanelRef = useRef(null);
  const navigate = useNavigate();
  const [aiOpen, setAiOpen] = useState(false);
  const [noticeOpen, setNoticeOpen] = useState(false);
  const [changes, setChanges] = useState([]);

  // 썸네일 URL 제공: 카드 이름이 아니라 id/파일명일 경우엔 맞게 바꿔주세요.
  const getThumbUrl = (nameOrId) => {
    // 서버에서 DTO에 id를 함께 준다면 그걸 쓰는 게 제일 안전합니다.
    const file = filenameOf(nameOrId) ?? `${encodeURIComponent(nameOrId)}.jpg`;
    return `/images/small/${file}`;
  };

  useEffect(() => {
    (async () => {
      try{
        const res = await fetch('/cards/current', { cache: 'no-store' });
        if(!res.ok){
          console.warn('changes fetch failed', res.status);
          // 디버깅: 데이터가 없어도 일단 켜서 모달이 보이는지 확인
          setChanges([]);
          setNoticeOpen(false);
          return;
        }
        // 서버가 배열 그대로 주는지, {changes:[...]}로 주는지 확인
        const json = await res.json();
        const list = Array.isArray(json) ? json : (json?.changes ?? []);
        console.log('changes loaded:', list);
        setChanges(list);
        setNoticeOpen(list.length > 0); // 변경 있을 때만 자동 오픈
      }catch(err){
        console.error('changes fetch error', err);
        // 디버깅용으로 강제 오픈해 시각적으로는 뜨는지 확인
        setChanges([]);
        setNoticeOpen(false);
      }
    })();
  }, []);

  useEffect(() => {
    if (typeof DeviceOrientationEvent !== 'undefined' &&
        typeof DeviceOrientationEvent.requestPermission !== 'function') {
      setOrientationPermissionGranted(true);
    }
  }, []);

 useEffect(() => {
    if (typeof window.ChannelIO === 'function') {
      window.ChannelIO('boot', {
        pluginKey: 'c04dce9d-99b7-47bb-9f9a-470519116888'
      });
    }
  }, []);

  useEffect(() => {
    const handleResize = () => {
      const mobile = window.innerWidth < 768;
      setIsMobile(mobile);
      if (!mobile) {
        setIsSearchOpen(false);
      }
    };
    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, []);

  // useEffect(() => {
  //   if (activeBoard !== 'deck') {
  //     setIsSearchOpen(false);
  //   }
  // }, [activeBoard]);

  useEffect(() => {
    const handleScrollBtn = () => {
      const offset = 190 - window.scrollY;
      setSearchButtonTop(offset > 20 ? offset : 20);
    };
    if (isMobile) {
      handleScrollBtn();
      window.addEventListener('scroll', handleScrollBtn);
    } else {
      setSearchButtonTop(190);
    }
    return () => window.removeEventListener('scroll', handleScrollBtn);
  }, [isMobile]);

useEffect(() => {
  const leftContainer = document.querySelector('.left-container');

  // ✅ 스크롤 잠금 조건: 모바일 검색 패널 열림 OR AI 모달 열림
  const shouldLock =
    (isMobile && isSearchOpen) || aiOpen;

  if (shouldLock) {
    // 현재 스크롤 위치 저장
    const scrollY = window.scrollY || document.documentElement.scrollTop || 0;
    document.body.dataset.scrollY = String(scrollY);

    // 레이아웃 점프 없이 스크롤 잠금 (iOS 사파리 대응)
    document.body.style.position = 'fixed';
    document.body.style.top = `-${scrollY}px`;
    document.body.style.left = '0';
    document.body.style.right = '0';
    document.body.style.width = '100%';
    document.body.style.overflow = 'hidden';

    // 내부 스크롤도 차단
    if (leftContainer) leftContainer.style.overflow = 'hidden';
  } else {
    // 원복 + 저장된 스크롤 위치로 복귀
    const y = parseInt(document.body.dataset.scrollY || '0', 10);
    document.body.style.position = '';
    document.body.style.top = '';
    document.body.style.left = '';
    document.body.style.right = '';
    document.body.style.width = '';
    document.body.style.overflow = '';
    delete document.body.dataset.scrollY;
    window.scrollTo(0, y);

    if (leftContainer) leftContainer.style.overflow = '';
  }

  // 언마운트 안전장치
  return () => {
    document.body.style.position = '';
    document.body.style.top = '';
    document.body.style.left = '';
    document.body.style.right = '';
    document.body.style.width = '';
    document.body.style.overflow = '';
    delete document.body.dataset.scrollY;
  };
}, [isMobile, isSearchOpen, aiOpen]);
  
  useEffect(() => {
    sendPageView(window.location.pathname);
  }, []);

  const cardRefs = useRef([]);
  const overlayRefs = useRef([]);
  const expandedOverlayRef = useRef(null);
  const expandedIndexRef = useRef(null);
  const isAnimatingRef = useRef(false);

  const searchCards = useCallback(async (keyWord, frame, page) => {
    if (!hasMoreResults || isLoading) return;

    const size = isMobile ? 18 : 25;
    setIsLoading(true);
    try {
      const response = await fetch(`/cards/search?keyWord=${encodeURIComponent(keyWord)}&frameType=${encodeURIComponent(frame)}&page=${page}&size=${size}`);
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

      const withLocalImages = resultsWithInfo.map(r => {
        const idOrFile = r.id ?? filenameOf(r.imageUrl) ?? filenameOf(r.imageUrlSmall);
        return {
          ...r,
          imageUrlSmall: localImagePath(idOrFile, 'small'), // 목록/썸네일
          imageUrl:      localImagePath(idOrFile, 'large'), // 상세/확대
        };
      });

      setSearchResults(prev => page === 0 ? withLocalImages : [...prev, ...withLocalImages]);
      setHasMoreResults(!data.last);
      setCurrentPage(data.number);
    } catch (error) {
      console.error('Error:', error);
    } finally {
      setIsLoading(false);
    }
  }, [hasMoreResults, isLoading, isMobile]);

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
      const percent = Math.round(
        window.scrollY /
        (document.body.scrollHeight - window.innerHeight) *
        100
      );
      trackEvent('scroll_depth', { percent_scrolled: percent });
      if (window.innerHeight + document.documentElement.scrollTop >= document.documentElement.offsetHeight - 1 &&
        hasMoreResults && !isLoading) {
        searchCards(searchKeyword, frameType, currentPage + 1);
      }
    };

    window.addEventListener('scroll', handleScroll);
    return () => window.removeEventListener('scroll', handleScroll);
  }, [searchCards, searchKeyword, frameType, currentPage, hasMoreResults, isLoading]);

  const handleSearch = (event) => {
    if (event.key === 'Enter') {
      if (/^[a-zA-Z0-9 가-힣()]*$/.test(searchKeyword)) {
        setCurrentPage(0);
        setHasMoreResults(true);
        searchCards(searchKeyword, frameType, 0);
        trackEvent('search', {
          search_term: searchKeyword,
          result_count: isLoading ? 0 : searchResults.length
        });
      } else {
        showMessage('유효하지 않은 입력입니다.');
      }
    }
  };

  const showMessage = (msg) => {
    setMessage(msg);
    setTimeout(() => setMessage(''), 2300);
  };

  const handlePanelScroll = () => {
    const el = searchPanelRef.current;
    if (
      el &&
      hasMoreResults &&
      !isLoading &&
      el.scrollTop + el.clientHeight >= el.scrollHeight - 1
    ) {
      searchCards(searchKeyword, frameType, currentPage + 1);
    }
  };

const copyUrl = () => {
  const url = window.location.href;

  if (navigator.clipboard && window.isSecureContext) {
    // HTTPS/localhost 환경
    navigator.clipboard.writeText(url)
      .then(() => {
        showMessage('주소가 복사되었습니다.');
        trackEvent('share_deck', { deck_url: window.location.search });
      })
      .catch(() => showMessage('복사 실패'));
  } else {
    // HTTP 환경 폴백
    const textarea = document.createElement('textarea');
    textarea.value = url;
    // 화면에 보이지 않게
    textarea.style.position = 'fixed';
    textarea.style.top = '0';
    textarea.style.left = '0';
    textarea.style.width = '1px';
    textarea.style.height = '1px';
    textarea.style.opacity = '0';
    document.body.appendChild(textarea);

    textarea.select();
    try {
      const successful = document.execCommand('copy');
      if (successful) {
        showMessage('주소가 복사되었습니다.');
        trackEvent('share_deck', { deck_url: window.location.search });
      } else {
        throw new Error('execCommand 실패');
      }
    } catch (err) {
      console.error('Clipboard 폴백 에러:', err);
      showMessage('복사 실패');
    }

    document.body.removeChild(textarea);
  }
};

  const addCardToDeck = async (imageUrl, frameType, name) => {
    const response = await fetch(`/cards/cardinfo?cardName=${encodeURIComponent(name)}`);
    if (!response.ok) {
      showMessage('카드 정보를 불러오지 못했습니다.');
      return;
    }
    const info = await response.json();
    const restriction = (info.restrictionType || 'unlimited').toLowerCase();
    const effectiveFrameType = (frameType || info.frameType || info.frame_type || '').toLowerCase();
    const deckType = getDeckType(effectiveFrameType);      // ✅ 여기서 최종 판단

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

    const idOrFile     = info?.id ?? filenameOf(imageUrl);      // 넘어온 small에서 파일명 추출
    const imageUrlSmall = localImagePath(idOrFile, 'small');    // 로컬 small
    const imageUrlLarge = localImagePath(idOrFile, 'large');    // 로컬 large

    // 중복 체크는 id로
    const cardId = info?.id ?? filenameOf(imageUrlLarge);
    const count = [...mainDeck, ...extraDeck].filter(c => (c.id ?? filenameOf(c.imageUrl)) === String(cardId)).length;

    if (count >= limit) {
      showMessage(`같은 ${resType} 카드는 ${limit}장만 추가 가능합니다`);
      return;
    }
    
    const cardData = new Card({
      id: info?.id,                   // 가능하면 id 저장(중복 판별용)
      imageUrl:      imageUrlLarge,   // 상세/확대: large
      imageUrlSmall: imageUrlSmall,   // 목록/썸네일: small
      frameType: effectiveFrameType,
      name,
      restrictionType: restriction
    });

    // 덱 용량 체크
    if (deckType === 'extra' && extraDeck.length >= 15) {
      showMessage('엑스트라 덱은 15장까지만 가능합니다.');
      return;
    }
    if (deckType === 'main' && mainDeck.length >= 60) {
      showMessage('메인 덱은 60장까지만 가능합니다.');
      return;
    }

    // 공통 로직
    if (deckType === 'extra') {
      const newExtra = sortCards([...extraDeck, cardData]);
      setExtraDeck(newExtra);
      saveUrl(mainDeck, newExtra);
    } else {
      const newMain = sortCards([...mainDeck, cardData]);
      setMainDeck(newMain);
      saveUrl(newMain, extraDeck);
    }
    trackEvent('add_to_deck', { card_name: name, deck_type: deckType });
  };

  const removeCardFromDeck = useCallback((index, deckType) => {
    if (deckType === 'main') {
      setMainDeck(prevDeck => {
        const newDeck = sortCards([...prevDeck.slice(0, index), ...prevDeck.slice(index + 1)]);
        saveUrl(newDeck, extraDeck);
        trackEvent('remove_from_deck', {
          card_name: prevDeck[index].name,
          deck_type: 'main'
        });
        return newDeck;
      });
    } else {
      setExtraDeck(prevDeck => {
        const newDeck = sortCards([...prevDeck.slice(0, index), ...prevDeck.slice(index + 1)]);
        saveUrl(mainDeck, newDeck);
        trackEvent('remove_from_deck', {
          card_name: prevDeck[index].name,
          deck_type: 'extra'
        });
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
        trackEvent('view_card_detail', {
          card_name: data.name
        });
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
        card.style.transform = `translate(-50%, -50%) scale(${getScaleFactor()})`;
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
      if (isMobile) {
        handleOverlayClick();
        return;
      }
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

  const applyCardEffect = useCallback((x, y, index) => {
    if (!cardRefs.current[index]) return;
    if (isMobile && !isExpanded) return;
    const width = cardRefs.current[index].clientWidth;
    const height = cardRefs.current[index].clientHeight;

    const bgPosX = (x / width) * 100;
    const bgPosY = (y / height) * 100;

    if (effectsEnabled) {
      overlayRefs.current[index].style.background = `radial-gradient(circle at ${bgPosX}% ${bgPosY}%, rgba(255, 255, 255, 0.8), transparent 70%)`;
    } else {
      overlayRefs.current[index].style.background = 'none';
    }

    if (effectsEnabled) {
      const rotateY = (-40 / 98) * x + 20;
      const rotateX = (40 / 143) * y - 26;

      if (isExpanded) {
        const scaleFactor = getScaleFactor();
        cardRefs.current[index].style.transform = `translate(-50%, -50%) scale(${scaleFactor}) perspective(350px) rotateX(${rotateX}deg) rotateY(${rotateY}deg)`;
      } else {
        cardRefs.current[index].style.transform = `perspective(350px) rotateX(${rotateX}deg) rotateY(${rotateY}deg)`;
      }
    } else {
      if (isExpanded) {
        const scaleFactor = getScaleFactor();
        cardRefs.current[index].style.transform = `translate(-50%, -50%) scale(${scaleFactor})`;
      } else {
        cardRefs.current[index].style.transform = '';
      }
    }
  }, [isMobile, isExpanded, effectsEnabled]);

  const handleMouseMove = (e, index) => {
    if (isAnimatingRef.current) return;
    if (cardRefs.current[index]) {
      applyCardEffect(e.nativeEvent.offsetX, e.nativeEvent.offsetY, index);
    }
  };


  const handleMouseOut = (index) => {
    if (isAnimatingRef.current) return;
    const node = cardRefs.current?.[index];
    const overlay = overlayRefs.current?.[index];
    if (!node) return;                // 노드가 없으면 조용히 종료
    if (overlay) overlay.style.background = 'none';

    if (isExpanded) {
      node.style.transform = `translate(-50%, -50%) scale(${getScaleFactor()})`;
    } else {
      node.style.transform = effectsEnabled
        ? 'perspective(350px) rotateY(0deg) rotateX(0deg)'
        : '';
    }
  };

const requestOrientationPermission = useCallback(async () => {
    if (orientationPermissionGranted || orientationRequestRef.current) return;
    orientationRequestRef.current = true;
    let granted = false;

    if (typeof DeviceOrientationEvent !== 'undefined' &&
        typeof DeviceOrientationEvent.requestPermission === 'function') {
      try {
        const res = await DeviceOrientationEvent.requestPermission();
        if (res === 'granted') {
          granted = true;
        }
      } catch (err) {
        console.error('Orientation permission denied', err);
      }
    }

    if (!granted && typeof DeviceMotionEvent !== 'undefined' &&
        typeof DeviceMotionEvent.requestPermission === 'function') {
      try {
        const res = await DeviceMotionEvent.requestPermission();
        if (res === 'granted') {
          granted = true;
        }
      } catch (err) {
        console.error('Motion permission denied', err);
      }
    }

    if (granted) {
      setOrientationPermissionGranted(true);
    } else {
      orientationRequestRef.current = false;
    }
  }, [orientationPermissionGranted]);

  useEffect(() => {
    if (orientationPermissionGranted) return;
    const firstTouch = () => {
      requestOrientationPermission();
    };
    window.addEventListener('touchstart', firstTouch, { once: true });
    return () => window.removeEventListener('touchstart', firstTouch);
  }, [orientationPermissionGranted, requestOrientationPermission]);

  const handleTouchStart = (index) => {
    requestOrientationPermission();
    activeTouchIndexRef.current = index;
    clearTimeout(longPressTimeoutRef.current);
    longPressTimeoutRef.current = setTimeout(() => {
      if (expandedIndexRef.current !== null) return;
      if (index < mainDeck.length) {
        removeCardFromDeck(index, 'main');
      } else {
        removeCardFromDeck(index - mainDeck.length, 'extra');
      }
    }, 2000);
  };

  const handleTouchEnd = () => {
    clearTimeout(longPressTimeoutRef.current);
    if (activeTouchIndexRef.current !== null) {
      handleMouseOut(activeTouchIndexRef.current);
    }
    activeTouchIndexRef.current = null;
  };

  const handleOverlayClick = () => {
    if (isAnimatingRef.current || expandedIndexRef.current === null) return;
    const card = cardRefs.current[expandedIndexRef.current];
    if (!card) return;
    if (expandedOverlayRef.current) {
      expandedOverlayRef.current.style.display = 'none';
    }

    
    // ✅ 이미지 small로 원복
    const cardImg = card.querySelector('.card');
    if (cardImg) {
      const small = cardImg.getAttribute('data-small');
      if (small) {
        cardImg.style.backgroundImage = `url("${small}")`;
        // <img>라면: cardImg.src = small;
      }
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

          const scaleFactor = getScaleFactor();
          card.style.transform = `translate(-50%, ${newY}px) scale(${scaleFactor})`;

          clearTimeout(scrollTimeout);
          scrollTimeout = setTimeout(() => {
            if (expandedIndexRef.current !== null) {
              card.style.transform = `translate(-50%, -50%) scale(${getScaleFactor()})`;
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

  useEffect(() => {
    if (!isMobile || !window.DeviceOrientationEvent || !orientationPermissionGranted || !effectsEnabled) return;

    const handleOrientation = (event) => {
      orientationRef.current = { beta: event.beta || 0, gamma: event.gamma || 0 };
      const index = expandedIndexRef.current !== null
        ? expandedIndexRef.current
        : activeTouchIndexRef.current;
      if (index === null) return;
      const card = cardRefs.current[index];
      if (!card) return;
      const width = card.clientWidth;
      const height = card.clientHeight;
      const gamma = Math.max(-45, Math.min(45, orientationRef.current.gamma));
      const beta = Math.max(-45, Math.min(45, orientationRef.current.beta));
      const x = ((gamma + 45) / 90) * width; 
      const y = ((beta + 45) / 90) * height;
      applyCardEffect(x, y, index);
    };

    window.addEventListener('deviceorientation', handleOrientation);
    return () => window.removeEventListener('deviceorientation', handleOrientation);
  }, [isMobile, orientationPermissionGranted, effectsEnabled, isExpanded]);


  const [isMenuOpen, setIsMenuOpen] = useState(false);

  return (
    <>
    <BanlistNoticeModal
        open={noticeOpen}
        onClose={() => setNoticeOpen(false)}
        changes={changes}
        getThumbUrl={(name) => `/images/small/${encodeURIComponent(name)}.jpg`} // 필요에 맞게 수정
      />
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
        <button onClick={() => { navigate('/limit'); setIsMenuOpen(false); trackEvent('view_limit_board', { board: 'limit' });}}>리미트 레귤레이션</button>
        <button onClick={() => { navigate('/'); setIsMenuOpen(false); trackEvent('switch_board', { board: 'deck' });}}>덱 빌딩</button>
        <button onClick={() => { navigate('/admin/queue'); setIsMenuOpen(false); trackEvent('view_admin_page', { board: 'admin' });}}>관리자</button>
      </div>
    </div>
    {isMobile && (
      <>
        <button
          className="search-button"
          style={{ top: `${searchButtonTop}px` }}
          onClick={() => setIsSearchOpen(true)}
          aria-label="검색창 열기"
        >
          검색
        </button>
        <div
          className={`search-overlay ${isSearchOpen ? 'open' : ''}`}
          onClick={() => setIsSearchOpen(false)}
        ></div>
        <div
          className={`search-panel ${isSearchOpen ? 'open' : ''}`}
          ref={searchPanelRef}
          onScroll={handlePanelScroll}
        >
          <SearchBar
            searchKeyword={searchKeyword}
            onChange={setSearchKeyword}
            onSearch={handleSearch}
            isLoading={isLoading}
            frameType={frameType}
            onFrameChange={setFrameType}
          />
          {/* AI 카드 판별 버튼: 검색창 아래 */}
          <button
            className="ai-button ai-button--mobile"
            onClick={() => setAiOpen(true)}
          >
            AI 카드 판별
          </button>
          <div className="divider"></div>
          <SearchResults results={searchResults} addCardToDeck={addCardToDeck} />
        </div>
      </>
    )}
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
      <button
        id="copyButton"
        className="action-button"
        onClick={copyUrl}
      >
        URL 복사
      </button>
      {!orientationPermissionGranted && (
        <>
          <button
            id="orientationButton"
            className="action-button"
            onClick={() => setIsOrientationModalOpen(true)}
          >
            센서 허용
          </button>
          <OrientationModal
            open={isOrientationModalOpen}
            onAllow={() => { requestOrientationPermission(); setIsOrientationModalOpen(false); }}
            onClose={() => setIsOrientationModalOpen(false)}
          />
        </>
      )}
     <div
      ref={expandedOverlayRef}
      className="expanded-overlay"
      onClick={handleOverlayClick}
    ></div>
    
    <AICardRecognizerModal
      open={aiOpen}
      onClose={() => setAiOpen(false)}
      onPick={(card) => {
        // card: { imageUrl, frameType, name } 형태라고 가정
        addCardToDeck(card.imageUrl, card.frameType, card.name);
        setAiOpen(false);
      }}
    />

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
              useLarge={expandedIndex === index}
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
                onTouchStart={handleTouchStart}
                onTouchEnd={handleTouchEnd}
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
              useLarge={expandedIndex === (mainDeck.length + index)}
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
                onTouchStart={handleTouchStart}
                onTouchEnd={handleTouchEnd}
              />
          ))}
        </div>
      </div>
      {!isMobile && (
        <div className="right-container">
          <SearchBar
            searchKeyword={searchKeyword}
            onChange={setSearchKeyword}
            onSearch={handleSearch}
            isLoading={isLoading}
            frameType={frameType}
            onFrameChange={setFrameType}
          />
          <button
            className="ai-button ai-button--desktop"
            onClick={() => setAiOpen(true)}
          >
            AI 카드 판별
          </button>
          <div className="divider"></div>
     
          <SearchResults results={searchResults} addCardToDeck={addCardToDeck} />
        </div>
      )}
    </div>
    </>
  );
}

export default App;
