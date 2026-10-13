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

    const ansiColors = {
      '30': '#1E293B', '31': '#F87171', '32': '#4ADE80', '33': '#FBBF24',
      '34': '#60A5FA', '35': '#C084FC', '36': '#38BDF8', '37': '#F1F5F9',
      '90': '#94A3B8', '91': '#FCA5A5', '92': '#86EFAC', '93': '#FDE047',
      '94': '#93C5FD', '95': '#E879F9', '96': '#67E8F9', '97': '#FFFFFF'
    };

    let hadAnsi = false;
    if (str.includes('\u001b[')) {
      hadAnsi = true;
      str = str.replace(/\u001b\[0?m/g, '</span>');
      str = str.replace(/\u001b\[([0-9]+)(?:;([0-9]+))?m/g, (match, p1, p2) => {
        if (p1 === '0') return '</span>';
        let code = p2 ? (p1 === '1' ? p2 : p1) : p1;
        let isBold = p1 === '1' || p2 === '1';
        let col = ansiColors[code];
        if (col) {
          return `<span style="color:${col};${isBold ? 'font-weight:700;' : ''}">`;
        }
        return '';
      });
      str = str.replace(/\u001b\[[0-9;?]*[a-zA-Z]/g, '');
    }

    const mcColors = {
      '0': '#000000', '1': '#0000AA', '2': '#00AA00', '3': '#00AAAA',
      '4': '#AA0000', '5': '#AA00AA', '6': '#FFAA00', '7': '#AAAAAA',
      '8': '#555555', '9': '#5555FF', 'a': '#55FF55', 'b': '#55FFFF',
      'c': '#FF5555', 'd': '#FF55FF', 'e': '#FFFF55', 'f': '#FFFFFF'
    };
    if (str.includes('§')) {
      str = str.replace(/§([0-9a-f])/gi, (match, code) => {
        const col = mcColors[code.toLowerCase()] || '#FFFFFF';
        return `<span style="color:${col};">`;
      });
      str = str.replace(/§[rR]/g, '</span>');
      str = str.replace(/§[l-okL-OK]/g, '');
    }

    if (str.startsWith('&gt;') || str.startsWith('&gt; ')) {
      return `<span style="color:#38BDF8;font-weight:700;">&gt;</span> <span style="color:#F8FAFC;font-weight:600;">${str.replace(/^&gt;\s*/, '')}</span>`;
    }

    if (!hadAnsi) {
      const logHeaderRegex = /^(\[\d{2}:\d{2}:\d{2}(?:\.\d{3})?\])\s*(\[([^\]\/]+)\/(INFO|WARN|WARNING|ERROR|FATAL|DEBUG|TRACE)\]:?)(.*)$/;
      const m = str.match(logHeaderRegex);
      if (m) {
        const timePart = m[1];
        const threadName = m[3];
        const level = m[4];
        let rest = m[5];

        let levelColor = '#38BDF8';
        if (level === 'WARN' || level === 'WARNING') levelColor = '#FBBF24';
        else if (level === 'ERROR' || level === 'FATAL') levelColor = '#F87171';
        else if (level === 'DEBUG' || level === 'TRACE') levelColor = '#C084FC';

        rest = rest.replace(/(\[([a-zA-Z0-9_\-\.\+]+)\])/g, '<span style="color:#FCD34D;font-weight:600;">$1</span>');
        rest = rest.replace(/([a-zA-Z0-9_]{3,16})\s+(issued server command:)\s*(\/.*)?/g, (match, pName, issuedText, cmdText) => {
          return `<span style="color:#4ADE80;font-weight:600;">${pName}</span> <span style="color:#94A3B8;">${issuedText}</span> ${cmdText ? `<span style="color:#38BDF8;font-weight:600;">${cmdText}</span>` : ''}`;
        });
        rest = rest.replace(/(&lt;[a-zA-Z0-9_]{3,16}&gt;)/g, '<span style="color:#4ADE80;font-weight:600;">$1</span>');

        return `<span style="color:#64748B;">${timePart}</span> <span style="color:#475569;">[</span><span style="color:#94A3B8;">${threadName}</span><span style="color:#475569;">/</span><span style="color:${levelColor};font-weight:700;">${level}</span><span style="color:#475569;">]:</span>${rest}`;
      }

      const shortHeaderRegex = /^(\[\d{2}:\d{2}:\d{2}(?:\.\d{3})?\]|\d{2}:\d{2}:\d{2})\s+(\[(INFO|WARN|WARNING|ERROR|FATAL|DEBUG|TRACE)\]:?|(INFO|WARN|WARNING|ERROR|FATAL|DEBUG|TRACE):?)(.*)$/;
      const sm = str.match(shortHeaderRegex);
      if (sm) {
        const timePart = sm[1];
        const level = sm[3] || sm[4];
        let rest = sm[5];

        let levelColor = '#38BDF8';
        if (level === 'WARN' || level === 'WARNING') levelColor = '#FBBF24';
        else if (level === 'ERROR' || level === 'FATAL') levelColor = '#F87171';
        else if (level === 'DEBUG' || level === 'TRACE') levelColor = '#C084FC';

        rest = rest.replace(/(\[([a-zA-Z0-9_\-\.\+]+)\])/g, '<span style="color:#FCD34D;font-weight:600;">$1</span>');
        rest = rest.replace(/([a-zA-Z0-9_]{3,16})\s+(issued server command:)\s*(\/.*)?/g, (match, pName, issuedText, cmdText) => {
          return `<span style="color:#4ADE80;font-weight:600;">${pName}</span> <span style="color:#94A3B8;">${issuedText}</span> ${cmdText ? `<span style="color:#38BDF8;font-weight:600;">${cmdText}</span>` : ''}`;
        });
        rest = rest.replace(/(&lt;[a-zA-Z0-9_]{3,16}&gt;)/g, '<span style="color:#4ADE80;font-weight:600;">$1</span>');

        return `<span style="color:#64748B;">${timePart}</span> <span style="color:${levelColor};font-weight:700;">[${level}]:</span>${rest}`;
      }

      const stackRegex = /^(\s*at\s+)([a-zA-Z0-9_\.$]+)(\(.*?\))$/;
      const stMatch = str.match(stackRegex);
      if (stMatch) {
        return `<span style="color:#F87171;opacity:0.8;">${stMatch[1]}</span><span style="color:#CBD5E1;">${stMatch[2]}</span><span style="color:#38BDF8;">${stMatch[3]}</span>`;
      }

      if (str.trimStart().startsWith('Caused by:')) {
        return `<span style="color:#EF4444;font-weight:700;">${str}</span>`;
      }
    }

    return str;
  }

  function appendLine(lineData) {
    if (!outputArea) return;
    const rawLine = (lineData && typeof lineData === 'object') ? (lineData.line ?? '') : String(lineData ?? '');
    const div = document.createElement('div');

    let levelClass = 'info';
    const trimmed = rawLine.trim();
    const lower = trimmed.toLowerCase();
    if (trimmed.startsWith('>')) {
      levelClass = 'cmd';
    } else if (lower.includes('/error') || lower.includes('[error') || lower.includes('exception') || lower.includes('fatal') || trimmed.startsWith('at ') || trimmed.startsWith('caused by:')) {
      levelClass = 'error';
    } else if (lower.includes('/warn') || lower.includes('[warn')) {
      levelClass = 'warn';
    }

    div.className = `console-line ${levelClass}`;
    div.innerHTML = formatConsoleMessage(rawLine) || '&nbsp;';

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
