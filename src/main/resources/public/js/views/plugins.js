import { api } from '../api.js';
import { toast, modal, formatBytes, escapeHtml, loadingModal } from '../ui.js';
import { i18n } from '../i18n.js';
import { icons } from '../icons.js';
import { ALL_MC_VERSIONS } from './modrinth.js';

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
                      <button class="btn btn-primary btn-sm btn-load-plug" data-file="${escapeHtml(p.filename)}">
                        ${i18n.t('loadNow')}
                      </button>
                    ` : ''}
                    <button class="btn btn-secondary btn-sm btn-toggle-plug" data-file="${escapeHtml(p.filename)}">
                      ${p.enabled ? i18n.t('disable') : i18n.t('enable')}
                    </button>
                    <button class="btn btn-danger btn-sm btn-delete-plug" data-file="${escapeHtml(p.filename)}">
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

async function openInstallPluginModal(serverId) {
  let detectedLoader = '';
  let serverForkName = '';
  let serverMcVersion = '';

  try {
    const res = await api.get('/api/servers/' + serverId);
    const srv = res?.server;
    if (srv) {
      const soft = (srv.software || '').toLowerCase();
      serverForkName = srv.software || '';
      serverMcVersion = srv.minecraftVersion || '';
      if (soft.includes('folia')) {
        detectedLoader = 'folia';
      } else if (soft.includes('purpur')) {
        detectedLoader = 'purpur';
      } else if (soft.includes('paper')) {
        detectedLoader = 'paper';
      } else if (soft.includes('spigot')) {
        detectedLoader = 'spigot';
      } else if (soft.includes('bukkit')) {
        detectedLoader = 'bukkit';
      }
    }
  } catch (e) {}

  const versionOptions = ALL_MC_VERSIONS.map(v => `<option value="${v}">${v}</option>`).join('');

  modal.open({
    title: i18n.t('installPlugin'),
    wide: true,
    contentHtml: `
      <div style="display:flex;flex-direction:column;gap:14px;">
        <div style="display:flex;gap:8px;border-bottom:1px solid var(--border);padding-bottom:10px;">
          <button class="btn btn-sm btn-primary" id="tab-btn-modrinth" style="flex:1;">${i18n.t('modrinthStore')}</button>
          <button class="btn btn-sm btn-secondary" id="tab-btn-upload" style="flex:1;">${i18n.t('uploadJar')}</button>
        </div>

        <!-- Tab 1: Modrinth Store -->
        <div id="tab-content-modrinth" style="display:flex;flex-direction:column;gap:12px;">
          <div style="display:flex;flex-wrap:wrap;gap:8px;align-items:center;">
            <input type="text" id="modal-modrinth-search" class="form-input" placeholder="${i18n.t('searchPlaceholder')}" style="flex:1;min-width:180px;">

            <div style="width:130px;">
              <select id="modal-modrinth-version" class="form-select" title="${i18n.t('minecraftVersion')}" style="font-size:12px;padding:6px 8px;">
                <option value="">${i18n.t('allVersions')}</option>
                ${serverMcVersion && !ALL_MC_VERSIONS.includes(serverMcVersion) ? `<option value="${escapeHtml(serverMcVersion)}">${escapeHtml(serverMcVersion)} (${escapeHtml(serverForkName || 'Server')})</option>` : ''}
                ${versionOptions}
              </select>
            </div>

            <div style="width:130px;">
              <select id="modal-modrinth-loader" class="form-select" title="${i18n.t('serverForkFilter')}" style="font-size:12px;padding:6px 8px;">
                <option value="">${i18n.t('allPlatforms')}</option>
                <option value="folia" ${detectedLoader === 'folia' ? 'selected' : ''}>Folia</option>
                <option value="paper" ${detectedLoader === 'paper' ? 'selected' : ''}>Paper</option>
                <option value="purpur" ${detectedLoader === 'purpur' ? 'selected' : ''}>Purpur</option>
                <option value="spigot" ${detectedLoader === 'spigot' ? 'selected' : ''}>Spigot</option>
                <option value="bukkit" ${detectedLoader === 'bukkit' ? 'selected' : ''}>Bukkit</option>
                <option value="velocity" ${detectedLoader === 'velocity' ? 'selected' : ''}>Velocity</option>
                <option value="bungeecord" ${detectedLoader === 'bungeecord' ? 'selected' : ''}>BungeeCord</option>
              </select>
            </div>

            <button class="btn btn-primary btn-sm" id="modal-btn-search">
              <svg style="width:13px;height:13px;" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
              <span>${i18n.t('search')}</span>
            </button>
          </div>

          <div id="modal-modrinth-fork-badge" style="display:${detectedLoader ? 'flex' : 'none'};align-items:center;gap:6px;font-size:11px;color:var(--accent-teal);margin-top:-4px;">
            <svg style="width:13px;height:13px;" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>
            <span>${detectedLoader ? i18n.t('detectedServerFork', { fork: detectedLoader.toUpperCase() }) : ''}</span>
          </div>

          <div id="modal-modrinth-results" style="max-height:360px;min-height:200px;overflow-y:auto;display:flex;flex-direction:column;gap:8px;padding-right:4px;">
            <div style="text-align:center;padding:24px;color:var(--text-muted);">${i18n.t('searchPrompt')}</div>
          </div>

          <!-- Pagination Controls -->
          <div id="modal-modrinth-pagination" style="display:none;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:8px;padding:8px 12px;background:var(--bg-main);border:1px solid var(--border);border-radius:6px;">
            <div style="font-size:12px;color:var(--text-muted);" id="modal-pagination-info"></div>

            <div style="display:inline-flex;gap:6px;align-items:center;">
              <button class="btn btn-secondary btn-sm" id="modal-btn-prev">
                <svg style="width:12px;height:12px;" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="15 18 9 12 15 6"/></svg>
                <span>${i18n.t('previousPage')}</span>
              </button>

              <span id="modal-page-indicator" style="font-size:11px;padding:3px 8px;border-radius:4px;background:var(--bg-subtle);color:var(--text-main);font-weight:600;border:1px solid var(--border);">1 / 1</span>

              <button class="btn btn-secondary btn-sm" id="modal-btn-next">
                <span>${i18n.t('nextPage')}</span>
                <svg style="width:12px;height:12px;" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
              </button>
            </div>
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
  const versionSelect = document.getElementById('modal-modrinth-version');
  const loaderSelect = document.getElementById('modal-modrinth-loader');
  const searchBtn = document.getElementById('modal-btn-search');
  const forkBadgeEl = document.getElementById('modal-modrinth-fork-badge');
  const resultsContainer = document.getElementById('modal-modrinth-results');
  const paginationEl = document.getElementById('modal-modrinth-pagination');
  const paginationInfoEl = document.getElementById('modal-pagination-info');
  const pageIndicatorEl = document.getElementById('modal-page-indicator');
  const prevBtn = document.getElementById('modal-btn-prev');
  const nextBtn = document.getElementById('modal-btn-next');
  const dropZone = document.getElementById('modal-drop-zone');
  const fileInput = document.getElementById('modal-file-input');

  let modalPagination = {
    currentPage: 1,
    pageSize: 10,
    totalHits: 0,
    totalPages: 1,
    query: '',
    version: '',
    loader: ''
  };
  let modalSearchSeq = 0;

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

  const performModalSearch = async (targetPage = null) => {
    if (targetPage !== null) {
      modalPagination.currentPage = targetPage;
    } else {
      modalPagination.query = searchInput?.value.trim() || '';
      modalPagination.version = versionSelect?.value || '';
      modalPagination.loader = loaderSelect?.value || '';
    }
    if (!resultsContainer) return;

    const { query, version, loader } = modalPagination;
    const seq = ++modalSearchSeq;

    if (forkBadgeEl) {
      forkBadgeEl.style.display = loader ? 'flex' : 'none';
      const badgeSpan = forkBadgeEl.querySelector('span');
      if (badgeSpan && loader) {
        badgeSpan.textContent = i18n.t('detectedServerFork', { fork: loader.toUpperCase() });
      }
    }

    resultsContainer.innerHTML = `<div style="text-align:center;padding:24px;color:var(--text-muted);">${i18n.t('searchingModrinth')}</div>`;
    if (paginationEl) paginationEl.style.display = 'none';

    const offset = (modalPagination.currentPage - 1) * modalPagination.pageSize;

    try {
      const res = await api.get('/api/modrinth/search', {
        query: query || undefined,
        version: version || undefined,
        loader: loader || undefined,
        limit: modalPagination.pageSize,
        offset: offset
      });

      if (seq !== modalSearchSeq) return;
      const hits = res.hits || [];
      modalPagination.totalHits = res.totalHits || 0;
      modalPagination.totalPages = Math.max(1, Math.ceil(modalPagination.totalHits / modalPagination.pageSize));

      if (hits.length === 0) {
        resultsContainer.innerHTML = `<div style="text-align:center;padding:24px;color:var(--text-muted);">${i18n.t('noPluginsFound')}</div>`;
        if (paginationEl) paginationEl.style.display = 'none';
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

      if (paginationEl) {
        paginationEl.style.display = 'flex';
        if (paginationInfoEl) {
          paginationInfoEl.innerHTML = `${i18n.t('pageOf', { page: modalPagination.currentPage, total: modalPagination.totalPages })} &bull; <span style="color:var(--text-main);font-weight:600;">${i18n.t('pluginsFound', { count: modalPagination.totalHits.toLocaleString() })}</span>`;
        }
        if (pageIndicatorEl) {
          pageIndicatorEl.textContent = `${modalPagination.currentPage} / ${modalPagination.totalPages}`;
        }
        if (prevBtn) {
          prevBtn.disabled = modalPagination.currentPage <= 1;
          prevBtn.style.opacity = modalPagination.currentPage <= 1 ? '0.5' : '1';
          prevBtn.style.cursor = modalPagination.currentPage <= 1 ? 'not-allowed' : 'pointer';
        }
        if (nextBtn) {
          nextBtn.disabled = modalPagination.currentPage >= modalPagination.totalPages;
          nextBtn.style.opacity = modalPagination.currentPage >= modalPagination.totalPages ? '0.5' : '1';
          nextBtn.style.cursor = modalPagination.currentPage >= modalPagination.totalPages ? 'not-allowed' : 'pointer';
        }
      }

      resultsContainer.scrollTop = 0;

      resultsContainer.querySelectorAll('.btn-modal-install').forEach(btn => {
        btn.addEventListener('click', async () => {
          const projId = btn.dataset.project;
          const projName = btn.dataset.name;
          btn.disabled = true;
          loadingModal.show(i18n.t('downloadingAndInstalling'), projName, 10);
          try {
            const versions = await api.get(`/api/modrinth/project/${projId}/versions`, { serverId });
            const candidates = (Array.isArray(versions) ? versions : []).filter(v => (v.compatibility?.matchingLoaders || []).length > 0);
            const selectedEntry = ['COMPATIBLE_CONFIRMED', 'PARTIAL_COMPATIBILITY', 'NEEDS_ANALYSIS']
              .map(status => candidates.find(v => v.compatibility.status === status))
              .find(Boolean);
            if (!selectedEntry) {
              loadingModal.hide();
              btn.disabled = false;
              toast.error(`${projName}: ${i18n.t('noCompatibleVersion')}`);
              return;
            }

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

            if (!task?.id) throw new Error('Install task was not created');
            await api.waitForTask(task.id, t => {
              loadingModal.update(Math.min(25 + (t.progress || 0) * 0.7, 95), `${i18n.t('downloadingAndInstalling')} - ${projName}...`);
            });

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
      if (seq !== modalSearchSeq) return;
      resultsContainer.innerHTML = `<div style="text-align:center;padding:24px;color:var(--accent-rose);">Error: ${escapeHtml(e.message)}</div>`;
      if (paginationEl) paginationEl.style.display = 'none';
    }
  };

  prevBtn?.addEventListener('click', () => {
    if (modalPagination.currentPage > 1) {
      performModalSearch(modalPagination.currentPage - 1);
    }
  });

  nextBtn?.addEventListener('click', () => {
    if (modalPagination.currentPage < modalPagination.totalPages) {
      performModalSearch(modalPagination.currentPage + 1);
    }
  });

  searchBtn?.addEventListener('click', () => {
    modalPagination.currentPage = 1;
    performModalSearch();
  });

  searchInput?.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
      modalPagination.currentPage = 1;
      performModalSearch();
    }
  });

  versionSelect?.addEventListener('change', () => {
    modalPagination.currentPage = 1;
    performModalSearch();
  });

  loaderSelect?.addEventListener('change', () => {
    modalPagination.currentPage = 1;
    performModalSearch();
  });

  dropZone?.addEventListener('click', () => fileInput?.click());
  dropZone?.addEventListener('dragover', (e) => e.preventDefault());
  dropZone?.addEventListener('drop', (e) => {
    e.preventDefault();
    const file = e.dataTransfer?.files?.[0];
    if (!file) return;
    if (!file.name.toLowerCase().endsWith('.jar')) {
      toast.error(i18n.t('jarFilesOnly'));
      return;
    }
    uploadPluginFile(file, serverId);
  });
  fileInput?.addEventListener('change', (e) => handlePluginUpload(e, serverId));

  api.get('/api/modrinth/game-versions').then(versions => {
    if (Array.isArray(versions) && versions.length > 0) {
      const select = document.getElementById('modal-modrinth-version');
      if (!select) return;
      const currentVal = select.value;
      const merged = Array.from(new Set([...versions, ...ALL_MC_VERSIONS]));
      merged.sort((a, b) => {
        const a26 = a.startsWith('26');
        const b26 = b.startsWith('26');
        if (a26 && !b26) return -1;
        if (!a26 && b26) return 1;
        return b.localeCompare(a, undefined, { numeric: true, sensitivity: 'base' });
      });
      let html = `<option value="">${i18n.t('allVersions')}</option>`;
      if (serverMcVersion && !merged.includes(serverMcVersion)) {
        html += `<option value="${escapeHtml(serverMcVersion)}">${escapeHtml(serverMcVersion)} (${escapeHtml(serverForkName || 'Server')})</option>`;
      }
      html += merged.map(v => `<option value="${v}">${v}</option>`).join('');
      select.innerHTML = html;
      select.value = currentVal;
    }
  }).catch(() => {});

  performModalSearch();
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
  await uploadPluginFile(event.target.files[0], serverId);
  event.target.value = '';
}

async function uploadPluginFile(file, serverId) {
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
