import pako from 'pako';

export function sortCards(deck) {
  return deck.slice().sort((a, b) => {
    const imageUrlA = a.imageUrl.split('/').pop();
    const imageUrlB = b.imageUrl.split('/').pop();
    return imageUrlA.localeCompare(imageUrlB);
  });
}

// Base64URL(unpadded) 인코더
const b64urlEnc = (u8) => {
  let s = btoa(String.fromCharCode(...u8));
  return s.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/,'');
};

export function saveUrl(mainDeck, extraDeck) {
  const dataObj = { cardsContent: mainDeck, extraDeckContent: extraDeck };
  const dataStr = JSON.stringify(dataObj);

  // ✅ zlib 헤더 없는 deflateRaw + Base64URL(무패딩) → 더 짧음
  const compressedRaw = pako.deflateRaw(dataStr, { level: 9, memLevel: 9 });
  const token = b64urlEnc(compressedRaw);

  // ✅ encodeURIComponent 불필요(문자열이 URL-safe), 길이도 줄어듦
  window.history.replaceState({ data: token }, '', `?deck=${token}`);
}
