import React, { useEffect, useMemo, useState } from 'react';
import '../styles/BanlistNoticeModern.css';

const PLACEHOLDER = '/back_image/101206057.jpg';
const SECTION_LABELS = {
  forbidden: '금지로 지정된 카드',
  limited: '제한으로 변경되는 카드',
  semilimited: '준제한으로 변경되는 카드',
  unlimited: '매수 제한이 해제되는 카드',
};

function getNoticeSortGroup(frameType = '') {
  if (frameType === 'spell') {
    return 1;
  }

  if (frameType === 'trap') {
    return 2;
  }

  return 0;
}

function LimitMark({ type, tone }) {
  const markClass = type === 'forbidden' ? 'is-forbidden' : '';
  const markText =
    type === 'limited' ? '1' : type === 'semilimited' ? '2' : type === 'unlimited' ? '3' : '';

  return (
    <span
      className={`limit-mark ${tone} ${markClass}`}
      aria-label={type}
      title={type}
    >
      {markText}
    </span>
  );
}

export default function BanlistNotice({
  open = true,
  changes: externalChanges,
  getThumbUrl,
  endpoint = '/cards/notice',
}) {
  const [changes, setChanges] = useState(Array.isArray(externalChanges) ? externalChanges : []);
  const [loading, setLoading] = useState(false);
  const [err, setErr] = useState('');

  useEffect(() => {
    if (Array.isArray(externalChanges)) {
      setChanges(externalChanges);
    }
  }, [externalChanges]);

  useEffect(() => {
    const hasExternal = Array.isArray(externalChanges) && externalChanges.length > 0;
    if (!open || hasExternal || !endpoint) {
      return undefined;
    }

    const ac = new AbortController();

    const run = async () => {
      setLoading(true);
      setErr('');

      const url = /^https?:\/\//i.test(endpoint)
        ? endpoint
        : endpoint.startsWith('/')
          ? endpoint
          : `/${endpoint}`;

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
          setErr(`HTTP ${res.status}`);
          setChanges([]);
          return;
        }

        const data = await res.json();
        setChanges(Array.isArray(data) ? data : []);
      } catch (e) {
        if (e.name !== 'AbortError') {
          setErr('네트워크 오류');
          setChanges([]);
        }
      } finally {
        setLoading(false);
      }
    };

    run();
    return () => ac.abort();
  }, [open, endpoint, externalChanges]);

  const groups = useMemo(() => {
    const grouped = { forbidden: [], limited: [], semilimited: [], unlimited: [] };
    for (const change of changes) {
      if (grouped[change.toType]) {
        grouped[change.toType].push(change);
      }
    }

    Object.keys(grouped).forEach((key) => {
      grouped[key].sort((left, right) => {
        const groupDiff =
          getNoticeSortGroup(left.frameType) - getNoticeSortGroup(right.frameType);
        if (groupDiff !== 0) {
          return groupDiff;
        }

        const leftName = (left.kor_name || left.korName || left.name || left.cardName || '').trim();
        const rightName = (right.kor_name || right.korName || right.name || right.cardName || '').trim();
        return leftName.localeCompare(rightName, 'ko');
      });
    });

    return grouped;
  }, [changes]);

  const renderSection = (toType) => {
    const list = groups[toType] || [];
    if (!list.length) {
      return null;
    }

    return (
      <section className="ban-sec" key={toType}>
        <div className="ban-sec__title">
          <div>
            <div className="ban-sec__eyebrow">{toType.toUpperCase()}</div>
            <strong>{SECTION_LABELS[toType]}</strong>
          </div>
          <span className="ban-sec__count">{list.length}장</span>
        </div>

        <div className="ban-grid">
          {list.map((change, index) => {
            const displayName = change.kor_name || change.korName || change.name || change.cardName;
            const fromType = change.fromType ?? 'unlimited';
            const thumb =
              change.thumbUrl ||
              change.imageUrlSmall ||
              change.imageUrl ||
              (typeof getThumbUrl === 'function' ? getThumbUrl(displayName) : null) ||
              PLACEHOLDER;

            return (
              <div className="ban-card" key={`${displayName}-${index}`}>
                <div className="ban-card__badges">
                  <LimitMark type={fromType} tone="from" />
                  <span className="arrow" aria-hidden="true">→</span>
                  <LimitMark type={change.toType} tone="to" />
                </div>
                <div className="ban-card__thumb-wrap">
                  <img
                    className="ban-card__thumb"
                    src={thumb}
                    alt={displayName}
                    loading="lazy"
                    onError={(e) => {
                      e.currentTarget.src = PLACEHOLDER;
                    }}
                  />
                </div>
                <div className="ban-card__transition">
                  <span>{fromType}</span>
                  <span>to</span>
                  <span>{change.toType}</span>
                </div>
                <div className="ban-card__name">{displayName}</div>
              </div>
            );
          })}
        </div>
      </section>
    );
  };

  if (loading) {
    return <div className="notice-empty">불러오는 중...</div>;
  }

  if (err) {
    return <div className="notice-empty">{err}</div>;
  }

  return (
    <div className="ban-wrap">
      {renderSection('forbidden')}
      {renderSection('limited')}
      {renderSection('semilimited')}
      {renderSection('unlimited')}
      {!changes.length && <div className="notice-empty">표시할 변경 공지가 없습니다.</div>}
    </div>
  );
}
