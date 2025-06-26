// src/utils/analytics.js
export function trackEvent(name, params = {}) {
  if (window.gtag) {
    window.gtag('event', name, params);
  }
}
