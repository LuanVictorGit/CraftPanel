import { i18n } from './i18n.js';

if (!document.getElementById('craftpanel-spin-styles')) {
  const style = document.createElement('style');
  style.id = 'craftpanel-spin-styles';
  style.textContent = `
    @keyframes craftpanel-spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }
    @keyframes craftpanel-pulse { 0%, 100% { opacity: 0.4; } 50% { opacity: 1; } }
  `;
  document.head.appendChild(style);
}

export const toast = {
  show(message, type = 'info', duration = 4000) {
    const container = document.getElementById('toast-container');
    if (!container) return;

    const el = document.createElement('div');
    el.className = `toast ${type}`;

    let icon = '';
    if (type === 'success') {
      icon = '<svg style="width:18px;height:18px;color:#059669;flex-shrink:0;" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>';
    } else if (type === 'error') {
      icon = '<svg style="width:18px;height:18px;color:#DC2626;flex-shrink:0;" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>';
    } else {
      icon = '<svg style="width:18px;height:18px;color:#2563EB;flex-shrink:0;" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>';
    }

    el.innerHTML = `
      ${icon}
      <div style="flex:1;font-size:13px;font-weight:500;color:#0F172A;line-height:1.4;">${escapeHtml(message)}</div>
    `;

    container.appendChild(el);

    setTimeout(() => {
      el.style.opacity = '0';
      el.style.transform = 'translateY(10px)';
      el.style.transition = 'all 0.2s ease';
      setTimeout(() => el.remove(), 200);
    }, duration);
  },

  success(msg) { this.show(msg, 'success'); },
  error(msg) { this.show(msg, 'error'); },
  info(msg) { this.show(msg, 'info'); }
};

export const loadingModal = {
  el: null,
  show(title = '', message = '', progress = -1) {
    let host = document.getElementById('loading-modal-host');
    if (!host) {
      host = document.createElement('div');
      host.id = 'loading-modal-host';
      document.body.appendChild(host);
    }
    host.style.display = 'block';

    const displayTitle = title || (i18n.t('operationInProgress') || 'Operation in progress');
    const displayMsg = message || (i18n.t('doNotClosePage') || 'Please do not close or leave the page until finished.');
    const pctText = progress >= 0 ? `${Math.round(progress)}%` : '';
    const barWidth = progress >= 0 ? `${Math.min(Math.max(progress, 0), 100)}%` : '100%';
    const isIndeterminate = progress < 0;

    host.innerHTML = `
      <div style="position:fixed;top:0;left:0;right:0;bottom:0;background:rgba(15,23,42,0.85);backdrop-filter:blur(6px);z-index:99999;display:flex;align-items:center;justify-content:center;padding:20px;user-select:none;">
        <div style="background:var(--bg-card);border:1px solid var(--border);border-radius:12px;padding:32px 28px;max-width:440px;width:100%;box-shadow:0 25px 50px -12px rgba(0,0,0,0.5);display:flex;flex-direction:column;align-items:center;text-align:center;gap:16px;">
          <div style="width:56px;height:56px;position:relative;display:flex;align-items:center;justify-content:center;">
            <svg style="width:56px;height:56px;animation:craftpanel-spin 1.2s linear infinite;color:var(--brand-primary);" viewBox="0 0 50 50">
              <circle cx="25" cy="25" r="20" fill="none" stroke="currentColor" stroke-width="4" stroke-linecap="round" stroke-dasharray="80" stroke-dashoffset="60" style="opacity:0.25;"/>
              <circle cx="25" cy="25" r="20" fill="none" stroke="currentColor" stroke-width="4" stroke-linecap="round" stroke-dasharray="90" stroke-dashoffset="30"/>
            </svg>
            <div style="position:absolute;display:flex;align-items:center;justify-content:center;color:var(--brand-primary);">
              <svg style="width:20px;height:20px;" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/></svg>
            </div>
          </div>

          <div>
            <h3 id="loading-modal-title" style="font-size:17px;font-weight:700;color:var(--text-main);margin:0 0 6px 0;">${escapeHtml(displayTitle)}</h3>
            <p id="loading-modal-msg" style="font-size:13px;color:var(--text-muted);margin:0;line-height:1.4;">${escapeHtml(displayMsg)}</p>
          </div>

          <div style="width:100%;background:var(--bg-subtle);height:8px;border-radius:4px;overflow:hidden;position:relative;">
            <div id="loading-modal-bar" style="height:100%;width:${barWidth};background:var(--brand-primary);border-radius:4px;transition:width 0.3s ease;${isIndeterminate ? 'animation:craftpanel-pulse 1.5s ease-in-out infinite;' : ''}"></div>
          </div>

          <div id="loading-modal-pct" style="font-size:11px;font-weight:600;color:var(--text-muted);margin-top:-6px;">
            ${pctText || (isIndeterminate ? (i18n.t('pleaseWait') || 'Please wait...') : '')}
          </div>
        </div>
      </div>
    `;
    this.el = host;
  },

  update(progress, message = null) {
    if (!this.el) return;
    const bar = document.getElementById('loading-modal-bar');
    const msgEl = document.getElementById('loading-modal-msg');
    const pctEl = document.getElementById('loading-modal-pct');

    if (progress >= 0 && bar) {
      const pct = Math.min(Math.max(progress, 0), 100);
      bar.style.width = `${pct}%`;
      bar.style.animation = 'none';
      if (pctEl) pctEl.innerText = `${Math.round(pct)}%`;
    }
    if (message && msgEl) {
      msgEl.innerText = message;
    }
  },

  hide() {
    if (this.el) {
      this.el.style.display = 'none';
      this.el.innerHTML = '';
      this.el = null;
    }
    const host = document.getElementById('loading-modal-host');
    if (host) {
      host.style.display = 'none';
      host.innerHTML = '';
    }
  }
};

export const modal = {
  open({ title, contentHtml, footerButtons = [], wide = false, onClose = null }) {
    const host = document.getElementById('modal-host');
    if (!host) return;

    host.innerHTML = `
      <div class="modal-overlay" id="active-modal-overlay">
        <div class="modal-card ${wide ? 'wide' : ''}">
          <div class="modal-header">
            <h3 class="card-title">${escapeHtml(title)}</h3>
            <button class="btn btn-icon" id="modal-close-btn" title="${i18n.t('close') || 'Close'}">
              <svg style="width:18px;height:18px;" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
            </button>
          </div>
          <div class="modal-body" id="modal-body-content">
            ${contentHtml}
          </div>
          ${footerButtons.length > 0 ? `
            <div class="modal-footer" id="modal-footer-content"></div>
          ` : ''}
        </div>
      </div>
    `;
    host.style.display = 'block';

    const close = () => {
      host.style.display = 'none';
      host.innerHTML = '';
      if (onClose) onClose();
    };

    document.getElementById('modal-close-btn')?.addEventListener('click', close);
    document.getElementById('active-modal-overlay')?.addEventListener('click', (e) => {
      if (e.target.id === 'active-modal-overlay') close();
    });

    const footer = document.getElementById('modal-footer-content');
    if (footer && footerButtons.length > 0) {
      footerButtons.forEach(btn => {
        const button = document.createElement('button');
        button.className = `btn ${btn.className || 'btn-secondary'}`;
        button.innerText = btn.text;
        button.addEventListener('click', () => {
          if (btn.onClick) {
            btn.onClick(close);
          } else {
            close();
          }
        });
        footer.appendChild(button);
      });
    }

    return { close };
  },

  confirm(title, message, onConfirm, isDestructive = false) {
    this.open({
      title,
      contentHtml: `<p style="font-size:14px;color:#475569;">${escapeHtml(message)}</p>`,
      footerButtons: [
        { text: i18n.t('cancel') || 'Cancel', className: 'btn-secondary', onClick: (close) => close() },
        {
          text: isDestructive ? (i18n.t('confirmAndExecute') || 'Confirm and Execute') : (i18n.t('confirm') || 'Confirm'),
          className: isDestructive ? 'btn-danger' : 'btn-primary',
          onClick: (close) => {
            close();
            onConfirm();
          }
        }
      ]
    });
  }
};

export function escapeHtml(str) {
  if (!str) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

export function formatBytes(bytes) {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
}

export function formatUptime(seconds) {
  if (!seconds || seconds <= 0) return i18n.t('inactive') || 'Inactive';
  const d = Math.floor(seconds / (3600 * 24));
  const h = Math.floor((seconds % (3600 * 24)) / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = seconds % 60;
  if (d > 0) return `${d}d ${h}h ${m}m`;
  if (h > 0) return `${h}h ${m}m ${s}s`;
  if (m > 0) return `${m}m ${s}s`;
  return `${s}s`;
}
