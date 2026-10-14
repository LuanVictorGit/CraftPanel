import { api } from '../api.js';
import { toast, modal, escapeHtml } from '../ui.js';
import { i18n } from '../i18n.js';

export async function renderSettings(root, state) {
  const serverId = state.activeServerId;

  root.innerHTML = `
    <div style="display:flex;flex-direction:column;gap:24px;">
      <div>
        <h3 style="font-size:18px;font-weight:700;color:var(--text-main);">${i18n.t('settings')}</h3>
        <p style="font-size:13px;color:var(--text-muted);">${i18n.t('settingsSubtitle')}</p>
      </div>

      <!-- Language & Preferences Card -->
      <div class="card">
        <h4 class="card-title" style="margin-bottom:12px;">${i18n.t('displayAndLanguage')}</h4>
        <div style="max-width:320px;">
          <label class="form-label">${i18n.t('language')}</label>
          <select id="settings-lang-select" class="form-select">
            <option value="en">English (Default)</option>
            <option value="pt">Português (Brasil)</option>
          </select>
        </div>
      </div>

      <!-- Server Instance Settings (if selected) -->
      ${serverId ? `
        <div class="card" id="instance-settings-card">
          <div class="card-header">
            <div>
              <h4 class="card-title">${i18n.t('instanceConfig')}</h4>
              <span class="card-subtitle">${i18n.t('instanceConfigSubtitle')}</span>
            </div>
          </div>

          <form id="instance-settings-form">
            <div class="grid-cols-2">
              <div class="form-group">
                <label class="form-label">${i18n.t('instanceName')}</label>
                <input type="text" id="set-srv-name" class="form-input" required>
              </div>
              <div class="form-group">
                <label class="form-label">${i18n.t('serverPort')}</label>
                <input type="number" id="set-srv-port" class="form-input" min="1024" max="65535" required>
              </div>
            </div>

            <div class="grid-cols-2">
              <div class="form-group">
                <label class="form-label">${i18n.t('minMemory')}</label>
                <input type="number" id="set-srv-min-ram" class="form-input" required>
              </div>
              <div class="form-group">
                <label class="form-label">${i18n.t('maxMemory')}</label>
                <input type="number" id="set-srv-max-ram" class="form-input" required>
              </div>
            </div>

            <div class="form-group">
              <label class="form-label">${i18n.t('description')}</label>
              <input type="text" id="set-srv-desc" class="form-input">
            </div>

            <div class="form-group">
              <label class="form-label">${i18n.t('jvmArgs')}</label>
              <input type="text" id="set-srv-jvm" class="form-input">
            </div>

            <div style="margin-bottom:18px;display:flex;align-items:center;gap:8px;">
              <input type="checkbox" id="set-srv-autostart" style="width:16px;height:16px;">
              <label for="set-srv-autostart" style="font-size:13px;font-weight:600;cursor:pointer;">
                ${i18n.t('autoStartDesc')}
              </label>
            </div>

            <div style="display:flex;align-items:center;justify-content:space-between;border-top:1px solid var(--border);padding-top:16px;">
              <button type="submit" class="btn btn-primary">${i18n.t('save')}</button>
              <button type="button" class="btn btn-danger" id="btn-delete-instance-btn">${i18n.t('deleteInstance')}</button>
            </div>
          </form>
        </div>
      ` : ''}

      <!-- Security / Change Password -->
      <div class="card">
        <h4 class="card-title" style="margin-bottom:12px;">${i18n.t('accountSecurity')}</h4>
        <form id="change-pwd-form" style="max-width:440px;">
          <div class="form-group">
            <label class="form-label">${i18n.t('currentPassword')}</label>
            <input type="password" id="chg-old-pwd" class="form-input" required>
          </div>
          <div class="form-group">
            <label class="form-label">${i18n.t('newPasswordMin')}</label>
            <input type="password" id="chg-new-pwd" class="form-input" required>
          </div>
          <button type="submit" class="btn btn-secondary">${i18n.t('save')}</button>
        </form>
      </div>
    </div>
  `;

  const langSelect = document.getElementById('settings-lang-select');
  if (langSelect) {
    langSelect.value = i18n.getLanguage();
    langSelect.addEventListener('change', (e) => {
      i18n.setLanguage(e.target.value);
    });
  }

  document.getElementById('change-pwd-form')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const oldPassword = document.getElementById('chg-old-pwd').value;
    const newPassword = document.getElementById('chg-new-pwd').value;

    try {
      await api.post('/api/auth/change-password', { oldPassword, newPassword });
      toast.success(i18n.t('passwordChangedSuccess'));
      e.target.reset();
    } catch (err) {
      toast.error(err.message);
    }
  });

  if (serverId) {
    loadServerSettings(serverId, state);
  }
}

async function loadServerSettings(serverId, state) {
  try {
    const res = await api.get(`/api/servers/${serverId}`);
    const s = res.server;

    document.getElementById('set-srv-name').value = s.name || '';
    document.getElementById('set-srv-port').value = s.serverPort || 25565;
    document.getElementById('set-srv-min-ram').value = s.minMemoryMb || 1024;
    document.getElementById('set-srv-max-ram').value = s.maxMemoryMb || 2048;
    document.getElementById('set-srv-desc').value = s.description || '';
    document.getElementById('set-srv-jvm').value = s.jvmArgs || '';
    document.getElementById('set-srv-autostart').checked = s.autoStart || false;

    document.getElementById('instance-settings-form')?.addEventListener('submit', async (e) => {
      e.preventDefault();
      try {
        await api.put(`/api/servers/${serverId}`, {
          name: document.getElementById('set-srv-name').value,
          serverPort: parseInt(document.getElementById('set-srv-port').value, 10),
          minMemoryMb: parseInt(document.getElementById('set-srv-min-ram').value, 10),
          maxMemoryMb: parseInt(document.getElementById('set-srv-max-ram').value, 10),
          description: document.getElementById('set-srv-desc').value,
          jvmArgs: document.getElementById('set-srv-jvm').value,
          autoStart: document.getElementById('set-srv-autostart').checked
        });
        toast.success(i18n.t('instanceSettingsSaved'));
      } catch (err) {
        toast.error(err.message);
      }
    });

    document.getElementById('btn-delete-instance-btn')?.addEventListener('click', () => {
      modal.confirm(i18n.t('deleteInstance'), `${i18n.t('deleteServerConfirm')} "${s.name}"? ${i18n.t('deleteServerDesc')}`, async () => {
        try {
          await api.delete(`/api/servers/${serverId}`, { deleteFiles: true });
          toast.success(i18n.t('operationSuccess'));
          state.setActiveServer(null);
          state.navigateTo('servers');
        } catch (err) {
          toast.error(err.message);
        }
      }, true);
    });

  } catch (e) {
    toast.error('Failed to load server settings: ' + e.message);
  }
}
