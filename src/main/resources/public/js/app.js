import { api } from './api.js';
import { toast, modal } from './ui.js';
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
  }

  navigateTo(viewName, params = {}) {
    this.currentView = viewName;
    document.querySelectorAll('.sidebar-nav .nav-item').forEach(el => {
      el.classList.toggle('active', el.dataset.view === viewName);
    });

    const pageTitle = document.getElementById('page-title');
    if (pageTitle) {
      pageTitle.innerText = i18n.t(viewName, viewName.charAt(0).toUpperCase() + viewName.slice(1));
    }

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

    if (!this.activeServerId) {
      if (quickControls) quickControls.style.display = 'none';
      if (headerStatus) headerStatus.style.display = 'none';
      return;
    }

    if (quickControls) quickControls.style.display = 'flex';
    if (headerStatus) headerStatus.style.display = 'flex';

    try {
      const res = await api.get(`/api/servers/${this.activeServerId}/status`);
      const state = res.status;
      if (headerStatusText) headerStatusText.innerText = state;
      if (headerStatusDot) headerStatusDot.className = `status-dot ${state.toLowerCase()}`;
    } catch (e) {}
  }
}

const state = new AppState();
window.appState = state;

async function bootstrap() {
  applyTranslations();

  const langSwitcher = document.getElementById('lang-switcher');
  if (langSwitcher) {
    langSwitcher.value = i18n.getLanguage();
    langSwitcher.addEventListener('change', (e) => {
      i18n.setLanguage(e.target.value);
    });
  }

  window.addEventListener('language:changed', () => {
    applyTranslations();
    state.navigateTo(state.currentView);
  });

  try {
    const setupStatus = await api.get('/api/setup/status');
    if (setupStatus.setupRequired) {
      showScreen('setup');
      setupSetupForm();
      return;
    }

    try {
      const auth = await api.get('/api/auth/me');
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
        <span style="font-size:12px;color:#64748B;">${err.message}</span>
      </div>
    `;
  }
}

function showScreen(name) {
  document.getElementById('app-loading').style.display = 'none';
  document.getElementById('setup-screen').style.display = (name === 'setup') ? 'flex' : 'none';
  document.getElementById('login-screen').style.display = (name === 'login') ? 'flex' : 'none';
  document.getElementById('main-app').style.display = (name === 'main') ? 'flex' : 'none';
}

function setupSetupForm() {
  const form = document.getElementById('setup-form');
  form.onsubmit = async (e) => {
    e.preventDefault();
    const u = document.getElementById('setup-username').value.trim();
    const d = document.getElementById('setup-displayname').value.trim();
    const p1 = document.getElementById('setup-password').value;
    const p2 = document.getElementById('setup-password-confirm').value;

    if (p1 !== p2) {
      toast.error('Passwords do not match');
      return;
    }

    try {
      await api.post('/api/setup/first-admin', { username: u, displayName: d, password: p1 });
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

  document.getElementById('btn-logout')?.addEventListener('click', async () => {
    try {
      await api.post('/api/auth/logout');
    } catch (e) {}
    state.eventsWs?.close();
    showScreen('login');
    setupLoginForm();
  });

  window.addEventListener('auth:unauthorized', () => {
    state.eventsWs?.close();
    showScreen('login');
    setupLoginForm();
  });

  document.querySelectorAll('.sidebar-nav .nav-item').forEach(item => {
    item.addEventListener('click', () => {
      state.navigateTo(item.dataset.view);
    });
  });

  await populateServerSelector();

  setupHeaderQuickControls();

  connectEventsWebSocket();

  state.navigateTo('dashboard');
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

function setupHeaderQuickControls() {
  document.getElementById('btn-quick-start')?.addEventListener('click', async () => {
    if (!state.activeServerId) return;
    try {
      await api.post(`/api/servers/${state.activeServerId}/start`);
      toast.success('Start command sent');
      state.updateHeaderControls();
    } catch (e) {
      toast.error(e.message);
    }
  });

  document.getElementById('btn-quick-stop')?.addEventListener('click', async () => {
    if (!state.activeServerId) return;
    try {
      await api.post(`/api/servers/${state.activeServerId}/stop`);
      toast.info('Graceful stop command sent');
      state.updateHeaderControls();
    } catch (e) {
      toast.error(e.message);
    }
  });

  document.getElementById('btn-quick-restart')?.addEventListener('click', async () => {
    if (!state.activeServerId) return;
    try {
      await api.post(`/api/servers/${state.activeServerId}/restart`);
      toast.info('Restart initiated');
      state.updateHeaderControls();
    } catch (e) {
      toast.error(e.message);
    }
  });
}

function connectEventsWebSocket() {
  state.eventsWs = new PanelWebSocket('/ws/events', (msg) => {
    if (msg.type === 'task_update') {
      handleTaskUpdate(msg.payload);
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
    if (task.state === 'COMPLETED') {
      toast.success(task.message || 'Task completed');
    } else if (task.state === 'FAILED') {
      toast.error(task.error || task.message || 'Task failed');
    }
    if (badge) badge.style.display = 'none';
  }
}

function applyTranslations() {
  document.querySelectorAll('[data-i18n]').forEach(el => {
    const key = el.dataset.i18n;
    el.innerText = i18n.t(key, el.innerText);
  });
}

function escapeHtml(str) {
  if (!str) return '';
  return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

document.addEventListener('DOMContentLoaded', bootstrap);
