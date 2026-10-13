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

export const modal = {
  open({ title, contentHtml, footerButtons = [], wide = false, onClose = null }) {
    const host = document.getElementById('modal-host');
    if (!host) return;

    host.innerHTML = `
      <div class="modal-overlay" id="active-modal-overlay">
        <div class="modal-card ${wide ? 'wide' : ''}">
          <div class="modal-header">
            <h3 class="card-title">${escapeHtml(title)}</h3>
            <button class="btn btn-icon" id="modal-close-btn" title="Fechar">
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
        { text: 'Cancelar', className: 'btn-secondary', onClick: (close) => close() },
        {
          text: isDestructive ? 'Confirmar e Executar' : 'Confirmar',
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
  if (!seconds || seconds <= 0) return 'Inativo';
  const d = Math.floor(seconds / (3600 * 24));
  const h = Math.floor((seconds % (3600 * 24)) / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = seconds % 60;
  if (d > 0) return `${d}d ${h}h ${m}m`;
  if (h > 0) return `${h}h ${m}m ${s}s`;
  if (m > 0) return `${m}m ${s}s`;
  return `${s}s`;
}
