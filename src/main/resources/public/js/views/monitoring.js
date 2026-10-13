import { api } from '../api.js';
import { toast, formatBytes, formatUptime, escapeHtml } from '../ui.js';
import { i18n } from '../i18n.js';
import { PanelWebSocket } from '../websocket.js';
import { icons } from '../icons.js';

let activeWs = null;
let fallbackInterval = null;
let currentCategory = 'overview';
let localHistory = [];

export async function renderMonitoring(root, state) {
  const serverId = state.activeServerId || 'local';

  if (activeWs) {
    activeWs.close();
    activeWs = null;
  }
  if (fallbackInterval) {
    clearInterval(fallbackInterval);
    fallbackInterval = null;
  }

  localHistory = [];

  root.innerHTML = `
    <div style="display:flex;flex-direction:column;gap:18px;">
      <!-- Header -->
      <div style="display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:12px;">
        <div>
          <h3 style="font-size:18px;font-weight:700;color:var(--text-main);">${i18n.t('monitoring')}</h3>
          <p style="font-size:13px;color:var(--text-muted);">${i18n.t('sparkTelemetry')}</p>
        </div>

        <div style="display:flex;align-items:center;gap:10px;flex-wrap:wrap;">
          <span class="badge badge-amber" id="mon-ws-badge" style="display:inline-flex;align-items:center;gap:6px;font-size:11px;font-weight:600;">
            <span class="status-dot stopped" id="mon-ws-dot"></span>
            <span id="mon-ws-text">CONNECTING...</span>
          </span>
          <span class="badge badge-blue" id="mon-server-badge" style="font-size:11px;">Server</span>
          <span class="badge badge-gray" id="mon-uptime-badge" style="font-size:11px;">Uptime: -</span>
        </div>
      </div>

      <!-- Category Navigation Pills -->
      <div class="mon-nav-pills">
        <button class="mon-pill ${currentCategory === 'overview' ? 'active' : ''}" data-cat="overview">
          ${icons.chart(14)}
          <span>${i18n.t('overview')}</span>
        </button>
        <button class="mon-pill ${currentCategory === 'worlds' ? 'active' : ''}" data-cat="worlds">
          ${icons.map(14)}
          <span>${i18n.t('worldsAndChunks')}</span>
        </button>
        <button class="mon-pill ${currentCategory === 'plugins' ? 'active' : ''}" data-cat="plugins">
          ${icons.plugin(14)}
          <span>${i18n.t('pluginsProfiler')}</span>
        </button>
        <button class="mon-pill ${currentCategory === 'jvm' ? 'active' : ''}" data-cat="jvm">
          ${icons.settings(14)}
          <span>${i18n.t('jvmAndSystem')}</span>
        </button>
      </div>

      <!-- =============================================================== -->
      <!-- SECTION 1: OVERVIEW & 1-HOUR HISTORY GRAPHS                     -->
      <!-- =============================================================== -->
      <div id="mon-cat-overview" class="mon-cat-section" style="display:${currentCategory === 'overview' ? 'flex' : 'none'};flex-direction:column;gap:16px;">
        <!-- TPS & MSPT Current Gauges -->
        <div class="card" style="padding:16px;">
          <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:12px;flex-wrap:wrap;gap:8px;">
            <div style="font-size:13.5px;font-weight:700;color:var(--text-main);display:flex;align-items:center;gap:8px;">
              ${icons.bolt(16)}
              <span>${i18n.t('tps')} &amp; ${i18n.t('mspt')}</span>
            </div>
            <span style="font-size:11px;color:var(--text-muted);">${i18n.t('msptTarget')}</span>
          </div>

          <!-- 3 TPS Gauges -->
          <div class="grid-cols-3" style="gap:12px;margin-bottom:14px;">
            <div style="background:var(--bg-subtle);padding:12px;border-radius:8px;border:1px solid var(--border);text-align:center;">
              <div style="font-size:11px;font-weight:600;color:var(--text-muted);margin-bottom:2px;">TPS (1 min)</div>
              <div style="font-size:24px;font-weight:800;color:var(--accent-emerald);" id="mon-tps-1m">20.00</div>
              <div style="font-size:10px;color:var(--text-muted);">Last 60 seconds</div>
            </div>
            <div style="background:var(--bg-subtle);padding:12px;border-radius:8px;border:1px solid var(--border);text-align:center;">
              <div style="font-size:11px;font-weight:600;color:var(--text-muted);margin-bottom:2px;">TPS (5 min)</div>
              <div style="font-size:24px;font-weight:800;color:var(--accent-emerald);" id="mon-tps-5m">20.00</div>
              <div style="font-size:10px;color:var(--text-muted);">Last 5 minutes</div>
            </div>
            <div style="background:var(--bg-subtle);padding:12px;border-radius:8px;border:1px solid var(--border);text-align:center;">
              <div style="font-size:11px;font-weight:600;color:var(--text-muted);margin-bottom:2px;">TPS (15 min)</div>
              <div style="font-size:24px;font-weight:800;color:var(--accent-emerald);" id="mon-tps-15m">20.00</div>
              <div style="font-size:10px;color:var(--text-muted);">Last 15 minutes</div>
            </div>
          </div>

          <!-- MSPT Bar -->
          <div style="background:var(--bg-subtle);padding:12px;border-radius:8px;border:1px solid var(--border);">
            <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:8px;flex-wrap:wrap;gap:8px;">
              <div style="display:flex;align-items:center;gap:8px;">
                <span style="font-size:12px;font-weight:600;color:var(--text-main);">${i18n.t('mspt')} Current:</span>
                <span style="font-size:16px;font-weight:800;color:var(--accent-emerald);" id="mon-mspt-curr">0.0 ms</span>
              </div>
              <div style="display:flex;gap:12px;font-size:11px;color:var(--text-muted);flex-wrap:wrap;">
                <span>Min: <strong style="color:var(--text-main);" id="mon-mspt-min">0.0 ms</strong></span>
                <span>Median: <strong style="color:var(--text-main);" id="mon-mspt-med">0.0 ms</strong></span>
                <span>95th %: <strong style="color:var(--text-main);" id="mon-mspt-95p">0.0 ms</strong></span>
                <span>Max: <strong style="color:var(--text-main);" id="mon-mspt-max">0.0 ms</strong></span>
              </div>
            </div>
            <div style="position:relative;width:100%;height:8px;background:var(--bg-page);border-radius:4px;overflow:hidden;border:1px solid var(--border);">
              <div id="mon-mspt-bar" style="height:100%;width:0%;background:var(--accent-emerald);border-radius:3px;transition:width 0.3s ease;"></div>
            </div>
          </div>
        </div>

        <!-- 1-HOUR HISTORY GRAPHS -->
        <div class="grid-cols-2" style="gap:16px;">
          <!-- Graph 1: CPU Usage History -->
          <div class="chart-card">
            <div class="chart-header">
              <div class="chart-title-wrap">
                ${icons.cpu(16)}
                <span>${i18n.t('cpuHistory')}</span>
              </div>
              <div class="chart-legend">
                <div class="chart-legend-item">
                  <span class="chart-legend-dot" style="background:#3B82F6;"></span>
                  <span>Process: <strong id="mon-proc-cpu-val" style="color:var(--text-main);">0.0%</strong></span>
                </div>
                <div class="chart-legend-item">
                  <span class="chart-legend-dot" style="background:#06B6D4;"></span>
                  <span>System: <strong id="mon-sys-cpu-val" style="color:var(--text-main);">0.0%</strong></span>
                </div>
              </div>
            </div>
            <div class="chart-canvas-wrap">
              <canvas id="chart-cpu-history" class="chart-canvas"></canvas>
              <div id="tooltip-cpu-history" class="chart-tooltip"></div>
            </div>
          </div>

          <!-- Graph 2: Memory Usage History -->
          <div class="chart-card">
            <div class="chart-header">
              <div class="chart-title-wrap">
                ${icons.ram(16)}
                <span>${i18n.t('memoryHistory')}</span>
              </div>
              <div class="chart-legend">
                <div class="chart-legend-item">
                  <span class="chart-legend-dot" style="background:#10B981;"></span>
                  <span>JVM Heap: <strong id="mon-heap-pct-val" style="color:var(--text-main);">0%</strong></span>
                </div>
                <div class="chart-legend-item">
                  <span class="chart-legend-dot" style="background:#3B82F6;"></span>
                  <span>Physical RAM: <strong id="mon-phys-pct-val" style="color:var(--text-main);">0%</strong></span>
                </div>
              </div>
            </div>
            <div class="chart-canvas-wrap">
              <canvas id="chart-mem-history" class="chart-canvas"></canvas>
              <div id="tooltip-mem-history" class="chart-tooltip"></div>
            </div>
          </div>
        </div>

        <!-- Graph 3: Performance History (TPS & MSPT) -->
        <div class="chart-card">
          <div class="chart-header">
            <div class="chart-title-wrap">
              ${icons.chartLine(16)}
              <span>${i18n.t('perfHistory')}</span>
            </div>
            <div class="chart-legend">
              <div class="chart-legend-item">
                <span class="chart-legend-dot" style="background:#10B981;"></span>
                <span>TPS: <strong id="mon-perf-tps-val" style="color:var(--text-main);">20.0</strong></span>
              </div>
              <div class="chart-legend-item">
                <span class="chart-legend-dot" style="background:#F59E0B;"></span>
                <span>MSPT: <strong id="mon-perf-mspt-val" style="color:var(--text-main);">0.0 ms</strong></span>
              </div>
            </div>
          </div>
          <div class="chart-canvas-wrap">
            <canvas id="chart-perf-history" class="chart-canvas"></canvas>
            <div id="tooltip-perf-history" class="chart-tooltip"></div>
          </div>
        </div>
      </div>

      <!-- =============================================================== -->
      <!-- SECTION 2: WORLDS & CHUNKS TELEMETRY                            -->
      <!-- =============================================================== -->
      <div id="mon-cat-worlds" class="mon-cat-section" style="display:${currentCategory === 'worlds' ? 'flex' : 'none'};flex-direction:column;gap:16px;">
        <div class="card" style="padding:18px;">
          <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:14px;flex-wrap:wrap;gap:8px;">
            <div style="font-size:14px;font-weight:700;color:var(--text-main);display:flex;align-items:center;gap:8px;">
              ${icons.map(16)}
              <span>${i18n.t('loadedWorlds')}</span>
            </div>

            <div style="display:flex;gap:8px;flex-wrap:wrap;">
              <span class="badge badge-blue" id="mon-worlds-chunks-badge">Chunks: 0</span>
              <span class="badge badge-green" id="mon-worlds-entities-badge">Entities: 0</span>
              <span class="badge badge-purple" id="mon-worlds-players-badge">Players: 0</span>
            </div>
          </div>

          <div id="mon-worlds-grid" style="display:grid;grid-template-columns:repeat(auto-fill, minmax(290px, 1fr));gap:14px;">
            <div style="text-align:center;padding:24px;color:var(--text-muted);">${i18n.t('loading')}</div>
          </div>
        </div>
      </div>

      <!-- =============================================================== -->
      <!-- SECTION 3: PLUGINS & SPARK CPU PROFILER                         -->
      <!-- =============================================================== -->
      <div id="mon-cat-plugins" class="mon-cat-section" style="display:${currentCategory === 'plugins' ? 'flex' : 'none'};flex-direction:column;gap:16px;">
        <div class="card" style="padding:18px;">
          <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:14px;flex-wrap:wrap;gap:8px;">
            <div>
              <div style="font-size:14px;font-weight:700;color:var(--text-main);display:flex;align-items:center;gap:8px;">
                ${icons.plugin(16)}
                <span>${i18n.t('pluginsProfiler')}</span>
              </div>
              <p style="font-size:12px;color:var(--text-muted);margin-top:2px;">Real-time JVM sampling of thread execution per plugin and heaviest active classes (Spark standard)</p>
            </div>
            <span class="badge badge-green" id="mon-plugins-badge">0 Active</span>
          </div>

          <div class="table-wrap">
            <table class="data-table">
              <thead>
                <tr>
                  <th style="width:24%;">${i18n.t('plugin')}</th>
                  <th style="width:12%;">${i18n.t('version')}</th>
                  <th style="width:12%;">${i18n.t('status')}</th>
                  <th style="width:24%;">${i18n.t('cpuUsage')}</th>
                  <th style="width:28%;">${i18n.t('heaviestClass')}</th>
                </tr>
              </thead>
              <tbody id="mon-plugins-tbody">
                <tr>
                  <td colspan="5" style="text-align:center;padding:24px;color:var(--text-muted);">${i18n.t('loading')}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <!-- =============================================================== -->
      <!-- SECTION 4: JVM & SYSTEM INTERNALS                               -->
      <!-- =============================================================== -->
      <div id="mon-cat-jvm" class="mon-cat-section" style="display:${currentCategory === 'jvm' ? 'flex' : 'none'};flex-direction:column;gap:16px;">
        <!-- Memory Details -->
        <div class="card" style="padding:18px;">
          <div style="font-size:14px;font-weight:700;color:var(--text-main);margin-bottom:14px;display:flex;align-items:center;gap:8px;">
            ${icons.ram(16)}
            <span>${i18n.t('heapMemory')}, ${i18n.t('nonHeapMemory')} &amp; ${i18n.t('hostRam')}</span>
          </div>

          <div class="grid-cols-3" style="gap:14px;margin-bottom:16px;">
            <div style="background:var(--bg-subtle);padding:14px;border-radius:8px;border:1px solid var(--border);">
              <div style="display:flex;justify-content:space-between;align-items:center;">
                <span style="font-size:11px;font-weight:600;color:var(--text-muted);">${i18n.t('heapMemory')}</span>
                <span style="font-size:11px;font-weight:700;color:var(--text-main);" id="mon-heap-pct">0%</span>
              </div>
              <div style="font-size:20px;font-weight:800;color:var(--text-main);margin:4px 0;" id="mon-heap-used">0 / 0 MB</div>
              <div style="width:100%;height:6px;background:var(--bg-page);border-radius:3px;overflow:hidden;margin-bottom:6px;">
                <div id="mon-heap-bar" style="height:100%;width:0%;background:var(--accent-emerald);transition:width 0.3s ease;"></div>
              </div>
              <div style="font-size:11px;color:var(--text-muted);" id="mon-heap-comm">Committed: 0 MB</div>
            </div>

            <div style="background:var(--bg-subtle);padding:14px;border-radius:8px;border:1px solid var(--border);">
              <span style="font-size:11px;font-weight:600;color:var(--text-muted);">${i18n.t('nonHeapMemory')}</span>
              <div style="font-size:20px;font-weight:800;color:var(--text-main);margin:4px 0;" id="mon-nonheap-used">0 MB</div>
              <div style="font-size:11px;color:var(--text-muted);margin-top:10px;">Metaspace &amp; JVM Internals</div>
            </div>

            <div style="background:var(--bg-subtle);padding:14px;border-radius:8px;border:1px solid var(--border);">
              <div style="display:flex;justify-content:space-between;align-items:center;">
                <span style="font-size:11px;font-weight:600;color:var(--text-muted);">${i18n.t('hostRam')}</span>
                <span style="font-size:11px;font-weight:700;color:var(--text-main);" id="mon-host-ram-pct">0%</span>
              </div>
              <div style="font-size:20px;font-weight:800;color:var(--text-main);margin:4px 0;" id="mon-phys-ram">0 / 0 GB</div>
              <div style="width:100%;height:6px;background:var(--bg-page);border-radius:3px;overflow:hidden;margin-bottom:6px;">
                <div id="mon-phys-ram-bar" style="height:100%;width:0%;background:var(--accent-blue);transition:width 0.3s ease;"></div>
              </div>
              <div style="font-size:11px;color:var(--text-muted);" id="mon-phys-free">Free: 0 GB</div>
            </div>
          </div>

          <!-- Garbage Collection -->
          <div style="background:var(--bg-subtle);padding:12px 14px;border-radius:8px;border:1px solid var(--border);">
            <div style="font-size:12px;font-weight:600;color:var(--text-main);margin-bottom:8px;">${i18n.t('garbageCollection')}</div>
            <div id="mon-gc-container" style="display:flex;flex-wrap:wrap;gap:12px;font-size:12px;color:var(--text-muted);">
              <div>No GC collectors data yet</div>
            </div>
          </div>
        </div>

        <!-- Hardware & Threads -->
        <div class="card" style="padding:18px;">
          <div style="font-size:14px;font-weight:700;color:var(--text-main);margin-bottom:14px;display:flex;align-items:center;gap:8px;">
            ${icons.cpu(16)}
            <span>Hardware &amp; JVM Threads</span>
          </div>

          <div class="grid-cols-4" style="gap:12px;">
            <div style="background:var(--bg-subtle);padding:14px;border-radius:8px;border:1px solid var(--border);">
              <div style="font-size:11px;font-weight:600;color:var(--text-muted);">${i18n.t('cores')}</div>
              <div style="font-size:22px;font-weight:800;color:var(--text-main);margin:4px 0;" id="mon-cores">0</div>
              <div style="font-size:11px;color:var(--text-muted);">Available Processors</div>
            </div>

            <div style="background:var(--bg-subtle);padding:14px;border-radius:8px;border:1px solid var(--border);">
              <div style="font-size:11px;font-weight:600;color:var(--text-muted);">${i18n.t('threads')}</div>
              <div style="font-size:22px;font-weight:800;color:var(--text-main);margin:4px 0;" id="mon-threads">0</div>
              <div style="font-size:11px;color:var(--text-muted);" id="mon-peak-threads">Peak: 0</div>
            </div>

            <div style="background:var(--bg-subtle);padding:14px;border-radius:8px;border:1px solid var(--border);">
              <div style="font-size:11px;font-weight:600;color:var(--text-muted);">${i18n.t('javaVersion')}</div>
              <div style="font-size:15px;font-weight:700;color:var(--text-main);margin:4px 0;" id="mon-java-ver">-</div>
              <div style="font-size:11px;color:var(--text-muted);" id="mon-java-vendor">-</div>
            </div>

            <div style="background:var(--bg-subtle);padding:14px;border-radius:8px;border:1px solid var(--border);">
              <div style="font-size:11px;font-weight:600;color:var(--text-muted);">OS Platform</div>
              <div style="font-size:15px;font-weight:700;color:var(--text-main);margin:4px 0;" id="mon-os-name">-</div>
              <div style="font-size:11px;color:var(--text-muted);">Operating System</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  `;

  // Category pill tab switching
  root.querySelectorAll('.mon-pill').forEach(btn => {
    btn.addEventListener('click', () => {
      const cat = btn.dataset.cat;
      if (!cat) return;
      currentCategory = cat;

      root.querySelectorAll('.mon-pill').forEach(b => {
        b.classList.toggle('active', b.dataset.cat === cat);
      });

      root.querySelectorAll('.mon-cat-section').forEach(sec => {
        sec.style.display = (sec.id === `mon-cat-${cat}`) ? 'flex' : 'none';
      });

      if (cat === 'overview') {
        drawAllCharts();
      }
    });
  });

  function drawAllCharts() {
    if (localHistory.length < 1) return;

    // Chart 1: CPU History (Process vs System %)
    drawCanvasChart(
      'chart-cpu-history',
      'tooltip-cpu-history',
      [
        { name: 'Process CPU', data: localHistory.map(p => ({ timestamp: p.timestamp, value: p.processCpu })), strokeColor: '#3B82F6', fillColor: 'rgba(59, 130, 246, 0.22)' },
        { name: 'System CPU', data: localHistory.map(p => ({ timestamp: p.timestamp, value: p.systemCpu })), strokeColor: '#06B6D4', fillColor: 'rgba(6, 182, 212, 0.12)' }
      ],
      100,
      '%'
    );

    // Chart 2: Memory History (JVM Heap vs Physical RAM %)
    drawCanvasChart(
      'chart-mem-history',
      'tooltip-mem-history',
      [
        { name: 'JVM Heap', data: localHistory.map(p => ({ timestamp: p.timestamp, value: p.heapPercent })), strokeColor: '#10B981', fillColor: 'rgba(16, 185, 129, 0.22)' },
        { name: 'Physical RAM', data: localHistory.map(p => ({ timestamp: p.timestamp, value: p.physicalPercent })), strokeColor: '#3B82F6', fillColor: 'rgba(59, 130, 246, 0.12)' }
      ],
      100,
      '%'
    );

    // Chart 3: Performance History (TPS & MSPT)
    drawCanvasChart(
      'chart-perf-history',
      'tooltip-perf-history',
      [
        { name: 'TPS', data: localHistory.map(p => ({ timestamp: p.timestamp, value: (p.tps / 20.0) * 100 })), strokeColor: '#10B981', fillColor: 'rgba(16, 185, 129, 0.18)', customVal: p => `${p.tps.toFixed(2)} TPS` },
        { name: 'MSPT', data: localHistory.map(p => ({ timestamp: p.timestamp, value: Math.min((p.mspt / 50.0) * 100, 100) })), strokeColor: '#F59E0B', fillColor: 'rgba(245, 158, 11, 0.12)', customVal: p => `${p.mspt.toFixed(1)} ms` }
      ],
      100,
      '%'
    );
  }

  function drawCanvasChart(canvasId, tooltipId, seriesList, maxVal = 100, unit = '%') {
    const canvas = document.getElementById(canvasId);
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    const dpr = window.devicePixelRatio || 1;
    const rect = canvas.getBoundingClientRect();
    if (rect.width === 0 || rect.height === 0) return;

    canvas.width = rect.width * dpr;
    canvas.height = rect.height * dpr;
    ctx.scale(dpr, dpr);

    const w = rect.width;
    const h = rect.height;
    const padLeft = 38;
    const padRight = 14;
    const padTop = 14;
    const padBottom = 22;
    const plotW = w - padLeft - padRight;
    const plotH = h - padTop - padBottom;

    ctx.clearRect(0, 0, w, h);

    // Grid lines
    ctx.strokeStyle = 'rgba(51, 65, 85, 0.4)';
    ctx.lineWidth = 1;
    ctx.fillStyle = '#64748B';
    ctx.font = '10px ui-monospace, monospace';
    ctx.textAlign = 'right';
    ctx.textBaseline = 'middle';

    const steps = 4;
    for (let i = 0; i <= steps; i++) {
      const val = (maxVal / steps) * (steps - i);
      const y = padTop + (plotH / steps) * i;
      ctx.beginPath();
      ctx.moveTo(padLeft, y);
      ctx.lineTo(w - padRight, y);
      ctx.stroke();
      ctx.fillText(`${Math.round(val)}${unit}`, padLeft - 6, y);
    }

    // Time horizon: 1 hour (3600 seconds)
    const now = Date.now();
    const oneHourAgo = now - 3600 * 1000;

    ctx.textAlign = 'center';
    ctx.textBaseline = 'top';
    const markers = [
      { label: '-60m', pct: 0 },
      { label: '-45m', pct: 0.25 },
      { label: '-30m', pct: 0.50 },
      { label: '-15m', pct: 0.75 },
      { label: i18n.t('now') || 'Now', pct: 1.0 }
    ];
    for (const m of markers) {
      const x = padLeft + plotW * m.pct;
      ctx.fillText(m.label, x, h - padBottom + 5);
    }

    // Render each series
    for (const s of seriesList) {
      const data = s.data || [];
      if (data.length === 0) continue;

      const points = [];
      for (const pt of data) {
        const t = Math.max(pt.timestamp, oneHourAgo);
        const v = Math.min(Math.max(pt.value, 0), maxVal);
        const x = padLeft + ((t - oneHourAgo) / (3600 * 1000)) * plotW;
        const y = padTop + plotH - (v / maxVal) * plotH;
        points.push({ x: Math.max(padLeft, Math.min(x, w - padRight)), y, orig: pt });
      }

      if (points.length === 1) {
        points.unshift({ x: padLeft, y: points[0].y, orig: points[0].orig });
      }

      // Gradient area
      const grad = ctx.createLinearGradient(0, padTop, 0, padTop + plotH);
      grad.addColorStop(0, s.fillColor || 'rgba(59, 130, 246, 0.2)');
      grad.addColorStop(1, 'rgba(0, 0, 0, 0)');

      ctx.beginPath();
      ctx.moveTo(points[0].x, padTop + plotH);
      for (const p of points) ctx.lineTo(p.x, p.y);
      ctx.lineTo(points[points.length - 1].x, padTop + plotH);
      ctx.closePath();
      ctx.fillStyle = grad;
      ctx.fill();

      // Line
      ctx.beginPath();
      ctx.moveTo(points[0].x, points[0].y);
      for (let i = 1; i < points.length; i++) {
        ctx.lineTo(points[i].x, points[i].y);
      }
      ctx.strokeStyle = s.strokeColor || '#3B82F6';
      ctx.lineWidth = 2;
      ctx.stroke();

      // Current Dot
      const last = points[points.length - 1];
      ctx.beginPath();
      ctx.arc(last.x, last.y, 4, 0, Math.PI * 2);
      ctx.fillStyle = s.strokeColor || '#3B82F6';
      ctx.fill();
      ctx.strokeStyle = '#0F172A';
      ctx.lineWidth = 1.5;
      ctx.stroke();
    }
  }

  function applyTelemetry(data) {
    if (!data) return;

    // Header info
    if (data.server) {
      const elServer = document.getElementById('mon-server-badge');
      const elUptime = document.getElementById('mon-uptime-badge');
      if (elServer) {
        elServer.innerText = `${data.server.name} (${data.server.minecraftVersion || data.server.version || ''})`;
      }
      if (elUptime) {
        elUptime.innerText = `${i18n.t('uptime')}: ${formatUptime(data.server.uptimeSeconds)}`;
      }

      const elJavaVer = document.getElementById('mon-java-ver');
      const elJavaVendor = document.getElementById('mon-java-vendor');
      const elOs = document.getElementById('mon-os-name');
      if (elJavaVer) elJavaVer.innerText = data.server.javaVersion || '-';
      if (elJavaVendor) elJavaVendor.innerText = data.server.javaVendor || '-';
      if (elOs) elOs.innerText = data.server.osName || '-';
    }

    // TPS & MSPT
    if (data.tps) {
      const tps1 = data.tps.tps1m ?? 20;
      const tps5 = data.tps.tps5m ?? 20;
      const tps15 = data.tps.tps15m ?? 20;

      const setTpsEl = (id, val) => {
        const el = document.getElementById(id);
        if (el) {
          el.innerText = val.toFixed(2);
          el.style.color = val >= 19.5 ? 'var(--accent-emerald)' : (val >= 18.0 ? 'var(--accent-amber)' : 'var(--accent-rose)');
        }
      };

      setTpsEl('mon-tps-1m', tps1);
      setTpsEl('mon-tps-5m', tps5);
      setTpsEl('mon-tps-15m', tps15);

      const msptCurr = data.tps.msptCurrent ?? 0;
      const msptMin = data.tps.msptMin ?? (data.tps.msptMedian ? (data.tps.msptMedian * 0.7) : 0);
      const msptMed = data.tps.msptMedian ?? 0;
      const mspt95 = data.tps.mspt95th ?? data.tps.msptPercentile95 ?? 0;
      const msptMax = data.tps.msptMax ?? 0;

      const elCurr = document.getElementById('mon-mspt-curr');
      if (elCurr) {
        elCurr.innerText = `${msptCurr.toFixed(1)} ms`;
        elCurr.style.color = msptCurr <= 40 ? 'var(--accent-emerald)' : (msptCurr <= 50 ? 'var(--accent-amber)' : 'var(--accent-rose)');
      }

      const elMin = document.getElementById('mon-mspt-min');
      if (elMin) elMin.innerText = `${msptMin.toFixed(1)} ms`;
      const elMed = document.getElementById('mon-mspt-med');
      if (elMed) elMed.innerText = `${msptMed.toFixed(1)} ms`;
      const el95p = document.getElementById('mon-mspt-95p');
      if (el95p) el95p.innerText = `${mspt95.toFixed(1)} ms`;
      const elMax = document.getElementById('mon-mspt-max');
      if (elMax) elMax.innerText = `${msptMax.toFixed(1)} ms`;

      const barEl = document.getElementById('mon-mspt-bar');
      if (barEl) {
        const pct = Math.min(Math.max((msptCurr / 50.0) * 100, 0), 100);
        barEl.style.width = `${pct}%`;
        barEl.style.background = msptCurr <= 40 ? 'var(--accent-emerald)' : (msptCurr <= 50 ? 'var(--accent-amber)' : 'var(--accent-rose)');
      }

      const elPerfTps = document.getElementById('mon-perf-tps-val');
      if (elPerfTps) elPerfTps.innerText = `${tps1.toFixed(2)}`;
      const elPerfMspt = document.getElementById('mon-perf-mspt-val');
      if (elPerfMspt) elPerfMspt.innerText = `${msptCurr.toFixed(1)} ms`;
    }

    // CPU
    if (data.cpu) {
      const proc = data.cpu.processCpuPercent ?? 0;
      const sys = data.cpu.systemCpuPercent ?? 0;
      const cores = data.cpu.cores ?? data.cpu.availableProcessors ?? 1;
      const threads = data.cpu.threadCount ?? data.cpu.liveThreads ?? 0;
      const peak = data.cpu.peakThreads ?? threads;

      const elProcVal = document.getElementById('mon-proc-cpu-val');
      if (elProcVal) elProcVal.innerText = `${proc.toFixed(1)}%`;
      const elSysVal = document.getElementById('mon-sys-cpu-val');
      if (elSysVal) elSysVal.innerText = `${sys.toFixed(1)}%`;

      const elCores = document.getElementById('mon-cores');
      if (elCores) elCores.innerText = cores;
      const elThreads = document.getElementById('mon-threads');
      if (elThreads) elThreads.innerText = threads;
      const elPeak = document.getElementById('mon-peak-threads');
      if (elPeak) elPeak.innerText = peak > 0 ? `Peak: ${peak}` : `Threads: ${threads}`;
    }

    // Memory
    if (data.memory) {
      const mem = data.memory;
      let heapUsedStr = '';
      let heapPct = 0;
      let heapCommStr = '';

      if (mem.heapUsedMb !== undefined) {
        heapUsedStr = `${mem.heapUsedMb} MB / ${mem.heapMaxMb} MB`;
        heapPct = mem.heapPercent ?? (mem.heapMaxMb > 0 ? (mem.heapUsedMb * 100 / mem.heapMaxMb) : 0);
        heapCommStr = `Committed: ${mem.heapCommittedMb || 0} MB`;
      } else {
        const u = mem.heapUsedBytes || 0;
        const m = mem.heapMaxBytes || 1;
        heapUsedStr = `${formatBytes(u)} / ${formatBytes(m)}`;
        heapPct = m > 0 ? (u * 100 / m) : 0;
        heapCommStr = `Committed: ${formatBytes(mem.heapCommittedBytes || 0)}`;
      }

      const elHeapUsed = document.getElementById('mon-heap-used');
      const elHeapPct = document.getElementById('mon-heap-pct');
      const elHeapBar = document.getElementById('mon-heap-bar');
      const elHeapComm = document.getElementById('mon-heap-comm');
      const elHeapPctVal = document.getElementById('mon-heap-pct-val');

      if (elHeapUsed) elHeapUsed.innerText = heapUsedStr;
      if (elHeapPct) elHeapPct.innerText = `${heapPct.toFixed(1)}%`;
      if (elHeapBar) elHeapBar.style.width = `${Math.min(heapPct, 100)}%`;
      if (elHeapComm) elHeapComm.innerText = heapCommStr;
      if (elHeapPctVal) elHeapPctVal.innerText = `${heapPct.toFixed(1)}%`;

      const elNonHeap = document.getElementById('mon-nonheap-used');
      if (elNonHeap) {
        elNonHeap.innerText = mem.nonHeapUsedMb !== undefined ? `${mem.nonHeapUsedMb} MB` : formatBytes(mem.nonHeapUsedBytes || 0);
      }

      let physStr = '';
      let physPct = 0;
      let physFreeStr = '';
      if (mem.physicalUsedGb !== undefined) {
        physStr = `${mem.physicalUsedGb} GB / ${mem.physicalTotalGb} GB`;
        physPct = mem.physicalPercent ?? (mem.physicalTotalGb > 0 ? (mem.physicalUsedGb * 100 / mem.physicalTotalGb) : 0);
        physFreeStr = `Free: ${mem.physicalFreeGb || 0} GB`;
      } else {
        const u = mem.physicalUsedBytes || 0;
        const t = mem.physicalTotalBytes || 1;
        physStr = `${formatBytes(u)} / ${formatBytes(t)}`;
        physPct = t > 0 ? (u * 100 / t) : 0;
        physFreeStr = `Free: ${formatBytes(mem.physicalFreeBytes || 0)}`;
      }

      const elPhysRam = document.getElementById('mon-phys-ram');
      const elPhysPct = document.getElementById('mon-host-ram-pct');
      const elPhysBar = document.getElementById('mon-phys-ram-bar');
      const elPhysFree = document.getElementById('mon-phys-free');
      const elPhysPctVal = document.getElementById('mon-phys-pct-val');

      if (elPhysRam) elPhysRam.innerText = physStr;
      if (elPhysPct) elPhysPct.innerText = `${physPct.toFixed(1)}%`;
      if (elPhysBar) elPhysBar.style.width = `${Math.min(physPct, 100)}%`;
      if (elPhysFree) elPhysFree.innerText = physFreeStr;
      if (elPhysPctVal) elPhysPctVal.innerText = `${physPct.toFixed(1)}%`;
    }

    // Garbage Collection
    const gcList = (data.memory && Array.isArray(data.memory.gc)) ? data.memory.gc : (data.gc && Array.isArray(data.gc.collectors) ? data.gc.collectors : []);
    const gcCont = document.getElementById('mon-gc-container');
    if (gcCont) {
      if (gcList.length === 0) {
        gcCont.innerHTML = '<div>No GC collectors active</div>';
      } else {
        gcCont.innerHTML = gcList.map(c => `
          <div style="background:var(--bg-main);padding:6px 10px;border-radius:6px;border:1px solid var(--border);display:flex;align-items:center;gap:8px;">
            <span style="font-weight:600;color:var(--text-main);">${escapeHtml(c.name)}</span>
            <span class="badge badge-gray" style="font-size:10px;">${c.count ?? c.collectionCount ?? 0} runs</span>
            <span class="badge badge-blue" style="font-size:10px;">${c.timeMs ?? c.collectionTimeMs ?? 0} ms pause</span>
          </div>
        `).join('');
      }
    }

    // Worlds & Chunks
    const worlds = data.worldsSummary || data.worlds;
    if (worlds) {
      const wBadge = document.getElementById('mon-worlds-chunks-badge');
      const eBadge = document.getElementById('mon-worlds-entities-badge');
      const pBadge = document.getElementById('mon-worlds-players-badge');

      if (wBadge) wBadge.innerText = `${i18n.t('chunks')}: ${worlds.totalChunks ?? worlds.totalLoadedChunks ?? 0}`;
      if (eBadge) eBadge.innerText = `${i18n.t('entities')}: ${worlds.totalEntities ?? 0}`;
      if (pBadge) pBadge.innerText = `${i18n.t('players')}: ${worlds.totalPlayers ?? 0}`;

      const grid = document.getElementById('mon-worlds-grid');
      const wList = worlds.worlds || worlds.list || [];
      if (grid) {
        if (!Array.isArray(wList) || wList.length === 0) {
          grid.innerHTML = '<div style="color:var(--text-muted);font-size:13px;padding:24px;text-align:center;">No worlds currently loaded</div>';
        } else {
          grid.innerHTML = wList.map(w => {
            let envIcon = icons.globe(16);
            let envBadge = 'badge-green';
            if (w.environment === 'NETHER') {
              envIcon = icons.flame(16);
              envBadge = 'badge-rose';
            } else if (w.environment === 'THE_END') {
              envIcon = icons.portal(16);
              envBadge = 'badge-purple';
            }

            const pCount = w.players ?? w.playerEntities ?? 0;
            const lCount = w.livingEntities ?? 0;
            const iCount = w.items ?? w.itemEntities ?? 0;
            const oCount = w.otherEntities ?? 0;

            return `
              <div style="background:var(--bg-subtle);border:1px solid var(--border);border-radius:8px;padding:14px;display:flex;flex-direction:column;gap:10px;">
                <div style="display:flex;align-items:center;justify-content:space-between;">
                  <div style="display:flex;align-items:center;gap:7px;">
                    ${envIcon}
                    <span style="font-weight:700;font-size:13.5px;color:var(--text-main);">${escapeHtml(w.name)}</span>
                  </div>
                  <span class="badge ${envBadge}" style="font-size:10px;">${escapeHtml(w.environment)}</span>
                </div>

                <div style="display:flex;align-items:center;justify-content:space-between;font-size:12px;color:var(--text-muted);background:var(--bg-page);padding:8px 12px;border-radius:6px;">
                  <span>${i18n.t('chunks')}: <strong style="color:var(--text-main);">${w.loadedChunks}</strong></span>
                  <span>${i18n.t('entities')}: <strong style="color:var(--text-main);">${w.totalEntities}</strong></span>
                </div>

                <div style="display:flex;gap:5px;flex-wrap:wrap;font-size:10.5px;">
                  <span class="badge badge-gray" title="Living Entities" style="display:inline-flex;align-items:center;gap:4px;">${icons.heart(12)} ${lCount} Living</span>
                  <span class="badge badge-gray" title="Item Entities" style="display:inline-flex;align-items:center;gap:4px;">${icons.package(12)} ${iCount} Items</span>
                  <span class="badge badge-blue" title="Players" style="display:inline-flex;align-items:center;gap:4px;">${icons.user(12)} ${pCount} Players</span>
                  <span class="badge badge-gray" title="Other Entities" style="display:inline-flex;align-items:center;gap:4px;">${icons.bolt(12)} ${oCount} Other</span>
                </div>
              </div>
            `;
          }).join('');
        }
      }
    }

    // Plugins & Spark CPU Profiler
    const plugins = data.pluginsSummary || data.plugins;
    if (plugins) {
      const pBadge = document.getElementById('mon-plugins-badge');
      const enCount = plugins.enabled ?? plugins.enabledCount ?? 0;
      const totCount = plugins.total ?? plugins.totalCount ?? 0;
      if (pBadge) pBadge.innerText = `${enCount} / ${totCount} ${i18n.t('activePlugins')}`;

      const tbody = document.getElementById('mon-plugins-tbody');
      const pList = plugins.list || [];
      if (tbody) {
        if (!Array.isArray(pList) || pList.length === 0) {
          tbody.innerHTML = '<tr><td colspan="5" style="color:var(--text-muted);font-size:12px;text-align:center;padding:24px;">No plugins loaded</td></tr>';
        } else {
          // Sort plugins by CPU usage descending
          const sorted = [...pList].sort((a, b) => (b.cpuPercent || 0) - (a.cpuPercent || 0));

          tbody.innerHTML = sorted.map(p => {
            const cpu = p.cpuPercent || 0;
            const topClass = p.topClass && p.topClass !== '-' ? p.topClass : '-';
            const topClassPct = p.topClassPercent || 0;

            let cpuColor = 'var(--accent-emerald)';
            if (cpu >= 15.0) cpuColor = 'var(--accent-rose)';
            else if (cpu >= 5.0) cpuColor = 'var(--accent-amber)';

            return `
              <tr>
                <td>
                  <div style="font-weight:600;color:var(--text-main);">${escapeHtml(p.name)}</div>
                  <div style="font-size:11px;color:var(--text-muted);">${escapeHtml(p.author || '')}</div>
                </td>
                <td><span class="badge badge-gray">v${escapeHtml(p.version)}</span></td>
                <td>
                  <span class="badge ${p.enabled ? 'badge-green' : 'badge-gray'}">
                    ${p.enabled ? i18n.t('enabled') : i18n.t('disabled')}
                  </span>
                </td>
                <td>
                  <div style="display:flex;align-items:center;gap:8px;">
                    <div style="font-weight:700;font-size:12px;color:${cpuColor};min-width:40px;">${cpu.toFixed(1)}%</div>
                    <div style="flex:1;height:5px;background:var(--bg-subtle);border-radius:3px;overflow:hidden;">
                      <div style="height:100%;width:${Math.min(cpu * 2, 100)}%;background:${cpuColor};border-radius:3px;"></div>
                    </div>
                  </div>
                </td>
                <td>
                  ${topClass !== '-' ? `
                    <div style="display:inline-flex;align-items:center;gap:6px;background:var(--bg-subtle);padding:3px 8px;border-radius:4px;border:1px solid var(--border);">
                      <span style="font-family:ui-monospace, monospace;font-size:11.5px;color:#38BDF8;">${escapeHtml(topClass)}</span>
                      <span class="badge badge-blue" style="font-size:9.5px;padding:1px 5px;">${topClassPct.toFixed(0)}%</span>
                    </div>
                  ` : `
                    <span style="color:var(--text-muted);font-size:11px;">Idle</span>
                  `}
                </td>
              </tr>
            `;
          }).join('');
        }
      }
    }

    // Historical Points Management
    if (data.history && Array.isArray(data.history) && data.history.length > localHistory.length) {
      localHistory = [...data.history];
    } else {
      const now = Date.now();
      const pt = {
        timestamp: now,
        processCpu: data.cpu ? (data.cpu.processCpuPercent ?? 0) : 0,
        systemCpu: data.cpu ? (data.cpu.systemCpuPercent ?? 0) : 0,
        heapPercent: data.memory ? (data.memory.heapPercent ?? 0) : 0,
        physicalPercent: data.memory ? (data.memory.physicalPercent ?? 0) : 0,
        tps: data.tps ? (data.tps.tps1m ?? 20) : 20,
        mspt: data.tps ? (data.tps.msptCurrent ?? 0) : 0
      };
      localHistory.push(pt);
      const cutoff = now - 3600 * 1000;
      while (localHistory.length > 0 && localHistory[0].timestamp < cutoff) {
        localHistory.shift();
      }
    }

    if (currentCategory === 'overview') {
      drawAllCharts();
    }
  }

  // Quick initial HTTP fetch
  try {
    const initial = await api.get(`/api/servers/${serverId}/monitoring/spark`);
    applyTelemetry(initial);
  } catch (e) {}

  // Setup WebSocket connection
  const wsBadge = document.getElementById('mon-ws-badge');
  const wsDot = document.getElementById('mon-ws-dot');
  const wsText = document.getElementById('mon-ws-text');

  activeWs = new PanelWebSocket(
    `/ws/monitoring/${serverId}`,
    (msg) => {
      if (msg.type === 'telemetry' && msg.data) {
        applyTelemetry(msg.data);
      } else if (msg.server || msg.tps) {
        applyTelemetry(msg);
      }
    },
    () => {
      if (wsBadge) wsBadge.className = 'badge badge-green';
      if (wsDot) wsDot.className = 'status-dot online';
      if (wsText) wsText.innerText = `${i18n.t('liveWebSocket')} (1s)`;
    },
    () => {
      if (wsBadge) wsBadge.className = 'badge badge-amber';
      if (wsDot) wsDot.className = 'status-dot stopped';
      if (wsText) wsText.innerText = 'RECONNECTING...';
    }
  );

  await activeWs.connect();

  // Fallback polling
  fallbackInterval = setInterval(async () => {
    if (!activeWs || !activeWs.ws || activeWs.ws.readyState !== WebSocket.OPEN) {
      try {
        const polled = await api.get(`/api/servers/${serverId}/monitoring/spark`);
        applyTelemetry(polled);
      } catch (e) {}
    }
  }, 2500);

  // Resize listener to redraw charts crisply
  window.addEventListener('resize', () => {
    if (currentCategory === 'overview') {
      drawAllCharts();
    }
  });
}
