// utils/analytics.js
/**
 * SPA 페이지 이동 시 호출
 */
export const sendPageView = (path) => {
  window.gtag?.('event', 'page_view', {
    page_location: window.location.href,
    page_path: path,              // ex) '/deck'
    page_title: document.title,
  });
};

/**
 * 임의 이벤트 전송
 * @param {string} name  – GA4 이벤트 이름
 * @param {object} params – 매개변수(객체)
 */
export const trackEvent = (name, params = {}) => {
  window.gtag?.('event', name, params);
};
