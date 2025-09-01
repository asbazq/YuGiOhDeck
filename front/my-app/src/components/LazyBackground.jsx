import React, { useState, useEffect } from 'react';

const PLACEHOLDER_IMAGE = '/back_image/101206057.jpg';

function LazyBackground({ src, className }) {
  const [bg, setBg] = useState(`url(${PLACEHOLDER_IMAGE})`);

  useEffect(() => {
    if (!src) { setBg(`url(${PLACEHOLDER_IMAGE})`); return; }
    setBg(`url(${PLACEHOLDER_IMAGE})`); // src 바뀌면 일단 placeholder
    const img = new Image();
    img.onload = () => setBg(`url(${src})`);
    img.onerror = () => setBg(`url(${PLACEHOLDER_IMAGE})`);
    img.src = src;
    return () => { img.onload = null; img.onerror = null; };
  }, [src]);

  return <div className={className} style={{ backgroundImage: bg }}></div>;
}

export default LazyBackground;