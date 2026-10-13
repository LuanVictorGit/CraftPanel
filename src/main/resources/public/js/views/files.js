import { api } from '../api.js';
import { toast, modal, formatBytes, escapeHtml, loadingModal } from '../ui.js';
import { i18n } from '../i18n.js';
import { CodeEditor } from '../editor.js';

let currentPath = '';

export async function renderFiles(root, state) {
  const serverId = state.activeServerId || 'local';

  root.innerHTML = `
    <div style="display:flex;flex-direction:column;gap:16px;">
      <!-- Action Bar -->
      <div style="display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:10px;">
        <div id="files-breadcrumb" style="font-size:13px;font-weight:600;display:flex;align-items:center;gap:6px;color:var(--text-muted);">
          <span>/</span>
        </div>

        <div style="display:flex;align-items:center;gap:8px;">
          <button class="btn btn-secondary btn-sm" id="btn-file-new-file">
            <svg style="width:14px;height:14px;" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
            ${i18n.t('newFile')}
          </button>
          <button class="btn btn-secondary btn-sm" id="btn-file-new-folder">
            <svg style="width:14px;height:14px;" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/></svg>
            ${i18n.t('newFolder')}
          </button>
          <label class="btn btn-primary btn-sm" style="cursor:pointer;">
            <svg style="width:14px;height:14px;" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" x2="12" y1="3" y2="15"/></svg>
            ${i18n.t('uploadFile')}
            <input type="file" id="files-upload-input" style="display:none;">
          </label>
        </div>
      </div>

      <!-- File Table -->
      <div class="card" style="padding:0;overflow:hidden;">
        <div id="files-table-container">
          <div style="text-align:center;padding:32px;color:var(--text-muted);">${i18n.t('loading')}</div>
        </div>
      </div>
    </div>
  `;

  document.getElementById('btn-file-new-file')?.addEventListener('click', () => promptNewFile(serverId));
  document.getElementById('btn-file-new-folder')?.addEventListener('click', () => promptNewFolder(serverId));
  document.getElementById('files-upload-input')?.addEventListener('change', (e) => handleUpload(e, serverId));

  await loadDirectory(serverId, '');
}

async function loadDirectory(serverId, path) {
  currentPath = path;
  updateBreadcrumb(serverId, path);

  try {
    const items = await api.get(`/api/servers/${serverId}/files`, { path });
    renderFileTable(serverId, items);
  } catch (e) {
    toast.error('Failed to load files: ' + e.message);
  }
}

function updateBreadcrumb(serverId, path) {
  const bc = document.getElementById('files-breadcrumb');
  if (!bc) return;

  const parts = path ? path.split('/').filter(Boolean) : [];
  let html = `<span style="cursor:pointer;color:var(--brand-primary);" class="bc-item" data-path="">root</span>`;
  let accum = '';
  parts.forEach(p => {
    accum += (accum ? '/' : '') + p;
    html += ` <span style="color:var(--text-subtle);">/</span> <span style="cursor:pointer;color:var(--brand-primary);" class="bc-item" data-path="${accum}">${escapeHtml(p)}</span>`;
  });
  bc.innerHTML = html;

  bc.querySelectorAll('.bc-item').forEach(el => {
    el.addEventListener('click', () => loadDirectory(serverId, el.dataset.path));
  });
}

function renderFileTable(serverId, items) {
  const container = document.getElementById('files-table-container');
  if (!container) return;

  if (items.length === 0) {
    container.innerHTML = `<div style="text-align:center;padding:48px;color:var(--text-muted);">This folder is empty</div>`;
    return;
  }

  container.innerHTML = `
    <div class="table-wrap">
      <table class="data-table">
        <thead>
          <tr>
            <th>${i18n.t('name')}</th>
            <th>${i18n.t('size')}</th>
            <th>${i18n.t('lastModified')}</th>
            <th style="text-align:right;">${i18n.t('actions')}</th>
          </tr>
        </thead>
        <tbody>
          ${items.map(item => `
            <tr>
              <td>
                <div style="display:flex;align-items:center;gap:8px;cursor:pointer;" class="file-name-click" data-path="${item.relativePath}" data-isdir="${item.isDirectory}">
                  ${item.isDirectory ? `
                    <svg style="width:18px;height:18px;color:#F59E0B;flex-shrink:0;" viewBox="0 0 24 24" fill="currentColor"><path d="M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z"/></svg>
                  ` : `
                    <svg style="width:18px;height:18px;color:#64748B;flex-shrink:0;" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2z"/><polyline points="14 2 14 8 20 8"/></svg>
                  `}
                  <span style="font-weight:500;color:var(--text-main);">${escapeHtml(item.name)}</span>
                </div>
              </td>
              <td style="font-size:12px;color:var(--text-muted);">${item.isDirectory ? '-' : formatBytes(item.sizeBytes)}</td>
              <td style="font-size:12px;color:var(--text-muted);">${new Date(item.lastModified).toLocaleString()}</td>
              <td style="text-align:right;">
                <div style="display:inline-flex;gap:4px;">
                  ${!item.isDirectory ? `
                    <button class="btn btn-icon btn-file-download" data-path="${item.relativePath}" title="Download">
                      <svg style="width:15px;height:15px;" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" x2="12" y1="15" y2="3"/></svg>
                    </button>
                  ` : ''}
                  <button class="btn btn-icon btn-file-delete" data-path="${item.relativePath}" title="Delete">
                    <svg style="width:15px;height:15px;color:var(--accent-rose);" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
                  </button>
                </div>
              </td>
            </tr>
          `).join('')}
        </tbody>
      </table>
    </div>
  `;

  container.querySelectorAll('.file-name-click').forEach(el => {
    el.addEventListener('click', () => {
      const isDir = el.dataset.isdir === 'true';
      const path = el.dataset.path;
      if (isDir) {
        loadDirectory(serverId, path);
      } else {
        openFileEditor(serverId, path);
      }
    });
  });

  container.querySelectorAll('.btn-file-download').forEach(btn => {
    btn.addEventListener('click', (e) => {
      e.stopPropagation();
      const path = btn.dataset.path;
      window.open(`/api/servers/${serverId}/files/download?path=${encodeURIComponent(path)}`, '_blank');
    });
  });

  container.querySelectorAll('.btn-file-delete').forEach(btn => {
    btn.addEventListener('click', (e) => {
      e.stopPropagation();
      const path = btn.dataset.path;
      modal.confirm(i18n.t('delete'), `Are you sure you want to delete "${path}"?`, async () => {
        try {
          await api.delete(`/api/servers/${serverId}/files`, { path });
          toast.success('Deleted successfully');
          loadDirectory(serverId, currentPath);
        } catch (err) {
          toast.error(err.message);
        }
      }, true);
    });
  });
}

async function openFileEditor(serverId, path) {
  try {
    const res = await api.get(`/api/servers/${serverId}/files/read`, { path });
    const content = res.content || '';
    let editorInstance = null;

    modal.open({
      title: `${i18n.t('editingFile')}: ${path}`,
      wide: true,
      contentHtml: `
        <div id="code-editor-mount" style="min-height:520px;"></div>
      `,
      footerButtons: [
        { text: i18n.t('cancel'), className: 'btn-secondary', onClick: (close) => close() },
        {
          text: i18n.t('saveFile'),
          className: 'btn-primary',
          onClick: async (close) => {
            if (!editorInstance) return;
            const newContent = editorInstance.getValue();
            try {
              await api.post(`/api/servers/${serverId}/files/write`, { path, content: newContent });
              toast.success('File saved successfully');
              close();
            } catch (e) {
              toast.error(e.message);
            }
          }
        }
      ]
    });

    const mount = document.getElementById('code-editor-mount');
    if (mount) {
      editorInstance = new CodeEditor(mount, {
        content,
        filename: path.split('/').pop() || path,
        onSave: async (val) => {
          try {
            await api.post(`/api/servers/${serverId}/files/write`, { path, content: val });
            toast.success('File saved successfully (Ctrl+S)');
          } catch (e) {
            toast.error(e.message);
          }
        }
      });
    }
  } catch (e) {
    toast.error('Could not open file: ' + e.message);
  }
}

function promptNewFile(serverId) {
  modal.open({
    title: i18n.t('newFile'),
    contentHtml: `
      <div class="form-group">
        <label class="form-label">Filename</label>
        <input type="text" id="prompt-file-name" class="form-input" placeholder="e.g. server.properties" required>
      </div>
    `,
    footerButtons: [
      { text: i18n.t('cancel'), className: 'btn-secondary', onClick: (close) => close() },
      {
        text: i18n.t('create'),
        className: 'btn-primary',
        onClick: async (close) => {
          const name = document.getElementById('prompt-file-name').value.trim();
          if (!name) return;
          const fullPath = currentPath ? `${currentPath}/${name}` : name;
          try {
            await api.post(`/api/servers/${serverId}/files/create`, { path: fullPath });
            toast.success('File created');
            close();
            loadDirectory(serverId, currentPath);
          } catch (e) {
            toast.error(e.message);
          }
        }
      }
    ]
  });
}

function promptNewFolder(serverId) {
  modal.open({
    title: i18n.t('newFolder'),
    contentHtml: `
      <div class="form-group">
        <label class="form-label">Folder Name</label>
        <input type="text" id="prompt-folder-name" class="form-input" placeholder="e.g. plugins" required>
      </div>
    `,
    footerButtons: [
      { text: i18n.t('cancel'), className: 'btn-secondary', onClick: (close) => close() },
      {
        text: i18n.t('create'),
        className: 'btn-primary',
        onClick: async (close) => {
          const name = document.getElementById('prompt-folder-name').value.trim();
          if (!name) return;
          const fullPath = currentPath ? `${currentPath}/${name}` : name;
          try {
            await api.post(`/api/servers/${serverId}/files/directory`, { path: fullPath });
            toast.success('Folder created');
            close();
            loadDirectory(serverId, currentPath);
          } catch (e) {
            toast.error(e.message);
          }
        }
      }
    ]
  });
}

async function handleUpload(event, serverId) {
  const file = event.target.files[0];
  if (!file) return;

  const formData = new FormData();
  formData.append('file', file);
  formData.append('targetDir', currentPath);

  loadingModal.show(i18n.t('uploading'), file.name, -1);
  try {
    await api.upload(`/api/servers/${serverId}/files/upload`, formData);
    loadingModal.update(100, i18n.t('operationSuccess'));
    await new Promise(r => setTimeout(r, 400));
    loadingModal.hide();
    toast.success(i18n.t('operationSuccess'));
    loadDirectory(serverId, currentPath);
  } catch (e) {
    loadingModal.hide();
    toast.error('Upload failed: ' + e.message);
  }
}
