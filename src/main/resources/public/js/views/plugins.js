import { api } from '../api.js';
import { toast, modal, formatBytes, escapeHtml } from '../ui.js';
import { i18n } from '../i18n.js';

export async function renderPlugins(root, state) {
  const serverId = state.activeServerId;
  if (!serverId) {
    root.innerHTML = `<div class="card" style="text-align:center;padding:48px;"><p style="font-weight:600;">${i18n.t('selectServerPrompt')}</p></div>`;
    return;
  }

  root.innerHTML = `
    <div style="display:flex;flex-direction:column;gap:16px;">
      <!-- Action Bar -->
      <div style="display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:10px;">
        <div>
          <h3 style="font-size:18px;font-weight:700;color:var(--text-main);">${i18n.t('pluginManager')}</h3>
          <p style="font-size:13px;color:var(--text-muted);">Manage installed Bukkit, Spigot, Paper and Folia plugins</p>
        </div>

        <div style="display:flex;align-items:center;gap:8px;">
          <button class="btn btn-secondary btn-sm" id="btn-goto-modrinth">
            <svg style="width:14px;height:14px;" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="m10 15 5-3-5-3v6Z"/></svg>
            ${i18n.t('searchModrinth')}
          </button>
          <label class="btn btn-primary btn-sm" style="cursor:pointer;">
            <svg style="width:14px;height:14px;" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" x2="12" y1="3" y2="15"/></svg>
            ${i18n.t('installLocalJar')}
            <input type="file" id="plugins-upload-input" accept=".jar" style="display:none;">
          </label>
        </div>
      </div>

      <!-- Plugins List -->
      <div class="card" style="padding:0;overflow:hidden;">
        <div id="plugins-list-container">
          <div style="text-align:center;padding:32px;color:var(--text-muted);">${i18n.t('loading')}</div>
        </div>
      </div>
    </div>
  `;

  document.getElementById('btn-goto-modrinth')?.addEventListener('click', () => state.navigateTo('modrinth'));
  document.getElementById('plugins-upload-input')?.addEventListener('change', (e) => handlePluginUpload(e, serverId));

  await loadPlugins(serverId);
}

async function loadPlugins(serverId) {
  try {
    const list = await api.get(`/api/servers/${serverId}/plugins`);
    renderPluginsTable(serverId, list);
  } catch (e) {
    toast.error('Failed to load plugins: ' + e.message);
  }
}

function renderPluginsTable(serverId, plugins) {
  const container = document.getElementById('plugins-list-container');
  if (!container) return;

  if (plugins.length === 0) {
    container.innerHTML = `
      <div style="text-align:center;padding:48px;color:var(--text-muted);">
        <p style="font-weight:600;font-size:15px;margin-bottom:4px;">No plugins installed</p>
        <p style="font-size:13px;margin-bottom:16px;">Download plugins directly from the Modrinth store or upload your own .jar files.</p>
        <button class="btn btn-primary" id="btn-empty-modrinth">${i18n.t('searchModrinth')}</button>
      </div>
    `;
    document.getElementById('btn-empty-modrinth')?.addEventListener('click', () => window.appState.navigateTo('modrinth'));
    return;
  }

  container.innerHTML = `
    <div class="table-wrap">
      <table class="data-table">
        <thead>
          <tr>
            <th>Status</th>
            <th>Plugin</th>
            <th>Version</th>
            <th>Author</th>
            <th>File Size</th>
            <th style="text-align:right;">Actions</th>
          </tr>
        </thead>
        <tbody>
          ${plugins.map(p => `
            <tr>
              <td>
                <span class="badge ${p.enabled ? 'badge-green' : 'badge-amber'}">
                  ${p.enabled ? i18n.t('enabled') : i18n.t('disabled')}
                </span>
              </td>
              <td>
                <div style="display:flex;align-items:center;gap:6px;">
                  <span style="font-weight:600;color:var(--text-main);">${escapeHtml(p.name)}</span>
                  ${p.foliaSupported ? '<span class="badge badge-blue" style="font-size:9px;">Folia</span>' : ''}
                </div>
                <div style="font-size:11px;color:var(--text-muted);">${escapeHtml(p.description || p.filename)}</div>
              </td>
              <td><span class="badge badge-gray">${escapeHtml(p.version)}</span></td>
              <td style="color:var(--text-muted);font-size:12px;">${escapeHtml(p.author || 'Unknown')}</td>
              <td style="color:var(--text-muted);font-size:12px;">${formatBytes(p.sizeBytes)}</td>
              <td style="text-align:right;">
                <div style="display:inline-flex;gap:6px;">
                  <button class="btn btn-secondary btn-sm btn-toggle-plug" data-file="${p.filename}">
                    ${p.enabled ? 'Disable' : 'Enable'}
                  </button>
                  <button class="btn btn-danger btn-sm btn-delete-plug" data-file="${p.filename}">
                    ${i18n.t('delete')}
                  </button>
                </div>
              </td>
            </tr>
          `).join('')}
        </tbody>
      </table>
    </div>
  `;

  container.querySelectorAll('.btn-toggle-plug').forEach(btn => {
    btn.addEventListener('click', async () => {
      const filename = btn.dataset.file;
      try {
        await api.post(`/api/servers/${serverId}/plugins/toggle`, { filename });
        toast.success('Plugin status updated');
        loadPlugins(serverId);
      } catch (e) {
        toast.error(e.message);
      }
    });
  });

  container.querySelectorAll('.btn-delete-plug').forEach(btn => {
    btn.addEventListener('click', () => {
      const filename = btn.dataset.file;
      modal.confirm(i18n.t('delete'), `Delete plugin "${filename}"? A backup copy will be preserved in the backups folder.`, async () => {
        try {
          await api.delete(`/api/servers/${serverId}/plugins`, { filename });
          toast.success('Plugin removed with backup');
          loadPlugins(serverId);
        } catch (e) {
          toast.error(e.message);
        }
      }, true);
    });
  });
}

async function handlePluginUpload(event, serverId) {
  const file = event.target.files[0];
  if (!file) return;

  const formData = new FormData();
  formData.append('file', file);

  toast.info(`Uploading ${file.name}...`);
  try {
    await api.upload(`/api/servers/${serverId}/plugins/upload`, formData);
    toast.success('Plugin installed successfully');
    loadPlugins(serverId);
  } catch (e) {
    toast.error('Plugin upload failed: ' + e.message);
  }
}
