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
            <span class="card-subtitle">Manage individual user accounts and individual privileges</span>
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
            <span class="card-subtitle">Predefined system roles and customizable permission templates</span>
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
            <th>Username</th>
            <th>Display Name</th>
            <th>Role</th>
            <th>Created</th>
            <th style="text-align:right;">Actions</th>
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
      modal.confirm('Delete User', 'Are you sure you want to permanently delete this user account?', async () => {
        try {
          await api.delete(`/api/users/${id}`);
          toast.success('User deleted');
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
            <th>Role Identifier</th>
            <th>Name</th>
            <th>Description</th>
            <th>Type</th>
            <th>Permissions Count</th>
          </tr>
        </thead>
        <tbody>
          ${roles.map(r => `
            <tr>
              <td style="font-family:var(--font-mono);font-size:12px;font-weight:600;">${escapeHtml(r.id)}</td>
              <td style="font-weight:600;">${escapeHtml(r.name)}</td>
              <td style="font-size:12px;color:var(--text-muted);">${escapeHtml(r.description || '')}</td>
              <td><span class="badge ${r.isSystem ? 'badge-gray' : 'badge-green'}">${r.isSystem ? 'System' : 'Custom'}</span></td>
              <td style="font-size:12px;"><strong>${r.permissions ? r.permissions.length : 0}</strong> privileges</td>
            </tr>
          `).join('')}
        </tbody>
      </table>
    </div>
  `;
}

function openCreateUserModal() {
  modal.open({
    title: i18n.t('createUser'),
    contentHtml: `
      <form id="create-user-form">
        <div class="form-group">
          <label class="form-label">${i18n.t('username')}</label>
          <input type="text" id="new-u-username" class="form-input" placeholder="e.g. friend1" required>
        </div>
        <div class="form-group">
          <label class="form-label">${i18n.t('displayName')}</label>
          <input type="text" id="new-u-display" class="form-input" placeholder="e.g. John Doe" required>
        </div>
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

          try {
            await api.post('/api/users', {
              username: u,
              displayName: d,
              password: p,
              roleId: r
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

    const availablePerms = [
      'server.view', 'server.start', 'server.stop', 'server.restart', 'server.kill',
      'console.view', 'console.execute',
      'files.view', 'files.edit', 'files.upload', 'files.download', 'files.delete',
      'plugins.view', 'plugins.manage',
      'modrinth.view', 'modrinth.install',
      'backups.view', 'backups.manage',
      'server.edit'
    ];

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

          <div style="display:grid;grid-template-columns:repeat(2, 1fr);gap:10px;padding:12px;background:var(--bg-subtle);border-radius:8px;" id="perms-checkbox-list">
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
        container.innerHTML = availablePerms.map(p => `
          <label style="display:flex;align-items:center;gap:8px;font-size:12px;font-weight:500;cursor:pointer;">
            <input type="checkbox" value="${p}" ${assigned.includes(p) ? 'checked' : ''}>
            <span>${p}</span>
          </label>
        `).join('');
      } catch (e) {
        container.innerHTML = 'Failed to load permissions';
      }
    }

    sSelect?.addEventListener('change', (e) => loadPermsForServer(e.target.value));
    loadPermsForServer(servers[0].id);

  } catch (e) {
    toast.error(e.message);
  }
}
