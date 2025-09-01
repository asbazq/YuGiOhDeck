// LazyImage.jsx
import React, { useState, useEffect } from 'react';

const PLACEHOLDER_IMAGE = '/back_image/101206057.jpg';

function LazyImage({
  src,
  fallbackSrc,          // ← 추가: small 실패 시 large로 폴백
  alt,
  className,
  style,
  loading = 'lazy',
  onLoad,
  onError,
  ...rest
}) {
  const [imgSrc, setImgSrc] = useState(PLACEHOLDER_IMAGE);

  useEffect(() => {
    let cancelled = false;

    const tryLoad = (url, nextFallback) => {
      if (!url) {
        if (!cancelled) setImgSrc(PLACEHOLDER_IMAGE);
        return;
      }
      const img = new Image();
      img.decoding = 'async';
      img.src = url;

      img.onload = async () => {
        if (cancelled) return;
        // decode()가 있으면 렌더 전에 디코드해서 깜빡임 줄이기
        if (img.decode) {
          try { await img.decode(); } catch {}
        }
        setImgSrc(url);
        onLoad && onLoad();
      };

      img.onerror = () => {
        if (cancelled) return;
        if (nextFallback) {
          // small 실패 → large 시도
          tryLoad(nextFallback, null);
        } else {
          setImgSrc(PLACEHOLDER_IMAGE);
          onError && onError();
        }
      };
    };

    // 우선 small(src) 시도, 실패 시 fallbackSrc(large) 시도
    tryLoad(src, fallbackSrc);

    return () => { cancelled = true; };
  }, [src, fallbackSrc, onLoad, onError]);

  return (
    <img
      src={imgSrc}
      alt={alt}
      className={className}
      style={style}
      loading={loading}
      decoding="async"
      {...rest}
    />
  );
}

export default LazyImage;
