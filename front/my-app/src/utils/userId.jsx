export function getOrCreateUserId() {
  // 디바이스 ID (영구)
  let deviceId = null;
  try {
    deviceId = localStorage.getItem('device_id'); // 브라우저/도메인 공통, 장치 식별
    if (!deviceId) {
      const rnd = (crypto?.randomUUID?.() || Math.random().toString(36).slice(2)) + Date.now().toString(36);
      deviceId = 'd-' + rnd;
      localStorage.setItem('device_id', deviceId);
    }
  } catch {
    // 무시
  }

  // 탭 ID (탭 생명주기)
  let tabId = null;
  try {
    tabId = sessionStorage.getItem('tab_id'); // 탭마다 고유
    if (!tabId) {
      const rnd = (crypto?.randomUUID?.() || Math.random().toString(36).slice(2)) + Date.now().toString(36);
      tabId = 't-' + rnd;
      sessionStorage.setItem('tab_id', tabId);
    }
  } catch {
    // 무시
  }

  // 조합 (둘 다 실패하면 폴백)
  const uid = [deviceId, tabId].filter(Boolean).join(':') || ('u-' + Date.now());
  return uid;
}
