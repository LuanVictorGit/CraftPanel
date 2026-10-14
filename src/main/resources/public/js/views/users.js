import { api } from '../api.js';
import { toast, modal, escapeHtml } from '../ui.js';
import { i18n } from '../i18n.js';

export async function renderUsers(root, state) {
  root.innerHTML = `
    <div style="display:flex;flex-direction:column;gap:24px;">
      <!-- Users Section -->
      <div class="card">
        <div class="card-header">
          <div>
            <h3 class="card-title">${i18n.t('usersTitle')}</h3>
            <span class="card-subtitle">${i18n.t('usersSubtitle')}</span>
          </div>
          <button class="btn btn-primary btn-sm" id="btn-create-user-modal">
            <svg style="width:14px;height:14px;" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
            ${i18n.t('createUser')}
          </button>
        </div>

        <div id="users-table-container">
          <div style="text-align:center;padding:24px;color:var(--text-muted);">${i18n.t('loading')}</div>
        </div>
      </div>

      <!-- Roles Section -->
      <div class="card">
        <div class="card-header">
          <div>
            <h3 class="card-title">${i18n.t('rolesTitle')}</h3>
            <span class="card-subtitle">${i18n.t('rolesSubtitle')}</span>
          </div>
        </div>

        <div id="roles-table-container">
          <div style="text-align:center;padding:24px;color:var(--text-muted);">${i18n.t('loading')}</div>
        </div>
      </div>
    </div>
  `;

  document.getElementById('btn-create-user-modal')?.addEventListener('click', () => openCreateUserModal());

  await loadUsersAndRoles();
}

let cachedRoles = [];

async function loadUsersAndRoles() {
  try {
    const [users, roles] = await Promise.all([
      api.get('/api/users'),
      api.get('/api/roles')
    ]);
    cachedRoles = roles;
    renderUsersTable(users);
    renderRolesTable(roles);
  } catch (e) {
    toast.error('Failed to load users: ' + e.message);
  }
}

function renderUsersTable(users) {
  const container = document.getElementById('users-table-container');
  if (!container) return;

  container.innerHTML = `
    <div class="table-wrap">
      <table class="data-table">
        <thead>
          <tr>
            <th>${i18n.t('username')}</th>
            <th>${i18n.t('displayName')}</th>
            <th>${i18n.t('role')}</th>
            <th>${i18n.t('created')}</th>
            <th style="text-align:right;">${i18n.t('actions')}</th>
          </tr>
        </thead>
        <tbody>
          ${users.map(u => `
            <tr>
              <td style="font-weight:600;color:var(--text-main);">${escapeHtml(u.username)}</td>
              <td>${escapeHtml(u.displayName)}</td>
              <td><span class="badge ${u.roleId === 'admin' ? 'badge-rose' : 'badge-blue'}">${escapeHtml(u.roleId)}</span></td>
              <td style="font-size:12px;color:var(--text-muted);">${new Date(u.createdAt).toLocaleDateString()}</td>
              <td style="text-align:right;">
                <div style="display:inline-flex;gap:6px;">
                  <button class="btn btn-secondary btn-sm btn-edit-perms" data-id="${u.id}" data-name="${escapeHtml(u.username)}">
                    ${i18n.t('serverPermissions')}
                  </button>
                  <button class="btn btn-secondary btn-sm btn-reset-pwd" data-id="${u.id}">
                    ${i18n.t('resetPassword')}
                  </button>
                  <button class="btn btn-danger btn-sm btn-delete-user" data-id="${u.id}">
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

  container.querySelectorAll('.btn-reset-pwd').forEach(btn => {
    btn.addEventListener('click', () => openResetPasswordModal(btn.dataset.id));
  });

  container.querySelectorAll('.btn-delete-user').forEach(btn => {
    btn.addEventListener('click', () => {
      const id = btn.dataset.id;
      modal.confirm(i18n.t('delete'), i18n.t('deleteUserConfirm'), async () => {
        try {
          await api.delete(`/api/users/${id}`);
          toast.success(i18n.t('userDeleted'));
          loadUsersAndRoles();
        } catch (e) {
          toast.error(e.message);
        }
      }, true);
    });
  });

  container.querySelectorAll('.btn-edit-perms').forEach(btn => {
    btn.addEventListener('click', () => {
      openServerPermissionsModal(btn.dataset.id, btn.dataset.name);
    });
  });
}

function renderRolesTable(roles) {
  const container = document.getElementById('roles-table-container');
  if (!container) return;

  container.innerHTML = `
    <div class="table-wrap">
      <table class="data-table">
        <thead>
          <tr>
            <th>${i18n.t('roleIdentifier')}</th>
            <th>${i18n.t('name')}</th>
            <th>${i18n.t('description')}</th>
            <th>${i18n.t('type')}</th>
            <th>${i18n.t('permissionsCount')}</th>
          </tr>
        </thead>
        <tbody>
          ${roles.map(r => `
            <tr>
              <td style="font-family:var(--font-mono);font-size:12px;font-weight:600;">${escapeHtml(r.id)}</td>
              <td style="font-weight:600;">${escapeHtml(r.name)}</td>
              <td style="font-size:12px;color:var(--text-muted);">${escapeHtml(r.description || '')}</td>
              <td><span class="badge ${r.isSystem ? 'badge-gray' : 'badge-green'}">${r.isSystem ? i18n.t('systemRole') : i18n.t('customRole')}</span></td>
              <td style="font-size:12px;"><strong>${r.permissions ? r.permissions.length : 0}</strong> ${i18n.t('privileges')}</td>
            </tr>
          `).join('')}
        </tbody>
      </table>
    </div>
  `;
}

const AVAILABLE_PERMS = [
  'server.view', 'server.start', 'server.stop', 'server.restart', 'server.kill',
  'console.view', 'console.execute',
  'files.view', 'files.edit', 'files.upload', 'files.download', 'files.delete',
  'plugins.view', 'plugins.manage',
  'modrinth.view', 'modrinth.install',
  'backups.view', 'backups.manage',
  'server.edit'
];

function openCreateUserModal() {
  modal.open({
    title: i18n.t('createUser'),
    wide: true,
    contentHtml: `
      <form id="create-user-form" style="display:flex;flex-direction:column;gap:14px;">
        <div style="display:grid;grid-template-columns:repeat(2, 1fr);gap:12px;">
          <div class="form-group">
            <label class="form-label">${i18n.t('username')}</label>
            <input type="text" id="new-u-username" class="form-input" placeholder="e.g. friend1" required>
          </div>
          <div class="form-group">
            <label class="form-label">${i18n.t('displayName')}</label>
            <input type="text" id="new-u-display" class="form-input" placeholder="e.g. John Doe" required>
          </div>
        </div>

        <div style="display:grid;grid-template-columns:repeat(2, 1fr);gap:12px;">
          <div class="form-group">
            <label class="form-label">${i18n.t('password')}</label>
            <input type="password" id="new-u-password" class="form-input" placeholder="••••••••" required>
          </div>
          <div class="form-group">
            <label class="form-label">${i18n.t('role')}</label>
            <select id="new-u-role" class="form-select">
              ${cachedRoles.map(r => `<option value="${r.id}">${escapeHtml(r.name)}</option>`).join('')}
            </select>
          </div>
        </div>

        <!-- Permissions Checkboxes Section -->
        <div class="form-group">
          <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:8px;">
            <label class="form-label" style="margin:0;">${i18n.t('serverPermissions')}</label>
            <div style="display:flex;gap:6px;">
              <button type="button" class="btn btn-secondary btn-sm" id="btn-create-select-all" style="padding:2px 8px;font-size:11px;">Select All</button>
              <button type="button" class="btn btn-secondary btn-sm" id="btn-create-deselect-all" style="padding:2px 8px;font-size:11px;">Deselect All</button>
            </div>
          </div>
          <div style="display:grid;grid-template-columns:repeat(2, 1fr);gap:8px;padding:12px;background:var(--bg-subtle);border:1px solid var(--border);border-radius:8px;max-height:180px;overflow-y:auto;" id="create-perms-list">
            ${AVAILABLE_PERMS.map(p => `
              <label style="display:flex;align-items:center;gap:8px;font-size:12px;font-weight:500;cursor:pointer;">
                <input type="checkbox" value="${p}" class="create-perm-cb" checked>
                <span>${p}</span>
              </label>
            `).join('')}
          </div>
        </div>
      </form>
    `,
    footerButtons: [
      { text: i18n.t('cancel'), className: 'btn-secondary', onClick: (close) => close() },
      {
        text: i18n.t('create'),
        className: 'btn-primary',
        onClick: async (close) => {
          const u = document.getElementById('new-u-username').value.trim();
          const d = document.getElementById('new-u-display').value.trim();
          const p = document.getElementById('new-u-password').value;
          const r = document.getElementById('new-u-role').value;

          const perms = [];
          document.querySelectorAll('#create-perms-list input[type="checkbox"]:checked').forEach(cb => {
            perms.push(cb.value);
          });

          try {
            await api.post('/api/users', {
              username: u,
              displayName: d,
              password: p,
              roleId: r,
              permissions: perms
            });
            toast.success('User account created');
            close();
            loadUsersAndRoles();
          } catch (e) {
            toast.error(e.message);
          }
        }
      }
    ]
  });

  const roleSelect = document.getElementById('new-u-role');
  roleSelect?.addEventListener('change', () => {
    const val = roleSelect.value;
    const cbs = document.querySelectorAll('#create-perms-list input[type="checkbox"]');
    cbs.forEach(cb => {
      if (val === 'admin') {
        cb.checked = true;
      } else if (val === 'moderator') {
        cb.checked = !cb.value.includes('delete') && !cb.value.includes('kill');
      } else {
        cb.checked = cb.value.endsWith('.view');
      }
    });
  });

  document.getElementById('btn-create-select-all')?.addEventListener('click', () => {
    document.querySelectorAll('#create-perms-list input[type="checkbox"]').forEach(cb => cb.checked = true);
  });
  document.getElementById('btn-create-deselect-all')?.addEventListener('click', () => {
    document.querySelectorAll('#create-perms-list input[type="checkbox"]').forEach(cb => cb.checked = false);
  });
}

function openResetPasswordModal(userId) {
  modal.open({
    title: i18n.t('resetPassword'),
    contentHtml: `
      <div class="form-group">
        <label class="form-label">New Password (min 8 chars)</label>
        <input type="password" id="reset-new-pwd" class="form-input" placeholder="••••••••" required>
      </div>
    `,
    footerButtons: [
      { text: i18n.t('cancel'), className: 'btn-secondary', onClick: (close) => close() },
      {
        text: i18n.t('save'),
        className: 'btn-primary',
        onClick: async (close) => {
          const np = document.getElementById('reset-new-pwd').value;
          try {
            await api.post(`/api/users/${userId}/reset-password`, { newPassword: np });
            toast.success('Password updated successfully');
            close();
          } catch (e) {
            toast.error(e.message);
          }
        }
      }
    ]
  });
}

async function openServerPermissionsModal(userId, username) {
  try {
    const servers = await api.get('/api/servers');
    if (servers.length === 0) {
      toast.info('No servers registered yet');
      return;
    }

    modal.open({
      title: `${i18n.t('serverPermissions')}: ${username}`,
      wide: true,
      contentHtml: `
        <div style="display:flex;flex-direction:column;gap:16px;">
          <div class="form-group">
            <label class="form-label">Select Server Instance</label>
            <select id="modal-perm-server-select" class="form-select">
              ${servers.map(s => `<option value="${s.id}">${escapeHtml(s.name)}</option>`).join('')}
            </select>
          </div>

          <div style="display:flex;align-items:center;justify-content:space-between;">
            <label class="form-label" style="margin:0;">Privileges</label>
            <div style="display:flex;gap:6px;">
              <button type="button" class="btn btn-secondary btn-sm" id="btn-edit-select-all" style="padding:2px 8px;font-size:11px;">Select All</button>
              <button type="button" class="btn btn-secondary btn-sm" id="btn-edit-deselect-all" style="padding:2px 8px;font-size:11px;">Deselect All</button>
            </div>
          </div>

          <div style="display:grid;grid-template-columns:repeat(2, 1fr);gap:10px;padding:12px;background:var(--bg-subtle);border-radius:8px;max-height:240px;overflow-y:auto;" id="perms-checkbox-list">
            Loading permissions...
          </div>
        </div>
      `,
      footerButtons: [
        { text: i18n.t('cancel'), className: 'btn-secondary', onClick: (close) => close() },
        {
          text: i18n.t('save'),
          className: 'btn-primary',
          onClick: async (close) => {
            const serverSelect = document.getElementById('modal-perm-server-select');
            const serverId = serverSelect.value;
            const checked = [];
            document.querySelectorAll('#perms-checkbox-list input[type="checkbox"]:checked').forEach(cb => {
              checked.push(cb.value);
            });

            try {
              await api.put(`/api/users/${userId}/permissions/${serverId}`, checked);
              toast.success('Server permissions updated');
              close();
            } catch (e) {
              toast.error(e.message);
            }
          }
        }
      ]
    });

    const sSelect = document.getElementById('modal-perm-server-select');
    async function loadPermsForServer(srvId) {
      const container = document.getElementById('perms-checkbox-list');
      try {
        const assigned = await api.get(`/api/users/${userId}/permissions/${srvId}`);
        const isAll = Array.isArray(assigned) && (assigned.includes('*') || assigned.includes('all'));
        container.innerHTML = AVAILABLE_PERMS.map(p => {
          const isChecked = isAll || (Array.isArray(assigned) && assigned.includes(p));
          return `
            <label style="display:flex;align-items:center;gap:8px;font-size:12px;font-weight:500;cursor:pointer;">
              <input type="checkbox" value="${p}" class="edit-perm-cb" ${isChecked ? 'checked' : ''}>
              <span>${p}</span>
            </label>
          `;
        }).join('');
      } catch (e) {
        container.innerHTML = 'Failed to load permissions';
      }
    }

    document.getElementById('btn-edit-select-all')?.addEventListener('click', () => {
      document.querySelectorAll('#perms-checkbox-list input[type="checkbox"]').forEach(cb => cb.checked = true);
    });
    document.getElementById('btn-edit-deselect-all')?.addEventListener('click', () => {
      document.querySelectorAll('#perms-checkbox-list input[type="checkbox"]').forEach(cb => cb.checked = false);
    });

    sSelect?.addEventListener('change', (e) => loadPermsForServer(e.target.value));
    loadPermsForServer(servers[0].id);

  } catch (e) {
    toast.error(e.message);
  }
}
