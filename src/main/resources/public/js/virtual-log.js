// Scrolling log that only keeps the rows in view in the DOM, so a console with thousands of lines stays responsive.
// Row heights come from character counts, since the font is monospaced, and are corrected once a row is measured.

const OVERSCAN_PX = 400;
const BOTTOM_TOLERANCE_PX = 24;

const FORMATTING = /\u001b\[[0-9;?]*[a-zA-Z]|§[0-9a-fk-or]/gi;

export function stripFormatting(raw) {
  return String(raw ?? '').replace(FORMATTING, '');
}

export function estimateRows(textLength, columns) {
  if (textLength <= 0 || columns <= 0) return 1;
  return Math.max(1, Math.ceil(textLength / columns));
}

/** Index of the last offset that is <= position. `offsets` is ascending and starts at 0. */
export function findOffsetIndex(offsets, count, position) {
  let low = 0;
  let high = Math.max(0, count - 1);
  while (low < high) {
    const mid = Math.ceil((low + high) / 2);
    if (offsets[mid] <= position) {
      low = mid;
    } else {
      high = mid - 1;
    }
  }
  return low;
}

export class VirtualLog {
  /**
   * @param container the scrolling element
   * @param options.renderRow (item) => HTMLElement, called only for rows entering the view
   * @param options.maxItems lines to keep; the oldest are dropped in batches
   * @param options.onStickChange (isFollowing, unseenCount) => void
   */
  constructor(container, { renderRow, maxItems = 20000, onStickChange = null }) {
    this.container = container;
    this.renderRow = renderRow;
    this.maxItems = maxItems;
    this.onStickChange = onStickChange;

    this.items = [];
    this.nextId = 0;
    this.filterText = '';
    this.visible = [];   // items passing the filter
    this.offsets = [];   // top of each visible item, in px
    this.offsetsValidUpTo = 0;
    this.totalHeight = 0;

    this.autoFollow = true;
    this.following = true;
    this.unseen = 0;
    this.pointerDown = false;
    this.frame = 0;
    this.rendered = new Map();
    this.ownScrollTop = -1;

    this.topSpacer = document.createElement('div');
    this.viewport = document.createElement('div');
    this.bottomSpacer = document.createElement('div');
    container.replaceChildren(this.topSpacer, this.viewport, this.bottomSpacer);
    container.style.overflowAnchor = 'none';

    this.measureFont();

    this.onScroll = () => {
      // A scroll this class caused lands exactly where it left the position; anything else came from the user.
      if (Math.abs(this.container.scrollTop - this.ownScrollTop) > 1) {
        this.setFollowing(this.isAtBottom());
      }
      this.schedule();
    };
    this.onPointerDown = () => { this.pointerDown = true; };
    this.onPointerUp = () => {
      if (this.pointerDown) {
        this.pointerDown = false;
        this.schedule();
      }
    };

    container.addEventListener('scroll', this.onScroll, { passive: true });
    container.addEventListener('pointerdown', this.onPointerDown);
    window.addEventListener('pointerup', this.onPointerUp);

    this.resizeObserver = new ResizeObserver(() => {
      this.measureFont();
      this.invalidateHeights();
      this.schedule();
    });
    this.resizeObserver.observe(container);
  }

  destroy() {
    if (this.frame) cancelAnimationFrame(this.frame);
    this.frame = 0;
    this.resizeObserver.disconnect();
    this.container.removeEventListener('scroll', this.onScroll);
    this.container.removeEventListener('pointerdown', this.onPointerDown);
    window.removeEventListener('pointerup', this.onPointerUp);
  }

  /** Lines are plain objects with at least { raw }. The DOM is only touched on the next frame. */
  append(lines) {
    for (const line of lines) {
      const item = this.prepare(line);
      this.items.push(item);
      if (this.matches(item)) {
        this.pushVisible(item);
        if (!this.following) this.unseen++;
      }
    }
    if (this.items.length > this.maxItems + 2000) {
      this.items = this.items.slice(this.items.length - this.maxItems);
      this.rebuildVisible();
    }
    if (!this.following && this.onStickChange) {
      this.onStickChange(false, this.unseen);
    }
    this.schedule();
  }

  replaceAll(lines) {
    this.items = [];
    this.clearRendered();
    this.unseen = 0;
    this.setFollowing(this.autoFollow);
    this.visible = [];
    this.offsets = [];
    this.offsetsValidUpTo = 0;
    this.totalHeight = 0;
    this.append(lines);
  }

  setFilter(text) {
    this.filterText = (text || '').toLowerCase();
    this.rebuildVisible();
    this.clearRendered();
    this.schedule();
  }

  setAutoFollow(enabled) {
    this.autoFollow = enabled;
    this.setFollowing(enabled && this.isAtBottom());
    if (enabled) this.scrollToEnd();
  }

  scrollToEnd() {
    this.setFollowing(this.autoFollow);
    this.unseen = 0;
    if (this.onStickChange) this.onStickChange(this.following, 0);
    this.scrollToBottomNow();
    this.schedule();
  }

  prepare(line) {
    const plain = stripFormatting(line.raw);
    return { ...line, id: this.nextId++, plain, lower: plain.toLowerCase(), height: 0 };
  }

  matches(item) {
    return !this.filterText || item.lower.includes(this.filterText);
  }

  heightOf(item) {
    return item.height || estimateRows(item.plain.length, this.columns) * this.lineHeight;
  }

  pushVisible(item) {
    const index = this.visible.length;
    if (this.offsetsValidUpTo === index) {
      this.offsets[index] = this.totalHeight;
      this.totalHeight += this.heightOf(item);
      this.offsetsValidUpTo = index + 1;
    }
    this.visible.push(item);
  }

  rebuildVisible() {
    this.visible = [];
    this.offsets = [];
    this.offsetsValidUpTo = 0;
    this.totalHeight = 0;
    for (const item of this.items) {
      if (this.matches(item)) this.pushVisible(item);
    }
  }

  invalidateHeights() {
    for (const item of this.items) item.height = 0;
    this.offsetsValidUpTo = 0;
  }

  ensureOffsets() {
    let top = this.offsetsValidUpTo > 0
      ? this.offsets[this.offsetsValidUpTo - 1] + this.heightOf(this.visible[this.offsetsValidUpTo - 1])
      : 0;
    for (let i = this.offsetsValidUpTo; i < this.visible.length; i++) {
      this.offsets[i] = top;
      top += this.heightOf(this.visible[i]);
    }
    this.offsets.length = this.visible.length;
    this.offsetsValidUpTo = this.visible.length;
    this.totalHeight = top;
  }

  measureFont() {
    const style = getComputedStyle(this.container);
    this.paddingY = (parseFloat(style.paddingTop) || 0) + (parseFloat(style.paddingBottom) || 0);
    const paddingX = (parseFloat(style.paddingLeft) || 0) + (parseFloat(style.paddingRight) || 0);

    const probe = document.createElement('div');
    probe.className = 'console-line';
    probe.style.cssText = 'position:absolute;visibility:hidden;white-space:pre;';
    probe.textContent = 'M'.repeat(100);
    this.container.appendChild(probe);
    const rect = probe.getBoundingClientRect();
    probe.remove();

    this.lineHeight = rect.height || 19;
    const charWidth = rect.width > 0 ? rect.width / 100 : 7.2;
    this.columns = Math.max(10, Math.floor((this.container.clientWidth - paddingX) / charWidth));
  }

  isAtBottom() {
    const { scrollTop, scrollHeight, clientHeight } = this.container;
    return scrollHeight - scrollTop - clientHeight <= BOTTOM_TOLERANCE_PX;
  }

  setFollowing(following) {
    const next = following && this.autoFollow;
    if (next !== this.following) {
      this.following = next;
      if (next) this.unseen = 0;
      if (this.onStickChange) this.onStickChange(next, this.unseen);
    }
  }

  schedule() {
    if (!this.frame) {
      this.frame = requestAnimationFrame(() => {
        this.frame = 0;
        this.render();
      });
    }
  }

  clearRendered() {
    this.rendered.clear();
    this.viewport.replaceChildren();
  }

  hasSelectionInside() {
    const selection = window.getSelection();
    return !!selection && !selection.isCollapsed && this.container.contains(selection.anchorNode);
  }

  render() {
    // Replacing rows under the cursor would end a text selection in progress.
    if (this.pointerDown) return;

    this.ensureOffsets();
    const selecting = this.hasSelectionInside();
    const follow = this.following && !selecting;
    const height = this.container.clientHeight;

    let scrollTop = this.container.scrollTop;
    if (follow) {
      scrollTop = Math.max(0, this.totalHeight + this.paddingY - height);
    }

    const count = this.visible.length;
    const first = count ? findOffsetIndex(this.offsets, count, Math.max(0, scrollTop - OVERSCAN_PX)) : 0;
    const last = count ? findOffsetIndex(this.offsets, count, scrollTop + height + OVERSCAN_PX) : -1;

    const wanted = new Set();
    for (let i = first; i <= last; i++) wanted.add(this.visible[i].id);
    for (const [id, element] of this.rendered) {
      if (!wanted.has(id)) {
        element.remove();
        this.rendered.delete(id);
      }
    }

    let cursor = this.viewport.firstChild;
    for (let i = first; i <= last; i++) {
      const item = this.visible[i];
      let element = this.rendered.get(item.id);
      if (!element) {
        element = this.renderRow(item);
        this.rendered.set(item.id, element);
      }
      if (element === cursor) {
        cursor = cursor.nextSibling;
      } else {
        this.viewport.insertBefore(element, cursor);
      }
    }

    // Estimates can miss on word wrap and wide characters; what is on screen corrects them.
    let corrected = -1;
    for (let i = first; i <= last; i++) {
      const item = this.visible[i];
      const measured = this.rendered.get(item.id).offsetHeight;
      if (measured > 0 && measured !== this.heightOf(item)) {
        item.height = measured;
        if (corrected < 0) corrected = i;
      } else if (measured > 0) {
        item.height = measured;
      }
    }
    if (corrected >= 0) {
      this.offsetsValidUpTo = Math.min(this.offsetsValidUpTo, corrected);
      this.ensureOffsets();
    }

    const topHeight = count ? this.offsets[first] : 0;
    const bottomStart = last >= 0 ? this.offsets[last] + this.heightOf(this.visible[last]) : 0;
    this.topSpacer.style.height = `${topHeight}px`;
    this.bottomSpacer.style.height = `${Math.max(0, this.totalHeight - bottomStart)}px`;

    if (follow && Math.abs(this.container.scrollTop - (this.container.scrollHeight - height)) > 1) {
      this.scrollToBottomNow();
    }
  }

  scrollToBottomNow() {
    this.container.scrollTop = this.container.scrollHeight;
    this.ownScrollTop = this.container.scrollTop;
  }
}
