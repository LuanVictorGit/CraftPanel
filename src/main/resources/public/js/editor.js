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
              <button type="button" class="editor-tool-btn" id="editor-search-prev" title="Previous (Shift+F3)">▲</button>
              <button type="button" class="editor-tool-btn" id="editor-search-next" title="Next (F3)">▼</button>
              <button type="button" class="editor-tool-btn" id="editor-search-close" title="Close Search (Esc)">✕</button>
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
        if (e.shiftKey) this.prevMatch();
        else this.nextMatch();
      } else if (e.key === 'Escape') {
        this.closeSearch();
      }
    });

    this.container.querySelector('#editor-search-next')?.addEventListener('click', () => this.nextMatch());
    this.container.querySelector('#editor-search-prev')?.addEventListener('click', () => this.prevMatch());

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

      // Comment detection
      if (trimmed.startsWith('#') || trimmed.startsWith('//') || trimmed.startsWith(';')) {
        return `<span class="tok-comment">${escapeHtml(line)}</span>`;
      }

      // Section header detection (e.g. [header])
      if (trimmed.startsWith('[') && trimmed.endsWith(']')) {
        return `<span class="tok-section">${escapeHtml(line)}</span>`;
      }

      let parsed = escapeHtml(line);

      if (lang === 'yaml') {
        parsed = parsed.replace(/^(\s*)([a-zA-Z0-9_\-\.\/]+)(:)(\s*)/, (m, indent, key, colon, space) => {
          return `${indent}<span class="tok-key">${key}</span>${colon}${space}`;
        });
      } else if (lang === 'properties') {
        parsed = parsed.replace(/^(\s*)([a-zA-Z0-9_\-\.]+)(\s*=)(\s*)/, (m, indent, key, eq, space) => {
          return `${indent}<span class="tok-key">${key}</span>${eq}${space}`;
        });
      } else if (lang === 'json') {
        parsed = parsed.replace(/"([^"]+)"(\s*:)/g, '<span class="tok-key">"$1"</span>$2');
      }

      // Strings in quotes
      parsed = parsed.replace(/(['"])(.*?)\1/g, '<span class="tok-string">$1$2$1</span>');

      // Booleans & null
      parsed = parsed.replace(/\b(true|false|null|yes|no|on|off)\b/gi, '<span class="tok-bool">$1</span>');

      // Numbers
      parsed = parsed.replace(/\b(-?\d+(?:\.\d+)?)\b/g, '<span class="tok-number">$1</span>');

      // Search highlights if any
      if (this.searchQuery && this.searchQuery.length > 0) {
        const qEsc = escapeHtml(this.searchQuery);
        const reg = new RegExp(`(${qEsc.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')})`, 'gi');
        parsed = parsed.replace(reg, '<mark class="tok-search">$1</mark>');
      }

      return parsed;
    });

    return formattedLines.join('\n');
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
      this.jumpToMatch(0);
    } else {
      this.currentMatchIndex = -1;
    }

    if (this.searchCount) {
      this.searchCount.innerText = `${matches.length > 0 ? this.currentMatchIndex + 1 : 0}/${matches.length}`;
    }

    this.syncContent();
  }

  nextMatch() {
    if (this.searchMatches.length === 0) return;
    this.currentMatchIndex = (this.currentMatchIndex + 1) % this.searchMatches.length;
    this.jumpToMatch(this.currentMatchIndex);
  }

  prevMatch() {
    if (this.searchMatches.length === 0) return;
    this.currentMatchIndex = (this.currentMatchIndex - 1 + this.searchMatches.length) % this.searchMatches.length;
    this.jumpToMatch(this.currentMatchIndex);
  }

  jumpToMatch(index) {
    if (index < 0 || index >= this.searchMatches.length) return;
    const start = this.searchMatches[index];
    const len = this.searchQuery.length;
    this.textarea.focus();
    this.textarea.setSelectionRange(start, start + len);
    this.updateCursorPos();
    if (this.searchCount) {
      this.searchCount.innerText = `${index + 1}/${this.searchMatches.length}`;
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
