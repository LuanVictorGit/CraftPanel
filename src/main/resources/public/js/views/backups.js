import { api } from '../api.js';
import { toast, modal, formatBytes, escapeHtml, loadingModal } from '../ui.js';
import { i18n } from '../i18n.js';

export async function renderBackups(root, state) {
  const serverId = state.activeServerId;
  if (!serverId) {
    root.innerHTML = `<div class="card" style="text-align:center;padding:48px;"><p style="font-weight:600;">${i18n.t('selectServerPrompt')}</p></div>`;
    return;
  }

  root.innerHTML = `
    <div style="display:flex;flex-direction:column;gap:18px;">
      <div style="display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:10px;">
        <div>
          <h3 style="font-size:18px;font-weight:700;color:var(--text-main);">${i18n.t('backups')}</h3>
          <p style="font-size:13px;color:var(--text-muted);">${i18n.t('backupsSubtitle')}</p>
        </div>

        <button class="btn btn-primary" id="btn-open-create-backup">
          <svg style="width:15px;height:15px;" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"/><polyline points="17 21 17 13 7 13 7 21"/><polyline points="7 3 7 8 15 8"/></svg>
          ${i18n.t('createBackup')}
        </button>
      </div>

      <div class="card" style="padding:0;overflow:hidden;">
        <div id="backups-list-container">
          <div style="text-align:center;padding:32px;color:var(--text-muted);">${i18n.t('loading')}</div>
        </div>
      </div>
    </div>
  `;

  document.getElementById('btn-open-create-backup')?.addEventListener('click', () => promptCreateBackup(serverId));

  await loadBackups(serverId);
}

async function loadBackups(serverId) {
  try {
    const list = await api.get(`/api/servers/${serverId}/backups`);
    renderBackupsTable(serverId, list);
  } catch (e) {
    toast.error('Failed to load backups: ' + e.message);
  }
}

function renderBackupsTable(serverId, backups) {
  const container = document.getElementById('backups-list-container');
  if (!container) return;

  if (backups.length === 0) {
    container.innerHTML = `
      <div style="text-align:center;padding:48px;color:var(--text-muted);">
        <p style="font-weight:600;font-size:15px;margin-bottom:4px;">${i18n.t('noBackupsYet')}</p>
        <p style="font-size:13px;margin-bottom:16px;">${i18n.t('noBackupsDesc')}</p>
        <button class="btn btn-primary" id="btn-empty-backup">${i18n.t('createBackup')}</button>
      </div>
    `;
    document.getElementById('btn-empty-backup')?.addEventListener('click', () => promptCreateBackup(serverId));
    return;
  }

  container.innerHTML = `
    <div class="table-wrap">
      <table class="data-table">
        <thead>
          <tr>
            <th>${i18n.t('archiveName')}</th>
            <th>${i18n.t('notes')}</th>
            <th>${i18n.t('size')}</th>
            <th>${i18n.t('createdBy')}</th>
            <th>${i18n.t('timestamp')}</th>
            <th style="text-align:right;">${i18n.t('actions')}</th>
          </tr>
        </thead>
        <tbody>
          ${backups.map(b => `
            <tr>
              <td>
                <span style="font-weight:600;font-family:var(--font-mono);font-size:12.5px;color:var(--text-main);">${escapeHtml(b.filename)}</span>
              </td>
              <td style="font-size:12px;color:var(--text-muted);">${escapeHtml(b.notes || 'No description')}</td>
              <td style="font-size:12px;color:var(--text-muted);">${formatBytes(b.fileSizeBytes)}</td>
              <td style="font-size:12px;">${escapeHtml(b.createdBy || 'admin')}</td>
              <td style="font-size:12px;color:var(--text-muted);">${new Date(b.createdAt).toLocaleString()}</td>
              <td style="text-align:right;">
                <div style="display:inline-flex;gap:6px;">
                  <button class="btn btn-secondary btn-sm btn-restore-backup" data-id="${b.id}">
                    ${i18n.t('restoreBackup')}
                  </button>
                  <button class="btn btn-secondary btn-sm btn-dl-backup" data-id="${b.id}">
                    ${i18n.t('downloadBackup')}
                  </button>
                  <button class="btn btn-danger btn-sm btn-del-backup" data-id="${b.id}">
                    ${i18n.t('deleteBackup')}
                  </button>
                </div>
              </td>
            </tr>
          `).join('')}
        </tbody>
      </table>
    </div>
  `;

  container.querySelectorAll('.btn-restore-backup').forEach(btn => {
    btn.addEventListener('click', () => {
      const id = btn.dataset.id;
      modal.confirm(i18n.t('restoreBackup'), i18n.t('confirmRestore'), async () => {
        loadingModal.show(i18n.t('restoreBackup'), i18n.t('pleaseWait'), -1);
        try {
          await api.post(`/api/servers/${serverId}/backups/${id}/restore`);
          loadingModal.update(100, i18n.t('operationSuccess'));
          await new Promise(r => setTimeout(r, 400));
          loadingModal.hide();
          toast.success(i18n.t('operationSuccess'));
        } catch (e) {
          loadingModal.hide();
          toast.error(e.message);
        }
      }, true);
    });
  });

  container.querySelectorAll('.btn-dl-backup').forEach(btn => {
    btn.addEventListener('click', () => {
      const id = btn.dataset.id;
      window.open(`/api/servers/${serverId}/backups/${id}/download`, '_blank');
    });
  });

  container.querySelectorAll('.btn-del-backup').forEach(btn => {
    btn.addEventListener('click', () => {
      const id = btn.dataset.id;
      modal.confirm(i18n.t('delete'), 'Are you sure you want to delete this backup file?', async () => {
        loadingModal.show(i18n.t('delete'), i18n.t('pleaseWait'), -1);
        try {
          await api.delete(`/api/servers/${serverId}/backups/${id}`);
          loadingModal.hide();
          toast.success(i18n.t('operationSuccess'));
          loadBackups(serverId);
        } catch (e) {
          loadingModal.hide();
          toast.error(e.message);
        }
      }, true);
    });
  });
}

function promptCreateBackup(serverId) {
  modal.open({
    title: i18n.t('createBackup'),
    contentHtml: `
      <div class="form-group">
        <label class="form-label">${i18n.t('backupNote')}</label>
        <input type="text" id="backup-note-input" class="form-input" placeholder="e.g. Before update to 1.20.4">
      </div>
    `,
    footerButtons: [
      { text: i18n.t('cancel'), className: 'btn-secondary', onClick: (close) => close() },
      {
        text: i18n.t('createBackupBtn'),
        className: 'btn-primary',
        onClick: async (close) => {
          const notes = document.getElementById('backup-note-input').value.trim();
          close();
          loadingModal.show(i18n.t('createBackup'), i18n.t('pleaseWait'), -1);
          try {
            await api.post(`/api/servers/${serverId}/backups`, { notes });
            loadingModal.update(100, i18n.t('operationSuccess'));
            await new Promise(r => setTimeout(r, 400));
            loadingModal.hide();
            toast.success(i18n.t('operationSuccess'));
            setTimeout(() => loadBackups(serverId), 1000);
          } catch (e) {
            loadingModal.hide();
            toast.error(e.message);
          }
        }
      }
    ]
  });
}
