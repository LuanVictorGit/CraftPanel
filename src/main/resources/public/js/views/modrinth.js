import { api } from '../api.js';
import { toast, modal, formatBytes, escapeHtml, loadingModal } from '../ui.js';
import { i18n } from '../i18n.js';

export const ALL_MC_VERSIONS = [
  '26.3', '26.2', '26.1.2', '26.1.1', '26.1', '26.0',
  '1.21.11', '1.21.10', '1.21.9', '1.21.8', '1.21.7', '1.21.6', '1.21.5', '1.21.4', '1.21.3', '1.21.2', '1.21.1', '1.21',
  '1.20.6', '1.20.5', '1.20.4', '1.20.3', '1.20.2', '1.20.1', '1.20',
  '1.19.4', '1.19.3', '1.19.2', '1.19.1', '1.19',
  '1.18.2', '1.18.1', '1.18',
  '1.17.1', '1.17',
  '1.16.5', '1.16.4', '1.16.3', '1.16.2', '1.16.1', '1.16',
  '1.15.2', '1.15.1', '1.15',
  '1.14.4', '1.14',
  '1.13.2', '1.13',
  '1.12.2', '1.12',
  '1.11.2', '1.10.2', '1.9.4', '1.8.8'
];

let paginationState = {
  currentPage: 1,
  pageSize: 24,
  totalHits: 0,
  totalPages: 1,
  query: '',
  version: '',
  loader: ''
};
let searchSeq = 0;

export async function renderModrinth(root, state) {
  const serverId = state.activeServerId || 'local';

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

  root.innerHTML = `
    <div style="display:flex;flex-direction:column;gap:18px;">
      <!-- Search & Filters -->
      <div class="card" style="padding:16px;">
        <div style="display:flex;flex-wrap:wrap;gap:12px;align-items:center;">
          <div style="flex:1;min-width:240px;position:relative;">
            <input type="text" id="modrinth-search-input" class="form-input" placeholder="${i18n.t('searchPlaceholder')}">
          </div>

          <div style="width:170px;">
            <select id="modrinth-version-filter" class="form-select" title="${i18n.t('minecraftVersion')}">
              <option value="">${i18n.t('allVersions')}</option>
              ${serverMcVersion && !ALL_MC_VERSIONS.includes(serverMcVersion) ? `<option value="${escapeHtml(serverMcVersion)}">${escapeHtml(serverMcVersion)} (${escapeHtml(serverForkName || 'Server')})</option>` : ''}
              ${versionOptions}
            </select>
          </div>

          <div style="width:160px;">
            <select id="modrinth-loader-filter" class="form-select" title="${i18n.t('serverForkFilter')}">
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

          <button class="btn btn-primary" id="btn-search-modrinth">
            <svg style="width:15px;height:15px;" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
            ${i18n.t('search')}
          </button>
        </div>

        ${detectedLoader ? `
          <div id="modrinth-fork-badge" style="margin-top:10px;font-size:12px;color:var(--accent-teal);display:flex;align-items:center;gap:6px;">
            <svg style="width:14px;height:14px;" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>
            <span>${i18n.t('detectedServerFork', { fork: detectedLoader.toUpperCase() })}</span>
          </div>
        ` : ''}
      </div>

      <!-- Results Grid -->
      <div id="modrinth-results-container">
        <div style="text-align:center;padding:48px;color:var(--text-muted);">${i18n.t('loading')}</div>
      </div>
    </div>
  `;

  paginationState.currentPage = 1;

  document.getElementById('btn-search-modrinth')?.addEventListener('click', () => {
    paginationState.currentPage = 1;
    executeSearch(serverId);
  });
  document.getElementById('modrinth-search-input')?.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
      paginationState.currentPage = 1;
      executeSearch(serverId);
    }
  });
  document.getElementById('modrinth-version-filter')?.addEventListener('change', () => {
    paginationState.currentPage = 1;
    executeSearch(serverId);
  });
  document.getElementById('modrinth-loader-filter')?.addEventListener('change', () => {
    paginationState.currentPage = 1;
    executeSearch(serverId);
  });

  await executeSearch(serverId);

  api.get('/api/modrinth/game-versions').then(versions => {
    if (Array.isArray(versions) && versions.length > 0) {
      const select = document.getElementById('modrinth-version-filter');
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
}

async function executeSearch(serverId, targetPage = null) {
  if (targetPage !== null) {
    paginationState.currentPage = targetPage;
  } else {
    paginationState.query = document.getElementById('modrinth-search-input')?.value.trim() || '';
    paginationState.version = document.getElementById('modrinth-version-filter')?.value || '';
    paginationState.loader = document.getElementById('modrinth-loader-filter')?.value || '';
  }

  const { query, version, loader } = paginationState;
  const seq = ++searchSeq;

  const badgeEl = document.getElementById('modrinth-fork-badge');
  if (badgeEl) {
    badgeEl.style.display = loader ? 'flex' : 'none';
    const badgeText = badgeEl.querySelector('span');
    if (badgeText && loader) {
      badgeText.textContent = i18n.t('detectedServerFork', { fork: loader.toUpperCase() });
    }
  }

  const container = document.getElementById('modrinth-results-container');
  if (container) {
    container.innerHTML = `<div style="text-align:center;padding:32px;color:var(--text-muted);">${i18n.t('searchingModrinth')}</div>`;
  }

  const offset = (paginationState.currentPage - 1) * paginationState.pageSize;

  try {
    const res = await api.get('/api/modrinth/search', {
      query: query || undefined,
      version: version || undefined,
      loader: loader || undefined,
      limit: paginationState.pageSize,
      offset: offset
    });

    if (seq !== searchSeq) return;
    paginationState.totalHits = res.totalHits || 0;
    paginationState.totalPages = Math.max(1, Math.ceil(paginationState.totalHits / paginationState.pageSize));

    renderModrinthHits(res.hits || [], serverId, (newPage) => {
      executeSearch(serverId, newPage);
      const resContainer = document.getElementById('modrinth-results-container');
      if (resContainer) {
        resContainer.scrollIntoView({ behavior: 'smooth', block: 'start' });
      }
    });
  } catch (e) {
    if (seq !== searchSeq) return;
    if (container) {
      container.innerHTML = `<div style="text-align:center;padding:32px;color:var(--accent-rose);">Search error: ${escapeHtml(e.message)}</div>`;
    }
  }
}

function renderModrinthHits(hits, serverId, onPageChange) {
  const container = document.getElementById('modrinth-results-container');
  if (!container) return;

  if (hits.length === 0) {
    container.innerHTML = `<div style="text-align:center;padding:48px;color:var(--text-muted);">${i18n.t('noPluginsFound')}</div>`;
    return;
  }

  const currentPage = paginationState.currentPage;
  const totalPages = paginationState.totalPages;
  const totalHits = paginationState.totalHits;

  container.innerHTML = `
    <div class="grid-cols-3">
      ${hits.map(p => `
        <div class="card" style="display:flex;flex-direction:column;justify-content:space-between;cursor:pointer;" data-project-id="${p.id}">
          <div>
            <div style="display:flex;align-items:flex-start;gap:12px;margin-bottom:12px;">
              ${p.iconUrl ? `
                <img src="${p.iconUrl}" alt="" style="width:40px;height:40px;border-radius:8px;object-fit:cover;flex-shrink:0;" onerror="this.onerror=null;this.style.display='none';this.nextElementSibling.style.display='flex';">
                <div style="display:none;width:40px;height:40px;border-radius:8px;background:var(--bg-subtle);align-items:center;justify-content:center;color:var(--text-muted);">
                  <svg style="width:20px;height:20px;" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 2v8"/><path d="m4.93 10.93 4.24 4.24"/><path d="M2 12h8"/><path d="m10.93 19.07 4.24-4.24"/><path d="M12 22v-8"/><path d="m19.07 13.07-4.24-4.24"/><path d="M22 12h-8"/><path d="m13.07 4.93-4.24 4.24"/></svg>
                </div>
              ` : `
                <div style="width:40px;height:40px;border-radius:8px;background:var(--bg-subtle);display:flex;align-items:center;justify-content:center;color:var(--text-muted);">
                  <svg style="width:20px;height:20px;" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 2v8"/><path d="m4.93 10.93 4.24 4.24"/><path d="M2 12h8"/><path d="m10.93 19.07 4.24-4.24"/><path d="M12 22v-8"/><path d="m19.07 13.07-4.24-4.24"/><path d="M22 12h-8"/><path d="m13.07 4.93-4.24 4.24"/></svg>
                </div>
              `}
              <div style="min-width:0;">
                <h4 style="font-size:14px;font-weight:700;color:var(--text-main);line-height:1.3;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">
                  ${escapeHtml(p.title)}
                </h4>
                <span style="font-size:11px;color:var(--text-muted);">by ${escapeHtml(p.author || 'Modrinth')}</span>
              </div>
            </div>

            <p style="font-size:12px;color:var(--text-muted);display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden;line-height:1.4;margin-bottom:12px;">
              ${escapeHtml(p.description)}
            </p>
          </div>

          <div style="border-top:1px solid var(--border);padding-top:10px;display:flex;align-items:center;justify-content:space-between;gap:8px;">
            <div style="font-size:11px;color:var(--text-muted);display:flex;align-items:center;gap:4px;">
              <svg style="width:12px;height:12px;" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" x2="12" y1="15" y2="3"/></svg>
              <span>${p.downloads.toLocaleString()}</span>
            </div>
            <div style="display:inline-flex;gap:6px;align-items:center;">
              <a href="https://modrinth.com/plugin/${encodeURIComponent(p.slug || p.id)}" target="_blank" rel="noopener noreferrer" class="btn btn-secondary btn-sm" style="text-decoration:none;display:inline-flex;align-items:center;gap:4px;" title="View on Modrinth" onclick="event.stopPropagation();">
                <span>Page</span>
                <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"></path><polyline points="15 3 21 3 21 9"></polyline><line x1="10" y1="14" x2="21" y2="3"></line></svg>
              </a>
              <button class="btn btn-primary btn-sm btn-open-project" data-id="${p.id}">View Details</button>
            </div>
          </div>
        </div>
      `).join('')}
    </div>

    <!-- Pagination Controls -->
    <div class="card" style="margin-top:18px;padding:12px 18px;display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:12px;">
      <div style="font-size:13px;color:var(--text-muted);">
        ${i18n.t('pageOf', { page: currentPage, total: totalPages })} &bull; 
        <span style="color:var(--text-main);font-weight:600;">${i18n.t('pluginsFound', { count: totalHits.toLocaleString() })}</span>
      </div>

      <div style="display:inline-flex;gap:8px;align-items:center;">
        <button class="btn btn-secondary btn-sm" id="btn-modrinth-prev" ${currentPage <= 1 ? 'disabled style="opacity:0.5;cursor:not-allowed;"' : ''}>
          <svg style="width:14px;height:14px;" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="15 18 9 12 15 6"/></svg>
          <span>${i18n.t('previousPage')}</span>
        </button>

        <span style="font-size:12px;padding:4px 10px;border-radius:4px;background:var(--bg-subtle);color:var(--text-main);font-weight:600;border:1px solid var(--border);">
          ${currentPage} / ${totalPages}
        </span>

        <button class="btn btn-secondary btn-sm" id="btn-modrinth-next" ${currentPage >= totalPages ? 'disabled style="opacity:0.5;cursor:not-allowed;"' : ''}>
          <span>${i18n.t('nextPage')}</span>
          <svg style="width:14px;height:14px;" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
        </button>
      </div>
    </div>
  `;

  document.getElementById('btn-modrinth-prev')?.addEventListener('click', () => {
    if (currentPage > 1 && onPageChange) onPageChange(currentPage - 1);
  });

  document.getElementById('btn-modrinth-next')?.addEventListener('click', () => {
    if (currentPage < totalPages && onPageChange) onPageChange(currentPage + 1);
  });

  container.querySelectorAll('.btn-open-project').forEach(btn => {
    btn.addEventListener('click', (e) => {
      e.stopPropagation();
      openProjectModal(btn.dataset.id, serverId);
    });
  });

  container.querySelectorAll('.card[data-project-id]').forEach(card => {
    card.addEventListener('click', () => {
      openProjectModal(card.dataset.projectId, serverId);
    });
  });
}

async function openProjectModal(projectId, serverId) {
  modal.open({
    title: i18n.t('loadingProjectInfo'),
    contentHtml: `<div style="text-align:center;padding:32px;">${i18n.t('loadingProjectInfo')}</div>`,
    wide: true
  });

  try {
    const project = await api.get(`/api/modrinth/project/${projectId}`);
    const versions = await api.get(`/api/modrinth/project/${projectId}/versions`, { serverId });

    modal.open({
      title: project.title,
      wide: true,
      contentHtml: `
        <div style="display:flex;flex-direction:column;gap:16px;">
          <div style="display:flex;align-items:center;justify-content:space-between;gap:16px;">
            <div style="display:flex;align-items:center;gap:16px;min-width:0;flex:1;">
              ${project.iconUrl ? `<img src="${project.iconUrl}" style="width:48px;height:48px;border-radius:8px;object-fit:cover;flex-shrink:0;">` : ''}
              <div style="min-width:0;">
                <p style="font-size:13px;color:var(--text-muted);">${escapeHtml(project.description)}</p>
                <div style="display:flex;gap:6px;margin-top:6px;flex-wrap:wrap;">
                  ${(project.categories || []).map(c => `<span class="badge badge-gray">${escapeHtml(c)}</span>`).join('')}
                </div>
              </div>
            </div>
            <a href="https://modrinth.com/plugin/${encodeURIComponent(project.slug || project.id)}" target="_blank" rel="noopener noreferrer" class="btn btn-secondary btn-sm" style="text-decoration:none;display:inline-flex;align-items:center;gap:4px;flex-shrink:0;" title="View on Modrinth">
              <span>Modrinth</span>
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"></path><polyline points="15 3 21 3 21 9"></polyline><line x1="10" y1="14" x2="21" y2="3"></line></svg>
            </a>
          </div>

          <h4 style="font-size:14px;font-weight:700;margin-top:8px;">${i18n.t('allVersions')}</h4>
          <div style="max-height:300px;overflow-y:auto;border:1px solid var(--border);border-radius:6px;">
            <table class="data-table">
              <thead>
                <tr>
                  <th>${i18n.t('version')}</th>
                  <th>${i18n.t('minecraftVersion')}</th>
                  <th>${i18n.t('software')}</th>
                  <th>${i18n.t('compatibility')}</th>
                  <th style="text-align:right;">${i18n.t('actions')}</th>
                </tr>
              </thead>
              <tbody>
                ${versions.map(vItem => {
                  const v = vItem.version || vItem;
                  const comp = vItem.compatibility;
                  const primaryFile = (v.files && v.files.length > 0) ? v.files.find(f => f.primary) || v.files[0] : null;

                  let compBadge = '';
                  if (comp) {
                    const compTitle = escapeHtml(comp.message);
                    if (comp.status === 'COMPATIBLE_CONFIRMED') {
                      compBadge = `<span class="badge badge-green" title="${compTitle}">Confirmed</span>`;
                    } else if (comp.status === 'PARTIAL_COMPATIBILITY') {
                      compBadge = `<span class="badge badge-amber" title="${compTitle}">Partial</span>`;
                    } else if (comp.status === 'NEEDS_ANALYSIS') {
                      compBadge = `<span class="badge badge-amber" title="${compTitle}">Review</span>`;
                    } else if (comp.status === 'UNCONFIRMED') {
                      compBadge = `<span class="badge badge-gray" title="${compTitle}">Unknown</span>`;
                    } else {
                      compBadge = `<span class="badge badge-rose" title="${compTitle}">Incompatible</span>`;
                    }
                  }

                  return `
                    <tr>
                      <td style="font-weight:600;">${escapeHtml(v.versionNumber || v.name)}</td>
                      <td style="font-size:11px;color:var(--text-muted);">${(v.gameVersions || []).slice(0, 3).join(', ')}${(v.gameVersions || []).length > 3 ? '...' : ''}</td>
                      <td style="font-size:11px;">${(v.loaders || []).join(', ')}</td>
                      <td>${compBadge}</td>
                      <td style="text-align:right;">
                        ${primaryFile && serverId ? `
                          <button class="btn btn-primary btn-sm btn-install-file"
                            data-url="${escapeHtml(primaryFile.url)}"
                            data-filename="${escapeHtml(primaryFile.filename)}"
                            data-hash="${escapeHtml(primaryFile.hashes?.sha512 || primaryFile.hashes?.sha1 || '')}"
                            data-project="${escapeHtml(project.title)}">
                            ${i18n.t('install')}
                          </button>
                        ` : `<span style="font-size:11px;color:var(--text-subtle);">Select server first</span>`}
                      </td>
                    </tr>
                  `;
                }).join('')}
              </tbody>
            </table>
          </div>
        </div>
      `,
      footerButtons: [
        { text: i18n.t('close'), className: 'btn-secondary', onClick: (close) => close() }
      ]
    });

    document.querySelectorAll('.btn-install-file').forEach(btn => {
      btn.addEventListener('click', async () => {
        const fileUrl = btn.dataset.url;
        const filename = btn.dataset.filename;
        const hash = btn.dataset.hash;
        const projectName = btn.dataset.project;

        btn.disabled = true;
        loadingModal.show(i18n.t('downloadingAndInstalling'), projectName, 10);

        try {
          const task = await api.post(`/api/servers/${serverId}/modrinth/install`, {
            fileUrl,
            filename,
            hash,
            projectName
          });

          if (!task?.id) throw new Error('Install task was not created');
          await api.waitForTask(task.id, t => {
            loadingModal.update(Math.min(25 + (t.progress || 0) * 0.7, 95), `${i18n.t('downloadingAndInstalling')} - ${projectName}...`);
          });

          loadingModal.update(100, i18n.t('installedSuccess'));
          btn.innerText = i18n.t('installed');
          btn.className = 'btn btn-secondary btn-sm';
          await new Promise(r => setTimeout(r, 450));
          loadingModal.hide();
          toast.success(`${projectName} ${i18n.t('installedSuccess')}`);
        } catch (e) {
          loadingModal.hide();
          btn.disabled = false;
          toast.error('Install failed: ' + e.message);
        }
      });
    });

  } catch (e) {
    modal.open({
      title: i18n.t('error'),
      wide: true,
      contentHtml: `<div style="text-align:center;padding:32px;color:var(--accent-rose);">Failed to load project details: ${escapeHtml(e.message)}</div>`,
      footerButtons: [
        { text: i18n.t('close'), className: 'btn-secondary', onClick: (close) => close() }
      ]
    });
  }
}
