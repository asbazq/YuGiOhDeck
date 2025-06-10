import React, { useState, useEffect } from 'react';

const PLACEHOLDER_IMAGE = '/back_image/101206057.jpg';

function LazyBackground({ src, className }) {
  const [bg, setBg] = useState(`url(${PLACEHOLDER_IMAGE})`);

  useEffect(() => {
    const img = new Image();
    img.src = src;
    img.onload = () => setBg(`url(${src})`);
  }, [src]);

  return <div className={className} style={{ backgroundImage: bg }}></div>;
}

export default LazyBackground;