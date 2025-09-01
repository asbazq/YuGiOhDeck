import React, { useState, useEffect } from 'react';
import { localImagePath, filenameOf } from '../common/imagePath';
import "../styles/AICardRecognizerModal.css";


const AICardRecognizerModal = ({ open, onClose, onPick }) => {
  const [file, setFile] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [preview, setPreview] = useState('');
  const [resp, setResp] = useState(null);

   useEffect(() => {
    if (!open) {
      setFile(null);
      setBusy(false);
      setError('');
      setPreview('');
      setResp(null);
    }
  }, [open]);

  if (!open) return null;

  const onFile = (e) => {
    const f = e.target.files?.[0];
    setFile(f || null);
    setResp(null);
    setError('');
    if (f) setPreview(URL.createObjectURL(f));
  };

const send = async () => {
  if (!file) { setError('이미지를 선택하세요.'); return; }
  setBusy(true); setError(''); setResp(null);
  try {
    const fd = new FormData();
    fd.append('file', file);

    // FastAPI 직접 호출이면 풀 URL, 프록시면 "/predict"
    const r = await fetch('/predict', { method: 'POST', body: fd });
    if (!r.ok) throw new Error(await r.text() || 'AI 서버 오류');

    const data = await r.json();

    // 이미지 URL 생성기: 로컬 정적 서버 경로 또는 ygoprodeck 중 택1
    // const imgFromId = (id) => `/images/${id}.jpg`; 
    // const imgFromId = (id) => `https://images.ygoprodeck.com/images/cards/${id}.jpg`;

    const normalize = (m) => {
      if (!m) return null;
      const kor = m.kor_name || m.korName;     // 서버가 snake_case 또는 camelCase로 줄 수 있어 대비
      const frameType = (m.frame_type || m.frameType || '').toLowerCase();
      // id 우선, 없다면 파일명에서 추론
      const idOrFile =
        m.id ??
        m.image_id ??
        m.imageUrl?.split('/').pop() ??
        m.image_url?.split('/').pop() ??
        filenameOf(m.imageUrl || m.image_url || '');
      const imageUrlLarge = localImagePath(idOrFile, 'large');
      const imageUrlSmall = localImagePath(idOrFile, 'small');

      return {
        ...m,
        name: kor || m.name,                    // name을 한글명으로 덮어쓰기
        frameType,
        imageUrlLarge,
        imageUrlSmall,
      };
    };

   const top1 = normalize(data?.top1);
   const top4 = Array.isArray(data?.top4) ? data.top4.map(normalize) : [];
    if (!top1) { setError('결과가 없습니다.'); return; }
    setResp({ top1, top4: top4.slice(0, 4) });
  } catch (e) {
    setError(e.message || '오류가 발생했습니다.');
  } finally {
    setBusy(false);
  }
};


  const pick = (c) => {
    if (!c) return;
    // onPick: 부모(App)에서 addCardToDeck(imageUrl, frameType, name)로 연결
    onPick({
      ...c,
      imageUrl: c.imageUrlLarge || c.imageUrl || localImagePath(c.id ?? c.image_url ?? c.imageUrl, 'large'),
    });
    onClose();
  };

  return (
    <div className="ai-modal-backdrop">
      <div className="ai-modal">
        <div className="ai-modal-header">
          <strong>AI 카드 판별</strong>
          <button className="ai-close" onClick={onClose}>×</button>
        </div>

        <div className="ai-modal-body">
          <div className="ai-upload-row">
            <input type="file" accept="image/*" onChange={onFile} className="ai-file-input" />
            <button className="ai-btn" onClick={send} disabled={busy}>예측</button>
          </div>

          {error && <div className="ai-error">{error}</div>}

            <div className="ai-main-row">
              {preview && (
                <div className="ai-overlay">
                  <div className="ai-img-title">업로드 이미지</div>
                  <img src={preview} alt="preview" />
                </div>
              )}

              {resp?.top1 && (
                <div className="ai-top1">
                  <div className="ai-top1-title">가장 유사한 카드</div>
                  <img
                    className="ai-top1-img"
                    src={resp.top1.imageUrlLarge}
                    alt={resp.top1.name}
                    onClick={() => pick(resp.top1)}
                  />
                  <div className="ai-hint">{resp.top1.name}</div>
                </div>
              )}
            </div>

          {resp?.top4?.length > 0 && (
            <div className="ai-top4-row">
              {resp.top4.map((c, i) => (
                <div className="ai-top4-item" key={i} onClick={() => pick(c)}>
                  <div className="ai-top4-thumb">
                    <img className="ai-top4-img" src={c.imageUrlSmall} alt={c.name} />
                  </div>
                  <div className="ai-top4-caption">{c.name}</div>
                </div>
              ))}
            </div>
          )}
        </div>

        <div className="ai-modal-footer">
          <button className="ai-btn-outline" onClick={onClose}>닫기</button>
        </div>
      </div>
    </div>
  );
};

export default AICardRecognizerModal;
