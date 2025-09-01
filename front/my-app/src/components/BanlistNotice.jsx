import React, { useMemo } from 'react';
import '../styles/BanlistNotice.css';

/**
 * changes: Array<{ cardName: string, fromType: 'forbidden'|'limited'|'semilimited'|'unlimited', toType: same }>
 * getThumbUrl?: (cardName) => string | null
 *   - 썸네일 URL을 결정하는 함수. 주입하지 않으면 placeholder 사용.
 *   - 로컬 서버 이미지로 쓰려면: (name) => `/cards/cardinfo?cardName=${encodeURIComponent(name)}`
 *     로 id를 얻어 /images/small/{id}.jpg 를 만들도록 별도 래퍼에서 처리하세요.
 */
export default function BanlistNotice({ changes = [], getThumbUrl }) {
  const K = {
    forbidden:   '금지로 지정된 카드',
    limited:     '제한으로 변경되는 카드',
    semilimited: '준제한으로 변경되는 카드',
    unlimited:   '매수 제한이 해제되는 카드',
  };
  const LIMIT_NUM = { forbidden: 0, limited: 1, semilimited: 2, unlimited: 3 };
  const PLACEHOLDER = '/back_image/101206057.jpg';

  const groups = useMemo(() => {
    const g = { forbidden: [], limited: [], semilimited: [], unlimited: [] };
    for (const c of changes) {
      if (g[c.toType]) g[c.toType].push(c);
    }
    return g;
  }, [changes]);

  const renderSection = (toType) => {
    const list = groups[toType] || [];
    if (!list.length) return null;

    return (
      <section className="ban-sec" key={toType}>
        <div className="ban-sec__title">
          {K[toType]} <span className="ban-sec__count">({list.length}장)</span>
        </div>

        <div className="ban-grid">
          {list.map((c, i) => {
            const fromN = LIMIT_NUM[c.fromType ?? 'unlimited'];
            const toN   = LIMIT_NUM[c.toType];
            const thumb = (typeof getThumbUrl === 'function' && getThumbUrl(c.cardName)) || PLACEHOLDER;

            return (
              <div className="ban-card" key={`${c.cardName}-${i}`}>
                <div className="ban-card__badges">
                  <span className={`badge from n${fromN}`}>{fromN}</span>
                  <span className="arrow">→</span>
                  <span className={`badge to n${toN}`}>{toN}</span>
                </div>
                <div className="ban-card__thumb-wrap">
                  <img className="ban-card__thumb" src={thumb} alt={c.cardName} />
                </div>
                <div className="ban-card__name">{c.cardName}</div>
              </div>
            );
          })}
        </div>
      </section>
    );
  };

  return (
    <div className="ban-wrap">
      {renderSection('forbidden')}
      {renderSection('limited')}
      {renderSection('semilimited')}
      {renderSection('unlimited')}
    </div>
  );
}
