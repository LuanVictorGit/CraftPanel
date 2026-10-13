import { api } from '../api.js';
import { toast, modal, formatBytes, escapeHtml, loadingModal } from '../ui.js';
import { i18n } from '../i18n.js';
import { icons } from '../icons.js';

export async function renderPlugins(root, state) {
  const serverId = state.activeServerId || 'local';

  root.innerHTML = `
    <div style="display:flex;flex-direction:column;gap:16px;">
      <!-- Action Bar -->
      <div style="display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:10px;">
        <div>
          <h3 style="font-size:18px;font-weight:700;color:var(--text-main);">${i18n.t('pluginManager')}</h3>
          <p style="font-size:13px;color:var(--text-muted);">${i18n.t('pluginSubtitle')}</p>
        </div>

        <div style="display:flex;align-items:center;gap:8px;">
          <button class="btn btn-primary btn-sm" id="btn-open-install-plugin">
            <svg style="width:14px;height:14px;" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
            ${i18n.t('installPlugin')}
          </button>
          <button class="btn btn-secondary btn-sm" id="btn-check-updates">
            <svg style="width:14px;height:14px;" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21.5 2v6h-6M21.34 15.57a10 10 0 1 1-.57-8.38l5.67-5.67"/></svg>
            ${i18n.t('checkUpdates')}
          </button>
          <label class="btn btn-secondary btn-sm" style="cursor:pointer;">
            <svg style="width:14px;height:14px;" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
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

  document.getElementById('btn-open-install-plugin')?.addEventListener('click', () => openInstallPluginModal(serverId));
  document.getElementById('btn-check-updates')?.addEventListener('click', () => checkPluginUpdates(serverId));
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
        <p style="font-weight:600;font-size:15px;margin-bottom:4px;">${i18n.t('noPluginsInstalled')}</p>
        <p style="font-size:13px;margin-bottom:16px;">${i18n.t('noPluginsDesc')}</p>
        <button class="btn btn-primary" id="btn-empty-install">${i18n.t('installPlugin')}</button>
      </div>
    `;
    document.getElementById('btn-empty-install')?.addEventListener('click', () => openInstallPluginModal(serverId));
    return;
  }

  container.innerHTML = `
    <div class="table-wrap">
      <table class="data-table">
        <thead>
          <tr>
            <th>${i18n.t('status')}</th>
            <th>${i18n.t('plugin')}</th>
            <th>${i18n.t('version')}</th>
            <th>${i18n.t('author')}</th>
            <th>${i18n.t('fileSize')}</th>
            <th style="text-align:right;">${i18n.t('actions')}</th>
          </tr>
        </thead>
        <tbody>
          ${plugins.map(p => `
            <tr>
              <td>
                ${p.pendingRestart ? `
                  <span class="badge badge-amber" title="Installed on disk, requires load or restart">
                    ${i18n.t('pendingRestart')}
                  </span>
                ` : p.enabled ? `
                  <span class="badge badge-green">
                    ${i18n.t('enabled')}
                  </span>
                ` : `
                  <span class="badge badge-gray">
                    ${i18n.t('disabled')}
                  </span>
                `}
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
                <div style="display:inline-flex;gap:6px;align-items:center;">
                  ${((p.name && p.name.toLowerCase() === 'craftpanel') || (p.filename && p.filename.toLowerCase().includes('craftpanel'))) ? `
                    <span class="badge badge-purple" style="font-size:11px;font-weight:700;padding:4px 9px;" title="CraftPanel is the core system plugin and cannot be removed.">Core System</span>
                  ` : `
                    ${p.pendingRestart ? `
                      <button class="btn btn-primary btn-sm btn-load-plug" data-file="${p.filename}">
                        ${i18n.t('loadNow')}
                      </button>
                    ` : ''}
                    <button class="btn btn-secondary btn-sm btn-toggle-plug" data-file="${p.filename}">
                      ${p.enabled ? i18n.t('disable') : i18n.t('enable')}
                    </button>
                    <button class="btn btn-danger btn-sm btn-delete-plug" data-file="${p.filename}">
                      ${i18n.t('delete')}
                    </button>
                  `}
                </div>
              </td>
            </tr>
          `).join('')}
        </tbody>
      </table>
    </div>
  `;

  container.querySelectorAll('.btn-load-plug').forEach(btn => {
    btn.addEventListener('click', async () => {
      const filename = btn.dataset.file;
      btn.disabled = true;
      loadingModal.show(i18n.t('loadNow'), filename, -1);
      try {
        await api.post(`/api/servers/${serverId}/plugins/load`, { filename });
        loadingModal.update(100, i18n.t('loadedSuccess'));
        await new Promise(r => setTimeout(r, 400));
        loadingModal.hide();
        toast.success(i18n.t('loadedSuccess'));
        loadPlugins(serverId);
      } catch (e) {
        loadingModal.hide();
        btn.disabled = false;
        toast.error('Load failed: ' + e.message);
      }
    });
  });

  container.querySelectorAll('.btn-toggle-plug').forEach(btn => {
    btn.addEventListener('click', async () => {
      const filename = btn.dataset.file;
      loadingModal.show(i18n.t('operationInProgress'), filename, -1);
      try {
        await api.post(`/api/servers/${serverId}/plugins/toggle`, { filename });
        loadingModal.hide();
        toast.success(i18n.t('operationSuccess'));
        loadPlugins(serverId);
      } catch (e) {
        loadingModal.hide();
        toast.error(e.message);
      }
    });
  });

  container.querySelectorAll('.btn-delete-plug').forEach(btn => {
    btn.addEventListener('click', () => {
      const filename = btn.dataset.file;
      modal.open({
        title: i18n.t('deletePluginTitle'),
        contentHtml: `
          <div style="display:flex;flex-direction:column;gap:14px;">
            <p style="font-size:14px;color:var(--text-main);margin:0;line-height:1.5;">
              ${i18n.t('deletePluginConfirm')}<br>
              <strong style="color:var(--accent-rose);">${escapeHtml(filename)}</strong>
            </p>
            <div style="padding:12px;background:var(--bg-subtle);border-radius:6px;border:1px solid var(--border);display:flex;align-items:flex-start;gap:10px;">
              <input type="checkbox" id="chk-delete-plugin-config" style="margin-top:3px;cursor:pointer;width:16px;height:16px;">
              <label for="chk-delete-plugin-config" style="cursor:pointer;font-size:13px;color:var(--text-main);font-weight:600;">
                <span>${i18n.t('deletePluginConfigCheckbox')}</span>
                <span style="display:block;font-size:11px;color:var(--text-muted);font-weight:400;margin-top:2px;">${i18n.t('deletePluginConfigDesc')}</span>
              </label>
            </div>
            <p style="font-size:11px;color:var(--text-muted);margin:0;">
              ${i18n.t('deletePluginWarning')}
            </p>
          </div>
        `,
        footerButtons: [
          { text: i18n.t('cancel'), className: 'btn-secondary', onClick: (close) => close() },
          {
            text: i18n.t('delete'),
            className: 'btn-danger',
            onClick: async (close) => {
              const chk = document.getElementById('chk-delete-plugin-config');
              const deleteConfig = chk ? chk.checked : false;
              close();
              loadingModal.show(i18n.t('deletingPlugin'), filename, -1);
              try {
                await api.delete(`/api/servers/${serverId}/plugins`, { filename, deleteConfig });
                loadingModal.update(100, i18n.t('pluginDeletedSuccess'));
                await new Promise(r => setTimeout(r, 450));
                loadingModal.hide();
                toast.success(i18n.t('pluginDeletedSuccess'));
                loadPlugins(serverId);
              } catch (e) {
                loadingModal.hide();
                toast.error(e.message);
              }
            }
          }
        ]
      });
    });
  });
}

function openInstallPluginModal(serverId) {
  modal.open({
    title: i18n.t('installPlugin'),
    wide: true,
    contentHtml: `
      <div style="display:flex;flex-direction:column;gap:16px;">
        <div style="display:flex;gap:8px;border-bottom:1px solid var(--border);padding-bottom:10px;">
          <button class="btn btn-sm btn-primary" id="tab-btn-modrinth" style="flex:1;">${i18n.t('modrinthStore')}</button>
          <button class="btn btn-sm btn-secondary" id="tab-btn-upload" style="flex:1;">${i18n.t('uploadJar')}</button>
        </div>

        <!-- Tab 1: Modrinth Store -->
        <div id="tab-content-modrinth" style="display:flex;flex-direction:column;gap:12px;">
          <div style="display:flex;gap:8px;">
            <input type="text" id="modal-modrinth-search" class="form-input" placeholder="${i18n.t('searchPlaceholder')}" style="flex:1;">
            <button class="btn btn-primary btn-sm" id="modal-btn-search">${i18n.t('search')}</button>
          </div>
          <div id="modal-modrinth-results" style="max-height:360px;overflow-y:auto;display:flex;flex-direction:column;gap:8px;padding-right:4px;">
            <div style="text-align:center;padding:24px;color:var(--text-muted);">${i18n.t('searchPrompt')}</div>
          </div>
        </div>

        <!-- Tab 2: Upload JAR -->
        <div id="tab-content-upload" style="display:none;flex-direction:column;gap:12px;">
          <div id="modal-drop-zone" style="border:2px dashed var(--border);border-radius:8px;padding:36px;text-align:center;cursor:pointer;background:var(--bg-card);">
            <svg style="width:36px;height:36px;color:var(--text-muted);margin:0 auto 10px;" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
            <p style="font-weight:600;font-size:14px;color:var(--text-main);margin-bottom:4px;">${i18n.t('dragAndDropJar')}</p>
            <p style="font-size:12px;color:var(--text-muted);">${i18n.t('supportsJarDesc')}</p>
            <input type="file" id="modal-file-input" accept=".jar" style="display:none;">
          </div>
        </div>
      </div>
    `,
    footerButtons: [
      { text: i18n.t('close'), className: 'btn-secondary', onClick: (close) => close() }
    ]
  });

  const tabBtnModrinth = document.getElementById('tab-btn-modrinth');
  const tabBtnUpload = document.getElementById('tab-btn-upload');
  const tabContentModrinth = document.getElementById('tab-content-modrinth');
  const tabContentUpload = document.getElementById('tab-content-upload');
  const searchInput = document.getElementById('modal-modrinth-search');
  const searchBtn = document.getElementById('modal-btn-search');
  const resultsContainer = document.getElementById('modal-modrinth-results');
  const dropZone = document.getElementById('modal-drop-zone');
  const fileInput = document.getElementById('modal-file-input');

  tabBtnModrinth?.addEventListener('click', () => {
    tabBtnModrinth.className = 'btn btn-sm btn-primary';
    tabBtnUpload.className = 'btn btn-sm btn-secondary';
    tabContentModrinth.style.display = 'flex';
    tabContentUpload.style.display = 'none';
  });

  tabBtnUpload?.addEventListener('click', () => {
    tabBtnUpload.className = 'btn btn-sm btn-primary';
    tabBtnModrinth.className = 'btn btn-sm btn-secondary';
    tabContentUpload.style.display = 'flex';
    tabContentModrinth.style.display = 'none';
  });

  const performModalSearch = async (query) => {
    if (!resultsContainer) return;
    resultsContainer.innerHTML = `<div style="text-align:center;padding:24px;color:var(--text-muted);">${i18n.t('searchingModrinth')}</div>`;
    try {
      const res = await api.get('/api/modrinth/search', {
        query: query || undefined,
        limit: 15
      });
      const hits = res.hits || [];
      if (hits.length === 0) {
        resultsContainer.innerHTML = `<div style="text-align:center;padding:24px;color:var(--text-muted);">${i18n.t('noPluginsFound')}</div>`;
        return;
      }
      resultsContainer.innerHTML = hits.map(hit => {
        const iconSrc = hit.iconUrl || hit.icon_url || '';
        const projId = hit.id || hit.slug;
        const pageSlug = hit.slug || hit.id;
        const pageUrl = `https://modrinth.com/plugin/${encodeURIComponent(pageSlug)}`;
        return `
        <div style="display:flex;align-items:center;justify-content:space-between;gap:12px;padding:10px 12px;background:var(--bg-main);border:1px solid var(--border);border-radius:6px;">
          <div style="display:flex;align-items:center;gap:10px;min-width:0;flex:1;">
            ${iconSrc ? `
              <img src="${escapeHtml(iconSrc)}" alt="" style="width:38px;height:38px;border-radius:6px;flex-shrink:0;object-fit:cover;" onerror="this.onerror=null;this.style.display='none';this.nextElementSibling.style.display='flex';">
              <div style="display:none;width:38px;height:38px;border-radius:6px;background:var(--border);flex-shrink:0;align-items:center;justify-content:center;color:var(--text-muted);">${icons.plugin(20)}</div>
            ` : `
              <div style="width:38px;height:38px;border-radius:6px;background:var(--border);flex-shrink:0;display:flex;align-items:center;justify-content:center;color:var(--text-muted);">${icons.plugin(20)}</div>
            `}
            <div style="min-width:0;flex:1;">
              <div style="display:flex;align-items:center;gap:6px;">
                <span style="font-weight:600;font-size:13px;color:var(--text-main);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${escapeHtml(hit.title)}</span>
                ${hit.author ? `<span style="font-size:10px;color:var(--text-muted);flex-shrink:0;">by ${escapeHtml(hit.author)}</span>` : ''}
              </div>
              <p style="font-size:11px;color:var(--text-muted);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;margin:2px 0 0 0;">${escapeHtml(hit.description || '')}</p>
            </div>
          </div>
          <div style="display:flex;align-items:center;gap:8px;flex-shrink:0;">
            <a href="${pageUrl}" target="_blank" rel="noopener noreferrer" class="btn btn-secondary btn-sm" style="text-decoration:none;display:inline-flex;align-items:center;gap:4px;" title="View plugin details on Modrinth">
              <span>${i18n.t('view')}</span>
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"></path><polyline points="15 3 21 3 21 9"></polyline><line x1="10" y1="14" x2="21" y2="3"></line></svg>
            </a>
            <button class="btn btn-primary btn-sm btn-modal-install" data-project="${escapeHtml(projId)}" data-name="${escapeHtml(hit.title)}">
              ${i18n.t('install')}
            </button>
          </div>
        </div>
      `;
      }).join('');

      resultsContainer.querySelectorAll('.btn-modal-install').forEach(btn => {
        btn.addEventListener('click', async () => {
          const projId = btn.dataset.project;
          const projName = btn.dataset.name;
          btn.disabled = true;
          loadingModal.show(i18n.t('downloadingAndInstalling'), projName, 10);
          try {
            const versions = await api.get(`/api/modrinth/project/${projId}/versions`, { serverId });
            if (!versions || versions.length === 0) {
              loadingModal.hide();
              btn.disabled = false;
              toast.error('No compatible versions found');
              return;
            }
            const selectedEntry = versions.find(v => {
              const st = v.compatibility?.status;
              return st === 'COMPATIBLE_CONFIRMED';
            }) || versions.find(v => {
              const st = v.compatibility?.status;
              return st === 'PARTIAL_COMPATIBILITY' || st === 'NEEDS_ANALYSIS';
            }) || versions[0];

            const chosenVer = selectedEntry.version || selectedEntry;
            const files = chosenVer.files || [];
            const primaryFile = files.find(f => f.primary) || files.find(f => f.filename && f.filename.endsWith('.jar')) || files[0];
            if (!primaryFile || !primaryFile.url) {
              loadingModal.hide();
              btn.disabled = false;
              toast.error('No downloadable file in release');
              return;
            }

            loadingModal.update(25, `${i18n.t('downloadingAndInstalling')} - ${projName}...`);
            const task = await api.post(`/api/servers/${serverId}/modrinth/install`, {
              fileUrl: primaryFile.url,
              filename: primaryFile.filename,
              hash: primaryFile.hashes?.sha512 || primaryFile.hashes?.sha1 || '',
              projectName: projName
            });

            if (task && task.id) {
              for (let i = 0; i < 40; i++) {
                await new Promise(r => setTimeout(r, 600));
                loadingModal.update(Math.min(30 + i * 2.5, 95), `${i18n.t('downloadingAndInstalling')} - ${projName}...`);
                try {
                  const t = await api.get(`/api/tasks/${task.id}`);
                  if (t.state === 'COMPLETED') {
                    break;
                  } else if (t.state === 'FAILED' || t.state === 'CANCELLED') {
                    throw new Error(t.error || t.message || 'Installation task failed');
                  }
                } catch (pe) {
                  if (pe.message && (pe.message.includes('failed') || pe.message.includes('mismatch'))) {
                    throw pe;
                  }
                }
              }
            }

            loadingModal.update(100, i18n.t('installedSuccess'));
            btn.innerText = i18n.t('installed');
            btn.className = 'btn btn-secondary btn-sm';
            await new Promise(r => setTimeout(r, 450));
            loadingModal.hide();
            toast.success(`${projName} ${i18n.t('installedSuccess')}`);
            loadPlugins(serverId);
          } catch (err) {
            loadingModal.hide();
            btn.disabled = false;
            toast.error('Install failed: ' + err.message);
          }
        });
      });
    } catch (e) {
      resultsContainer.innerHTML = `<div style="text-align:center;padding:24px;color:var(--accent-rose);">Error: ${escapeHtml(e.message)}</div>`;
    }
  };

  searchBtn?.addEventListener('click', () => performModalSearch(searchInput?.value.trim()));
  searchInput?.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') performModalSearch(searchInput?.value.trim());
  });

  dropZone?.addEventListener('click', () => fileInput?.click());
  fileInput?.addEventListener('change', async (e) => {
    const file = e.target.files[0];
    if (!file) return;
    const formData = new FormData();
    formData.append('file', file);
    loadingModal.show(i18n.t('uploadingAndInstalling'), file.name, -1);
    try {
      await api.upload(`/api/servers/${serverId}/plugins/upload`, formData);
      loadingModal.update(100, i18n.t('installedSuccess'));
      await new Promise(r => setTimeout(r, 450));
      loadingModal.hide();
      toast.success(i18n.t('installedSuccess'));
      loadPlugins(serverId);
    } catch (err) {
      loadingModal.hide();
      toast.error('Upload failed: ' + err.message);
    }
  });

  // Load default top plugins for initial convenience
  performModalSearch('');
}

async function checkPluginUpdates(serverId) {
  loadingModal.show(i18n.t('checkUpdates'), i18n.t('pleaseWait'), -1);
  try {
    const res = await api.post(`/api/servers/${serverId}/modrinth/check-updates`);
    loadingModal.hide();
    const updates = Array.isArray(res) ? res.filter(r => r.hasUpdate) : (res.updates || []);
    if (updates.length === 0) {
      toast.success('All installed plugins are up to date!');
    } else {
      toast.info(`Found updates for ${updates.length} plugins!`);
      loadPlugins(serverId);
    }
  } catch (e) {
    loadingModal.hide();
    toast.error('Check updates failed: ' + e.message);
  }
}

async function handlePluginUpload(event, serverId) {
  const file = event.target.files[0];
  if (!file) return;

  const formData = new FormData();
  formData.append('file', file);

  loadingModal.show(i18n.t('uploadingAndInstalling'), file.name, -1);
  try {
    await api.upload(`/api/servers/${serverId}/plugins/upload`, formData);
    loadingModal.update(100, i18n.t('installedSuccess'));
    await new Promise(r => setTimeout(r, 450));
    loadingModal.hide();
    toast.success(i18n.t('installedSuccess'));
    loadPlugins(serverId);
  } catch (e) {
    loadingModal.hide();
    toast.error('Plugin upload failed: ' + e.message);
  }
}
