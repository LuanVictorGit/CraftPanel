import { api } from '../api.js';
import { toast, modal, escapeHtml } from '../ui.js';
import { i18n } from '../i18n.js';
import { PanelWebSocket } from '../websocket.js';

const PING_INTERVAL_MS = 30000;

/** Small, safe Markdown: everything is escaped first, then only a few known patterns become tags. */
export function renderMarkdown(source) {
  const blocks = [];
  const withoutFences = String(source ?? '').replace(/```([\w+-]*)\n?([\s\S]*?)(```|$)/g, (_, lang, code) => {
    blocks.push(`<pre class="chat-code"><code>${escapeHtml(code.replace(/\n$/, ''))}</code></pre>`);
    return `\u0000${blocks.length - 1}\u0000`;
  });

  const inline = (text) => escapeHtml(text)
    .replace(/`([^`\n]+)`/g, '<code>$1</code>')
    .replace(/\*\*([^*\n]+)\*\*/g, '<strong>$1</strong>')
    .replace(/(^|[\s(])\*([^*\n]+)\*/g, '$1<em>$2</em>')
    .replace(/\[([^\]\n]+)\]\((https?:\/\/[^\s)"]+)\)/g, '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>');

  const html = [];
  let list = null;
  let paragraph = [];
  const flushParagraph = () => {
    if (paragraph.length) {
      html.push(`<p>${paragraph.map(inline).join('<br>')}</p>`);
      paragraph = [];
    }
  };
  const closeList = () => {
    if (list) {
      html.push(`</${list}>`);
      list = null;
    }
  };

  for (const line of withoutFences.split('\n')) {
    const fence = line.match(/^\u0000(\d+)\u0000$/);
    const heading = line.match(/^(#{1,4})\s+(.*)$/);
    const bullet = line.match(/^\s*[-*]\s+(.*)$/);
    const numbered = line.match(/^\s*\d+[.)]\s+(.*)$/);

    if (fence) {
      flushParagraph();
      closeList();
      html.push(blocks[Number(fence[1])]);
    } else if (heading) {
      flushParagraph();
      closeList();
      const level = Math.min(heading[1].length + 2, 6);
      html.push(`<h${level}>${inline(heading[2])}</h${level}>`);
    } else if (bullet || numbered) {
      flushParagraph();
      const kind = bullet ? 'ul' : 'ol';
      if (list !== kind) {
        closeList();
        html.push(`<${kind}>`);
        list = kind;
      }
      html.push(`<li>${inline((bullet || numbered)[1])}</li>`);
    } else if (!line.trim()) {
      flushParagraph();
      closeList();
    } else {
      closeList();
      paragraph.push(line);
    }
  }
  flushParagraph();
  closeList();
  return html.join('').replace(/\u0000(\d+)\u0000/g, (_, index) => blocks[Number(index)]);
}

function formatArgs(args) {
  if (typeof args === 'string') {
    try {
      return JSON.stringify(JSON.parse(args), null, 2);
    } catch (e) {
      return args;
    }
  }
  return JSON.stringify(args ?? {}, null, 2);
}

// The one argument that says what a tool call is about, shown next to its name.
function argSummary(args) {
  let parsed = args;
  if (typeof args === 'string') {
    try {
      parsed = JSON.parse(args);
    } catch (e) {
      return '';
    }
  }
  const value = parsed?.command ?? parsed?.path ?? parsed?.filename ?? parsed?.text ?? parsed?.name ?? parsed?.contains;
  return value == null ? '' : String(value);
}

function toolLabel(name) {
  const key = `tool_${name}`;
  const translated = i18n.t(key);
  return translated === key ? name.replace(/_/g, ' ') : translated;
}

const icons = {
  spark: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3l1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9z"/><path d="M19 15l.8 2.2L22 18l-2.2.8L19 21l-.8-2.2L16 18l2.2-.8z"/></svg>',
  check: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>',
  cross: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>',
  wrench: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14.7 6.3a4 4 0 0 0-5.4 5.2L3 17.8V21h3.2l6.3-6.3a4 4 0 0 0 5.2-5.4l-2.6 2.6-2.9-.7-.7-2.9z"/></svg>',
  shield: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>',
  trash: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/><path d="M10 11v6M14 11v6"/><path d="M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/></svg>',
  plus: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>',
  send: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="19" x2="12" y2="5"/><polyline points="5 12 12 5 19 12"/></svg>',
  stop: '<svg viewBox="0 0 24 24" fill="currentColor"><rect x="6" y="6" width="12" height="12" rx="2"/></svg>'
};

export async function renderChat(root, state) {
  let status;
  try {
    status = await api.get('/api/ai/status');
  } catch (e) {
    root.innerHTML = `<div class="card">${escapeHtml(e.message)}</div>`;
    return;
  }

  if (!status.canChat) {
    root.innerHTML = `<div class="chat-notice"><h3>${i18n.t('chatNoPermissionTitle')}</h3><p>${i18n.t('chatNoPermissionDesc')}</p></div>`;
    return;
  }
  if (!status.configured) {
    root.innerHTML = `
      <div class="chat-notice">
        <div class="chat-notice-icon">${icons.spark}</div>
        <h3>${i18n.t('chatNotConfiguredTitle')}</h3>
        <p>${i18n.t(status.canManage ? 'chatNotConfiguredAdmin' : 'chatNotConfiguredUser')}</p>
        ${status.canManage ? `<button class="btn btn-primary" id="chat-go-settings">${i18n.t('chatOpenSettings')}</button>` : ''}
      </div>
    `;
    document.getElementById('chat-go-settings')?.addEventListener('click', () => state.navigateTo('settings'));
    return;
  }

  root.innerHTML = `
    <div class="chat-layout">
      <aside class="chat-sidebar">
        <button class="btn btn-primary btn-sm chat-new" id="chat-new">${icons.plus}<span>${i18n.t('chatNew')}</span></button>
        <div class="chat-history" id="chat-history"></div>
      </aside>
      <section class="chat-main">
        <header class="chat-header">
          <div class="chat-title" id="chat-title">${i18n.t('chatNew')}</div>
          <span class="badge badge-gray chat-model" title="${i18n.t('chatModel')}">${escapeHtml(status.model)}</span>
        </header>
        <div class="chat-messages" id="chat-messages" aria-live="polite"></div>
        <form class="chat-composer" id="chat-form">
          <textarea id="chat-input" rows="1" maxlength="20000" placeholder="${i18n.t('chatPlaceholder')}" aria-label="${i18n.t('chatPlaceholder')}"></textarea>
          <button type="submit" class="btn btn-primary chat-send" id="chat-send" title="${i18n.t('chatSend')}" aria-label="${i18n.t('chatSend')}">${icons.send}</button>
        </form>
        <p class="chat-disclaimer">${i18n.t('chatDisclaimer')}</p>
      </section>
    </div>
  `;

  const messagesEl = document.getElementById('chat-messages');
  const historyEl = document.getElementById('chat-history');
  const titleEl = document.getElementById('chat-title');
  const form = document.getElementById('chat-form');
  const input = document.getElementById('chat-input');
  const sendBtn = document.getElementById('chat-send');

  let conversationId = null;
  let running = false;
  let followBottom = true;
  let current = null;        // the block being streamed: { kind, element, body, text, startedAt }
  let renderFrame = 0;
  let toolCards = new Map();
  let turn = null;

  messagesEl.addEventListener('scroll', () => {
    followBottom = messagesEl.scrollHeight - messagesEl.scrollTop - messagesEl.clientHeight < 60;
  }, { passive: true });

  const scrollDown = () => {
    if (followBottom) messagesEl.scrollTop = messagesEl.scrollHeight;
  };

  const setRunning = (value) => {
    running = value;
    sendBtn.innerHTML = value ? icons.stop : icons.send;
    sendBtn.title = i18n.t(value ? 'chatStop' : 'chatSend');
    sendBtn.setAttribute('aria-label', sendBtn.title);
    sendBtn.classList.toggle('is-stop', value);
    updateSendState();
  };

  const updateSendState = () => {
    sendBtn.disabled = !running && !input.value.trim();
  };

  const renderWelcome = () => {
    const prompts = ['chatSuggestion1', 'chatSuggestion2', 'chatSuggestion3', 'chatSuggestion4'].map(key => i18n.t(key));
    messagesEl.innerHTML = `
      <div class="chat-welcome">
        <div class="chat-notice-icon">${icons.spark}</div>
        <h3>${i18n.t('chatWelcomeTitle')}</h3>
        <p>${i18n.t('chatWelcomeDesc')}</p>
        <div class="chat-suggestions">
          ${prompts.map(prompt => `<button type="button" class="chat-suggestion">${escapeHtml(prompt)}</button>`).join('')}
        </div>
      </div>
    `;
    messagesEl.querySelectorAll('.chat-suggestion').forEach(button => {
      button.addEventListener('click', () => {
        input.value = button.textContent;
        autoGrow();
        updateSendState();
        input.focus();
      });
    });
  };

  const addUserBubble = (text) => {
    const bubble = document.createElement('div');
    bubble.className = 'chat-row chat-row-user';
    bubble.innerHTML = `<div class="chat-bubble">${escapeHtml(text).replace(/\n/g, '<br>')}</div>`;
    messagesEl.appendChild(bubble);
    scrollDown();
  };

  // Everything the assistant does for one message sits in one column: thoughts, tools and text, in order.
  const startTurn = () => {
    turn = document.createElement('div');
    turn.className = 'chat-row chat-row-assistant';
    messagesEl.appendChild(turn);
    return turn;
  };

  const ensureTurn = () => turn || startTurn();

  const finishThinking = (block) => {
    if (block.kind !== 'thinking' || !block.element) return;
    const seconds = Math.max(1, Math.round((Date.now() - block.startedAt) / 1000));
    block.element.open = false;
    block.element.classList.remove('is-active');
    block.element.querySelector('.chat-thinking-label').textContent = i18n.t('chatThoughtFor', { seconds });
  };

  const closeCurrent = () => {
    if (current) {
      flushStream();
      finishThinking(current);
      current = null;
    }
  };

  const addThinkingBlock = (text, live) => {
    const details = document.createElement('details');
    details.className = `chat-thinking${live ? ' is-active' : ''}`;
    details.open = live;
    details.innerHTML = `
      <summary>${icons.spark}<span class="chat-thinking-label">${i18n.t(live ? 'chatThinking' : 'chatThoughts')}</span></summary>
      <div class="chat-thinking-body chat-md"></div>
    `;
    const body = details.querySelector('.chat-thinking-body');
    body.innerHTML = renderMarkdown(text);
    ensureTurn().appendChild(details);
    return { kind: 'thinking', element: details, body, text, startedAt: Date.now() };
  };

  const addTextBlock = (text) => {
    const body = document.createElement('div');
    body.className = 'chat-answer chat-md';
    body.innerHTML = renderMarkdown(text);
    ensureTurn().appendChild(body);
    return { kind: 'text', element: body, body, text };
  };

  const flushStream = () => {
    if (renderFrame) {
      cancelAnimationFrame(renderFrame);
      renderFrame = 0;
    }
    if (current) current.body.innerHTML = renderMarkdown(current.text);
  };

  const appendStream = (kind, text) => {
    if (!current || current.kind !== kind) {
      closeCurrent();
      current = kind === 'thinking' ? addThinkingBlock('', true) : addTextBlock('');
    }
    current.text += text;
    if (!renderFrame) {
      renderFrame = requestAnimationFrame(() => {
        renderFrame = 0;
        if (current) current.body.innerHTML = renderMarkdown(current.text);
        scrollDown();
      });
    }
  };

  const addToolCard = (id, name, args, access) => {
    const card = document.createElement('details');
    card.className = 'chat-tool is-running';
    const summary = argSummary(args);
    card.innerHTML = `
      <summary>
        <span class="chat-tool-status">${icons.wrench}</span>
        <span class="chat-tool-name">${escapeHtml(toolLabel(name))}</span>
        ${summary ? `<code class="chat-tool-arg">${escapeHtml(summary)}</code>` : ''}
        <span class="chat-tool-time"></span>
      </summary>
      <div class="chat-tool-body">
        <div class="chat-tool-section-title">${i18n.t('chatToolInput')}</div>
        <pre>${escapeHtml(formatArgs(args))}</pre>
        <div class="chat-tool-output" style="display:none;">
          <div class="chat-tool-section-title">${i18n.t('chatToolOutput')}</div>
          <pre></pre>
        </div>
      </div>
    `;
    ensureTurn().appendChild(card);
    toolCards.set(id, { card, name, access });
    scrollDown();
    return card;
  };

  const finishToolCard = (id, ok, output, ms) => {
    const entry = toolCards.get(id);
    if (!entry) return;
    const { card } = entry;
    card.classList.remove('is-running', 'is-waiting');
    card.classList.add(ok ? 'is-ok' : 'is-failed');
    card.querySelector('.chat-tool-status').innerHTML = ok ? icons.check : icons.cross;
    if (ms > 0) {
      card.querySelector('.chat-tool-time').textContent = ms < 1000 ? `${ms} ms` : `${(ms / 1000).toFixed(1)} s`;
    }
    const outputEl = card.querySelector('.chat-tool-output');
    outputEl.style.display = 'block';
    outputEl.querySelector('pre').textContent = output ?? '';
    card.querySelector('.chat-approval')?.remove();
    scrollDown();
  };

  const showApproval = (id, name, args, access) => {
    const entry = toolCards.get(id);
    if (!entry) return;
    const { card } = entry;
    const destructive = access === 'DESTRUCTIVE';
    card.classList.remove('is-running');
    card.classList.add('is-waiting');
    card.open = true;
    card.querySelector('.chat-tool-status').innerHTML = icons.shield;

    const panel = document.createElement('div');
    panel.className = `chat-approval${destructive ? ' is-destructive' : ''}`;
    panel.innerHTML = `
      <div class="chat-approval-text">${i18n.t(destructive ? 'chatApproveDestructive' : 'chatApproveWrite')}</div>
      ${destructive ? '' : `<label class="chat-approval-all"><input type="checkbox"><span>${i18n.t('chatApproveEdits')}</span></label>`}
      <div class="chat-approval-actions">
        <button type="button" class="btn btn-sm ${destructive ? 'btn-danger' : 'btn-primary'}" data-approve="1">${i18n.t('chatApprove')}</button>
        <button type="button" class="btn btn-sm btn-secondary" data-approve="0">${i18n.t('chatDeny')}</button>
      </div>
    `;
    panel.querySelectorAll('button').forEach(button => {
      button.addEventListener('click', () => {
        const approved = button.dataset.approve === '1';
        const approveEdits = !!panel.querySelector('input[type="checkbox"]')?.checked;
        ws.send({ type: 'approval', id, approved, approveEdits });
        panel.remove();
        card.classList.remove('is-waiting');
        card.classList.add('is-running');
        card.querySelector('.chat-tool-status').innerHTML = icons.wrench;
      });
    });
    card.querySelector('.chat-tool-body').prepend(panel);
    followBottom = true;
    scrollDown();
  };

  const addError = (message) => {
    const error = document.createElement('div');
    error.className = 'chat-error';
    error.textContent = message;
    ensureTurn().appendChild(error);
    scrollDown();
  };

  const loadHistory = async () => {
    try {
      const list = await api.get('/api/ai/conversations');
      if (list.length === 0) {
        historyEl.innerHTML = `<div class="chat-history-empty">${i18n.t('chatNoHistory')}</div>`;
        return;
      }
      historyEl.innerHTML = list.map(item => `
        <div class="chat-history-item ${item.id === conversationId ? 'active' : ''}" data-id="${escapeHtml(item.id)}" role="button" tabindex="0">
          <span class="chat-history-title">${escapeHtml(item.title)}</span>
          <button type="button" class="chat-history-delete" title="${i18n.t('delete')}" aria-label="${i18n.t('delete')}">${icons.trash}</button>
        </div>
      `).join('');
      historyEl.querySelectorAll('.chat-history-item').forEach(row => {
        const open = () => {
          if (!running) openConversation(row.dataset.id);
        };
        row.addEventListener('click', open);
        row.addEventListener('keydown', (e) => {
          if (e.key === 'Enter') open();
        });
        row.querySelector('.chat-history-delete').addEventListener('click', (e) => {
          e.stopPropagation();
          modal.confirm(i18n.t('chatDeleteTitle'), i18n.t('chatDeleteConfirm'), async () => {
            try {
              await api.delete(`/api/ai/conversations/${row.dataset.id}`);
              if (conversationId === row.dataset.id) newConversation();
              loadHistory();
            } catch (err) {
              toast.error(err.message);
            }
          }, true);
        });
      });
    } catch (e) {
      historyEl.innerHTML = '';
    }
  };

  const newConversation = () => {
    conversationId = null;
    current = null;
    turn = null;
    toolCards = new Map();
    titleEl.textContent = i18n.t('chatNew');
    renderWelcome();
    historyEl.querySelectorAll('.chat-history-item').forEach(row => row.classList.remove('active'));
    input.focus();
  };

  const openConversation = async (id) => {
    try {
      const data = await api.get(`/api/ai/conversations/${id}`);
      conversationId = id;
      current = null;
      turn = null;
      toolCards = new Map();
      titleEl.textContent = data.conversation.title;
      messagesEl.innerHTML = '';
      followBottom = true;
      const pendingCalls = [];
      for (const item of data.items) {
        if (item.kind === 'user') {
          turn = null;
          addUserBubble(item.text);
        } else if (item.kind === 'thinking') {
          addThinkingBlock(item.text, false);
        } else if (item.kind === 'assistant') {
          addTextBlock(item.text);
        } else if (item.kind === 'tool_call') {
          const key = `saved-${pendingCalls.length}`;
          pendingCalls.push({ key, name: item.name, id: item.id, done: false });
          addToolCard(key, item.name, item.args);
        } else if (item.kind === 'tool_result') {
          const call = pendingCalls.find(c => !c.done && c.id === item.id) || pendingCalls.find(c => !c.done && c.name === item.name);
          if (call) {
            call.done = true;
            finishToolCard(call.key, item.ok, item.output, 0);
          }
        }
      }
      // A call with no result belongs to an answer that was interrupted.
      pendingCalls.filter(c => !c.done).forEach(c => finishToolCard(c.key, false, i18n.t('chatInterrupted'), 0));
      turn = null;
      messagesEl.scrollTop = messagesEl.scrollHeight;
      historyEl.querySelectorAll('.chat-history-item').forEach(row => row.classList.toggle('active', row.dataset.id === id));
    } catch (e) {
      toast.error(e.message);
    }
  };

  const failPendingTools = (message) => {
    for (const [id, entry] of toolCards) {
      if (entry.card.classList.contains('is-running') || entry.card.classList.contains('is-waiting')) {
        finishToolCard(id, false, message, 0);
      }
    }
  };

  const onMessage = (data) => {
    switch (data.type) {
      case 'conversation': {
        const isNew = conversationId !== data.id;
        conversationId = data.id;
        titleEl.textContent = data.title;
        if (isNew) loadHistory();
        break;
      }
      case 'thinking':
        appendStream('thinking', data.text);
        break;
      case 'text':
        appendStream('text', data.text);
        break;
      case 'tool_start':
        closeCurrent();
        addToolCard(data.id, data.name, data.args, data.access);
        break;
      case 'approval_request':
        showApproval(data.id, data.name, data.args, data.access);
        break;
      case 'approval_expired':
        toast.info(i18n.t('chatApprovalExpired'));
        break;
      case 'tool_end':
        finishToolCard(data.id, data.ok, data.output, data.ms);
        break;
      case 'error':
        closeCurrent();
        addError(data.message);
        if (!running) toast.error(data.message);
        break;
      case 'done':
        closeCurrent();
        failPendingTools(i18n.t('chatInterrupted'));
        if (data.cancelled && turn) {
          const note = document.createElement('div');
          note.className = 'chat-cancelled';
          note.textContent = i18n.t('chatCancelled');
          turn.appendChild(note);
        }
        turn = null;
        setRunning(false);
        loadHistory();
        input.focus();
        break;
      default:
        break;
    }
  };

  const ws = new PanelWebSocket('/ws/ai', onMessage, null, () => {
    if (running) {
      closeCurrent();
      failPendingTools(i18n.t('chatInterrupted'));
      addError(i18n.t('chatConnectionLost'));
      turn = null;
      setRunning(false);
    }
  });
  ws.connect();
  const pingTimer = setInterval(() => ws.send({ type: 'ping' }), PING_INTERVAL_MS);

  state.currentCleanup = () => {
    clearInterval(pingTimer);
    if (renderFrame) cancelAnimationFrame(renderFrame);
    ws.close();
  };

  const autoGrow = () => {
    input.style.height = 'auto';
    input.style.height = `${Math.min(input.scrollHeight, 180)}px`;
  };

  input.addEventListener('input', () => {
    autoGrow();
    updateSendState();
  });
  input.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
      e.preventDefault();
      form.requestSubmit();
    }
  });

  form.addEventListener('submit', (e) => {
    e.preventDefault();
    if (running) {
      ws.send({ type: 'cancel' });
      return;
    }
    const text = input.value.trim();
    if (!text) return;
    if (!ws.ws || ws.ws.readyState !== WebSocket.OPEN) {
      toast.error(i18n.t('chatNotConnected'));
      return;
    }
    if (!conversationId) messagesEl.innerHTML = '';
    followBottom = true;
    turn = null;
    addUserBubble(text);
    startTurn();
    input.value = '';
    autoGrow();
    setRunning(true);
    ws.send({ type: 'send', text, conversationId, serverId: state.activeServerId || 'local' });
  });

  document.getElementById('chat-new').addEventListener('click', () => {
    if (!running) newConversation();
  });

  setRunning(false);
  renderWelcome();
  loadHistory();
  input.focus();
}
