import React, { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { trackEvent, sendPageView } from '../utils/analytics';
import LimitBoardModern from './LimitBoardModern';
import '../styles/LimitBoardModern.css';

function LimitPageModern() {
  const [message, setMessage] = useState('');
  const navigate = useNavigate();
  const messageTimerRef = useRef(null);

  useEffect(() => {
    sendPageView(window.location.pathname);

    return () => {
      if (messageTimerRef.current) {
        window.clearTimeout(messageTimerRef.current);
      }
    };
  }, []);

  const showMessage = (msg) => {
    setMessage(msg);
    if (messageTimerRef.current) {
      window.clearTimeout(messageTimerRef.current);
    }
    messageTimerRef.current = window.setTimeout(() => {
      setMessage('');
      messageTimerRef.current = null;
    }, 2300);
  };

  return (
    <div className="limit-page-modern">
      {message && <div className="limit-page-modern__toast">{message}</div>}

      <div className="limit-page-modern__shell">
        <aside className="limit-page-modern__hero">
          <div className="limit-page-modern__eyebrow">Limit Regulation</div>
          <h1 className="limit-page-modern__title">Forbidden and Limited</h1>
          <p className="limit-page-modern__description">
            금지, 제한, 준제한 카드 목록을 메인 화면과 같은 패널 시스템으로 정리했습니다.
          </p>

          <div className="limit-page-modern__nav">
            <button
              type="button"
              className="limit-page-modern__nav-button"
              onClick={() => {
                navigate('/');
                trackEvent('switch_board', { board: 'deck' });
              }}
            >
              Deck Builder
            </button>
            <button
              type="button"
              className="limit-page-modern__nav-button is-active"
              onClick={() => trackEvent('view_limit_board', { board: 'limit' })}
            >
              Limit Board
            </button>
            <button
              type="button"
              className="limit-page-modern__nav-button"
              onClick={() => {
                navigate('/admin/queue');
                trackEvent('switch_board', { board: 'admin' });
              }}
            >
              Admin
            </button>
          </div>

          <div className="limit-page-modern__note">
            <strong>Quick Notes</strong>
            <p>탭을 눌러 규제 등급을 바꾸고, 스크롤 끝에서 다음 목록을 불러옵니다.</p>
            <p>보조 패드는 숨김과 표시를 반복할 수 있습니다.</p>
            <p>전체 톤은 메인 덱 빌더와 같은 밝은 유리 패널 스타일입니다.</p>
          </div>
        </aside>

        <main className="limit-page-modern__content">
          <LimitBoardModern showMessage={showMessage} />
        </main>
      </div>
    </div>
  );
}

export default LimitPageModern;
