import React, { useEffect, useMemo, useState } from 'react';
import '../styles/BanlistNotice.css';

const PLACEHOLDER = '/back_image/101206057.jpg';

export default function BanlistNotice({
  open = true,
  onClose,
  changes: externalChanges,
  getThumbUrl,
  endpoint = '/cards/limit/notice',
}) {
  const K = {
    forbidden: '금지로 지정된 카드',
    limited: '제한으로 변경되는 카드',
    semilimited: '준제한으로 변경되는 카드',
    unlimited: '매수 제한이 해제되는 카드',
  };
  const LIMIT_NUM = { forbidden: 0, limited: 1, semilimited: 2, unlimited: 3 };

  const [changes, setChanges] = useState(Array.isArray(externalChanges) ? externalChanges : []);
  const [loading, setLoading] = useState(false);
  const [err, setErr] = useState('');

  // 외부 changes 존재 시 한 번 동기화 (배열 참조가 바뀌었을 때만)
  useEffect(() => {
    if (Array.isArray(externalChanges)) {
     setChanges(externalChanges);
    }
  }, [externalChanges]); // 부모가 같은 참조를 유지하면 불필요 렌더 없음

  // ✅ fetch 버전 (API_BASE 제거)
  useEffect(() => {
    const hasExternal = Array.isArray(externalChanges) && externalChanges.length > 0;
    if (!open) return;
    if (hasExternal) return;
    if (!endpoint) return;

    const ac = new AbortController();
    const run = async () => {
      setLoading(true);
      setErr('');

      const url = 'https://no86.xyz:8082/cards/notice';
      console.log('[BanlistNotice] GET', url);

      try {
        const res = await fetch(url, {
          method: 'GET',
          cache: 'no-store',
          signal: ac.signal,
        });

        if (res.status === 204) {
          setChanges([]);
          return;
        }

        if (!res.ok) {
          console.warn('[BanlistNotice] fetch failed', res.status);
          setErr(`HTTP ${res.status}`);
          setChanges([]);
          return;
        }

        const data = await res.json();
        setChanges(Array.isArray(data) ? data : []);
      } catch (e) {
        if (e.name !== 'AbortError') {
          console.error('[BanlistNotice] fetch error', e);
          setErr('네트워크 오류');
          setChanges([]);
        }
      } finally {
        setLoading(false);
      }
    };

    run();
    return () => ac.abort();
  }, [open, endpoint, /* 배열 대신 불리언으로 가드 */ Array.isArray(externalChanges) && externalChanges?.length > 0]);

  const groups = useMemo(() => {
    const g = { forbidden: [], limited: [], semilimited: [], unlimited: [] };
    for (const c of changes) if (g[c.toType]) g[c.toType].push(c);
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
            const displayName = c.kor_name || c.korName || c.name || c.cardName;
            const TYPE_KO = { forbidden: '금지', limited: '제한', semilimited: '준제한', unlimited: '무제한' };

            const fromType = c.fromType ?? 'unlimited';
            const toType = c.toType;
            const fromN = LIMIT_NUM[fromType];
            const toN = LIMIT_NUM[toType];

            const thumb =
              c.thumbUrl ||
              c.imageUrlSmall ||
              c.imageUrl ||
              (typeof getThumbUrl === 'function' ? getThumbUrl(displayName) : null) ||
              PLACEHOLDER;

            return (
              <div className="ban-card" key={`${displayName}-${i}`}>
                <div
                  className="change-chip-ovl"
                  title={`${TYPE_KO[fromType]} → ${TYPE_KO[toType]}`}
                  aria-label={`${TYPE_KO[fromType]}에서 ${TYPE_KO[toType]}로 변경`}
                >
                  <span className={`level-ball ${fromType}`}>{fromN}</span>
                  <span className="level-arrow" aria-hidden="true" />
                  <span className={`level-ball ${toType}`}>{toN}</span>
                </div>

                <div className="ban-card__thumb-wrap">
                  <img
                    className="ban-card__thumb"
                    src={thumb}
                    alt={displayName}
                    loading="lazy"
                    onError={(e) => { e.currentTarget.src = PLACEHOLDER; }}
                  />
                </div>

                <div className="ban-card__name">{displayName}</div>
              </div>
            );
          })}
        </div>
      </section>
    );
  };

  if (loading) return <div className="notice-empty">불러오는 중…</div>;
  if (err)      return <div className="notice-empty">{err}</div>;

  return (
    <div className="ban-wrap">
      {renderSection('forbidden')}
      {renderSection('limited')}
      {renderSection('semilimited')}
      {renderSection('unlimited')}
    </div>
  );
}
