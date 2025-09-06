// utils/analytics.js
/**
 * SPA 페이지 이동 시 호출
 */
const debug = (...args) => {
  if (process.env.NODE_ENV !== 'production') {
    // eslint-disable-next-line no-console
    console.log('[ga]', ...args);
  }
};

const ensureParams = (params = {}) => ({
  engagement_time_msec: 1,
  debug_mode: process.env.NODE_ENV !== 'production',
  ...params,
});

export const sendPageView = (path) => {
  const payload = ensureParams({
    page_location: window.location.href,
    page_path: path,              // ex) '/deck'
    page_title: document.title,
  });
  if (typeof window.gtag === 'function') {
    window.gtag('event', 'page_view', payload);
  } else {
    debug('gtag not ready: page_view', payload);
  }
};

/**
 * 임의 이벤트 전송
 * @param {string} name  – GA4 이벤트 이름
 * @param {object} params – 매개변수(객체)
 */
export const trackEvent = (name, params = {}) => {
  const payload = ensureParams(params);
  if (typeof window.gtag === 'function') {
    window.gtag('event', name, payload);
  } else {
    debug('gtag not ready:', name, payload);
  }
};
