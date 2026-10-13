import { api } from '../api.js';
import { toast, modal, formatUptime, escapeHtml } from '../ui.js';
import { i18n } from '../i18n.js';

export async function renderServers(root, state, params = {}) {
  root.innerHTML = `
    <div style="display:flex;flex-direction:column;gap:20px;">
      <div style="display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:12px;">
        <div>
          <h3 style="font-size:18px;font-weight:700;color:var(--text-main);">${i18n.t('servers')}</h3>
          <p style="font-size:13px;color:var(--text-muted);">${i18n.t('singleInstanceDesc')}</p>
        </div>
        <span class="badge green" style="padding:6px 14px;font-size:12px;font-weight:600;">${i18n.t('hostInstanceBadge')}</span>
      </div>

      <div class="card" style="padding:0;overflow:hidden;">
        <div style="padding:14px 18px;border-bottom:1px solid var(--border);display:flex;align-items:center;justify-content:space-between;gap:12px;background:var(--bg-page);">
          <div style="display:flex;align-items:center;gap:10px;flex:1;max-width:360px;">
            <svg style="width:16px;height:16px;color:var(--text-subtle);" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
            <input type="text" id="servers-search-input" class="form-input" style="padding:6px 10px;font-size:13px;" placeholder="${i18n.t('search')}...">
          </div>
          <button class="btn btn-secondary btn-sm" id="btn-refresh-servers">${i18n.t('refresh')}</button>
        </div>

        <div id="servers-table-container">
          <div style="text-align:center;padding:32px;color:var(--text-muted);">${i18n.t('loadingServers')}</div>
        </div>
      </div>
    </div>
  `;

  document.getElementById('btn-refresh-servers')?.addEventListener('click', () => loadServersTable(state));
  document.getElementById('servers-search-input')?.addEventListener('input', (e) => filterServers(e.target.value));

  await loadServersTable(state);
}

let cachedServers = [];

async function loadServersTable(state) {
  try {
    cachedServers = await api.get('/api/servers');
    renderTable(cachedServers, state);
  } catch (e) {
    toast.error('Failed to load servers: ' + e.message);
  }
}

function filterServers(query) {
  if (!query || query.trim() === '') {
    renderTable(cachedServers, window.appState);
    return;
  }
  const q = query.toLowerCase().trim();
  const filtered = cachedServers.filter(s =>
    s.name.toLowerCase().includes(q) ||
    s.software.toLowerCase().includes(q) ||
    s.minecraftVersion.toLowerCase().includes(q) ||
    String(s.serverPort).includes(q)
  );
  renderTable(filtered, window.appState);
}

function renderTable(servers, state) {
  const container = document.getElementById('servers-table-container');
  if (!container) return;

  if (servers.length === 0) {
    container.innerHTML = `
      <div style="text-align:center;padding:48px;color:var(--text-muted);">
        <p style="font-size:14px;font-weight:600;">${i18n.t('noInstancesYet')}</p>
      </div>
    `;
    return;
  }

  container.innerHTML = `
    <div class="table-wrap">
      <table class="data-table">
        <thead>
          <tr>
            <th>${i18n.t('status')}</th>
            <th>${i18n.t('instanceName')}</th>
            <th>${i18n.t('software')}</th>
            <th>${i18n.t('version')}</th>
            <th>${i18n.t('port')}</th>
            <th>${i18n.t('players')}</th>
            <th>RAM</th>
            <th>${i18n.t('uptime')}</th>
            <th style="text-align:right;">${i18n.t('actions')}</th>
          </tr>
        </thead>
        <tbody>
          ${servers.map(s => `
            <tr>
              <td>
                <span class="status-dot ${s.status.toLowerCase()}"></span>
                <span style="font-size:12px;font-weight:600;margin-left:6px;">${s.status}</span>
              </td>
              <td>
                <div style="font-weight:600;color:var(--text-main);cursor:pointer;" class="server-title-click" data-id="${s.id}">
                  ${escapeHtml(s.name)}
                </div>
                <div style="font-size:11px;color:var(--text-muted);">${escapeHtml(s.description || '')}</div>
              </td>
              <td><span class="badge badge-gray">${escapeHtml(s.software.toUpperCase())}</span></td>
              <td><span class="badge badge-blue">${escapeHtml(s.minecraftVersion)}</span></td>
              <td><span style="font-family:var(--font-mono);font-size:12px;">:${s.serverPort}</span></td>
              <td><strong>${s.onlinePlayers || 0}</strong> / ${s.maxPlayers || 20}</td>
              <td style="font-size:12px;">${s.minMemoryMb}M - ${s.maxMemoryMb}M</td>
              <td style="color:var(--text-muted);font-size:12px;">${formatUptime(s.uptimeSeconds)}</td>
              <td style="text-align:right;">
                <div style="display:inline-flex;gap:6px;">
                  <button class="btn btn-secondary btn-sm btn-manage-srv" data-id="${s.id}">${i18n.t('manage')}</button>
                </div>
              </td>
            </tr>
          `).join('')}
        </tbody>
      </table>
    </div>
  `;

  container.querySelectorAll('.server-title-click, .btn-manage-srv').forEach(el => {
    el.addEventListener('click', () => {
      const id = el.dataset.id;
      state.setActiveServer(id);
      state.navigateTo('console');
    });
  });
}

export function openCreateServerModal(state) {
  const content = `
    <form id="create-server-modal-form">
      <div class="grid-cols-2">
        <div class="form-group">
          <label class="form-label">${i18n.t('instanceName')}</label>
          <input type="text" id="new-srv-name" class="form-input" placeholder="e.g. Survival SMP" required>
        </div>
        <div class="form-group">
          <label class="form-label">${i18n.t('selectSoftware')}</label>
          <select id="new-srv-software" class="form-select">
            <option value="paper">Paper (Recommended, 1.8.8 - Latest)</option>
            <option value="purpur">Purpur (High Performance)</option>
            <option value="folia">Folia (Multi-threaded Regions)</option>
            <option value="vanilla">Vanilla (Official Mojang)</option>
          </select>
        </div>
      </div>

      <div class="grid-cols-2">
        <div class="form-group">
          <label class="form-label">${i18n.t('minecraftVersion')}</label>
          <select id="new-srv-version" class="form-select">
            <option value="1.20.4">Loading versions...</option>
          </select>
        </div>
        <div class="form-group">
          <label class="form-label">${i18n.t('buildNumber')}</label>
          <select id="new-srv-build" class="form-select">
            <option value="latest">latest</option>
          </select>
        </div>
      </div>

      <div class="grid-cols-3">
        <div class="form-group">
          <label class="form-label">${i18n.t('minMemory')}</label>
          <input type="number" id="new-srv-min-ram" class="form-input" value="1024" min="512" step="256" required>
        </div>
        <div class="form-group">
          <label class="form-label">${i18n.t('maxMemory')}</label>
          <input type="number" id="new-srv-max-ram" class="form-input" value="2048" min="1024" step="256" required>
        </div>
        <div class="form-group">
          <label class="form-label">${i18n.t('serverPort')}</label>
          <input type="number" id="new-srv-port" class="form-input" value="25565" min="1024" max="65535" required>
        </div>
      </div>

      <div class="form-group">
        <label class="form-label">${i18n.t('description')}</label>
        <input type="text" id="new-srv-desc" class="form-input" placeholder="Optional server description">
      </div>

      <div class="form-group">
        <label class="form-label">${i18n.t('jvmArgs')}</label>
        <input type="text" id="new-srv-jvm" class="form-input" placeholder="-XX:+UseG1GC -Dfile.encoding=UTF-8">
      </div>

      <div style="margin-top:14px;padding:12px;background:var(--bg-subtle);border-radius:var(--radius-sm);display:flex;align-items:center;gap:10px;">
        <input type="checkbox" id="new-srv-eula" checked style="width:16px;height:16px;cursor:pointer;">
        <label for="new-srv-eula" style="font-size:12px;font-weight:600;color:var(--text-main);cursor:pointer;">
          ${i18n.t('acceptEula')}
        </label>
      </div>
    </form>
  `;

  modal.open({
    title: i18n.t('createServerTitle'),
    contentHtml: content,
    wide: true,
    footerButtons: [
      { text: i18n.t('cancel'), className: 'btn-secondary', onClick: (close) => close() },
      {
        text: i18n.t('deployServer'),
        className: 'btn-primary',
        onClick: async (close) => {
          const form = document.getElementById('create-server-modal-form');
          if (!form || !form.checkValidity()) {
            form?.reportValidity();
            return;
          }

          const name = document.getElementById('new-srv-name').value;
          const software = document.getElementById('new-srv-software').value;
          const version = document.getElementById('new-srv-version').value;
          const build = document.getElementById('new-srv-build').value;
          const minRam = parseInt(document.getElementById('new-srv-min-ram').value, 10);
          const maxRam = parseInt(document.getElementById('new-srv-max-ram').value, 10);
          const port = parseInt(document.getElementById('new-srv-port').value, 10);
          const desc = document.getElementById('new-srv-desc').value;
          const jvm = document.getElementById('new-srv-jvm').value;
          const eula = document.getElementById('new-srv-eula').checked;

          try {
            close();
            toast.info('Starting server deployment...');
            await api.post('/api/installer/install', {
              name,
              description: desc,
              software,
              minecraftVersion: version,
              buildNumber: build,
              minMemoryMb: minRam,
              maxMemoryMb: maxRam,
              serverPort: port,
              jvmArgs: jvm,
              acceptEula: eula
            });
            toast.success('Deployment task queued. Check progress in active tasks indicator.');
            setTimeout(() => loadServersTable(state), 2000);
          } catch (e) {
            toast.error(e.message);
          }
        }
      }
    ]
  });

  const softSelect = document.getElementById('new-srv-software');
  const verSelect = document.getElementById('new-srv-version');
  const bldSelect = document.getElementById('new-srv-build');

  async function updateVersions(software) {
    try {
      verSelect.innerHTML = `<option value="">Loading versions...</option>`;
      const versions = await api.get('/api/installer/versions', { software });
      verSelect.innerHTML = versions.map(v => `<option value="${v}">${v}</option>`).join('');
      if (versions.length > 0) {
        updateBuilds(software, versions[0]);
      }
    } catch (e) {
      verSelect.innerHTML = `<option value="1.20.4">1.20.4</option>`;
    }
  }

  async function updateBuilds(software, version) {
    try {
      bldSelect.innerHTML = `<option value="latest">latest</option>`;
      const builds = await api.get('/api/installer/builds', { software, version });
      bldSelect.innerHTML = builds.map(b => `<option value="${b}">${b}</option>`).join('');
    } catch (e) {
      bldSelect.innerHTML = `<option value="latest">latest</option>`;
    }
  }

  softSelect?.addEventListener('change', (e) => updateVersions(e.target.value));
  verSelect?.addEventListener('change', (e) => updateBuilds(softSelect.value, e.target.value));

  updateVersions('paper');
}
