import { escapeHtml } from './ui.js';

export class CodeEditor {
  constructor(container, options = {}) {
    this.container = container;
    this.initialContent = options.content || '';
    this.filename = options.filename || 'file.txt';
    this.onSave = options.onSave || null;
    this.onChange = options.onChange || null;
    this.searchQuery = '';
    this.searchMatches = [];
    this.currentMatchIndex = -1;
    this.isFullscreen = false;

    this.language = this.detectLanguage(this.filename);
    this.render();
    this.bindEvents();
    this.setValue(this.initialContent);
  }

  detectLanguage(filename) {
    const lower = filename.toLowerCase();
    if (lower.endsWith('.yml') || lower.endsWith('.yaml')) return 'yaml';
    if (lower.endsWith('.properties') || lower.endsWith('.env') || lower.endsWith('.cfg') || lower.endsWith('.ini')) return 'properties';
    if (lower.endsWith('.json')) return 'json';
    if (lower.endsWith('.toml')) return 'toml';
    if (lower.endsWith('.bat') || lower.endsWith('.cmd')) return 'batch';
    if (lower.endsWith('.sh') || lower.endsWith('.bash')) return 'bash';
    return 'plaintext';
  }

  render() {
    this.container.innerHTML = `
      <div class="code-editor-root" id="code-editor-root">
        <!-- Editor Toolbar -->
        <div class="code-editor-toolbar">
          <div class="code-editor-meta">
            <span class="editor-file-badge">${escapeHtml(this.filename)}</span>
            <span class="editor-lang-badge">${this.language.toUpperCase()}</span>
          </div>

          <div class="code-editor-actions">
            <!-- Search Bar -->
            <div class="editor-search-wrap" id="editor-search-bar" style="display:none;">
              <input type="text" id="editor-search-input" class="editor-search-input" placeholder="Find in file...">
              <span id="editor-search-count" class="editor-search-count">0/0</span>
              <button type="button" class="editor-tool-btn" id="editor-search-prev" title="Previous (Shift+F3)"><svg style="width:12px;height:12px;" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="18 15 12 9 6 15"></polyline></svg></button>
              <button type="button" class="editor-tool-btn" id="editor-search-next" title="Next (F3)"><svg style="width:12px;height:12px;" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="6 9 12 15 18 9"></polyline></svg></button>
              <button type="button" class="editor-tool-btn" id="editor-search-close" title="Close Search (Esc)"><svg style="width:12px;height:12px;" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></svg></button>
            </div>

            <button type="button" class="editor-tool-btn" id="editor-toggle-search" title="Find (Ctrl+F)">
              <svg style="width:13px;height:13px;" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
              <span>Search</span>
            </button>
            <button type="button" class="editor-tool-btn" id="editor-toggle-fullscreen" title="Toggle Fullscreen">
              <svg style="width:13px;height:13px;" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M8 3H5a2 2 0 0 0-2 2v3m18 0V5a2 2 0 0 0-2-2h-3m0 18h3a2 2 0 0 0 2-2v-3M3 16v3a2 2 0 0 0 2 2h3"/></svg>
            </button>
          </div>
        </div>

        <!-- Editor Main Work Area -->
        <div class="code-editor-workspace">
          <!-- Gutter Column for Line Numbers -->
          <div class="code-editor-gutter" id="editor-gutter">
            <div class="gutter-line">1</div>
          </div>

          <!-- Highlight Layer and Textarea Container -->
          <div class="code-editor-stage">
            <pre class="code-editor-highlight" id="editor-highlight" aria-hidden="true"><code></code></pre>
            <textarea class="code-editor-input" id="editor-input" spellcheck="false" autocomplete="off" autocorrect="off" autocapitalize="off"></textarea>
          </div>
        </div>

        <!-- Status Bar -->
        <div class="code-editor-statusbar">
          <div style="display:flex;align-items:center;gap:12px;">
            <span id="editor-cursor-pos">Ln 1, Col 1</span>
            <span id="editor-line-count">1 lines</span>
          </div>
          <div style="display:flex;align-items:center;gap:12px;">
            <span>UTF-8</span>
            <span>Spaces: 2</span>
            <span>${this.language.toUpperCase()}</span>
          </div>
        </div>
      </div>
    `;

    this.root = this.container.querySelector('#code-editor-root');
    this.textarea = this.container.querySelector('#editor-input');
    this.highlightCode = this.container.querySelector('#editor-highlight code');
    this.highlightPre = this.container.querySelector('#editor-highlight');
    this.gutter = this.container.querySelector('#editor-gutter');
    this.searchBar = this.container.querySelector('#editor-search-bar');
    this.searchInput = this.container.querySelector('#editor-search-input');
    this.searchCount = this.container.querySelector('#editor-search-count');
    this.cursorPosEl = this.container.querySelector('#editor-cursor-pos');
    this.lineCountEl = this.container.querySelector('#editor-line-count');
  }

  bindEvents() {
    this.textarea.addEventListener('input', () => {
      this.syncContent();
      if (this.onChange) this.onChange(this.getValue());
    });

    this.textarea.addEventListener('scroll', () => {
      this.syncScroll();
    });

    this.textarea.addEventListener('click', () => this.updateCursorPos());
    this.textarea.addEventListener('keyup', (e) => {
      if (!['Control', 'Shift', 'Alt', 'Meta'].includes(e.key)) {
        this.updateCursorPos();
      }
    });

    this.textarea.addEventListener('keydown', (e) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 's') {
        e.preventDefault();
        if (this.onSave) this.onSave(this.getValue());
        return;
      }

      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'f') {
        e.preventDefault();
        this.openSearch();
        return;
      }

      if (e.key === 'Tab') {
        e.preventDefault();
        this.handleTab(e.shiftKey);
      }
    });

    this.container.querySelector('#editor-toggle-search')?.addEventListener('click', () => {
      this.toggleSearch();
    });

    this.container.querySelector('#editor-search-close')?.addEventListener('click', () => {
      this.closeSearch();
    });

    this.searchInput?.addEventListener('input', () => {
      this.searchQuery = this.searchInput.value;
      this.findMatches();
    });

    this.searchInput?.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') {
        e.preventDefault();
        if (e.shiftKey) this.prevMatch(false);
        else this.nextMatch(false);
      } else if (e.key === 'Escape') {
        this.closeSearch();
      }
    });

    this.container.querySelector('#editor-search-next')?.addEventListener('click', () => this.nextMatch(false));
    this.container.querySelector('#editor-search-prev')?.addEventListener('click', () => this.prevMatch(false));

    this.container.querySelector('#editor-toggle-fullscreen')?.addEventListener('click', () => {
      this.toggleFullscreen();
    });
  }

  handleTab(isShift) {
    const start = this.textarea.selectionStart;
    const end = this.textarea.selectionEnd;
    const val = this.textarea.value;

    if (!isShift) {
      if (start === end) {
        this.textarea.value = val.substring(0, start) + '  ' + val.substring(end);
        this.textarea.selectionStart = this.textarea.selectionEnd = start + 2;
      } else {
        const lineStart = val.lastIndexOf('\n', start - 1) + 1;
        const lineEnd = val.indexOf('\n', end);
        const actualEnd = lineEnd === -1 ? val.length : lineEnd;
        const slice = val.substring(lineStart, actualEnd);
        const replaced = slice.split('\n').map(l => '  ' + l).join('\n');
        this.textarea.value = val.substring(0, lineStart) + replaced + val.substring(actualEnd);
        this.textarea.selectionStart = lineStart;
        this.textarea.selectionEnd = lineStart + replaced.length;
      }
    } else {
      const lineStart = val.lastIndexOf('\n', start - 1) + 1;
      const lineEnd = val.indexOf('\n', end);
      const actualEnd = lineEnd === -1 ? val.length : lineEnd;
      const slice = val.substring(lineStart, actualEnd);
      const replaced = slice.split('\n').map(l => l.startsWith('  ') ? l.substring(2) : (l.startsWith(' ') ? l.substring(1) : l)).join('\n');
      this.textarea.value = val.substring(0, lineStart) + replaced + val.substring(actualEnd);
      this.textarea.selectionStart = lineStart;
      this.textarea.selectionEnd = lineStart + replaced.length;
    }

    this.syncContent();
  }

  syncContent() {
    const code = this.textarea.value;
    const highlighted = this.highlightSyntax(code);
    this.highlightCode.innerHTML = highlighted + '\n';
    if (this.searchQuery && this.searchQuery.length > 0) {
      this.applySearchHighlight();
    }
    this.updateGutter();
    this.updateCursorPos();
  }

  syncScroll() {
    const top = this.textarea.scrollTop;
    const left = this.textarea.scrollLeft;
    this.highlightPre.scrollTop = top;
    this.highlightPre.scrollLeft = left;
    this.gutter.scrollTop = top;
  }

  updateGutter() {
    const lines = this.textarea.value.split('\n').length;
    let gutterHtml = '';
    for (let i = 1; i <= lines; i++) {
      gutterHtml += `<div class="gutter-line">${i}</div>`;
    }
    this.gutter.innerHTML = gutterHtml;
    if (this.lineCountEl) {
      this.lineCountEl.innerText = `${lines} line${lines === 1 ? '' : 's'}`;
    }
  }

  updateCursorPos() {
    const val = this.textarea.value;
    const selStart = this.textarea.selectionStart;
    const linesBefore = val.substring(0, selStart).split('\n');
    const currentLine = linesBefore.length;
    const currentCol = linesBefore[linesBefore.length - 1].length + 1;

    if (this.cursorPosEl) {
      this.cursorPosEl.innerText = `Ln ${currentLine}, Col ${currentCol}`;
    }

    const gutterLines = this.gutter.querySelectorAll('.gutter-line');
    gutterLines.forEach((el, idx) => {
      el.classList.toggle('active-line', idx + 1 === currentLine);
    });
  }

  highlightSyntax(text) {
    if (!text) return '';
    const lines = text.split('\n');
    const lang = this.language;

    const formattedLines = lines.map(line => {
      const trimmed = line.trimStart();

      if (trimmed.startsWith('#') || trimmed.startsWith('//') || trimmed.startsWith(';')) {
        return `<span class="tok-comment">${escapeHtml(line)}</span>`;
      }

      if (trimmed.startsWith('[') && trimmed.endsWith(']')) {
        return `<span class="tok-section">${escapeHtml(line)}</span>`;
      }

      if (lang === 'yaml') {
        return this.highlightYamlLine(line);
      } else if (lang === 'properties') {
        return this.highlightPropertiesLine(line);
      } else if (lang === 'json') {
        return this.highlightJsonLine(line);
      }

      return this.highlightGenericLine(line);
    });

    return formattedLines.join('\n');
  }

  highlightYamlLine(line) {
    let codePart = line;
    let commentPart = '';
    const commentIdx = line.indexOf(' #');
    if (commentIdx !== -1) {
      codePart = line.substring(0, commentIdx);
      commentPart = ` <span class="tok-comment">${escapeHtml(line.substring(commentIdx + 1))}</span>`;
    }

    const match = codePart.match(/^(\s*)([a-zA-Z0-9_\-\.\/]+)(\s*:\s*)(.*)$/);
    if (match) {
      const indent = escapeHtml(match[1]);
      const key = escapeHtml(match[2]);
      const colon = escapeHtml(match[3]);
      const val = match[4];
      return `${indent}<span class="tok-key">${key}</span>${colon}${this.highlightValue(val)}${commentPart}`;
    }

    const listMatch = codePart.match(/^(\s*-\s+)([a-zA-Z0-9_\-\.\/]+)(\s*:\s*)(.*)$/);
    if (listMatch) {
      const bullet = escapeHtml(listMatch[1]);
      const key = escapeHtml(listMatch[2]);
      const colon = escapeHtml(listMatch[3]);
      const val = listMatch[4];
      return `${bullet}<span class="tok-key">${key}</span>${colon}${this.highlightValue(val)}${commentPart}`;
    }

    return `${this.highlightValue(codePart)}${commentPart}`;
  }

  highlightPropertiesLine(line) {
    let codePart = line;
    let commentPart = '';
    const commentIdx = line.search(/\s+[#;]/);
    if (commentIdx !== -1) {
      codePart = line.substring(0, commentIdx);
      commentPart = ` <span class="tok-comment">${escapeHtml(line.substring(commentIdx + 1))}</span>`;
    }

    const match = codePart.match(/^(\s*)([a-zA-Z0-9_\-\.]+)(\s*=\s*)(.*)$/);
    if (match) {
      const indent = escapeHtml(match[1]);
      const key = escapeHtml(match[2]);
      const eq = escapeHtml(match[3]);
      const val = match[4];
      return `${indent}<span class="tok-key">${key}</span>${eq}${this.highlightValue(val)}${commentPart}`;
    }
    return `${this.highlightValue(codePart)}${commentPart}`;
  }

  highlightJsonLine(line) {
    const match = line.match(/^(\s*)"([^"]+)"(\s*:\s*)(.*)$/);
    if (match) {
      const indent = escapeHtml(match[1]);
      const key = escapeHtml(match[2]);
      const colon = escapeHtml(match[3]);
      const val = match[4];
      return `${indent}<span class="tok-key">"${key}"</span>${colon}${this.highlightValue(val)}`;
    }
    return this.highlightValue(line);
  }

  highlightGenericLine(line) {
    return this.highlightValue(line);
  }

  highlightValue(val) {
    if (!val) return '';
    const tokenRegex = /("(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*'|\b(?:true|false|null|yes|no|on|off)\b|\b-?\d+(?:\.\d+)?\b|[^\s"']+|\s+)/gi;
    let result = '';
    let m;
    while ((m = tokenRegex.exec(val)) !== null) {
      const token = m[0];
      if ((token.startsWith('"') && token.endsWith('"')) || (token.startsWith("'") && token.endsWith("'"))) {
        result += `<span class="tok-string">${escapeHtml(token)}</span>`;
      } else if (/^(true|false|null|yes|no|on|off)$/i.test(token)) {
        result += `<span class="tok-bool">${escapeHtml(token)}</span>`;
      } else if (/^-?\d+(?:\.\d+)?$/.test(token)) {
        result += `<span class="tok-number">${escapeHtml(token)}</span>`;
      } else {
        result += escapeHtml(token);
      }
    }
    return result;
  }

  openSearch() {
    if (this.searchBar) {
      this.searchBar.style.display = 'flex';
      this.searchInput.focus();
      this.searchInput.select();
    }
  }

  closeSearch() {
    if (this.searchBar) {
      this.searchBar.style.display = 'none';
      this.searchQuery = '';
      this.searchMatches = [];
      this.currentMatchIndex = -1;
      this.syncContent();
      this.textarea.focus();
    }
  }

  toggleSearch() {
    if (this.searchBar.style.display === 'none') {
      this.openSearch();
    } else {
      this.closeSearch();
    }
  }

  findMatches() {
    if (!this.searchQuery) {
      this.searchMatches = [];
      this.currentMatchIndex = -1;
      if (this.searchCount) this.searchCount.innerText = '0/0';
      this.syncContent();
      return;
    }

    const val = this.textarea.value.toLowerCase();
    const q = this.searchQuery.toLowerCase();
    const matches = [];
    let pos = 0;
    while ((pos = val.indexOf(q, pos)) !== -1) {
      matches.push(pos);
      pos += q.length;
    }

    this.searchMatches = matches;
    if (matches.length > 0) {
      this.currentMatchIndex = 0;
      this.jumpToMatch(0, false);
    } else {
      this.currentMatchIndex = -1;
      if (this.searchCount) this.searchCount.innerText = '0/0';
      this.syncContent();
    }
  }

  nextMatch(shouldFocus = false) {
    if (this.searchMatches.length === 0) return;
    this.currentMatchIndex = (this.currentMatchIndex + 1) % this.searchMatches.length;
    this.jumpToMatch(this.currentMatchIndex, shouldFocus);
  }

  prevMatch(shouldFocus = false) {
    if (this.searchMatches.length === 0) return;
    this.currentMatchIndex = (this.currentMatchIndex - 1 + this.searchMatches.length) % this.searchMatches.length;
    this.jumpToMatch(this.currentMatchIndex, shouldFocus);
  }

  jumpToMatch(index, shouldFocus = false) {
    if (index < 0 || index >= this.searchMatches.length) return;
    this.currentMatchIndex = index;
    const start = this.searchMatches[index];
    const len = this.searchQuery.length;

    const isSearchFocused = (document.activeElement === this.searchInput);

    if (shouldFocus) {
      this.textarea.focus();
    }
    this.textarea.setSelectionRange(start, start + len);
    if (!shouldFocus && isSearchFocused && document.activeElement !== this.searchInput) {
      this.searchInput.focus();
    }
    this.updateCursorPos();

    const val = this.textarea.value;
    const lineNum = val.substring(0, start).split('\n').length;
    const lineHeight = 20;
    const targetScrollTop = Math.max(0, (lineNum - 5) * lineHeight);
    this.textarea.scrollTop = targetScrollTop;
    this.syncScroll();

    if (this.searchCount) {
      this.searchCount.innerText = `${index + 1}/${this.searchMatches.length}`;
    }

    this.syncContent();
  }

  applySearchHighlight() {
    if (!this.searchQuery || this.searchQuery.length === 0) return;
    const qLower = this.searchQuery.toLowerCase();
    const walker = document.createTreeWalker(this.highlightCode, NodeFilter.SHOW_TEXT, null);
    const textNodes = [];
    let node;
    while ((node = walker.nextNode())) {
      textNodes.push(node);
    }

    let matchIdx = 0;
    for (const textNode of textNodes) {
      const text = textNode.nodeValue;
      if (!text) continue;
      const lower = text.toLowerCase();
      let idx = lower.indexOf(qLower);
      if (idx === -1) continue;

      const frag = document.createDocumentFragment();
      let lastIdx = 0;
      while (idx !== -1) {
        if (idx > lastIdx) {
          frag.appendChild(document.createTextNode(text.substring(lastIdx, idx)));
        }
        const mark = document.createElement('mark');
        const isActive = (matchIdx === this.currentMatchIndex);
        mark.className = isActive ? 'tok-search tok-search-active' : 'tok-search';
        mark.textContent = text.substring(idx, idx + this.searchQuery.length);
        frag.appendChild(mark);
        matchIdx++;

        lastIdx = idx + this.searchQuery.length;
        idx = lower.indexOf(qLower, lastIdx);
      }
      if (lastIdx < text.length) {
        frag.appendChild(document.createTextNode(text.substring(lastIdx)));
      }
      textNode.parentNode?.replaceChild(frag, textNode);
    }
  }

  toggleFullscreen() {
    this.isFullscreen = !this.isFullscreen;
    const modalBox = this.container.closest('.modal-card');
    if (modalBox) {
      modalBox.classList.toggle('editor-modal-fullscreen', this.isFullscreen);
    }
    this.root.classList.toggle('is-fullscreen', this.isFullscreen);
  }

  setValue(val) {
    this.textarea.value = val || '';
    this.syncContent();
  }

  getValue() {
    return this.textarea.value;
  }
}
