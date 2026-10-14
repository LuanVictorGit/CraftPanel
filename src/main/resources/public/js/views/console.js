import { api } from '../api.js';
import { toast, escapeHtml } from '../ui.js';
import { i18n } from '../i18n.js';
import { PanelWebSocket } from '../websocket.js';

let activeWs = null;
let commandHistory = [];
let historyIndex = -1;
let autoscrollEnabled = true;

let currentSuggestions = [];
let selectedSuggestionIndex = -1;
let debounceCompleteTimer = null;

export async function renderConsole(root, state) {
  const serverId = state.activeServerId || 'local';

  state.currentCleanup = () => {
    if (activeWs) {
      activeWs.close();
      activeWs = null;
    }
  };

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

        <div style="position:relative;width:100%;">
          <!-- In-Game Minecraft Command Assistant HUD -->
          <div id="console-assistant-overlay" class="console-assistant-hud" style="display:none;">
            <!-- Brigadier Command Syntax Indicator -->
            <div id="console-hint-bar" class="console-hint-bar" style="display:none;">
              <span class="hint-icon"><svg style="width:13px;height:13px;display:inline-block;vertical-align:middle;" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/></svg></span>
              <span id="console-hint-usage" class="hint-usage"></span>
              <span id="console-hint-desc" class="hint-desc"></span>
            </div>
            <!-- Suggestions List -->
            <div id="console-suggestions-list" class="console-suggestions-list"></div>
          </div>

          <form class="console-input-wrap" id="console-cmd-form">
            <span class="console-prompt">&gt;</span>
            <input type="text" id="console-cmd-input" class="console-input" placeholder="${i18n.t('commandPlaceholder')}" autocomplete="off" spellcheck="false">
            <button type="submit" class="btn btn-primary btn-sm">${i18n.t('sendCommand')}</button>
          </form>
        </div>
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

  const assistantOverlay = document.getElementById('console-assistant-overlay');
  const hintBar = document.getElementById('console-hint-bar');
  const hintUsage = document.getElementById('console-hint-usage');
  const hintDesc = document.getElementById('console-hint-desc');
  const suggestionsList = document.getElementById('console-suggestions-list');

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

  function hideAssistant() {
    if (assistantOverlay) assistantOverlay.style.display = 'none';
    currentSuggestions = [];
    selectedSuggestionIndex = -1;
  }

  function formatSyntaxUsage(usageStr) {
    if (!usageStr) return '';
    let esc = escapeHtml(usageStr);
    esc = esc.replace(/&lt;([^&]+)&gt;/g, '<span class="syntax-param">&lt;$1&gt;</span>');
    esc = esc.replace(/\[([^\]]+)\]/g, '<span class="syntax-opt">[$1]</span>');
    return esc;
  }

  function renderAssistant(data) {
    if (!assistantOverlay || !suggestionsList) return;
    const currentVal = cmdInput?.value ?? '';
    if (!currentVal.trim()) {
      hideAssistant();
      return;
    }

    const hasHint = data.hint && data.hint.usage;
    if (hasHint && hintBar && hintUsage) {
      hintBar.style.display = 'flex';
      hintUsage.innerHTML = formatSyntaxUsage(data.hint.usage);
      if (hintDesc) {
        hintDesc.innerText = data.hint.description || '';
      }
    } else if (hintBar) {
      hintBar.style.display = 'none';
    }

    currentSuggestions = Array.isArray(data.completions) ? data.completions : [];
    selectedSuggestionIndex = -1;

    if (currentSuggestions.length === 0 && !hasHint) {
      hideAssistant();
      return;
    }

    assistantOverlay.style.display = 'flex';

    if (currentSuggestions.length === 0) {
      suggestionsList.innerHTML = '';
      return;
    }

    const buffer = data.buffer || '';
    const lastWord = buffer.includes(' ') ? buffer.substring(buffer.lastIndexOf(' ') + 1) : buffer;
    const cleanLastWord = lastWord.toLowerCase();

    suggestionsList.innerHTML = currentSuggestions.slice(0, 15).map((sug, idx) => {
      let displayHtml = escapeHtml(sug);
      if (cleanLastWord && sug.toLowerCase().startsWith(cleanLastWord)) {
        displayHtml = `<span class="sug-match">${escapeHtml(sug.substring(0, cleanLastWord.length))}</span>${escapeHtml(sug.substring(cleanLastWord.length))}`;
      }

      return `
        <div class="suggestion-item ${idx === 0 ? 'active' : ''}" data-index="${idx}" data-sug="${escapeHtml(sug)}">
          <span>${displayHtml}</span>
          <span class="sug-key-hint">${idx === 0 ? 'Tab' : '↵'}</span>
        </div>
      `;
    }).join('');

    selectedSuggestionIndex = 0;

    suggestionsList.querySelectorAll('.suggestion-item').forEach(item => {
      item.addEventListener('click', (e) => {
        e.stopPropagation();
        const sug = item.dataset.sug;
        if (sug) {
          applySuggestion(sug);
        }
      });
    });
  }

  function applySuggestion(sug) {
    if (!cmdInput) return;
    const val = cmdInput.value;
    if (val.includes(' ')) {
      const parts = val.split(' ');
      parts[parts.length - 1] = sug;
      cmdInput.value = parts.join(' ') + ' ';
    } else {
      const startsSlash = val.startsWith('/');
      cmdInput.value = (startsSlash ? (sug.startsWith('/') ? sug : ('/' + sug)) : sug) + ' ';
    }
    cmdInput.focus();
    requestAutocomplete(cmdInput.value);
  }

  function requestAutocomplete(text) {
    if (!text || !text.trim()) {
      hideAssistant();
      return;
    }
    if (activeWs && activeWs.ws && activeWs.ws.readyState === WebSocket.OPEN) {
      activeWs.send({ type: 'tab_complete', buffer: text });
    }
  }

  cmdInput?.addEventListener('input', (e) => {
    const val = e.target.value;
    if (debounceCompleteTimer) clearTimeout(debounceCompleteTimer);
    if (!val.trim()) {
      hideAssistant();
      return;
    }
    debounceCompleteTimer = setTimeout(() => {
      requestAutocomplete(val);
    }, 45);
  });

  cmdInput?.addEventListener('keydown', (e) => {
    const isAssistantOpen = assistantOverlay && assistantOverlay.style.display !== 'none';

    if (e.key === 'Tab') {
      e.preventDefault();
      if (isAssistantOpen && currentSuggestions.length > 0) {
        const idx = selectedSuggestionIndex >= 0 ? selectedSuggestionIndex : 0;
        const sug = currentSuggestions[idx];
        if (sug) {
          applySuggestion(sug);
        }
      } else {
        requestAutocomplete(cmdInput.value);
      }
      return;
    }

    if (e.key === 'ArrowDown') {
      if (isAssistantOpen && currentSuggestions.length > 0) {
        e.preventDefault();
        selectedSuggestionIndex = (selectedSuggestionIndex + 1) % Math.min(currentSuggestions.length, 15);
        updateActiveSuggestion();
        return;
      }

      e.preventDefault();
      if (historyIndex > 0) {
        historyIndex--;
        cmdInput.value = commandHistory[commandHistory.length - 1 - historyIndex];
      } else if (historyIndex === 0) {
        historyIndex = -1;
        cmdInput.value = '';
      }
      hideAssistant();
      return;
    }

    if (e.key === 'ArrowUp') {
      if (isAssistantOpen && currentSuggestions.length > 0) {
        e.preventDefault();
        selectedSuggestionIndex = (selectedSuggestionIndex - 1 + Math.min(currentSuggestions.length, 15)) % Math.min(currentSuggestions.length, 15);
        updateActiveSuggestion();
        return;
      }

      e.preventDefault();
      if (commandHistory.length > 0 && historyIndex < commandHistory.length - 1) {
        historyIndex++;
        cmdInput.value = commandHistory[commandHistory.length - 1 - historyIndex];
      }
      hideAssistant();
      return;
    }

    if (e.key === 'Escape') {
      hideAssistant();
      return;
    }

    if (e.key === 'Enter') {
      if (isAssistantOpen && selectedSuggestionIndex > 0) {
        e.preventDefault();
        const sug = currentSuggestions[selectedSuggestionIndex];
        if (sug) {
          applySuggestion(sug);
        }
        return;
      }
      hideAssistant();
    }
  });

  function updateActiveSuggestion() {
    if (!suggestionsList) return;
    const items = suggestionsList.querySelectorAll('.suggestion-item');
    items.forEach((item, idx) => {
      const isActive = idx === selectedSuggestionIndex;
      item.classList.toggle('active', isActive);
      if (isActive) {
        item.scrollIntoView({ block: 'nearest' });
      }
    });
  }

  document.addEventListener('click', (e) => {
    if (!assistantOverlay?.contains(e.target) && e.target !== cmdInput) {
      hideAssistant();
    }
  });

  cmdForm?.addEventListener('submit', (e) => {
    e.preventDefault();
    const cmd = cmdInput.value.trim();
    if (!cmd) return;

    commandHistory.push(cmd);
    historyIndex = -1;
    cmdInput.value = '';
    hideAssistant();

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
    } else if (data.type === 'tab_complete') {
      renderAssistant(data);
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
