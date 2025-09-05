import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { trackEvent, sendPageView } from '../utils/analytics';
import LimitBoard from '../components/LimitBoard';
import alertCard from '../img/black-magician-girl-card-8bit.png';
import '../styles/Message.css';
import '../styles/LimitBoard.css';
import '../styles/Menu.css';
import '../styles/Button.css';

function LimitPage() {
  const [message, setMessage] = useState('');
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const navigate = useNavigate();

  useEffect(() => {
    sendPageView(window.location.pathname);
  }, []);

  const showMessage = (msg) => {
    setMessage(msg);
    setTimeout(() => setMessage(''), 2300);
  };

  return (
    <>
      <button
        className="menu-button"
        onClick={() => setIsMenuOpen(true)}
        aria-label="메뉴 열기"
      >
        <span></span>
        <span></span>
        <span></span>
      </button>
      <div
        className={`menu-overlay ${isMenuOpen ? 'open' : ''}`}
        onClick={() => setIsMenuOpen(false)}
      ></div>
      <div className={`side-menu ${isMenuOpen ? 'open' : ''}`}>
        <div className="board-switch">
          <button
            onClick={() => {
              navigate('/limit');
              setIsMenuOpen(false);
              trackEvent('view_limit_board', { board: 'limit' });
            }}
          >
            리미트 레귤레이션
          </button>
          <button
            onClick={() => {
              navigate('/');
              setIsMenuOpen(false);
              trackEvent('switch_board', { board: 'deck' });
            }}
          >
            덱 빌딩
          </button>
          <button
            onClick={() => {
              navigate('/admin/queue');
              setIsMenuOpen(false);
              trackEvent('switch_board', { board: 'admin' });
            }}
          >
            관리자
          </button>
        </div>
      </div>
      <div id="msgWrap" style={{ display: message ? 'flex' : 'none' }}>
        <div id="msgBubble">{message}</div>
        <img src={alertCard} alt="alert" className="msgImg" />
      </div>
      <div className="container">
        <LimitBoard showMessage={showMessage} />
      </div>
    </>
  );
}

export default LimitPage;
