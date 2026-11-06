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

      <div id="ai-settings-slot" style="display:contents;"></div>
      <div id="mcp-settings-slot" style="display:contents;"></div>

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
  loadAiAndMcpCards();
}

async function loadAiAndMcpCards() {
  try {
    const status = await api.get('/api/ai/status');
    if (status.canManage) renderAiCard(status);
    if (status.canUseMcp) renderMcpCard();
  } catch (e) {
    // The cards are optional; the rest of the page still works.
  }
}

async function renderAiCard(status) {
  const slot = document.getElementById('ai-settings-slot');
  if (!slot) return;

  slot.innerHTML = `
    <div class="card">
      <div class="card-header">
        <div>
          <h4 class="card-title">${i18n.t('aiSettingsTitle')}</h4>
          <span class="card-subtitle">${i18n.t('aiSettingsSubtitle')}</span>
        </div>
        <span class="badge ${status.configured ? 'badge-green' : 'badge-gray'}">${i18n.t(status.configured ? 'aiConfigured' : 'aiNotConfigured')}</span>
      </div>
      <form id="ai-settings-form" style="max-width:560px;">
        <div class="form-group">
          <label class="form-label" for="ai-api-key">${i18n.t('aiApiKey')}</label>
          <input type="password" id="ai-api-key" class="form-input" autocomplete="off" spellcheck="false"
                 placeholder="${i18n.t(status.configured ? 'aiApiKeyKeep' : 'aiApiKeyPlaceholder')}">
          <span class="form-hint">${i18n.t('aiApiKeyHint')} <a href="https://aistudio.google.com/apikey" target="_blank" rel="noopener noreferrer">Google AI Studio</a></span>
        </div>
        <div class="form-group" id="ai-model-group" style="${status.configured ? '' : 'display:none;'}">
          <label class="form-label" for="ai-model">${i18n.t('aiModel')}</label>
          <select id="ai-model" class="form-select"></select>
          <span class="form-hint" id="ai-thinking-hint"></span>
        </div>
        <p class="form-hint" style="margin-bottom:14px;">${i18n.t('aiDataNotice')}</p>
        <div style="display:flex;gap:8px;">
          <button type="submit" class="btn btn-primary" id="ai-save">${i18n.t('save')}</button>
          ${status.configured ? `<button type="button" class="btn btn-danger" id="ai-remove">${i18n.t('aiRemoveKey')}</button>` : ''}
        </div>
      </form>
    </div>
  `;

  const modelSelect = document.getElementById('ai-model');
  const thinkingHint = document.getElementById('ai-thinking-hint');
  const updateHint = () => {
    const option = modelSelect.selectedOptions[0];
    if (thinkingHint) {
      thinkingHint.textContent = option ? i18n.t(option.dataset.thinking === 'true' ? 'aiModelThinks' : 'aiModelNoThinking') : '';
    }
  };

  if (status.configured) {
    try {
      const models = await api.get('/api/ai/models');
      modelSelect.innerHTML = models.map(m => `
        <option value="${escapeHtml(m.id)}" data-thinking="${m.thinking}" ${m.id === status.model ? 'selected' : ''}>${escapeHtml(m.displayName)} (${escapeHtml(m.id)})</option>
      `).join('');
      updateHint();
    } catch (e) {
      modelSelect.innerHTML = `<option value="${escapeHtml(status.model)}" data-thinking="${status.thinking}">${escapeHtml(status.model)}</option>`;
      updateHint();
      toast.error(e.message);
    }
  }
  modelSelect.addEventListener('change', updateHint);

  document.getElementById('ai-settings-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const saveBtn = document.getElementById('ai-save');
    saveBtn.disabled = true;
    try {
      const res = await api.put('/api/ai/settings', {
        apiKey: document.getElementById('ai-api-key').value.trim(),
        model: modelSelect.value || ''
      });
      toast.success(i18n.t('aiSaved', { model: res.model }));
      renderAiCard({ ...status, configured: true, model: res.model, thinking: res.thinking });
    } catch (err) {
      toast.error(err.message);
      saveBtn.disabled = false;
    }
  });

  document.getElementById('ai-remove')?.addEventListener('click', () => {
    modal.confirm(i18n.t('aiRemoveKey'), i18n.t('aiRemoveConfirm'), async () => {
      try {
        await api.put('/api/ai/settings', { clear: true });
        toast.success(i18n.t('operationSuccess'));
        renderAiCard({ ...status, configured: false, model: null, thinking: false });
      } catch (err) {
        toast.error(err.message);
      }
    }, true);
  });
}

async function renderMcpCard() {
  const slot = document.getElementById('mcp-settings-slot');
  if (!slot) return;

  let data;
  try {
    data = await api.get('/api/mcp/tokens');
  } catch (e) {
    return;
  }
  const endpoint = `${window.location.origin}${data.path}`;

  slot.innerHTML = `
    <div class="card">
      <div class="card-header">
        <div>
          <h4 class="card-title">${i18n.t('mcpTitle')}</h4>
          <span class="card-subtitle">${i18n.t('mcpSubtitle')}</span>
        </div>
        <button class="btn btn-primary btn-sm" id="mcp-new-token">${i18n.t('mcpNewToken')}</button>
      </div>
      <div class="form-group">
        <label class="form-label">${i18n.t('mcpEndpoint')}</label>
        <code class="mcp-endpoint">${escapeHtml(endpoint)}</code>
      </div>
      ${data.tokens.length === 0 ? `<p class="form-hint">${i18n.t('mcpNoTokens')}</p>` : `
        <div class="table-wrap">
          <table class="data-table">
            <thead><tr>
              <th>${i18n.t('mcpTokenName')}</th>
              <th>${i18n.t('mcpTokenAccess')}</th>
              <th>${i18n.t('mcpTokenLastUsed')}</th>
              <th style="text-align:right;">${i18n.t('actions')}</th>
            </tr></thead>
            <tbody>
              ${data.tokens.map(t => `
                <tr>
                  <td style="font-weight:600;">${escapeHtml(t.name)}</td>
                  <td><span class="badge ${t.readOnly ? 'badge-gray' : 'badge-amber'}">${i18n.t(t.readOnly ? 'mcpReadOnly' : 'mcpFullAccess')}</span></td>
                  <td style="font-size:12px;color:var(--text-muted);">${t.lastUsedAt ? escapeHtml(new Date(t.lastUsedAt * 1000).toLocaleString()) : i18n.t('mcpNeverUsed')}</td>
                  <td style="text-align:right;"><button class="btn btn-danger btn-sm" data-revoke="${escapeHtml(t.id)}" data-name="${escapeHtml(t.name)}">${i18n.t('mcpRevoke')}</button></td>
                </tr>
              `).join('')}
            </tbody>
          </table>
        </div>
      `}
    </div>
  `;

  document.getElementById('mcp-new-token').addEventListener('click', () => openNewTokenModal(endpoint));
  slot.querySelectorAll('[data-revoke]').forEach(button => {
    button.addEventListener('click', () => {
      modal.confirm(i18n.t('mcpRevoke'), `${i18n.t('mcpRevokeConfirm')} "${button.dataset.name}"`, async () => {
        try {
          await api.delete(`/api/mcp/tokens/${button.dataset.revoke}`);
          toast.success(i18n.t('operationSuccess'));
          renderMcpCard();
        } catch (err) {
          toast.error(err.message);
        }
      }, true);
    });
  });
}

function openNewTokenModal(endpoint) {
  modal.open({
    title: i18n.t('mcpNewToken'),
    contentHtml: `
      <div class="form-group">
        <label class="form-label" for="mcp-token-name">${i18n.t('mcpTokenName')}</label>
        <input type="text" id="mcp-token-name" class="form-input" maxlength="60" placeholder="Claude Code">
      </div>
      <label style="display:flex;align-items:flex-start;gap:8px;font-size:13px;cursor:pointer;">
        <input type="checkbox" id="mcp-token-readonly" style="margin-top:3px;">
        <span><strong>${i18n.t('mcpReadOnly')}</strong><span style="display:block;color:var(--text-muted);font-size:12px;">${i18n.t('mcpReadOnlyDesc')}</span></span>
      </label>
    `,
    footerButtons: [
      { text: i18n.t('cancel'), className: 'btn-secondary', onClick: (close) => close() },
      {
        text: i18n.t('create'),
        className: 'btn-primary',
        onClick: async (close) => {
          try {
            const created = await api.post('/api/mcp/tokens', {
              name: document.getElementById('mcp-token-name').value.trim(),
              readOnly: document.getElementById('mcp-token-readonly').checked
            });
            close();
            showCreatedToken(created.token, endpoint);
            renderMcpCard();
          } catch (err) {
            toast.error(err.message);
          }
        }
      }
    ]
  });
}

function showCreatedToken(token, endpoint) {
  const config = JSON.stringify({
    mcpServers: {
      craftpanel: { type: 'http', url: endpoint, headers: { Authorization: `Bearer ${token}` } }
    }
  }, null, 2);
  const insecure = window.location.protocol !== 'https:' && !['localhost', '127.0.0.1'].includes(window.location.hostname);

  modal.open({
    title: i18n.t('mcpTokenCreated'),
    wide: true,
    contentHtml: `
      <p style="font-size:13px;margin-bottom:10px;font-weight:600;">${i18n.t('mcpTokenOnce')}</p>
      <div class="form-group">
        <label class="form-label">Token</label>
        <code class="mcp-endpoint">${escapeHtml(token)}</code>
      </div>
      <div class="form-group">
        <label class="form-label">${i18n.t('mcpClientConfig')}</label>
        <pre class="mcp-config">${escapeHtml(config)}</pre>
      </div>
      ${insecure ? `<p class="mcp-warning">${i18n.t('mcpInsecureWarning')}</p>` : ''}
    `,
    footerButtons: [
      {
        text: i18n.t('mcpCopyConfig'),
        className: 'btn-secondary',
        onClick: async () => {
          try {
            await navigator.clipboard.writeText(config);
            toast.success(i18n.t('mcpCopied'));
          } catch (e) {
            toast.error(i18n.t('mcpCopyFailed'));
          }
        }
      },
      { text: i18n.t('close'), className: 'btn-primary', onClick: (close) => close() }
    ]
  });
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
