import { api } from '../api.js';
import { toast, escapeHtml } from '../ui.js';
import { i18n } from '../i18n.js';

export async function renderAudit(root, state) {
  root.innerHTML = `
    <div style="display:flex;flex-direction:column;gap:18px;">
      <div style="display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:10px;">
        <div>
          <h3 style="font-size:18px;font-weight:700;color:var(--text-main);">${i18n.t('auditLogs')}</h3>
          <p style="font-size:13px;color:var(--text-muted);">${i18n.t('auditSubtitle')}</p>
        </div>

        <div style="display:flex;align-items:center;gap:10px;width:320px;">
          <input type="text" id="audit-search-input" class="form-input" placeholder="${i18n.t('search')} logs...">
          <button class="btn btn-secondary btn-sm" id="btn-audit-refresh">${i18n.t('refresh')}</button>
        </div>
      </div>

      <div class="card" style="padding:0;overflow:hidden;">
        <div id="audit-table-container">
          <div style="text-align:center;padding:32px;color:var(--text-muted);">${i18n.t('loading')}</div>
        </div>
      </div>
    </div>
  `;

  document.getElementById('btn-audit-refresh')?.addEventListener('click', () => loadAuditLogs());
  document.getElementById('audit-search-input')?.addEventListener('input', (e) => loadAuditLogs(e.target.value));

  await loadAuditLogs();
}

async function loadAuditLogs(search = '') {
  try {
    const logs = await api.get('/api/audit', { search: search || undefined, limit: 100 });
    renderAuditTable(logs);
  } catch (e) {
    toast.error('Failed to load audit logs: ' + e.message);
  }
}

function renderAuditTable(logs) {
  const container = document.getElementById('audit-table-container');
  if (!container) return;

  if (logs.length === 0) {
    container.innerHTML = `<div style="text-align:center;padding:48px;color:var(--text-muted);">${i18n.t('noAuditEvents')}</div>`;
    return;
  }

  container.innerHTML = `
    <div class="table-wrap">
      <table class="data-table">
        <thead>
          <tr>
            <th>${i18n.t('status')}</th>
            <th>${i18n.t('action')}</th>
            <th>${i18n.t('user')}</th>
            <th>${i18n.t('target')}</th>
            <th>${i18n.t('details')}</th>
            <th>${i18n.t('ipAddress')}</th>
            <th>${i18n.t('timestamp')}</th>
          </tr>
        </thead>
        <tbody>
          ${logs.map(l => `
            <tr>
              <td>
                <span class="badge ${l.status === 'SUCCESS' ? 'badge-green' : 'badge-rose'}">
                  ${l.status}
                </span>
              </td>
              <td><span style="font-family:var(--font-mono);font-size:12px;font-weight:600;">${escapeHtml(l.action)}</span></td>
              <td style="font-weight:600;">${escapeHtml(l.username || 'System')}</td>
              <td style="font-size:12px;color:var(--text-muted);">${escapeHtml(l.targetType || '')} ${escapeHtml(l.targetId || '')}</td>
              <td style="font-size:12px;max-width:280px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${escapeHtml(l.details || '')}</td>
              <td style="font-family:var(--font-mono);font-size:11px;color:var(--text-muted);">${escapeHtml(l.ipAddress || '-')}</td>
              <td style="font-size:12px;color:var(--text-muted);">${new Date(l.createdAt).toLocaleString()}</td>
            </tr>
          `).join('')}
        </tbody>
      </table>
    </div>
  `;
}
