import React from 'react';
import '../styles/OrientationModal.css';

export default function OrientationModal({ open, onAllow, onClose }) {
  if (!open) return null;
  return (
    <div className="orientation-backdrop" onClick={onClose}>
      <div className="orientation-box" onClick={e => e.stopPropagation()}>
        <p>센서를 사용하시겠습니까?</p>
        <div className="orientation-buttons">
          <button className="action-button" onClick={onAllow}>허용</button>
          <button className="action-button" onClick={onClose}>취소</button>
        </div>
      </div>
    </div>
  );
}