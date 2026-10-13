import { api } from '../api.js';
import { toast, escapeHtml } from '../ui.js';
import { i18n } from '../i18n.js';
import { PanelWebSocket } from '../websocket.js';

let activeWs = null;
let commandHistory = [];
let historyIndex = -1;
let autoscrollEnabled = true;

export async function renderConsole(root, state) {
  const serverId = state.activeServerId || 'local';

  root.innerHTML = `
    <div style="display:flex;flex-direction:column;gap:16px;">
      <!-- Console Container -->
      <div class="console-box">
        <div class="console-header">
          <div class="console-title">
            <span class="status-dot stopped" id="console-status-dot"></span>
            <span id="console-status-text">CONNECTING...</span>
          </div>

          <div style="display:flex;align-items:center;gap:12px;">
            <input type="text" id="console-search-input" class="form-input" style="padding:3px 8px;font-size:11px;background:#1E293B;color:#FFF;border-color:#334155;width:150px;" placeholder="${i18n.t('search')}...">
            <label style="display:flex;align-items:center;gap:5px;font-size:11px;color:#94A3B8;cursor:pointer;">
              <input type="checkbox" id="chk-autoscroll" checked>
              <span>${i18n.t('autoscroll')}</span>
            </label>
            <button class="btn btn-secondary btn-sm" id="btn-clear-console" style="background:#1E293B;color:#E2E8F0;border-color:#334155;font-size:11px;">
              ${i18n.t('clearConsole')}
            </button>
          </div>
        </div>

        <div class="console-output" id="console-output-area"></div>

        <form class="console-input-wrap" id="console-cmd-form">
          <span class="console-prompt">&gt;</span>
          <input type="text" id="console-cmd-input" class="console-input" placeholder="${i18n.t('commandPlaceholder')}" autocomplete="off">
          <button type="submit" class="btn btn-primary btn-sm">${i18n.t('sendCommand')}</button>
        </form>
      </div>
    </div>
  `;

  const outputArea = document.getElementById('console-output-area');
  const cmdForm = document.getElementById('console-cmd-form');
  const cmdInput = document.getElementById('console-cmd-input');
  const autoscrollChk = document.getElementById('chk-autoscroll');
  const clearBtn = document.getElementById('btn-clear-console');
  const searchInput = document.getElementById('console-search-input');
  const statusDot = document.getElementById('console-status-dot');
  const statusText = document.getElementById('console-status-text');

  autoscrollChk?.addEventListener('change', (e) => {
    autoscrollEnabled = e.target.checked;
  });

  clearBtn?.addEventListener('click', () => {
    if (outputArea) outputArea.innerHTML = '';
  });

  searchInput?.addEventListener('input', (e) => {
    const q = e.target.value.toLowerCase();
    if (!outputArea) return;
    const lines = outputArea.querySelectorAll('.console-line');
    lines.forEach(line => {
      line.style.display = (!q || line.innerText.toLowerCase().includes(q)) ? 'flex' : 'none';
    });
  });

  cmdInput?.addEventListener('keydown', (e) => {
    if (e.key === 'ArrowUp') {
      e.preventDefault();
      if (commandHistory.length > 0 && historyIndex < commandHistory.length - 1) {
        historyIndex++;
        cmdInput.value = commandHistory[commandHistory.length - 1 - historyIndex];
      }
    } else if (e.key === 'ArrowDown') {
      e.preventDefault();
      if (historyIndex > 0) {
        historyIndex--;
        cmdInput.value = commandHistory[commandHistory.length - 1 - historyIndex];
      } else if (historyIndex === 0) {
        historyIndex = -1;
        cmdInput.value = '';
      }
    }
  });

  cmdForm?.addEventListener('submit', (e) => {
    e.preventDefault();
    const cmd = cmdInput.value.trim();
    if (!cmd) return;

    commandHistory.push(cmd);
    historyIndex = -1;
    cmdInput.value = '';

    if (activeWs) {
      activeWs.send({ command: cmd });
    }
  });

  if (activeWs) {
    activeWs.close();
  }

  activeWs = new PanelWebSocket(`/ws/console/${serverId}`, (data) => {
    if (data.type === 'backlog') {
      outputArea.innerHTML = '';
      if (data.lines && Array.isArray(data.lines)) {
        data.lines.forEach(appendLine);
      }
      updateStatus(data.state);
    } else if (data.type === 'line') {
      appendLine(data);
    } else if (data.type === 'state') {
      updateStatus(data.state);
    } else if (data.type === 'error') {
      toast.error(data.message);
    }
  }, () => {
    updateStatus('CONNECTED');
  }, () => {
    updateStatus('DISCONNECTED');
  });

  activeWs.connect();

  function formatConsoleMessage(raw) {
    if (!raw) return '';
    let str = escapeHtml(raw);
    str = str.replace(/\u001b\[0?m/g, '</span>');
    str = str.replace(/\u001b\[0;31(?:;1)?m/g, '<span style="color:#F87171;">');
    str = str.replace(/\u001b\[0;32(?:;1)?m/g, '<span style="color:#4ADE80;">');
    str = str.replace(/\u001b\[0;33(?:;1)?m/g, '<span style="color:#FBBF24;">');
    str = str.replace(/\u001b\[0;34(?:;1)?m/g, '<span style="color:#60A5FA;">');
    str = str.replace(/\u001b\[0;35(?:;1)?m/g, '<span style="color:#C084FC;">');
    str = str.replace(/\u001b\[0;36(?:;1)?m/g, '<span style="color:#38BDF8;">');
    str = str.replace(/\u001b\[0;37(?:;1)?m/g, '<span style="color:#F1F5F9;">');
    str = str.replace(/\u001b\[1;30m/g, '<span style="color:#94A3B8;">');
    str = str.replace(/\u001b\[[0-9;]*m/g, '');

    const mcColors = {
      '0': '#000000', '1': '#0000AA', '2': '#00AA00', '3': '#00AAAA',
      '4': '#AA0000', '5': '#AA00AA', '6': '#FFAA00', '7': '#AAAAAA',
      '8': '#555555', '9': '#5555FF', 'a': '#55FF55', 'b': '#55FFFF',
      'c': '#FF5555', 'd': '#FF55FF', 'e': '#FFFF55', 'f': '#FFFFFF'
    };
    str = str.replace(/§([0-9a-f])/gi, (match, code) => {
      const col = mcColors[code.toLowerCase()] || '#FFFFFF';
      return `<span style="color:${col};">`;
    });
    str = str.replace(/§[rR]/g, '</span>');
    str = str.replace(/§[l-okL-OK]/g, '');
    return str;
  }

  function appendLine(lineData) {
    if (!outputArea) return;
    const div = document.createElement('div');
    div.className = 'console-line';

    const time = lineData.timestamp ? new Date(lineData.timestamp).toLocaleTimeString() : new Date().toLocaleTimeString();
    const lvl = (lineData.level || 'info').toLowerCase();

    div.innerHTML = `
      <span class="console-time">${time}</span>
      <span class="console-msg ${lvl}">${formatConsoleMessage(lineData.line || '')}</span>
    `;

    outputArea.appendChild(div);

    if (autoscrollEnabled) {
      outputArea.scrollTop = outputArea.scrollHeight;
    }
  }

  function updateStatus(stateStr) {
    if (!statusText || !statusDot) return;
    statusText.innerText = stateStr || 'UNKNOWN';
    statusDot.className = `status-dot ${(stateStr || '').toLowerCase()}`;
    const headerStatusText = document.getElementById('header-server-status-text');
    const headerStatusDot = document.querySelector('#header-server-status .status-dot');
    if (headerStatusText) headerStatusText.innerText = stateStr;
    if (headerStatusDot) headerStatusDot.className = `status-dot ${(stateStr || '').toLowerCase()}`;
  }
}
