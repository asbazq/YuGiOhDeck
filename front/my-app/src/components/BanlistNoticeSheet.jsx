import React, { useCallback, useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import BanlistNotice from './BanlistNotice';
import '../styles/BanlistNoticeSheet.css';

export default function BanlistNoticeSheet({ open, onClose, onDismissToday, getThumbUrl }) {
  const [dismissTodayChecked, setDismissTodayChecked] = useState(false);

  useEffect(() => {
    if (!open) {
      setDismissTodayChecked(false);
    }
  }, [open]);

  const handleClose = useCallback(() => {
    if (dismissTodayChecked && typeof onDismissToday === 'function') {
      onDismissToday();
      return;
    }

    onClose();
  }, [dismissTodayChecked, onClose, onDismissToday]);

  useEffect(() => {
    const onKeyDown = (event) => {
      if (event.key === 'Escape') {
        handleClose();
      }
    };

    if (open) {
      window.addEventListener('keydown', onKeyDown);
    }

    return () => window.removeEventListener('keydown', onKeyDown);
  }, [open, handleClose]);

  if (!open) {
    return null;
  }

  return createPortal(
    <div className="notice-sheet-backdrop" onClick={handleClose}>
      <div className="notice-sheet" onClick={(event) => event.stopPropagation()}>
        <div className="notice-sheet__header">
          <div className="notice-sheet__header-copy">
            <div className="notice-sheet__eyebrow">Limit Regulation</div>
            <h2>리미트 레귤레이션 변경 공지</h2>
            <p>최신 금제 변경 카드를 덱 빌더 화면 톤에 맞춰 정리했습니다.</p>
          </div>
          <button
            type="button"
            className="notice-sheet__close"
            onClick={handleClose}
            aria-label="공지 닫기"
          >
            닫기
          </button>
        </div>

        <div className="notice-sheet__body">
          <BanlistNotice
            open={open}
            getThumbUrl={getThumbUrl}
            endpoint="/cards/notice"
          />
        </div>

        <div className="notice-sheet__footer">
          <label className="notice-sheet__dismiss">
            <input
              type="checkbox"
              checked={dismissTodayChecked}
              onChange={(event) => setDismissTodayChecked(event.target.checked)}
            />
            <span>오늘 하루 보지 않기</span>
          </label>
          <button type="button" className="notice-sheet__confirm" onClick={handleClose}>
            확인
          </button>
        </div>
      </div>
    </div>,
    document.body
  );
}
