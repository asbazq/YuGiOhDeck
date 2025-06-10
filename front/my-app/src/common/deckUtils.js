import pako from 'pako';

export function sortCards(deck) {
  return deck.slice().sort((a, b) => {
    const imageUrlA = a.imageUrl.split('/').pop();
    const imageUrlB = b.imageUrl.split('/').pop();
    return imageUrlA.localeCompare(imageUrlB);
  });
}

export function saveUrl(mainDeck, extraDeck) {
  const dataObj = { cardsContent: mainDeck, extraDeckContent: extraDeck };
  const dataStr = JSON.stringify(dataObj);
  const compressed = pako.deflate(dataStr);
  const save = btoa(String.fromCharCode(...compressed));
  window.history.pushState({ data: save }, '', `?deck=${encodeURIComponent(save)}`);
}