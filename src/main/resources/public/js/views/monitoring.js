import { api } from '../api.js';
import { toast, formatBytes, formatUptime, escapeHtml } from '../ui.js';
import { i18n } from '../i18n.js';
import { PanelWebSocket } from '../websocket.js';

let activeWs = null;
let fallbackInterval = null;

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

  root.innerHTML = `
    <div style="display:flex;flex-direction:column;gap:20px;">
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

      <!-- Section 1: TPS & MSPT (Spark style) -->
      <div class="card" style="padding:18px;">
        <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:14px;flex-wrap:wrap;gap:8px;">
          <div style="font-size:14px;font-weight:700;color:var(--text-main);display:flex;align-items:center;gap:8px;">
            <span>⚡</span>
            <span>${i18n.t('tps')} &amp; ${i18n.t('mspt')}</span>
          </div>
          <span style="font-size:11px;color:var(--text-muted);font-weight:500;">${i18n.t('msptTarget')}</span>
        </div>

        <!-- 3 TPS Gauges -->
        <div class="grid-cols-3" style="gap:12px;margin-bottom:16px;">
          <div style="background:var(--bg-subtle);padding:14px;border-radius:8px;border:1px solid var(--border);text-align:center;">
            <div style="font-size:11px;font-weight:600;color:var(--text-muted);margin-bottom:4px;">TPS (1 min)</div>
            <div style="font-size:26px;font-weight:800;color:var(--accent-emerald);" id="mon-tps-1m">20.00</div>
            <div style="font-size:10px;color:var(--text-muted);margin-top:2px;">Last 60 seconds</div>
          </div>

          <div style="background:var(--bg-subtle);padding:14px;border-radius:8px;border:1px solid var(--border);text-align:center;">
            <div style="font-size:11px;font-weight:600;color:var(--text-muted);margin-bottom:4px;">TPS (5 min)</div>
            <div style="font-size:26px;font-weight:800;color:var(--accent-emerald);" id="mon-tps-5m">20.00</div>
            <div style="font-size:10px;color:var(--text-muted);margin-top:2px;">Last 5 minutes</div>
          </div>

          <div style="background:var(--bg-subtle);padding:14px;border-radius:8px;border:1px solid var(--border);text-align:center;">
            <div style="font-size:11px;font-weight:600;color:var(--text-muted);margin-bottom:4px;">TPS (15 min)</div>
            <div style="font-size:26px;font-weight:800;color:var(--accent-emerald);" id="mon-tps-15m">20.00</div>
            <div style="font-size:10px;color:var(--text-muted);margin-top:2px;">Last 15 minutes</div>
          </div>
        </div>

        <!-- MSPT Spark Bar & Detailed percentiles -->
        <div style="background:var(--bg-subtle);padding:14px;border-radius:8px;border:1px solid var(--border);">
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

          <!-- Progress Bar relative to 50ms -->
          <div style="position:relative;width:100%;height:10px;background:var(--bg-page);border-radius:5px;overflow:hidden;border:1px solid var(--border);">
            <div id="mon-mspt-bar" style="height:100%;width:0%;background:var(--accent-emerald);border-radius:4px;transition:width 0.4s ease, background 0.4s ease;"></div>
          </div>
          <div style="display:flex;justify-content:space-between;margin-top:4px;font-size:10px;color:var(--text-muted);">
            <span>0 ms (Optimal)</span>
            <span style="color:var(--accent-amber);">25 ms (50%)</span>
            <span style="color:var(--accent-rose);">50 ms (20 TPS Limit)</span>
          </div>
        </div>
      </div>

      <!-- Section 2: CPU & Hardware -->
      <div class="card" style="padding:18px;">
        <div style="font-size:14px;font-weight:700;color:var(--text-main);margin-bottom:14px;display:flex;align-items:center;gap:8px;">
          <span>🖥️</span>
          <span>${i18n.t('processCpu')} &amp; ${i18n.t('systemCpu')}</span>
        </div>

        <div class="grid-cols-4" style="gap:12px;">
          <div style="background:var(--bg-subtle);padding:14px;border-radius:8px;border:1px solid var(--border);">
            <div style="font-size:11px;font-weight:600;color:var(--text-muted);">${i18n.t('processCpu')}</div>
            <div style="font-size:22px;font-weight:800;color:var(--text-main);margin:4px 0;" id="mon-proc-cpu">0.0%</div>
            <div style="width:100%;height:5px;background:var(--bg-page);border-radius:3px;overflow:hidden;">
              <div id="mon-proc-cpu-bar" style="height:100%;width:0%;background:var(--brand-primary);transition:width 0.3s ease;"></div>
            </div>
          </div>

          <div style="background:var(--bg-subtle);padding:14px;border-radius:8px;border:1px solid var(--border);">
            <div style="font-size:11px;font-weight:600;color:var(--text-muted);">${i18n.t('systemCpu')}</div>
            <div style="font-size:22px;font-weight:800;color:var(--text-main);margin:4px 0;" id="mon-sys-cpu">0.0%</div>
            <div style="width:100%;height:5px;background:var(--bg-page);border-radius:3px;overflow:hidden;">
              <div id="mon-sys-cpu-bar" style="height:100%;width:0%;background:var(--accent-blue);transition:width 0.3s ease;"></div>
            </div>
          </div>

          <div style="background:var(--bg-subtle);padding:14px;border-radius:8px;border:1px solid var(--border);">
            <div style="font-size:11px;font-weight:600;color:var(--text-muted);">${i18n.t('cores')}</div>
            <div style="font-size:22px;font-weight:800;color:var(--text-main);margin:4px 0;" id="mon-cores">0</div>
            <div style="font-size:10px;color:var(--text-muted);margin-top:4px;">Available Processors</div>
          </div>

          <div style="background:var(--bg-subtle);padding:14px;border-radius:8px;border:1px solid var(--border);">
            <div style="font-size:11px;font-weight:600;color:var(--text-muted);">${i18n.t('threads')}</div>
            <div style="font-size:22px;font-weight:800;color:var(--text-main);margin:4px 0;" id="mon-threads">0</div>
            <div style="font-size:10px;color:var(--text-muted);margin-top:4px;" id="mon-peak-threads">Peak: 0</div>
          </div>
        </div>
      </div>

      <!-- Section 3: Memory & GC -->
      <div class="card" style="padding:18px;">
        <div style="font-size:14px;font-weight:700;color:var(--text-main);margin-bottom:14px;display:flex;align-items:center;gap:8px;">
          <span>💾</span>
          <span>${i18n.t('heapMemory')}, ${i18n.t('nonHeapMemory')} &amp; ${i18n.t('hostRam')}</span>
        </div>

        <div class="grid-cols-3" style="gap:12px;margin-bottom:16px;">
          <div style="background:var(--bg-subtle);padding:14px;border-radius:8px;border:1px solid var(--border);">
            <div style="display:flex;justify-content:space-between;align-items:center;">
              <span style="font-size:11px;font-weight:600;color:var(--text-muted);">${i18n.t('heapMemory')}</span>
              <span style="font-size:10px;font-weight:700;color:var(--text-muted);" id="mon-heap-pct">0%</span>
            </div>
            <div style="font-size:20px;font-weight:800;color:var(--text-main);margin:4px 0;" id="mon-heap-used">0 / 0 MB</div>
            <div style="width:100%;height:6px;background:var(--bg-page);border-radius:3px;overflow:hidden;margin-bottom:6px;">
              <div id="mon-heap-bar" style="height:100%;width:0%;background:var(--accent-emerald);transition:width 0.3s ease;"></div>
            </div>
            <div style="font-size:10px;color:var(--text-muted);" id="mon-heap-comm">Committed: 0 MB</div>
          </div>

          <div style="background:var(--bg-subtle);padding:14px;border-radius:8px;border:1px solid var(--border);">
            <span style="font-size:11px;font-weight:600;color:var(--text-muted);">${i18n.t('nonHeapMemory')}</span>
            <div style="font-size:20px;font-weight:800;color:var(--text-main);margin:4px 0;" id="mon-nonheap-used">0 MB</div>
            <div style="font-size:10px;color:var(--text-muted);margin-top:12px;">Metaspace &amp; JVM Internals</div>
          </div>

          <div style="background:var(--bg-subtle);padding:14px;border-radius:8px;border:1px solid var(--border);">
            <div style="display:flex;justify-content:space-between;align-items:center;">
              <span style="font-size:11px;font-weight:600;color:var(--text-muted);">${i18n.t('hostRam')}</span>
              <span style="font-size:10px;font-weight:700;color:var(--text-muted);" id="mon-host-ram-pct">0%</span>
            </div>
            <div style="font-size:20px;font-weight:800;color:var(--text-main);margin:4px 0;" id="mon-phys-ram">0 / 0 GB</div>
            <div style="width:100%;height:6px;background:var(--bg-page);border-radius:3px;overflow:hidden;margin-bottom:6px;">
              <div id="mon-phys-ram-bar" style="height:100%;width:0%;background:var(--accent-blue);transition:width 0.3s ease;"></div>
            </div>
            <div style="font-size:10px;color:var(--text-muted);" id="mon-phys-free">Free: 0 GB</div>
          </div>
        </div>

        <!-- Garbage Collection Table -->
        <div style="background:var(--bg-subtle);padding:12px 14px;border-radius:8px;border:1px solid var(--border);">
          <div style="font-size:12px;font-weight:600;color:var(--text-main);margin-bottom:8px;">${i18n.t('garbageCollection')}</div>
          <div id="mon-gc-container" style="display:flex;flex-wrap:wrap;gap:12px;font-size:12px;color:var(--text-muted);">
            <div>No GC collectors data yet</div>
          </div>
        </div>
      </div>

      <!-- Section 4: Worlds & Chunks Telemetry -->
      <div class="card" style="padding:18px;">
        <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:14px;flex-wrap:wrap;gap:8px;">
          <div style="font-size:14px;font-weight:700;color:var(--text-main);display:flex;align-items:center;gap:8px;">
            <span>🗺️</span>
            <span>${i18n.t('loadedWorlds')}</span>
          </div>

          <div style="display:flex;gap:8px;flex-wrap:wrap;">
            <span class="badge badge-blue" id="mon-worlds-chunks-badge">Chunks: 0</span>
            <span class="badge badge-green" id="mon-worlds-entities-badge">Entities: 0</span>
            <span class="badge badge-purple" id="mon-worlds-players-badge">Players: 0</span>
          </div>
        </div>

        <div id="mon-worlds-grid" style="display:grid;grid-template-columns:repeat(auto-fill, minmax(280px, 1fr));gap:12px;">
          <div style="text-align:center;padding:24px;color:var(--text-muted);">${i18n.t('loading')}</div>
        </div>
      </div>

      <!-- Section 5: Active Plugins -->
      <div class="card" style="padding:18px;">
        <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:14px;flex-wrap:wrap;gap:8px;">
          <div style="font-size:14px;font-weight:700;color:var(--text-main);display:flex;align-items:center;gap:8px;">
            <span>🧩</span>
            <span>${i18n.t('activePlugins')}</span>
          </div>
          <span class="badge badge-green" id="mon-plugins-badge">0 Active</span>
        </div>

        <div id="mon-plugins-container" style="display:flex;flex-wrap:wrap;gap:6px;max-height:220px;overflow-y:auto;padding-right:4px;">
          <div style="font-size:12px;color:var(--text-muted);">${i18n.t('loading')}</div>
        </div>
      </div>
    </div>
  `;

  function applyTelemetry(data) {
    if (!data) return;

    // Header info
    if (data.server) {
      const elServer = document.getElementById('mon-server-badge');
      const elUptime = document.getElementById('mon-uptime-badge');
      if (elServer) {
        elServer.innerText = `${data.server.name} (${data.server.version})${data.server.folia ? ' [Folia]' : ''}`;
      }
      if (elUptime) {
        elUptime.innerText = `${i18n.t('uptime')}: ${formatUptime(data.server.uptimeSeconds)}`;
      }
    }

    // TPS
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

      // MSPT
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
    }

    // CPU
    if (data.cpu) {
      const proc = data.cpu.processCpuPercent ?? 0;
      const sys = data.cpu.systemCpuPercent ?? 0;
      const cores = data.cpu.cores ?? data.cpu.availableProcessors ?? 1;
      const threads = data.cpu.threadCount ?? data.cpu.liveThreads ?? 0;
      const peak = data.cpu.peakThreads ?? threads;

      const elProc = document.getElementById('mon-proc-cpu');
      const elProcBar = document.getElementById('mon-proc-cpu-bar');
      if (elProc) elProc.innerText = `${proc.toFixed(1)}%`;
      if (elProcBar) elProcBar.style.width = `${Math.min(proc, 100)}%`;

      const elSys = document.getElementById('mon-sys-cpu');
      const elSysBar = document.getElementById('mon-sys-cpu-bar');
      if (elSys) elSys.innerText = `${sys.toFixed(1)}%`;
      if (elSysBar) elSysBar.style.width = `${Math.min(sys, 100)}%`;

      const elCores = document.getElementById('mon-cores');
      if (elCores) elCores.innerText = cores;

      const elThreads = document.getElementById('mon-threads');
      const elPeak = document.getElementById('mon-peak-threads');
      if (elThreads) elThreads.innerText = threads;
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

      if (elHeapUsed) elHeapUsed.innerText = heapUsedStr;
      if (elHeapPct) elHeapPct.innerText = `${heapPct.toFixed(1)}%`;
      if (elHeapBar) elHeapBar.style.width = `${Math.min(heapPct, 100)}%`;
      if (elHeapComm) elHeapComm.innerText = heapCommStr;

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

      if (elPhysRam) elPhysRam.innerText = physStr;
      if (elPhysPct) elPhysPct.innerText = `${physPct.toFixed(1)}%`;
      if (elPhysBar) elPhysBar.style.width = `${Math.min(physPct, 100)}%`;
      if (elPhysFree) elPhysFree.innerText = physFreeStr;
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
          grid.innerHTML = '<div style="color:var(--text-muted);font-size:13px;">No worlds currently loaded</div>';
        } else {
          grid.innerHTML = wList.map(w => {
            let envIcon = '🌍';
            let envBadge = 'badge-green';
            if (w.environment === 'NETHER') {
              envIcon = '🔥';
              envBadge = 'badge-rose';
            } else if (w.environment === 'THE_END') {
              envIcon = '🌌';
              envBadge = 'badge-purple';
            }

            const pCount = w.players ?? w.playerEntities ?? 0;
            const lCount = w.livingEntities ?? 0;
            const iCount = w.items ?? w.itemEntities ?? 0;
            const oCount = w.otherEntities ?? 0;

            return `
              <div style="background:var(--bg-subtle);border:1px solid var(--border);border-radius:8px;padding:12px 14px;display:flex;flex-direction:column;gap:8px;">
                <div style="display:flex;align-items:center;justify-content:space-between;">
                  <div style="display:flex;align-items:center;gap:6px;">
                    <span style="font-size:16px;">${envIcon}</span>
                    <span style="font-weight:700;font-size:13px;color:var(--text-main);">${escapeHtml(w.name)}</span>
                  </div>
                  <span class="badge ${envBadge}" style="font-size:10px;">${escapeHtml(w.environment)}</span>
                </div>

                <div style="display:flex;align-items:center;justify-content:space-between;font-size:12px;color:var(--text-muted);background:var(--bg-page);padding:6px 10px;border-radius:6px;">
                  <span>${i18n.t('chunks')}: <strong style="color:var(--text-main);">${w.loadedChunks}</strong></span>
                  <span>${i18n.t('entities')}: <strong style="color:var(--text-main);">${w.totalEntities}</strong></span>
                </div>

                <div style="display:flex;gap:4px;flex-wrap:wrap;font-size:10px;">
                  <span class="badge badge-gray" title="Living Entities">🧟 ${lCount}</span>
                  <span class="badge badge-gray" title="Item Entities">📦 ${iCount}</span>
                  <span class="badge badge-blue" title="Players">🧑 ${pCount}</span>
                  <span class="badge badge-gray" title="Other Entities">⚡ ${oCount}</span>
                </div>
              </div>
            `;
          }).join('');
        }
      }
    }

    // Active Plugins
    const plugins = data.pluginsSummary || data.plugins;
    if (plugins) {
      const pBadge = document.getElementById('mon-plugins-badge');
      const enCount = plugins.enabled ?? plugins.enabledCount ?? 0;
      const totCount = plugins.total ?? plugins.totalCount ?? 0;
      if (pBadge) pBadge.innerText = `${enCount} / ${totCount} ${i18n.t('activePlugins')}`;

      const cont = document.getElementById('mon-plugins-container');
      const pList = plugins.list || [];
      if (cont) {
        if (!Array.isArray(pList) || pList.length === 0) {
          cont.innerHTML = '<div style="color:var(--text-muted);font-size:12px;">No plugins loaded</div>';
        } else {
          cont.innerHTML = pList.map(p => `
            <span class="badge ${p.enabled ? 'badge-green' : 'badge-gray'}" style="font-size:11px;padding:3px 8px;display:inline-flex;align-items:center;gap:4px;">
              <span>${escapeHtml(p.name)}</span>
              <span style="opacity:0.75;font-size:9px;">v${escapeHtml(p.version)}</span>
            </span>
          `).join('');
        }
      }
    }
  }

  // Quick initial HTTP fetch so user doesn't wait 1s for the first tick
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

  // Fallback polling in case WebSocket is blocked or disconnected
  fallbackInterval = setInterval(async () => {
    if (!activeWs || !activeWs.ws || activeWs.ws.readyState !== WebSocket.OPEN) {
      try {
        const polled = await api.get(`/api/servers/${serverId}/monitoring/spark`);
        applyTelemetry(polled);
      } catch (e) {}
    }
  }, 2500);
}
