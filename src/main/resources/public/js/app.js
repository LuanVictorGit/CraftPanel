import { api } from './api.js';
import { toast, modal, loadingModal, formatUptime } from './ui.js';
import { i18n } from './i18n.js';
import { PanelWebSocket } from './websocket.js';

import { renderDashboard } from './views/dashboard.js';
import { renderServers } from './views/servers.js';
import { renderConsole } from './views/console.js';
import { renderFiles } from './views/files.js';
import { renderPlugins } from './views/plugins.js';
import { renderModrinth } from './views/modrinth.js';
import { renderBackups } from './views/backups.js';
import { renderMonitoring } from './views/monitoring.js';
import { renderUsers } from './views/users.js';
import { renderAudit } from './views/audit.js';
import { renderSettings } from './views/settings.js';

class AppState {
  constructor() {
    this.currentUser = null;
    this.currentRole = null;
    this.currentPermissions = [];
    this.activeServerId = 'local';
    this.currentView = 'dashboard';
    this.eventsWs = null;
    this.activeTasks = [];
    this.currentCleanup = null;
  }

  navigateTo(viewName, params = {}) {
    if (this.currentCleanup) {
      try { this.currentCleanup(); } catch (e) {}
      this.currentCleanup = null;
    }

    this.currentView = viewName;
    try {
      localStorage.setItem('craftpanel_active_view', viewName);
      if (window.location.hash !== `#/${viewName}`) {
        history.replaceState(null, '', `#/${viewName}`);
      }
    } catch (e) {}

    document.querySelectorAll('.sidebar-nav .nav-item').forEach(el => {
      el.classList.toggle('active', el.dataset.view === viewName);
    });

    const pageTitle = document.getElementById('page-title');
    if (pageTitle) {
      pageTitle.innerText = i18n.t(viewName, viewName.charAt(0).toUpperCase() + viewName.slice(1));
    }
    applyTranslations();

    const contentRoot = document.getElementById('content-root');
    if (!contentRoot) return;

    switch (viewName) {
      case 'dashboard':
        renderDashboard(contentRoot, this, params);
        break;
      case 'servers':
        renderServers(contentRoot, this, params);
        break;
      case 'console':
        renderConsole(contentRoot, this, params);
        break;
      case 'files':
        renderFiles(contentRoot, this, params);
        break;
      case 'plugins':
        renderPlugins(contentRoot, this, params);
        break;
      case 'modrinth':
        renderModrinth(contentRoot, this, params);
        break;
      case 'backups':
        renderBackups(contentRoot, this, params);
        break;
      case 'monitoring':
        renderMonitoring(contentRoot, this, params);
        break;
      case 'users':
        renderUsers(contentRoot, this, params);
        break;
      case 'audit':
        renderAudit(contentRoot, this, params);
        break;
      case 'settings':
        renderSettings(contentRoot, this, params);
        break;
      default:
        renderDashboard(contentRoot, this, params);
        break;
    }
  }

  setActiveServer(serverId) {
    this.activeServerId = serverId;
    if (serverId) {
      localStorage.setItem('craftpanel_active_server', serverId);
    } else {
      localStorage.removeItem('craftpanel_active_server');
    }

    const select = document.getElementById('active-server-select');
    if (select && select.value !== (serverId || '')) {
      select.value = serverId || '';
    }

    this.updateHeaderControls();
  }

  async updateHeaderControls() {
    const quickControls = document.getElementById('header-quick-controls');
    const headerStatus = document.getElementById('header-server-status');
    const headerStatusText = document.getElementById('header-server-status-text');
    const headerStatusDot = document.querySelector('#header-server-status .status-dot');
    const btnStart = document.getElementById('btn-quick-start');
    const btnStop = document.getElementById('btn-quick-stop');
    const btnRestart = document.getElementById('btn-quick-restart');

    if (!this.activeServerId) {
      if (quickControls) quickControls.style.display = 'none';
      if (headerStatus) headerStatus.style.display = 'none';
      return;
    }

    if (quickControls) quickControls.style.display = 'flex';
    if (headerStatus) headerStatus.style.display = 'flex';

    try {
      const res = await api.get(`/api/servers/${this.activeServerId}/status`);
      const st = (res.status || 'UNKNOWN').toUpperCase();

      if (headerStatusText) {
        headerStatusText.innerText = (st === 'RUNNING') ? 'ONLINE' : (st === 'STOPPED' ? 'OFFLINE' : st);
      }
      if (headerStatusDot) {
        headerStatusDot.className = `status-dot ${st.toLowerCase()}`;
      }

      if (st === 'RUNNING') {
        startHeaderUptime(res.uptimeSeconds || 0);
        if (btnStart) {
          btnStart.disabled = true;
          btnStart.classList.add('btn-disabled');
          btnStart.title = i18n.t('serverAlreadyRunning');
        }
        if (btnStop) {
          btnStop.disabled = false;
          btnStop.classList.remove('btn-disabled');
          btnStop.className = 'btn btn-danger btn-sm';
          btnStop.title = '';
        }
        if (btnRestart) {
          btnRestart.disabled = false;
          btnRestart.classList.remove('btn-disabled');
          btnRestart.className = 'btn btn-secondary btn-sm';
          btnRestart.title = '';
        }
      } else if (st === 'STOPPED' || st === 'CRASHED') {
        stopHeaderUptime();
        if (btnStart) {
          btnStart.disabled = false;
          btnStart.classList.remove('btn-disabled');
          btnStart.className = 'btn btn-success btn-sm';
          btnStart.title = '';
        }
        if (btnStop) {
          btnStop.disabled = true;
          btnStop.classList.add('btn-disabled');
          btnStop.title = i18n.t('serverAlreadyStopped');
        }
        if (btnRestart) {
          btnRestart.disabled = true;
          btnRestart.classList.add('btn-disabled');
          btnRestart.title = i18n.t('serverAlreadyStopped');
        }
      } else {
        stopHeaderUptime();
        if (btnStart) { btnStart.disabled = true; btnStart.classList.add('btn-disabled'); }
        if (btnStop) { btnStop.disabled = true; btnStop.classList.add('btn-disabled'); }
        if (btnRestart) { btnRestart.disabled = true; btnRestart.classList.add('btn-disabled'); }
      }
    } catch (e) {
      stopHeaderUptime();
      if (headerStatusText) headerStatusText.innerText = 'OFFLINE';
      if (headerStatusDot) headerStatusDot.className = 'status-dot stopped';
      if (btnStart) {
        btnStart.disabled = false;
        btnStart.classList.remove('btn-disabled');
        btnStart.className = 'btn btn-success btn-sm';
      }
      if (btnStop) { btnStop.disabled = true; btnStop.classList.add('btn-disabled'); }
      if (btnRestart) { btnRestart.disabled = true; btnRestart.classList.add('btn-disabled'); }
    }
  }
}

let headerUptimeTimer = null;
let currentUptimeSeconds = 0;

function startHeaderUptime(seconds) {
  currentUptimeSeconds = Math.max(0, parseInt(seconds, 10) || 0);
  const badge = document.getElementById('header-uptime-badge');
  const text = document.getElementById('header-uptime-text');
  if (badge) badge.style.display = 'inline-flex';
  if (text) text.innerText = `Online: ${formatUptime(currentUptimeSeconds)}`;

  if (headerUptimeTimer) {
    clearInterval(headerUptimeTimer);
  }
  headerUptimeTimer = setInterval(() => {
    currentUptimeSeconds++;
    const t = document.getElementById('header-uptime-text');
    if (t) t.innerText = `Online: ${formatUptime(currentUptimeSeconds)}`;
  }, 1000);
}

function stopHeaderUptime() {
  if (headerUptimeTimer) {
    clearInterval(headerUptimeTimer);
    headerUptimeTimer = null;
  }
  const badge = document.getElementById('header-uptime-badge');
  if (badge) badge.style.display = 'none';
}

export function updateVersionBadge(v) {
  if (!v) return;
  const str = v.startsWith('v') ? v : `v${v}`;
  document.querySelectorAll('.brand-badge, #panel-version-badge').forEach(el => {
    el.innerText = str;
  });
}

const state = new AppState();
window.appState = state;

const validViews = ['dashboard', 'servers', 'console', 'files', 'plugins', 'modrinth', 'backups', 'monitoring', 'users', 'audit', 'settings'];
let languageListenersBound = false;
let mainListenersBound = false;

async function bootstrap() {
  applyTranslations();

  const handleLanguageChange = (newLang) => {
    const lang = newLang || i18n.getLanguage();
    const sw1 = document.getElementById('lang-switcher');
    if (sw1 && sw1.value !== lang) sw1.value = lang;
    const sw2 = document.getElementById('settings-lang-select');
    if (sw2 && sw2.value !== lang) sw2.value = lang;
    applyTranslations();
    const pageTitle = document.getElementById('page-title');
    if (pageTitle && state.currentView) {
      pageTitle.innerText = i18n.t(state.currentView, state.currentView.charAt(0).toUpperCase() + state.currentView.slice(1));
    }
    state.navigateTo(state.currentView);
  };

  const langSwitcher = document.getElementById('lang-switcher');
  if (langSwitcher) {
    langSwitcher.value = i18n.getLanguage();
  }

  if (!languageListenersBound) {
    languageListenersBound = true;
    langSwitcher?.addEventListener('change', (e) => {
      i18n.setLanguage(e.target.value);
    });
    window.addEventListener('language:changed', (e) => handleLanguageChange(e.detail));
  }

  try {
    const setupStatus = await api.get('/api/setup/status');
    if (setupStatus && setupStatus.version) {
      updateVersionBadge(setupStatus.version);
    }
    if (setupStatus.setupRequired) {
      showScreen('setup');
      setupSetupForm();
      return;
    }

    try {
      const auth = await api.get('/api/auth/me');
      if (auth && auth.version) {
        updateVersionBadge(auth.version);
      }
      state.currentUser = auth.user;
      state.currentRole = auth.role;
      state.currentPermissions = auth.permissions;
      initMainApp();
    } catch (err) {
      showScreen('login');
      setupLoginForm();
    }
  } catch (err) {
    document.getElementById('app-loading').innerHTML = `
      <div style="color:#DC2626;font-weight:600;text-align:center;padding:24px;">
        Failed to connect to CraftPanel server.<br>
        <span style="font-size:12px;color:#64748B;">${escapeHtml(err.message)}</span>
      </div>
    `;
  }
}

function showScreen(name) {
  document.getElementById('app-loading').style.display = 'none';
  document.getElementById('setup-screen').style.display = (name === 'setup') ? 'flex' : 'none';
  document.getElementById('login-screen').style.display = (name === 'login') ? 'flex' : 'none';
  document.getElementById('main-app').style.display = (name === 'main') ? 'flex' : 'none';
  applyTranslations();
}

function setupSetupForm() {
  const form = document.getElementById('setup-form');
  form.onsubmit = async (e) => {
    e.preventDefault();
    const u = document.getElementById('setup-username').value.trim();
    const d = document.getElementById('setup-displayname').value.trim();
    const p1 = document.getElementById('setup-password').value;
    const k = document.getElementById('setup-key')?.value.trim();

    try {
      await api.post('/api/setup/first-admin', { username: u, displayName: d, password: p1, setupKey: k });
      toast.success('Administrator account created successfully');
      bootstrap();
    } catch (err) {
      toast.error(err.message);
    }
  };
}

function setupLoginForm() {
  const form = document.getElementById('login-form');
  form.onsubmit = async (e) => {
    e.preventDefault();
    const u = document.getElementById('login-username').value.trim();
    const p = document.getElementById('login-password').value;

    try {
      await api.post('/api/auth/login', { username: u, password: p });
      toast.success('Signed in successfully');
      bootstrap();
    } catch (err) {
      toast.error(err.message);
    }
  };
}

async function initMainApp() {
  showScreen('main');

  const avatar = document.getElementById('user-avatar');
  const dName = document.getElementById('user-displayname');
  const rName = document.getElementById('user-rolename');

  if (avatar && state.currentUser) {
    const initials = (state.currentUser.displayName || state.currentUser.username || 'AD').substring(0, 2).toUpperCase();
    avatar.innerText = initials;
  }
  if (dName && state.currentUser) {
    dName.innerText = state.currentUser.displayName || state.currentUser.username;
  }
  if (rName && state.currentUser) {
    rName.innerText = state.currentUser.roleId || 'user';
  }

  bindMainListeners();

  await populateServerSelector();

  connectEventsWebSocket();

  const hashView = window.location.hash.replace(/^#\/?/, '').trim();
  const savedView = localStorage.getItem('craftpanel_active_view');
  const initialView = (hashView && validViews.includes(hashView)) ? hashView : ((savedView && validViews.includes(savedView)) ? savedView : 'dashboard');
  state.navigateTo(initialView);
}

function bindMainListeners() {
  if (mainListenersBound) return;
  mainListenersBound = true;

  document.getElementById('btn-logout')?.addEventListener('click', async () => {
    try {
      await api.post('/api/auth/logout');
    } catch (e) {}
    state.eventsWs?.close();
    try { state.currentCleanup?.(); } catch (e) {}
    state.currentCleanup = null;
    showScreen('login');
    setupLoginForm();
  });

  window.addEventListener('auth:unauthorized', () => {
    state.eventsWs?.close();
    loadingModal.hide();
    const modalHost = document.getElementById('modal-host');
    if (modalHost) {
      modalHost.style.display = 'none';
      modalHost.innerHTML = '';
    }
    try { state.currentCleanup?.(); } catch (e) {}
    state.currentCleanup = null;
    showScreen('login');
    setupLoginForm();
  });

  document.querySelectorAll('.sidebar-nav .nav-item').forEach(item => {
    item.addEventListener('click', () => {
      state.navigateTo(item.dataset.view);
    });
  });

  window.addEventListener('hashchange', () => {
    const hash = window.location.hash.replace(/^#\/?/, '').trim();
    if (hash && validViews.includes(hash) && hash !== state.currentView) {
      state.navigateTo(hash);
    }
  });

  setupHeaderQuickControls();
}

async function populateServerSelector() {
  const select = document.getElementById('active-server-select');
  if (!select) return;

  try {
    const servers = await api.get('/api/servers');
    if (servers.length > 0) {
      const s = servers[0];
      select.innerHTML = `<option value="${s.id}">${escapeHtml(s.name)} (:${s.serverPort})</option>`;
      state.setActiveServer(s.id);
    } else {
      select.innerHTML = `<option value="local">Host Server</option>`;
      state.setActiveServer('local');
    }
  } catch (e) {
    state.setActiveServer('local');
  }
}

let lifecyclePollInterval = null;

function showServerTransitionOverlay(cfg) {
  let host = document.getElementById('server-lifecycle-host');
  if (!host) {
    host = document.createElement('div');
    host.id = 'server-lifecycle-host';
    document.body.appendChild(host);
  }
  host.style.display = 'block';

  let iconHtml = '';
  let actionsHtml = '';

  if (cfg.status === 'stopping') {
    iconHtml = `
      <div class="server-lifecycle-icon-wrap stopping">
        <svg class="server-lifecycle-spinner" style="width:38px;height:38px;" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
          <circle cx="12" cy="12" r="10" stroke-opacity="0.25"/>
          <path d="M12 2a10 10 0 0 1 10 10" stroke-linecap="round"/>
        </svg>
      </div>
    `;
  } else if (cfg.status === 'stopped' || cfg.status === 'failed') {
    iconHtml = `
      <div class="server-lifecycle-icon-wrap stopped">
        <svg style="width:38px;height:38px;" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
          <path d="M18.36 6.64a9 9 0 1 1-12.73 0"/>
          <line x1="12" y1="2" x2="12" y2="12"/>
        </svg>
      </div>
    `;
    actionsHtml = `
      <div class="server-lifecycle-actions">
        ${cfg.status === 'stopped' ? `
        <button id="btn-lifecycle-start" class="btn btn-success" style="font-weight:600;padding:8px 20px;">
          ${i18n.t('startServer')}
        </button>` : ''}
        <button id="btn-lifecycle-close" class="btn btn-secondary" style="padding:8px 16px;">
          ${i18n.t('close')}
        </button>
      </div>
    `;
  } else if (cfg.status === 'restarting') {
    iconHtml = `
      <div class="server-lifecycle-icon-wrap restarting">
        <svg class="server-lifecycle-spinner" style="width:38px;height:38px;" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
          <polyline points="23 4 23 10 17 10"/>
          <polyline points="1 20 1 14 7 14"/>
          <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/>
        </svg>
      </div>
    `;
  } else if (cfg.status === 'starting') {
    iconHtml = `
      <div class="server-lifecycle-icon-wrap starting">
        <svg class="server-lifecycle-spinner" style="width:38px;height:38px;" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
          <circle cx="12" cy="12" r="10" stroke-opacity="0.25"/>
          <path d="M12 2a10 10 0 0 1 10 10" stroke-linecap="round"/>
        </svg>
      </div>
    `;
  } else if (cfg.status === 'online') {
    iconHtml = `
      <div class="server-lifecycle-icon-wrap starting">
        <svg style="width:38px;height:38px;color:#10B981;" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
          <polyline points="20 6 9 17 4 12"/>
        </svg>
      </div>
    `;
  }

  host.innerHTML = `
    <div class="server-lifecycle-overlay">
      <div class="server-lifecycle-card">
        ${iconHtml}
        <div>
          <h2 class="server-lifecycle-title">${escapeHtml(cfg.title)}</h2>
          <p class="server-lifecycle-desc">${escapeHtml(cfg.desc || '')}</p>
        </div>
        ${actionsHtml}
      </div>
    </div>
  `;

  document.getElementById('btn-lifecycle-start')?.addEventListener('click', () => {
    executeServerAction('start');
  });
  document.getElementById('btn-lifecycle-close')?.addEventListener('click', () => {
    hideServerTransitionOverlay();
  });
}

function hideServerTransitionOverlay() {
  if (lifecyclePollInterval) {
    clearInterval(lifecyclePollInterval);
    lifecyclePollInterval = null;
  }
  const host = document.getElementById('server-lifecycle-host');
  if (host) host.style.display = 'none';
}

async function executeServerAction(action) {
  if (lifecyclePollInterval) {
    clearInterval(lifecyclePollInterval);
    lifecyclePollInterval = null;
  }

  const serverId = state.activeServerId || 'local';

  if (action === 'stop') {
    showServerTransitionOverlay({
      status: 'stopping',
      title: i18n.t('serverStoppingTitle'),
      desc: i18n.t('serverStoppingDesc')
    });

    try {
      await api.post(`/api/servers/${serverId}/stop`);
    } catch (e) {
      toast.error(e.message);
    }
    state.updateHeaderControls();

    let checkCount = 0;
    lifecyclePollInterval = setInterval(async () => {
      checkCount++;
      try {
        const res = await api.get(`/api/servers/${serverId}/status`);
        if (res.status === 'STOPPED' || res.status === 'CRASHED') {
          clearInterval(lifecyclePollInterval);
          lifecyclePollInterval = null;
          showServerTransitionOverlay({
            status: 'stopped',
            title: i18n.t('serverStoppedTitle'),
            desc: i18n.t('serverStoppedDesc')
          });
          state.updateHeaderControls();
        }
      } catch (err) {
        if (checkCount >= 2) {
          clearInterval(lifecyclePollInterval);
          lifecyclePollInterval = null;
          showServerTransitionOverlay({
            status: 'stopped',
            title: i18n.t('serverStoppedTitle'),
            desc: i18n.t('serverStoppedDesc')
          });
          state.updateHeaderControls();
        }
      }
    }, 1500);

  } else if (action === 'restart') {
    showServerTransitionOverlay({
      status: 'restarting',
      title: i18n.t('serverRestartingTitle'),
      desc: i18n.t('serverRestartingDesc')
    });

    try {
      await api.post(`/api/servers/${serverId}/restart`);
    } catch (e) {
      // A refused restart leaves the server running; a dropped connection (TypeError) means it is already going down.
      if (!(e instanceof TypeError)) {
        hideServerTransitionOverlay();
        toast.error(e.message);
        state.updateHeaderControls();
        return;
      }
    }
    state.updateHeaderControls();

    // The panel goes down with the server and comes back with it; give up if that takes more than five minutes.
    const deadline = Date.now() + 5 * 60 * 1000;
    let waited = 0;
    lifecyclePollInterval = setInterval(async () => {
      waited++;
      if (waited < 3) return;
      if (Date.now() > deadline) {
        clearInterval(lifecyclePollInterval);
        lifecyclePollInterval = null;
        showServerTransitionOverlay({
          status: 'failed',
          title: i18n.t('serverRestartTimeoutTitle'),
          desc: i18n.t('serverRestartTimeoutDesc')
        });
        return;
      }
      try {
        const res = await api.get(`/api/servers/${serverId}/status`);
        if (res.status === 'RUNNING') {
          clearInterval(lifecyclePollInterval);
          lifecyclePollInterval = null;
          showServerTransitionOverlay({
            status: 'online',
            title: i18n.t('serverOnlineSuccess'),
            desc: i18n.t('operationSuccess')
          });
          state.updateHeaderControls();
          setTimeout(() => {
            hideServerTransitionOverlay();
            state.navigateTo(state.currentView);
          }, 900);
        }
      } catch (ignored) {}
    }, 1800);

  } else if (action === 'start') {
    showServerTransitionOverlay({
      status: 'starting',
      title: i18n.t('serverStartingTitle'),
      desc: i18n.t('serverStartingDesc')
    });

    try {
      await api.post(`/api/servers/${serverId}/start`);
    } catch (e) {
      toast.error(e.message);
    }
    state.updateHeaderControls();

    lifecyclePollInterval = setInterval(async () => {
      try {
        const res = await api.get(`/api/servers/${serverId}/status`);
        if (res.status === 'RUNNING') {
          clearInterval(lifecyclePollInterval);
          lifecyclePollInterval = null;
          showServerTransitionOverlay({
            status: 'online',
            title: i18n.t('serverOnlineSuccess'),
            desc: i18n.t('operationSuccess')
          });
          state.updateHeaderControls();
          setTimeout(() => {
            hideServerTransitionOverlay();
            state.navigateTo(state.currentView);
          }, 900);
        }
      } catch (ignored) {}
    }, 1500);
  }
}

state.executeServerAction = executeServerAction;

function setupHeaderQuickControls() {
  document.getElementById('btn-quick-start')?.addEventListener('click', () => {
    executeServerAction('start');
  });

  document.getElementById('btn-quick-stop')?.addEventListener('click', () => {
    executeServerAction('stop');
  });

  document.getElementById('btn-quick-restart')?.addEventListener('click', () => {
    executeServerAction('restart');
  });
}

function connectEventsWebSocket() {
  state.eventsWs = new PanelWebSocket('/ws/events', (msg) => {
    if (msg.type === 'task_update') {
      handleTaskUpdate(msg.payload);
    } else if (msg.type === 'file_change') {
      window.dispatchEvent(new CustomEvent('craftpanel:file_change', { detail: msg.payload }));
    }
  });
  state.eventsWs.connect();
}

function handleTaskUpdate(task) {
  const badge = document.getElementById('active-tasks-badge');
  const countEl = document.getElementById('active-tasks-count');

  if (task.state === 'RUNNING' || task.state === 'QUEUED') {
    if (badge) badge.style.display = 'inline-flex';
    if (countEl) countEl.innerText = `${task.message || 'Processing...'} (${task.progress}%)`;
  } else {
    // Plugin installs report their own result in the install dialog.
    if (task.type !== 'MODRINTH_INSTALL') {
      if (task.state === 'COMPLETED') {
        toast.success(task.message || 'Task completed');
      } else if (task.state === 'FAILED') {
        toast.error(task.error || task.message || 'Task failed');
      }
    }
    if (badge) badge.style.display = 'none';
  }
}

function applyTranslations() {
  document.querySelectorAll('[data-i18n]').forEach(el => {
    const key = el.dataset.i18n;
    const trans = i18n.t(key);
    if (trans && trans !== key) {
      el.innerText = trans;
    }
  });
  document.querySelectorAll('[data-i18n-placeholder]').forEach(el => {
    const key = el.dataset.i18nPlaceholder;
    const trans = i18n.t(key);
    if (trans && trans !== key) {
      el.placeholder = trans;
    }
  });
  const pageTitle = document.getElementById('page-title');
  if (pageTitle && state.currentView) {
    pageTitle.innerText = i18n.t(state.currentView, state.currentView.charAt(0).toUpperCase() + state.currentView.slice(1));
  }
  const btnLogout = document.getElementById('btn-logout');
  if (btnLogout) {
    btnLogout.title = i18n.t('logout');
  }
}

function escapeHtml(str) {
  if (!str) return '';
  return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

document.addEventListener('DOMContentLoaded', bootstrap);
