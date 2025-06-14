import React from 'react';
import '../styles/QueueModal.css';

export default function QueueModal({ open, position, onClose }) {
  if (!open) return null;
  return (
    <div className="qm-backdrop" onClick={onClose}>
      <div className="qm-box" onClick={e=>e.stopPropagation()}>
        <h2>잠시만 기다려 주세요…</h2>
        <p className="qm-pos">{position} 번째 순서입니다</p>
        <div className="qm-spinner" />
      </div>
    </div>
  );
}
