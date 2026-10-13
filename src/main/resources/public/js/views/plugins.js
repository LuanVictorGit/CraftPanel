import { api } from '../api.js';
import { toast, modal, formatBytes, escapeHtml } from '../ui.js';
import { i18n } from '../i18n.js';

export async function renderPlugins(root, state) {
  const serverId = state.activeServerId || 'local';

  root.innerHTML = `
    <div style="display:flex;flex-direction:column;gap:16px;">
      <!-- Action Bar -->
      <div style="display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:10px;">
        <div>
          <h3 style="font-size:18px;font-weight:700;color:var(--text-main);">${i18n.t('pluginManager')}</h3>
          <p style="font-size:13px;color:var(--text-muted);">Manage installed Bukkit, Spigot, Paper and Folia plugins</p>
        </div>

        <div style="display:flex;align-items:center;gap:8px;">
          <button class="btn btn-primary btn-sm" id="btn-open-install-plugin">
            <svg style="width:14px;height:14px;" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
            ${i18n.t('installPlugin')}
          </button>
          <button class="btn btn-secondary btn-sm" id="btn-check-updates">
            <svg style="width:14px;height:14px;" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21.5 2v6h-6M21.34 15.57a10 10 0 1 1-.57-8.38l5.67-5.67"/></svg>
            Check Updates
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
        <p style="font-weight:600;font-size:15px;margin-bottom:4px;">No plugins installed</p>
        <p style="font-size:13px;margin-bottom:16px;">Download plugins directly from the Modrinth catalog or upload your own .jar files.</p>
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

function openInstallPluginModal(serverId) {
  modal.open({
    title: i18n.t('installPlugin'),
    wide: true,
    contentHtml: `
      <div style="display:flex;flex-direction:column;gap:16px;">
        <div style="display:flex;gap:8px;border-bottom:1px solid var(--border);padding-bottom:10px;">
          <button class="btn btn-sm btn-primary" id="tab-btn-modrinth" style="flex:1;">Modrinth Store</button>
          <button class="btn btn-sm btn-secondary" id="tab-btn-upload" style="flex:1;">Upload .jar</button>
        </div>

        <!-- Tab 1: Modrinth Store -->
        <div id="tab-content-modrinth" style="display:flex;flex-direction:column;gap:12px;">
          <div style="display:flex;gap:8px;">
            <input type="text" id="modal-modrinth-search" class="form-input" placeholder="Search Modrinth (e.g. EssentialsX, LuckPerms, Vault, Chunky)..." style="flex:1;">
            <button class="btn btn-primary btn-sm" id="modal-btn-search">Search</button>
          </div>
          <div id="modal-modrinth-results" style="max-height:360px;overflow-y:auto;display:flex;flex-direction:column;gap:8px;padding-right:4px;">
            <div style="text-align:center;padding:24px;color:var(--text-muted);">Type a plugin name or search above</div>
          </div>
        </div>

        <!-- Tab 2: Upload JAR -->
        <div id="tab-content-upload" style="display:none;flex-direction:column;gap:12px;">
          <div id="modal-drop-zone" style="border:2px dashed var(--border);border-radius:8px;padding:36px;text-align:center;cursor:pointer;background:var(--bg-card);">
            <svg style="width:36px;height:36px;color:var(--text-muted);margin:0 auto 10px;" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
            <p style="font-weight:600;font-size:14px;color:var(--text-main);margin-bottom:4px;">Click to select or drag and drop a plugin .jar file</p>
            <p style="font-size:12px;color:var(--text-muted);">Supports Bukkit, Spigot, Paper and Folia plugins</p>
            <input type="file" id="modal-file-input" accept=".jar" style="display:none;">
          </div>
        </div>
      </div>
    `,
    footerButtons: [
      { text: 'Close', className: 'btn-secondary', onClick: (close) => close() }
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
    resultsContainer.innerHTML = '<div style="text-align:center;padding:24px;color:var(--text-muted);">Searching Modrinth...</div>';
    try {
      const res = await api.get('/api/modrinth/search', {
        query: query || undefined,
        limit: 15
      });
      const hits = res.hits || [];
      if (hits.length === 0) {
        resultsContainer.innerHTML = '<div style="text-align:center;padding:24px;color:var(--text-muted);">No plugins found for this search.</div>';
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
              <div style="display:none;width:38px;height:38px;border-radius:6px;background:var(--border);flex-shrink:0;align-items:center;justify-content:center;color:var(--text-muted);font-size:16px;">🧩</div>
            ` : `
              <div style="width:38px;height:38px;border-radius:6px;background:var(--border);flex-shrink:0;display:flex;align-items:center;justify-content:center;color:var(--text-muted);font-size:16px;">🧩</div>
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
              <span>View</span>
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"></path><polyline points="15 3 21 3 21 9"></polyline><line x1="10" y1="14" x2="21" y2="3"></line></svg>
            </a>
            <button class="btn btn-primary btn-sm btn-modal-install" data-project="${escapeHtml(projId)}" data-name="${escapeHtml(hit.title)}">
              Install
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
          btn.innerText = 'Installing...';
          try {
            const versions = await api.get(`/api/modrinth/project/${projId}/versions`, { serverId });
            if (!versions || versions.length === 0) {
              toast.error('No compatible versions found');
              btn.disabled = false;
              btn.innerText = 'Install';
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
              toast.error('No downloadable file in release');
              btn.disabled = false;
              btn.innerText = 'Install';
              return;
            }

            btn.innerText = 'Downloading...';
            const task = await api.post(`/api/servers/${serverId}/modrinth/install`, {
              fileUrl: primaryFile.url,
              filename: primaryFile.filename,
              hash: primaryFile.hashes?.sha512 || primaryFile.hashes?.sha1 || '',
              projectName: projName
            });

            if (task && task.id) {
              for (let i = 0; i < 40; i++) {
                await new Promise(r => setTimeout(r, 600));
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

            btn.innerText = 'Installed!';
            btn.className = 'btn btn-secondary btn-sm';
            toast.success(`${projName} installed successfully!`);
            loadPlugins(serverId);
          } catch (err) {
            btn.disabled = false;
            btn.innerText = 'Install';
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
    toast.info(`Uploading ${file.name}...`);
    try {
      await api.upload(`/api/servers/${serverId}/plugins/upload`, formData);
      toast.success('Plugin installed successfully!');
      loadPlugins(serverId);
    } catch (err) {
      toast.error('Upload failed: ' + err.message);
    }
  });

  // Load default top plugins for initial convenience
  performModalSearch('');
}

async function checkPluginUpdates(serverId) {
  toast.info('Checking for plugin updates on Modrinth...');
  try {
    const res = await api.post(`/api/servers/${serverId}/modrinth/check-updates`);
    const updates = Array.isArray(res) ? res.filter(r => r.hasUpdate) : (res.updates || []);
    if (updates.length === 0) {
      toast.success('All installed plugins are up to date!');
    } else {
      toast.info(`Found updates for ${updates.length} plugins!`);
      loadPlugins(serverId);
    }
  } catch (e) {
    toast.error('Check updates failed: ' + e.message);
  }
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
