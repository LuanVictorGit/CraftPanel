import { api } from '../api.js';
import { toast, formatUptime } from '../ui.js';
import { i18n } from '../i18n.js';

export async function renderDashboard(root, state) {
  root.innerHTML = `
    <div style="display:flex;flex-direction:column;gap:24px;">
      <!-- Stats Cards -->
      <div class="grid-cols-4">
        <div class="stat-card">
          <div class="stat-icon-wrap blue">
            <svg style="width:22px;height:22px;" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect width="20" height="8" x="2" y="2" rx="2"/><rect width="20" height="8" x="2" y="14" rx="2"/></svg>
          </div>
          <div class="stat-content">
            <span class="stat-value" id="dash-total-servers">-</span>
            <span class="stat-label">${i18n.t('registeredInstances')}</span>
          </div>
        </div>

        <div class="stat-card">
          <div class="stat-icon-wrap green">
            <svg style="width:22px;height:22px;" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="5 3 19 12 5 21 5 3"/></svg>
          </div>
          <div class="stat-content">
            <span class="stat-value" id="dash-running-servers">-</span>
            <span class="stat-label">${i18n.t('onlineServers')}</span>
          </div>
        </div>

        <div class="stat-card">
          <div class="stat-icon-wrap amber">
            <svg style="width:22px;height:22px;" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/></svg>
          </div>
          <div class="stat-content">
            <span class="stat-value" id="dash-total-players">-</span>
            <span class="stat-label">${i18n.t('connectedPlayers')}</span>
          </div>
        </div>

        <div class="stat-card">
          <div class="stat-icon-wrap rose">
            <svg style="width:22px;height:22px;" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect width="18" height="18" x="3" y="3" rx="2"/><path d="M7 7h10"/><path d="M7 12h10"/></svg>
          </div>
          <div class="stat-content">
            <span class="stat-value" id="dash-total-ram">-</span>
            <span class="stat-label">${i18n.t('allocatedRam')}</span>
          </div>
        </div>
      </div>

      <!-- Quick Server Management -->
      <div class="card">
        <div class="card-header">
          <div>
            <h3 class="card-title">${i18n.t('activeInstances')}</h3>
            <span class="card-subtitle">${i18n.t('instancesOverview')}</span>
          </div>
          <span class="badge green" style="padding:4px 10px;font-size:12px;font-weight:600;">${i18n.t('hostInstanceBadge')}</span>
        </div>

        <div id="dash-servers-list">
          <div style="text-align:center;padding:32px;color:var(--text-muted);">${i18n.t('loadingServers')}</div>
        </div>
      </div>
    </div>
  `;

  await loadDashboardData(state);
}

async function loadDashboardData(state) {
  try {
    const servers = await api.get('/api/servers');

    let runningCount = 0;
    let totalPlayers = 0;
    let totalRamMb = 0;

    servers.forEach(s => {
      if (s.status === 'RUNNING') runningCount++;
      totalPlayers += (s.onlinePlayers || 0);
      totalRamMb += (s.maxMemoryMb || 0);
    });

    const elTotal = document.getElementById('dash-total-servers');
    const elRun = document.getElementById('dash-running-servers');
    const elPlay = document.getElementById('dash-total-players');
    const elRam = document.getElementById('dash-total-ram');

    if (elTotal) elTotal.innerText = servers.length;
    if (elRun) elRun.innerText = runningCount;
    if (elPlay) elPlay.innerText = totalPlayers;
    if (elRam) elRam.innerText = `${(totalRamMb / 1024).toFixed(1)} GB`;

    const container = document.getElementById('dash-servers-list');
    if (!container) return;

    if (servers.length === 0) {
      container.innerHTML = `
        <div style="text-align:center;padding:48px;color:var(--text-muted);">
          <svg style="width:42px;height:42px;margin-bottom:12px;color:var(--text-subtle);" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><rect width="20" height="8" x="2" y="2" rx="2"/><rect width="20" height="8" x="2" y="14" rx="2"/></svg>
          <div style="font-weight:600;font-size:15px;color:var(--text-main);margin-bottom:4px;">${i18n.t('noInstancesYet')}</div>
          <p style="font-size:13px;max-width:360px;margin:0 auto 16px auto;">${i18n.t('noInstancesDesc')}</p>
          <button class="btn btn-primary" id="btn-create-first-server">${i18n.t('createFirstServer')}</button>
        </div>
      `;
      document.getElementById('btn-create-first-server')?.addEventListener('click', () => {
        state.navigateTo('servers', { openCreateModal: true });
      });
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
              <th>${i18n.t('uptime')}</th>
              <th style="text-align:right;">${i18n.t('quickActions')}</th>
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
                  <div style="font-weight:600;color:var(--text-main);cursor:pointer;" class="server-item-title" data-server-id="${s.id}">
                    ${escapeHtml(s.name)}
                  </div>
                  <div style="font-size:11px;color:var(--text-muted);">${escapeHtml(s.description || '')}</div>
                </td>
                <td><span class="badge badge-gray">${escapeHtml(s.software.toUpperCase())}</span></td>
                <td><span class="badge badge-blue">${escapeHtml(s.minecraftVersion)}</span></td>
                <td><span style="font-family:var(--font-mono);font-size:12px;">:${s.serverPort}</span></td>
                <td><strong>${s.onlinePlayers || 0}</strong> / ${s.maxPlayers || 20}</td>
                <td style="color:var(--text-muted);font-size:12px;">${formatUptime(s.uptimeSeconds)}</td>
                <td style="text-align:right;">
                  <div style="display:inline-flex;gap:6px;">
                    ${s.status === 'RUNNING' ? `
                      <button class="btn btn-secondary btn-sm btn-action-stop" data-id="${s.id}">${i18n.t('stop')}</button>
                      <button class="btn btn-secondary btn-sm btn-action-restart" data-id="${s.id}">${i18n.t('restart')}</button>
                    ` : `
                      <button class="btn btn-success btn-sm btn-action-start" data-id="${s.id}">${i18n.t('start')}</button>
                    `}
                    <button class="btn btn-primary btn-sm btn-action-manage" data-id="${s.id}">${i18n.t('manage')}</button>
                  </div>
                </td>
              </tr>
            `).join('')}
          </tbody>
        </table>
      </div>
    `;

    container.querySelectorAll('.btn-action-start').forEach(btn => {
      btn.addEventListener('click', async () => {
        const id = btn.dataset.id;
        try {
          await api.post(`/api/servers/${id}/start`);
          toast.success('Start command sent');
          await loadDashboardData(state);
        } catch (e) {
          toast.error(e.message);
        }
      });
    });

    container.querySelectorAll('.btn-action-stop').forEach(btn => {
      btn.addEventListener('click', async () => {
        const id = btn.dataset.id;
        try {
          await api.post(`/api/servers/${id}/stop`);
          toast.info('Graceful shutdown requested');
          await loadDashboardData(state);
        } catch (e) {
          toast.error(e.message);
        }
      });
    });

    container.querySelectorAll('.btn-action-restart').forEach(btn => {
      btn.addEventListener('click', async () => {
        const id = btn.dataset.id;
        try {
          await api.post(`/api/servers/${id}/restart`);
          toast.info('Restart requested');
          await loadDashboardData(state);
        } catch (e) {
          toast.error(e.message);
        }
      });
    });

    container.querySelectorAll('.btn-action-manage, .server-item-title').forEach(el => {
      el.addEventListener('click', () => {
        const id = el.dataset.id || el.dataset.serverId;
        state.setActiveServer(id);
        state.navigateTo('console');
      });
    });

  } catch (e) {
    toast.error('Failed to load dashboard: ' + e.message);
  }
}

function escapeHtml(str) {
  if (!str) return '';
  return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}
