import { api } from '../api.js';
import { toast, modal, formatBytes, escapeHtml, loadingModal } from '../ui.js';
import { i18n } from '../i18n.js';

export async function renderModrinth(root, state) {
  const serverId = state.activeServerId;

  root.innerHTML = `
    <div style="display:flex;flex-direction:column;gap:18px;">
      <!-- Search & Filters -->
      <div class="card" style="padding:16px;">
        <div style="display:flex;flex-wrap:wrap;gap:12px;align-items:center;">
          <div style="flex:1;min-width:240px;position:relative;">
            <input type="text" id="modrinth-search-input" class="form-input" placeholder="${i18n.t('searchPlaceholder')}">
          </div>

          <div style="width:160px;">
            <select id="modrinth-version-filter" class="form-select">
              <option value="">${i18n.t('allVersions')}</option>
              <option value="1.21.1">1.21.1</option>
              <option value="1.20.4">1.20.4</option>
              <option value="1.19.4">1.19.4</option>
              <option value="1.18.2">1.18.2</option>
              <option value="1.16.5">1.16.5</option>
              <option value="1.12.2">1.12.2</option>
              <option value="1.8.8">1.8.8</option>
            </select>
          </div>

          <div style="width:150px;">
            <select id="modrinth-loader-filter" class="form-select">
              <option value="">${i18n.t('allPlatforms')}</option>
              <option value="paper">Paper</option>
              <option value="folia">Folia</option>
              <option value="purpur">Purpur</option>
              <option value="spigot">Spigot</option>
              <option value="bukkit">Bukkit</option>
            </select>
          </div>

          <button class="btn btn-primary" id="btn-search-modrinth">
            <svg style="width:15px;height:15px;" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
            ${i18n.t('search')}
          </button>
        </div>
      </div>

      <!-- Results Grid -->
      <div id="modrinth-results-container">
        <div style="text-align:center;padding:48px;color:var(--text-muted);">${i18n.t('loading')}</div>
      </div>
    </div>
  `;

  document.getElementById('btn-search-modrinth')?.addEventListener('click', () => executeSearch(serverId));
  document.getElementById('modrinth-search-input')?.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') executeSearch(serverId);
  });
  document.getElementById('modrinth-version-filter')?.addEventListener('change', () => executeSearch(serverId));
  document.getElementById('modrinth-loader-filter')?.addEventListener('change', () => executeSearch(serverId));

  await executeSearch(serverId);
}

async function executeSearch(serverId) {
  const query = document.getElementById('modrinth-search-input')?.value.trim() || '';
  const version = document.getElementById('modrinth-version-filter')?.value || '';
  const loader = document.getElementById('modrinth-loader-filter')?.value || '';

  const container = document.getElementById('modrinth-results-container');
  if (container) {
    container.innerHTML = `<div style="text-align:center;padding:32px;color:var(--text-muted);">${i18n.t('searchingModrinth')}</div>`;
  }

  try {
    const res = await api.get('/api/modrinth/search', {
      query: query || undefined,
      version: version || undefined,
      loader: loader || undefined,
      limit: 24
    });

    renderModrinthHits(res.hits || [], serverId);
  } catch (e) {
    if (container) {
      container.innerHTML = `<div style="text-align:center;padding:32px;color:var(--accent-rose);">Search error: ${escapeHtml(e.message)}</div>`;
    }
  }
}

function renderModrinthHits(hits, serverId) {
  const container = document.getElementById('modrinth-results-container');
  if (!container) return;

  if (hits.length === 0) {
    container.innerHTML = `<div style="text-align:center;padding:48px;color:var(--text-muted);">${i18n.t('noPluginsFound')}</div>`;
    return;
  }

  container.innerHTML = `
    <div class="grid-cols-3">
      ${hits.map(p => `
        <div class="card" style="display:flex;flex-direction:column;justify-content:space-between;cursor:pointer;" data-project-id="${p.id}">
          <div>
            <div style="display:flex;align-items:flex-start;gap:12px;margin-bottom:12px;">
              ${p.iconUrl ? `
                <img src="${p.iconUrl}" alt="" style="width:40px;height:40px;border-radius:8px;object-fit:cover;flex-shrink:0;" onerror="this.onerror=null;this.style.display='none';this.nextElementSibling.style.display='flex';">
                <div style="display:none;width:40px;height:40px;border-radius:8px;background:var(--bg-subtle);align-items:center;justify-content:center;color:var(--text-subtle);font-size:18px;">🧩</div>
              ` : `
                <div style="width:40px;height:40px;border-radius:8px;background:var(--bg-subtle);display:flex;align-items:center;justify-content:center;color:var(--text-subtle);font-size:18px;">🧩</div>
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
  `;

  container.querySelectorAll('.btn-open-project').forEach(btn => {
    btn.addEventListener('click', (e) => {
      e.stopPropagation();
      openProjectModal(btn.dataset.id, serverId);
    });
  });

  container.querySelectorAll('.card').forEach(card => {
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
                    if (comp.status === 'COMPATIBLE_CONFIRMED') {
                      compBadge = `<span class="badge badge-green" title="${comp.message}">Confirmed</span>`;
                    } else if (comp.status === 'PARTIAL_COMPATIBILITY') {
                      compBadge = `<span class="badge badge-amber" title="${comp.message}">Partial</span>`;
                    } else if (comp.status === 'NEEDS_ANALYSIS') {
                      compBadge = `<span class="badge badge-amber" title="${comp.message}">Review</span>`;
                    } else {
                      compBadge = `<span class="badge badge-rose" title="${comp.message}">Incompatible</span>`;
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
                            data-url="${primaryFile.url}"
                            data-filename="${primaryFile.filename}"
                            data-hash="${primaryFile.hashes?.sha512 || primaryFile.hashes?.sha1 || ''}"
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

          if (task && task.id) {
            for (let i = 0; i < 40; i++) {
              await new Promise(r => setTimeout(r, 600));
              loadingModal.update(Math.min(25 + i * 2.5, 95), `${i18n.t('downloadingAndInstalling')} - ${projectName}...`);
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
          toast.success(`${projectName} ${i18n.t('installedSuccess')}`);
        } catch (e) {
          loadingModal.hide();
          btn.disabled = false;
          toast.error('Install failed: ' + e.message);
        }
      });
    });

  } catch (e) {
    toast.error('Failed to load project details: ' + e.message);
  }
}
