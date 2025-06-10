import React, { useState, useEffect } from 'react';

const PLACEHOLDER_IMAGE = '/back_image/101206057.jpg';

function LazyImage({ src, alt, className, style }) {
  const [imgSrc, setImgSrc] = useState(PLACEHOLDER_IMAGE);

  useEffect(() => {
    const img = new Image();
    img.src = src;
    img.onload = () => setImgSrc(src);
  }, [src]);

  return <img src={imgSrc} alt={alt} className={className} style={style} />;
}

export default LazyImage;