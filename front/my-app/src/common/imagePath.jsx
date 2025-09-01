// common/imagePath.js
const PLACEHOLDER_IMAGE = '/back_image/101206057.jpg';
const KNOWN_EXTS = new Set(['jpg','jpeg','png','webp','avif','gif']);

export function filenameOf(urlOrPath) {
  if (urlOrPath == null) return null;
  if (typeof urlOrPath === 'number') return `${urlOrPath}.jpg`;
  let s = String(urlOrPath).trim();
  if (!s) return null;

  let token = s;

  // 절대 URL: ygoprodeck 포함 어떤 도메인이든 파일명만 추출
  if (/^https?:\/\//i.test(s)) {
    try {
      const u = new URL(s);
      token = u.pathname.split('/').pop() || '';
    } catch {}
  } else {
    token = s.split('/').pop() || '';
  }

  token = token.split('?')[0].split('#')[0];
  if (!token) return null;

  const dot = token.lastIndexOf('.');
  if (dot <= 0) return `${token}.jpg`;
  const ext = token.slice(dot + 1).toLowerCase();
  if (!KNOWN_EXTS.has(ext)) return `${token.slice(0, dot)}.jpg`;
  return token;
}

export function localImagePath(idOrUrl, size = 'small') {
  const file = filenameOf(idOrUrl);
  if (!file) return PLACEHOLDER_IMAGE;
  return size === 'small' ? `/images/small/${file}` : `/images/${file}`;
}

/** 어떤 값이 들어와도 small/large 로컬 경로를 생성 */
export function toLocalSmall(any) { return localImagePath(any, 'small'); }
export function toLocalLarge(any) { return localImagePath(any, 'large'); }
