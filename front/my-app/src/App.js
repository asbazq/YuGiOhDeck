import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import pako from 'pako';
import AICardRecognizerModal from './components/AICardRecognizerModal';
import BanlistNoticeModal from './components/BanlistNoticeModal';
import DeckCard from './components/DeckCard';
import OrientationModal from './components/OrientationModal';
import blackMagicianGirlCard from './img/black-magician-girl-card-8bit.png';
import './App.css';
import './styles/DeckCard.css';

const PAGE_SIZE = 28;
const BANLIST_NOTICE_KEY = 'banlist_notice_hidden_until';
const DUMMY_DATA_KEY = 'deck_builder_dummy_data_enabled';
const EXTRA_DECK_FRAMES = new Set([
  'fusion',
  'fusion_pendulum',
  'link',
  'synchro',
  'synchro_pendulum',
  'xyz',
  'xyz_pendulum',
]);

const RESTRICTION_LABELS = {
  forbidden: '금지',
  limited: '제한',
  semilimited: '준제한',
  unlimited: '무제한',
};

const RESTRICTION_CLASS = {
  forbidden: 'is-forbidden',
  limited: 'is-limited',
  semilimited: 'is-semilimited',
  unlimited: 'is-unlimited',
};

const DUMMY_CARD = {
  id: 'dummy-card-1',
  imageUrl: blackMagicianGirlCard,
  frameType: 'normal',
  name: 'dummy-card',
  restrictionType: 'limited',
};

function createDummyDeck(count, frameType, namePrefix) {
  return Array.from({ length: count }, (_, index) => ({
    ...DUMMY_CARD,
    id: `${namePrefix}-${index + 1}`,
    frameType,
    name: `${namePrefix} ${index + 1}`,
  }));
}

const INITIAL_MAIN_DUMMY_DECK = createDummyDeck(60, 'normal', 'main-dummy-card');
const INITIAL_EXTRA_DUMMY_DECK = createDummyDeck(15, 'fusion', 'extra-dummy-card');

function getImageId(imageUrl = '') {
  return imageUrl.split('/').pop() || '';
}

function resolveCardImageUrl(imageUrl = '') {
  if (imageUrl.startsWith('/')) {
    return imageUrl;
  }
  const imageId = getImageId(imageUrl);
  return imageId ? `/images/${imageId}` : imageUrl;
}

function getNoticeThumbUrl(nameOrId = '') {
  return `/images/small/${encodeURIComponent(nameOrId)}.jpg`;
}

function sortCards(deck) {
  return [...deck].sort((a, b) => {
    const left = getImageId(a.imageUrl);
    const right = getImageId(b.imageUrl);
    return left.localeCompare(right);
  });
}

function buildDeckParam(mainDeck, extraDeck) {
  const payload = { cardsContent: mainDeck, extraDeckContent: extraDeck };
  const compressed = pako.deflate(JSON.stringify(payload));
  return btoa(String.fromCharCode(...compressed));
}

function parseDeckParam(deckParam) {
  if (!deckParam || !/^[a-zA-Z0-9+/=]*$/.test(deckParam)) {
    return { mainDeck: [], extraDeck: [] };
  }

  try {
    const decoded = atob(deckParam);
    const compressedData = new Uint8Array(decoded.split('').map(char => char.charCodeAt(0)));
    const parsed = JSON.parse(pako.inflate(compressedData, { to: 'string' }));

    return {
      mainDeck: sortCards(parsed.cardsContent || []),
      extraDeck: sortCards(parsed.extraDeckContent || []),
    };
  } catch (error) {
    console.error('Failed to parse deck from URL:', error);
    return { mainDeck: [], extraDeck: [] };
  }
}

function App() {
  const [mainDeck, setMainDeck] = useState([]);
  const [extraDeck, setExtraDeck] = useState([]);
  const [searchKeyword, setSearchKeyword] = useState('');
  const [searchResults, setSearchResults] = useState([]);
  const [currentPage, setCurrentPage] = useState(-1);
  const [hasMoreResults, setHasMoreResults] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [message, setMessage] = useState('');
  const [selectedCard, setSelectedCard] = useState(null);
  const [cardDetail, setCardDetail] = useState(null);
  const [isDetailLoading, setIsDetailLoading] = useState(false);
  const [hasSearched, setHasSearched] = useState(true);
  const [aiOpen, setAiOpen] = useState(false);
  const [noticeOpen, setNoticeOpen] = useState(false);
  const [effectsEnabled, setEffectsEnabled] = useState(true);
  const [dummyDataEnabled, setDummyDataEnabled] = useState(false);
  const [orientationPermissionGranted, setOrientationPermissionGranted] = useState(false);
  const [isOrientationModalOpen, setIsOrientationModalOpen] = useState(false);
  const [isExpanded, setIsExpanded] = useState(false);
  const [isMobile, setIsMobile] = useState(false);
  const [isSearchOpen, setIsSearchOpen] = useState(false);
  const expandedCardFrame = null;
  const isExpandedCardVisible = false;

  const hasHydratedRef = useRef(false);
  const messageTimerRef = useRef(null);
  const observerRef = useRef(null);
  const boardPanelRef = useRef(null);
  const activeSearchRef = useRef('');
  const cardRefs = useRef([]);
  const overlayRefs = useRef([]);
  const expandedOverlayRef = useRef(null);
  const expandedIndexRef = useRef(null);
  const expandedCardHostRef = useRef({ parent: null, placeholder: null });
  const activeTouchIndexRef = useRef(null);
  const longPressTimeoutRef = useRef(null);
  const scrollFollowTimeoutRef = useRef(null);
  const lastScrollYRef = useRef(0);
  const expandedScrollOffsetRef = useRef(0);
  const orientationRequestRef = useRef(false);
  const isAnimatingRef = useRef(false);
  const collapseTimerRef = useRef(null);
  const detailCacheRef = useRef({});
  const savedDeckBeforeDummyRef = useRef({ mainDeck: [], extraDeck: [] });
  const cardTiltRef = useRef({});

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const { mainDeck: initialMainDeck, extraDeck: initialExtraDeck } = parseDeckParam(params.get('deck'));
    const hasInitialDeck = initialMainDeck.length > 0 || initialExtraDeck.length > 0;
    const storedDummyPreference = window.localStorage.getItem(DUMMY_DATA_KEY);
    const shouldUseDummyData = hasInitialDeck ? false : storedDummyPreference !== 'false';

    setDummyDataEnabled(shouldUseDummyData);
    setMainDeck(hasInitialDeck ? initialMainDeck : shouldUseDummyData ? INITIAL_MAIN_DUMMY_DECK : []);
    setExtraDeck(hasInitialDeck ? initialExtraDeck : shouldUseDummyData ? INITIAL_EXTRA_DUMMY_DECK : []);
    savedDeckBeforeDummyRef.current = {
      mainDeck: initialMainDeck,
      extraDeck: initialExtraDeck,
    };
    hasHydratedRef.current = true;
  }, []);

  useEffect(() => {
    if (!hasHydratedRef.current || dummyDataEnabled) {
      return;
    }

    const nextUrl = new URL(window.location.href);
    if (mainDeck.length === 0 && extraDeck.length === 0) {
      nextUrl.searchParams.delete('deck');
    } else {
      nextUrl.searchParams.set('deck', buildDeckParam(mainDeck, extraDeck));
    }

    window.history.replaceState({}, '', `${nextUrl.pathname}${nextUrl.search}`);
  }, [dummyDataEnabled, mainDeck, extraDeck]);

  useEffect(() => {
    if (!hasHydratedRef.current) {
      return;
    }

    window.localStorage.setItem(DUMMY_DATA_KEY, String(dummyDataEnabled));
  }, [dummyDataEnabled]);

  useEffect(() => () => {
    if (messageTimerRef.current) {
      clearTimeout(messageTimerRef.current);
    }
    if (longPressTimeoutRef.current) {
      clearTimeout(longPressTimeoutRef.current);
    }
    if (collapseTimerRef.current) {
      clearTimeout(collapseTimerRef.current);
    }
  }, []);

  useEffect(() => {
    const syncViewportMode = () => {
      const nextIsMobile = window.innerWidth <= 980;
      setIsMobile(nextIsMobile);
      if (!nextIsMobile) {
        setIsSearchOpen(false);
      }
    };

    syncViewportMode();
    window.addEventListener('resize', syncViewportMode);
    return () => window.removeEventListener('resize', syncViewportMode);
  }, []);

  useEffect(() => {
    if (!isExpanded) {
      return undefined;
    }

    const handleGlobalPointerDown = (event) => {
      const expandedCard = cardRefs.current[expandedIndexRef.current];
      const titleBox = document.querySelector('.card-detail-container');
      const descBox = document.querySelector('.card-detail-desc-container');
      const target = event.target;

      if (expandedCard?.contains(target) || titleBox?.contains(target) || descBox?.contains(target)) {
        return;
      }

      collapseExpandedCard();
    };

    document.addEventListener('mousedown', handleGlobalPointerDown);
    document.addEventListener('touchstart', handleGlobalPointerDown, { passive: true });
    return () => {
      document.removeEventListener('mousedown', handleGlobalPointerDown);
      document.removeEventListener('touchstart', handleGlobalPointerDown);
    };
  }, [isExpanded]);

  useEffect(() => {
    if (
      typeof DeviceOrientationEvent !== 'undefined' &&
      typeof DeviceOrientationEvent.requestPermission !== 'function'
    ) {
      setOrientationPermissionGranted(true);
    }
  }, []);

  useEffect(() => {
    let cancelled = false;
    const hiddenUntil = Number(localStorage.getItem(BANLIST_NOTICE_KEY) || '0');

    if (hiddenUntil > Date.now()) {
      return undefined;
    }

    const loadNoticeStatus = async () => {
      try {
        const response = await fetch('/cards/current', { cache: 'no-store' });
        if (!response.ok) {
          return;
        }

        const data = await response.json();
        if (!cancelled && Array.isArray(data) && data.length > 0) {
          setNoticeOpen(true);
        }
      } catch (error) {
        console.error('Failed to load banlist notice status:', error);
      }
    };

    loadNoticeStatus();

    return () => {
      cancelled = true;
    };
  }, []);

  const showMessage = useCallback((nextMessage) => {
    if (messageTimerRef.current) {
      clearTimeout(messageTimerRef.current);
    }

    setMessage(nextMessage);
    messageTimerRef.current = setTimeout(() => {
      setMessage('');
      messageTimerRef.current = null;
    }, 2400);
  }, []);

  const totalDeckCount = mainDeck.length + extraDeck.length;
  const getScaleFactor = useCallback(() => (window.innerWidth <= 768 ? 3 : 4), []);
  const buildCardTransform = useCallback((index, rotateX = 0, rotateY = 0) => {
    const isExpandedCard = expandedIndexRef.current === index;
    const hasTilt = effectsEnabled && (rotateX !== 0 || rotateY !== 0);
    const tiltTransform = hasTilt
      ? ` perspective(350px) rotateX(${rotateX}deg) rotateY(${rotateY}deg)`
      : '';

    if (isExpandedCard) {
      return `translate(-50%, calc(-50% + ${expandedScrollOffsetRef.current}px)) scale(${getScaleFactor()})${tiltTransform}`;
    }

    return effectsEnabled ? `perspective(350px) rotateX(${rotateX}deg) rotateY(${rotateY}deg)` : '';
  }, [effectsEnabled, getScaleFactor]);

  const restrictionSummary = useMemo(() => {
    return [...mainDeck, ...extraDeck].reduce(
      (acc, card) => {
        const restriction = card.restrictionType || 'unlimited';
        acc[restriction] = (acc[restriction] || 0) + 1;
        return acc;
      },
      { forbidden: 0, limited: 0, semilimited: 0, unlimited: 0 }
    );
  }, [mainDeck, extraDeck]);

  const searchCards = useCallback(async (keyword, page = 0) => {
    const normalizedKeyword = keyword.trim();

    if (!normalizedKeyword) {
      setSearchResults([]);
      setCurrentPage(-1);
      setHasMoreResults(false);
      setHasSearched(false);
      return;
    }

    if (isLoading && page !== 0) {
      return;
    }

    setIsLoading(true);

    try {
      const response = await fetch(
        `/cards/search?keyWord=${encodeURIComponent(normalizedKeyword)}&page=${page}&size=${PAGE_SIZE}`
      );

      if (!response.ok) {
        throw new Error('Failed to fetch search results');
      }

      const data = await response.json();
      setSearchResults(prev => (page === 0 ? data.content : [...prev, ...data.content]));
      setCurrentPage(data.number);
      setHasMoreResults(!data.last);
      setHasSearched(true);
      activeSearchRef.current = normalizedKeyword;
    } catch (error) {
      console.error(error);
      showMessage('카드 검색에 실패했습니다. 잠시 후 다시 시도해 주세요.');
    } finally {
      setIsLoading(false);
    }
  }, [isLoading, showMessage]);

  const handleSearchSubmit = useCallback(() => {
    const normalizedKeyword = searchKeyword.trim();

    if (!normalizedKeyword) {
      showMessage('검색어를 입력해 주세요.');
      return;
    }

    searchCards(normalizedKeyword, 0);
  }, [searchCards, searchKeyword, showMessage]);

  const loadCardDetail = useCallback(async (card) => {
    if (!card?.name) {
      return;
    }

    setSelectedCard(card);
    setCardDetail(null);
    setIsDetailLoading(true);

    try {
      const cached = detailCacheRef.current[card.name];
      if (cached) {
        setCardDetail(cached);
        return;
      }

      const response = await fetch(`/cards/cardinfo?cardName=${encodeURIComponent(card.name)}`);
      if (!response.ok) {
        throw new Error('Failed to fetch card detail');
      }

      const data = await response.json();
      detailCacheRef.current[card.name] = data;
      setCardDetail(data);
    } catch (error) {
      console.error(error);
      showMessage('카드 상세 정보를 불러오지 못했습니다.');
    } finally {
      setIsDetailLoading(false);
    }
  }, [showMessage]);

  const addCardToDeck = useCallback((card) => {
    const cardImageId = getImageId(card.imageUrl);
    const duplicateCount = [...mainDeck, ...extraDeck].filter(
      deckCard => getImageId(deckCard.imageUrl) === cardImageId
    ).length;

    if (duplicateCount >= 3) {
      showMessage('동일 카드는 최대 3장까지만 넣을 수 있습니다.');
      return;
    }

    if (EXTRA_DECK_FRAMES.has(card.frameType)) {
      if (extraDeck.length >= 15) {
        showMessage('엑스트라 덱은 최대 15장까지 가능합니다.');
        return;
      }

      setExtraDeck(prev => sortCards([...prev, card]));
      return;
    }

    if (mainDeck.length >= 60) {
      showMessage('메인 덱은 최대 60장까지 가능합니다.');
      return;
    }

    setMainDeck(prev => sortCards([...prev, card]));
  }, [extraDeck, mainDeck, showMessage]);

  function removeCardFromDeck(index, deckType) {
    if (expandedIndexRef.current !== null) {
      collapseExpandedCard({ clearDetail: true });
    }

    if (deckType === 'main') {
      setMainDeck(prev => sortCards(prev.filter((_, currentIndex) => currentIndex !== index)));
    } else {
      setExtraDeck(prev => sortCards(prev.filter((_, currentIndex) => currentIndex !== index)));
    }
  }

  const toggleDummyData = useCallback(() => {
    setDummyDataEnabled(prev => {
      const next = !prev;
      if (next) {
        savedDeckBeforeDummyRef.current = {
          mainDeck,
          extraDeck,
        };
        setMainDeck(INITIAL_MAIN_DUMMY_DECK);
        setExtraDeck(INITIAL_EXTRA_DUMMY_DECK);
        collapseExpandedCard({ clearDetail: true });
      } else {
        setMainDeck(savedDeckBeforeDummyRef.current.mainDeck);
        setExtraDeck(savedDeckBeforeDummyRef.current.extraDeck);
        collapseExpandedCard({ clearDetail: true });
      }
      return next;
    });
  }, [extraDeck, mainDeck]);

  function clearDeck() {
    if (expandedIndexRef.current !== null) {
      collapseExpandedCard({ clearDetail: true });
    }
    setMainDeck([]);
    setExtraDeck([]);
    setSelectedCard(null);
    setCardDetail(null);
    showMessage('덱을 초기화했습니다.');
  }

  const copyShareLink = useCallback(async () => {
    const shareUrl = window.location.href;

    try {
      if (navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(shareUrl);
      } else {
        const tempInput = document.createElement('input');
        tempInput.value = shareUrl;
        document.body.appendChild(tempInput);
        tempInput.select();
        document.execCommand('copy');
        document.body.removeChild(tempInput);
      }

      showMessage('현재 덱 링크를 클립보드에 복사했습니다.');
    } catch (error) {
      console.error(error);
      showMessage('링크 복사에 실패했습니다.');
    }
  }, [showMessage]);

  const handleAiPick = useCallback((card) => {
    addCardToDeck(card);
    setAiOpen(false);
    showMessage('AI 판별 결과 카드를 덱에 추가했습니다.');
  }, [addCardToDeck, showMessage]);

  const dismissNoticeForOneDay = useCallback(() => {
    localStorage.setItem(BANLIST_NOTICE_KEY, String(Date.now() + 24 * 60 * 60 * 1000));
    setNoticeOpen(false);
  }, []);

  const loadMoreRef = useCallback((node) => {
    if (observerRef.current) {
      observerRef.current.disconnect();
    }

    if (!node) {
      return;
    }

    observerRef.current = new IntersectionObserver(entries => {
      const [entry] = entries;
      if (!entry.isIntersecting) {
        return;
      }

      if (hasMoreResults && !isLoading && activeSearchRef.current) {
        searchCards(activeSearchRef.current, currentPage + 1);
      }
    }, { rootMargin: '240px' });

    observerRef.current.observe(node);
  }, [currentPage, hasMoreResults, isLoading, searchCards]);

  function collapseExpandedCard(options = {}) {
    const { clearDetail = false } = options;
    if (isAnimatingRef.current || expandedIndexRef.current === null) {
      return;
    }

    const currentIndex = expandedIndexRef.current;
    const cardNode = cardRefs.current[currentIndex];
    if (!cardNode) {
      expandedIndexRef.current = null;
      setIsExpanded(false);
      if (clearDetail) {
        setSelectedCard(null);
        setCardDetail(null);
      }
      return;
    }

    if (expandedOverlayRef.current) {
      expandedOverlayRef.current.style.display = 'none';
    }

    const cardImg = cardNode.querySelector('.card');
    if (cardImg) {
      const small = cardImg.getAttribute('data-small');
      if (small) {
        cardImg.style.backgroundImage = `url("${small}")`;
      }
    }

    const origTop = parseFloat(cardNode.dataset.origTop || 0);
    const origLeft = parseFloat(cardNode.dataset.origLeft || 0);
    const origScrollY = parseFloat(cardNode.dataset.origScrollY || 0);
    const finalTop = origTop + (origScrollY - window.scrollY);
    const { parent, placeholder } = expandedCardHostRef.current;

    isAnimatingRef.current = true;
    setIsExpanded(false);
    cardNode.classList.remove('expanded');
    cardNode.style.zIndex = '90';
    cardNode.style.top = `${finalTop}px`;
    cardNode.style.left = `${origLeft}px`;
    cardNode.style.transform = 'translate(0, 0) scale(1)';

    if (collapseTimerRef.current) {
      clearTimeout(collapseTimerRef.current);
    }
    if (scrollFollowTimeoutRef.current) {
      clearTimeout(scrollFollowTimeoutRef.current);
      scrollFollowTimeoutRef.current = null;
    }
    expandedScrollOffsetRef.current = 0;
    delete cardTiltRef.current[currentIndex];

    const finishCollapse = () => {
      if (parent && placeholder && placeholder.parentNode === parent) {
        parent.insertBefore(cardNode, placeholder);
        parent.removeChild(placeholder);
      }
      cardNode.style.position = 'relative';
      cardNode.style.top = '';
      cardNode.style.left = '';
      cardNode.style.transform = '';
      cardNode.style.transition = '';
      cardNode.style.zIndex = '';
      expandedCardHostRef.current = { parent: null, placeholder: null };
      expandedIndexRef.current = null;
      isAnimatingRef.current = false;
      if (clearDetail) {
        setSelectedCard(null);
        setCardDetail(null);
      }
      cardNode.removeEventListener('transitionend', finishCollapse);
    };

    cardNode.addEventListener('transitionend', finishCollapse);
    collapseTimerRef.current = setTimeout(() => {
      finishCollapse();
      collapseTimerRef.current = null;
    }, 280);
  }

  const applyCardEffect = useCallback((x, y, index) => {
    const node = cardRefs.current[index];
    const overlay = overlayRefs.current[index];
    if (!node || !overlay) {
      return;
    }

    const rect = node.getBoundingClientRect();
    const width = rect.width || node.clientWidth;
    const height = rect.height || node.clientHeight;
    if (!width || !height) {
      return;
    }

    const bgPosX = (x / width) * 100;
    const bgPosY = (y / height) * 100;

    overlay.style.background = effectsEnabled
      ? `radial-gradient(circle at ${bgPosX}% ${bgPosY}%, rgba(255, 255, 255, 0.8), transparent 70%)`
      : 'none';

    if (!effectsEnabled) {
      node.style.transform = buildCardTransform(index, 0, 0);
      return;
    }

    const rotateY = 20 - (x / width) * 40;
    const rotateX = (y / height) * 40 - 20;
    cardTiltRef.current[index] = { rotateX, rotateY };
    node.style.transform = buildCardTransform(index, rotateX, rotateY);
  }, [buildCardTransform, effectsEnabled]);

  const resetCardEffect = useCallback((index) => {
    const node = cardRefs.current[index];
    const overlay = overlayRefs.current[index];
    if (!node) {
      return;
    }

    if (overlay) {
      overlay.style.background = 'none';
    }

    cardTiltRef.current[index] = { rotateX: 0, rotateY: 0 };
    node.style.transform = buildCardTransform(index, 0, 0);
  }, [buildCardTransform]);

  const requestOrientationPermission = useCallback(async () => {
    if (orientationPermissionGranted || orientationRequestRef.current) {
      return;
    }

    orientationRequestRef.current = true;
    let granted = false;

    if (
      typeof DeviceOrientationEvent !== 'undefined' &&
      typeof DeviceOrientationEvent.requestPermission === 'function'
    ) {
      try {
        granted = (await DeviceOrientationEvent.requestPermission()) === 'granted';
      } catch (error) {
        console.error('Orientation permission denied:', error);
      }
    }

    if (
      !granted &&
      typeof DeviceMotionEvent !== 'undefined' &&
      typeof DeviceMotionEvent.requestPermission === 'function'
    ) {
      try {
        granted = (await DeviceMotionEvent.requestPermission()) === 'granted';
      } catch (error) {
        console.error('Motion permission denied:', error);
      }
    }

    if (granted) {
      setOrientationPermissionGranted(true);
      return;
    }

    orientationRequestRef.current = false;
  }, [orientationPermissionGranted]);

  const handleMouseMove = useCallback((event, index) => {
    if (isAnimatingRef.current) {
      return;
    }
    const { currentTarget, clientX, clientY } = event;
    const rect = currentTarget.getBoundingClientRect();
    const x = clientX - rect.left;
    const y = clientY - rect.top;
    applyCardEffect(x, y, index);
  }, [applyCardEffect]);

  const handleMouseOut = useCallback((index) => {
    if (isAnimatingRef.current) {
      return;
    }
    resetCardEffect(index);
  }, [resetCardEffect]);

  function handleTouchStart(index, deckKey, deckIndex) {
    requestOrientationPermission();
    activeTouchIndexRef.current = index;
    clearTimeout(longPressTimeoutRef.current);
    longPressTimeoutRef.current = setTimeout(() => {
      removeCardFromDeck(deckIndex, deckKey);
    }, 1200);
  }

  function handleTouchEnd() {
    clearTimeout(longPressTimeoutRef.current);
    if (activeTouchIndexRef.current !== null) {
      resetCardEffect(activeTouchIndexRef.current);
    }
    activeTouchIndexRef.current = null;
  }

  function handleDeckCardClick(card, index) {
    if (isAnimatingRef.current) {
      return;
    }

    const cardNode = cardRefs.current[index];
    if (!cardNode) {
      return;
    }

    if (expandedIndexRef.current === index) {
      collapseExpandedCard();
      return;
    }

    if (expandedIndexRef.current !== null) {
      return;
    }

    loadCardDetail(card);
    setSelectedCard(card);

    if (expandedOverlayRef.current) {
      expandedOverlayRef.current.style.display = 'block';
    }

    const rect = cardNode.getBoundingClientRect();
    const originalParent = cardNode.parentNode;
    const placeholder = document.createElement('div');
    placeholder.className = 'expanded-card-placeholder';
    placeholder.style.width = `${rect.width}px`;
    placeholder.style.height = `${rect.height}px`;

    cardNode.dataset.origTop = rect.top;
    cardNode.dataset.origLeft = rect.left;
    cardNode.dataset.origScrollY = window.scrollY;
    lastScrollYRef.current = window.scrollY;
    expandedScrollOffsetRef.current = 0;
    expandedCardHostRef.current = { parent: originalParent, placeholder };

    originalParent.insertBefore(placeholder, cardNode);
    expandedOverlayRef.current.appendChild(cardNode);

    cardNode.style.position = 'fixed';
    cardNode.style.zIndex = '90';
    cardNode.style.top = `${rect.top}px`;
    cardNode.style.left = `${rect.left}px`;

    isAnimatingRef.current = true;
    setIsExpanded(true);
    requestAnimationFrame(() => {
      cardNode.classList.add('expanded');
      cardNode.style.top = '';
      cardNode.style.left = '';
      const tilt = cardTiltRef.current[index] || { rotateX: 0, rotateY: 0 };
      cardNode.style.transform = buildCardTransform(index, tilt.rotateX, tilt.rotateY);
    });

    const finishExpand = () => {
      isAnimatingRef.current = false;
      cardNode.removeEventListener('transitionend', finishExpand);
    };

    cardNode.addEventListener('transitionend', finishExpand);

    expandedIndexRef.current = index;
  }

  useEffect(() => {
    let scrollTimeout;
    const boardPanelNode = boardPanelRef.current;
    let lastScrollY = boardPanelNode ? boardPanelNode.scrollTop : window.scrollY;

    const handleScrollFollow = () => {
      if (expandedIndexRef.current !== null) {
        const card = cardRefs.current[expandedIndexRef.current];
        if (card) {
          const currentScrollY = boardPanelNode ? boardPanelNode.scrollTop : window.scrollY;
          const scrollDiff = currentScrollY - lastScrollY;
          if (scrollDiff === 0) {
            return;
          }

          expandedScrollOffsetRef.current -= scrollDiff;
          card.style.transition = 'none';
          const tilt = cardTiltRef.current[expandedIndexRef.current] || { rotateX: 0, rotateY: 0 };
          card.style.transform = buildCardTransform(
            expandedIndexRef.current,
            tilt.rotateX,
            tilt.rotateY
          );

          clearTimeout(scrollTimeout);
          scrollTimeout = setTimeout(() => {
            if (expandedIndexRef.current !== null) {
              expandedScrollOffsetRef.current = 0;
              card.style.transition = '';
              const tilt = cardTiltRef.current[expandedIndexRef.current] || { rotateX: 0, rotateY: 0 };
              card.style.transform = buildCardTransform(
                expandedIndexRef.current,
                tilt.rotateX,
                tilt.rotateY
              );
            }
          }, 400);

          lastScrollY = currentScrollY;
        }
      } else {
        lastScrollY = boardPanelNode ? boardPanelNode.scrollTop : window.scrollY;
      }
    };

    window.addEventListener('scroll', handleScrollFollow, { passive: true });
    boardPanelNode?.addEventListener('scroll', handleScrollFollow, { passive: true });
    return () => {
      window.removeEventListener('scroll', handleScrollFollow);
      boardPanelNode?.removeEventListener('scroll', handleScrollFollow);
      clearTimeout(scrollTimeout);
    };
  }, [buildCardTransform, getScaleFactor]);

  useEffect(() => {
    if (!isExpanded || expandedIndexRef.current === null) {
      return undefined;
    }

    const index = expandedIndexRef.current;
    const node = cardRefs.current[index];
    if (!node) {
      return undefined;
    }

    const handleExpandedPointerMove = (event) => {
      if (isAnimatingRef.current) {
        return;
      }

      const rect = node.getBoundingClientRect();
      const x = event.clientX - rect.left;
      const y = event.clientY - rect.top;
      applyCardEffect(x, y, index);
    };

    const handleExpandedPointerLeave = () => {
      if (isAnimatingRef.current) {
        return;
      }

      resetCardEffect(index);
    };

    node.addEventListener('pointermove', handleExpandedPointerMove);
    node.addEventListener('pointerleave', handleExpandedPointerLeave);

    return () => {
      node.removeEventListener('pointermove', handleExpandedPointerMove);
      node.removeEventListener('pointerleave', handleExpandedPointerLeave);
    };
  }, [applyCardEffect, isExpanded, resetCardEffect]);

  useEffect(() => {
    if (!window.DeviceOrientationEvent || !orientationPermissionGranted || !effectsEnabled) {
      return undefined;
    }

    const handleOrientation = (event) => {
      const index =
        expandedIndexRef.current !== null ? expandedIndexRef.current : activeTouchIndexRef.current;
      if (index === null) {
        return;
      }

      const node = cardRefs.current[index];
      if (!node) {
        return;
      }

      const width = node.clientWidth;
      const height = node.clientHeight;
      const gamma = Math.max(-45, Math.min(45, event.gamma || 0));
      const beta = Math.max(-45, Math.min(45, event.beta || 0));
      const x = ((gamma + 45) / 90) * width;
      const y = ((beta + 45) / 90) * height;
      applyCardEffect(x, y, index);
    };

    window.addEventListener('deviceorientation', handleOrientation);
    return () => window.removeEventListener('deviceorientation', handleOrientation);
  }, [applyCardEffect, effectsEnabled, orientationPermissionGranted]);

  const deckSections = [
    {
      deckKey: 'main',
      title: 'Main Deck',
      subtitle: '기본 몬스터와 마법/함정 카드',
      cards: mainDeck,
      limit: 60,
    },
    {
      deckKey: 'extra',
      title: 'Extra Deck',
      subtitle: '융합, 싱크로, 엑시즈, 링크 카드',
      cards: extraDeck,
      limit: 15,
    },
  ];

  const selectedRestriction = selectedCard?.restrictionType || 'unlimited';

  const handleSearchResultHover = useCallback((card) => {
    if (isMobile || isExpanded || !card) {
      return;
    }
    loadCardDetail(card);
  }, [isExpanded, isMobile, loadCardDetail]);


  return (
    <div className="deck-builder-page">
      {isMobile && (
        <>
          <button
            type="button"
            className="search-button"
            onClick={() => setIsSearchOpen(true)}
            aria-label="Open search panel"
          >
            Search
          </button>
          <div
            className={`search-overlay ${isSearchOpen ? 'open' : ''}`}
            onClick={() => setIsSearchOpen(false)}
          />
        </>
      )}
      <div className="deck-builder-shell">
        <aside className="hero-panel">
          <div className="hero-panel__eyebrow">Master Duel Deck Builder</div>
          <h1 className="hero-panel__title">YuGiOh Deck</h1>
          <p className="hero-panel__description">
            우클릭으로 카드를 제거하고, 카드를 눌러 상세 정보를 확인할 수 있습니다.
          </p>

          <div className="hero-stats">
            <div className="stat-card">
              <span className="stat-card__label">Total</span>
              <strong className="stat-card__value">{totalDeckCount}</strong>
            </div>
            <div className="stat-card">
              <span className="stat-card__label">Main</span>
              <strong className="stat-card__value">{mainDeck.length}/60</strong>
            </div>
            <div className="stat-card">
              <span className="stat-card__label">Extra</span>
              <strong className="stat-card__value">{extraDeck.length}/15</strong>
            </div>
          </div>

          <div className="restriction-strip">
            {Object.keys(RESTRICTION_LABELS).map(key => (
              <div key={key} className={`restriction-pill ${RESTRICTION_CLASS[key]}`}>
                <span>{RESTRICTION_LABELS[key]}</span>
                <strong>{restrictionSummary[key] || 0}</strong>
              </div>
            ))}
          </div>

          <div className="hero-nav">
            <button
              type="button"
              className="hero-nav__button"
              onClick={() => window.location.assign('/limit')}
            >
              Limit Regulation
            </button>
            <button
              type="button"
              className="hero-nav__button"
              onClick={() => window.location.assign('/admin/queue')}
            >
              Admin
            </button>
          </div>

          <div className="hero-actions">
            <button type="button" className="primary-action" onClick={copyShareLink}>
              링크 복사
            </button>
            <button type="button" className="secondary-action" onClick={clearDeck}>
              덱 초기화
            </button>
          </div>

          <div className="hero-actions hero-actions--utility">
            <button
              type="button"
              className={`secondary-action effect-toggle ${dummyDataEnabled ? 'is-on' : 'is-off'}`}
              onClick={toggleDummyData}
            >
              Dummy Data
            </button>
            <button
              type="button"
              className={`secondary-action effect-toggle ${effectsEnabled ? 'is-on' : 'is-off'}`}
              onClick={() => setEffectsEnabled(prev => !prev)}
            >
              3D Effect
            </button>
            {!orientationPermissionGranted && (
              <button
                type="button"
                className="secondary-action"
                onClick={() => setIsOrientationModalOpen(true)}
              >
                Gyro Permission
              </button>
            )}
          </div>

          <div className="help-card">
            <div className="help-card__title">Quick Tips</div>
            <p>검색 결과 카드를 클릭하면 덱에 추가됩니다.</p>
            <p>덱 카드 클릭: 상세 보기</p>
            <p>덱 카드 우클릭: 카드 제거</p>
          </div>
        </aside>

        <main className="board-panel" ref={boardPanelRef}>
          {message && <div className="floating-message">{message}</div>}

          {deckSections.map(section => (
            <section key={section.deckKey} className="deck-section">
              <div className="section-header">
                <div>
                  <h2>{section.title}</h2>
                  <p>{section.subtitle}</p>
                </div>
                <div className="section-count">
                  {section.cards.length} / {section.limit}
                </div>
              </div>

              {section.cards.length === 0 ? (
                <div className="deck-empty">
                  아직 카드가 없습니다. 우측 검색 결과에서 카드를 추가해 보세요.
                </div>
              ) : (
                <div className="deck-grid">
                  {section.cards.map((card, index) => (
                    <DeckCard
                      key={`${section.deckKey}-${getImageId(card.imageUrl)}-${index}`}
                      card={card}
                      index={section.deckKey === 'main' ? index : mainDeck.length + index}
                      cardRefs={cardRefs}
                      overlayRefs={overlayRefs}
                      onClick={(_, globalIndex) => handleDeckCardClick(card, globalIndex)}
                      onContextMenu={event => {
                        event.preventDefault();
                        removeCardFromDeck(index, section.deckKey);
                      }}
                      onMouseMove={handleMouseMove}
                      onMouseOut={handleMouseOut}
                      onTouchStart={(globalIndex) => handleTouchStart(globalIndex, section.deckKey, index)}
                      onTouchEnd={handleTouchEnd}
                    />
                  ))}
                </div>
              )}
            </section>
          ))}
        </main>

        <aside className={`search-panel ${isSearchOpen ? 'open' : ''}`}>
          <section className="search-box">
            <div className="search-box__label">Card Search</div>
            <div className="search-box__controls">
              <input
                type="search"
                value={searchKeyword}
                onChange={event => setSearchKeyword(event.target.value)}
                onKeyDown={event => {
                  if (event.key === 'Enter') {
                    handleSearchSubmit();
                  }
                }}
                placeholder="예: Dark Magician, 블랙 매지션"
                aria-label="카드 검색"
              />
              <button type="button" onClick={handleSearchSubmit} disabled={isLoading}>
                검색
              </button>
            </div>
            <button
              type="button"
              className="ai-entry-button"
              onClick={() => setAiOpen(true)}
            >
              AI 카드 판별
            </button>
            <p className="search-box__caption">
              한글/영문 이름으로 검색할 수 있습니다. 아래 결과를 눌러 덱에 추가하세요.
            </p>
          </section>

          {false && !isMobile && (
            <section className="detail-panel">
              <div className="detail-panel__header">
                <div>
                  <div className="detail-panel__label">Card Detail</div>
                  <h3>{selectedCard?.name || '카드를 선택해 주세요'}</h3>
                </div>
                {selectedCard && (
                  <span className={`detail-badge ${RESTRICTION_CLASS[selectedRestriction]}`}>
                    {RESTRICTION_LABELS[selectedRestriction]}
                  </span>
                )}
              </div>

              {selectedCard ? (
                <div className="detail-panel__content">
                  <img
                    className="detail-panel__image"
                    src={resolveCardImageUrl(selectedCard.imageUrl)}
                    alt={selectedCard.name}
                  />

                  {isDetailLoading ? (
                    <div className="detail-panel__placeholder">상세 정보를 불러오는 중입니다.</div>
                  ) : (
                    <>
                      <div className="detail-meta">
                        <div>
                          <span>종족</span>
                          <strong>{cardDetail?.race || '-'}</strong>
                        </div>
                        <div>
                          <span>구분</span>
                          <strong>{EXTRA_DECK_FRAMES.has(selectedCard.frameType) ? 'Extra' : 'Main'}</strong>
                        </div>
                      </div>
                      <div className="detail-description">
                        {cardDetail?.korDesc || '설명 정보가 아직 등록되지 않았습니다.'}
                      </div>
                    </>
                  )}
                </div>
              ) : (
                <div className="detail-panel__placeholder">
                  검색 결과 카드에 마우스를 올리면 이름과 효과가 여기에 표시됩니다.
                </div>
              )}
            </section>
          )}

          <section className="detail-panel" style={{ display: 'none' }}>
            {/*
            <div className="detail-panel__header">
              <div>
                <div className="detail-panel__label">Selected Card</div>
                <h3>{selectedCard?.name || '카드를 선택해 주세요'}</h3>
              </div>
              {selectedCard && (
                <span className={`detail-badge ${RESTRICTION_CLASS[selectedRestriction]}`}>
                  {RESTRICTION_LABELS[selectedRestriction]}
                </span>
              )}
            </div>

            {selectedCard ? (
              <div className="detail-panel__content">
                <img
                  className="detail-panel__image"
                  src={resolveCardImageUrl(selectedCard.imageUrl)}
                  alt={selectedCard.name}
                />

                {isDetailLoading ? (
                  <div className="detail-panel__placeholder">상세 정보를 불러오는 중입니다.</div>
                ) : (
                  <>
                    <div className="detail-meta">
                      <div>
                        <span>종족</span>
                        <strong>{cardDetail?.race || '-'}</strong>
                      </div>
                      <div>
                        <span>구분</span>
                        <strong>{EXTRA_DECK_FRAMES.has(selectedCard.frameType) ? 'Extra' : 'Main'}</strong>
                      </div>
                    </div>
                    <div className="detail-description">
                      {cardDetail?.korDesc || '설명 정보가 아직 등록되지 않았습니다.'}
                    </div>
                  </>
                )}
              </div>
            ) : (
              <div className="detail-panel__placeholder">
                덱에서 카드를 선택하면 설명과 제한 상태가 여기에 표시됩니다.
              </div>
            */}
          </section>
          <section className="results-panel">
            <div className="results-panel__header">
              <div>
                <div className="results-panel__label">Results</div>
                <h3>{hasSearched ? `${searchResults.length} cards` : '검색 대기 중'}</h3>
              </div>
              {isLoading && <span className="results-panel__loading">Loading</span>}
            </div>

            {!isLoading && hasSearched && searchResults.length === 0 && (
              <div className="results-empty">검색 결과가 없습니다. 다른 이름으로 다시 시도해 보세요.</div>
            )}

            <div className="results-grid">
              {searchResults.map((result, index) => (
                <button
                  key={`${result.id || result.name}-${index}`}
                  type="button"
                  className="result-card"
                  onClick={() => addCardToDeck(result)}
                  onMouseEnter={() => handleSearchResultHover(result)}
                  onFocus={() => handleSearchResultHover(result)}
                >
                  <div className="result-card__image-wrap">
                    <img src={resolveCardImageUrl(result.imageUrl)} alt={result.name} />
                    {result.restrictionType && result.restrictionType !== 'unlimited' && (
                      <span
                        className={`restriction-label ${
                          result.restrictionType === 'forbidden'
                            ? 'forbidden'
                            : result.restrictionType === 'limited'
                              ? 'limited'
                              : 'semi-limited'
                        }`}
                        aria-label={RESTRICTION_LABELS[result.restrictionType]}
                        title={RESTRICTION_LABELS[result.restrictionType]}
                      >
                        {result.restrictionType === 'forbidden'
                          ? null
                          : result.restrictionType === 'limited'
                            ? '1'
                            : '2'}
                      </span>
                    )}
                  </div>
                  <strong>{result.name}</strong>
                </button>
              ))}
            </div>

            <div ref={loadMoreRef} className="results-sentinel" aria-hidden="true" />
          </section>
        </aside>
      </div>

      <AICardRecognizerModal
        open={aiOpen}
        onClose={() => setAiOpen(false)}
        onPick={handleAiPick}
        onTimeout={(pos) => {
          setAiOpen(false);
          showMessage(`AI 판별 대기열 세션이 만료되었습니다. 현재 대기 위치: ${Math.max(0, pos || 0)}`);
        }}
      />
      <BanlistNoticeModal
        open={noticeOpen}
        onClose={() => setNoticeOpen(false)}
        onDismissToday={dismissNoticeForOneDay}
        getThumbUrl={getNoticeThumbUrl}
      />
      <OrientationModal
        open={isOrientationModalOpen}
        onAllow={() => {
          requestOrientationPermission();
          setIsOrientationModalOpen(false);
        }}
        onClose={() => setIsOrientationModalOpen(false)}
      />
      <div
        ref={expandedOverlayRef}
        className="expanded-overlay"
        onClick={() => collapseExpandedCard()}
      >
        {expandedCardFrame && selectedCard && (
          <div
            className={`expanded-card-preview ${isExpandedCardVisible ? 'is-active' : ''}`}
            style={{
              top: `${expandedCardFrame.top}px`,
              left: `${expandedCardFrame.left}px`,
              width: `${expandedCardFrame.width}px`,
              height: `${expandedCardFrame.height}px`,
            }}
            onClick={event => event.stopPropagation()}
          >
            <div className="expanded-card-preview__media">
              <img
                className="expanded-card-preview__image"
                src={resolveCardImageUrl(selectedCard.imageUrl)}
                alt={selectedCard.name}
              />
            </div>
            <div className="expanded-card-preview__body">
              <div className="expanded-card-preview__title">{selectedCard.name}</div>
              <div className="expanded-card-preview__meta">
                {cardDetail?.race || (EXTRA_DECK_FRAMES.has(selectedCard.frameType) ? 'Extra Deck' : 'Main Deck')}
              </div>
              <div className="expanded-card-preview__desc">
                {isDetailLoading
                  ? '효과 정보를 불러오는 중입니다.'
                  : cardDetail?.korDesc || '설명 정보가 아직 등록되지 않았습니다.'}
              </div>
            </div>
          </div>
        )}
      </div>
      {isExpanded && selectedCard && (
        <div className="card-detail-container" style={{ display: 'block' }}>
          <div id="cardDetailContainer">{selectedCard.name}</div>
        </div>
      )}
      {isExpanded && selectedCard && (
        <div className="card-detail-desc-container" style={{ display: 'block' }}>
          <div className="race-text" style={{ marginBottom: '4px', fontWeight: 'bold' }}>
            [{cardDetail?.race || (EXTRA_DECK_FRAMES.has(selectedCard.frameType) ? 'Extra' : 'Main')}]
          </div>
          <div className="desc-text" style={{ whiteSpace: 'pre-line' }}>
            {isDetailLoading
              ? '효과 정보를 불러오는 중입니다.'
              : cardDetail?.korDesc || '설명 정보가 아직 등록되지 않았습니다.'}
          </div>
        </div>
      )}
    </div>
  );
}

export default App;
