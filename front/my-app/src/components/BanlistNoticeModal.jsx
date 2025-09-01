import React, { useEffect } from 'react';
import BanlistNotice from './BanlistNotice';
import '../styles/BanlistNoticeModal.css';
import { createPortal } from 'react-dom';

export default function BanlistNoticeModal({ open, onClose, changes = [], getThumbUrl }) {
  useEffect(() => {
    const onKey = (e) => { if (e.key === 'Escape') onClose(); };
    if (open) window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, onClose]);

  if (!open) return null;

  const body = (
    <div className="notice-backdrop" onClick={onClose}>
      <div className="notice-modal" onClick={(e) => e.stopPropagation()}>
        <div className="notice-header">
          <h2>리미트 레귤레이션 변경 공지</h2>
          <button className="notice-close" onClick={onClose}>×</button>
        </div>
        <div className="notice-body">
          {Array.isArray(changes) && changes.length > 0 ? (
            <BanlistNotice changes={changes} getThumbUrl={getThumbUrl} />
          ) : (
            <div className="notice-empty">최근 변경 내역이 없습니다.</div>
          )}
        </div>
      </div>
    </div>
  );

  // 포털: document.body로 렌더 → 다른 레이아웃/오버플로우 영향 안 받음
  return createPortal(body, document.body);
}
