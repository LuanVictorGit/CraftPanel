import { api } from '../api.js';
import { toast, formatBytes, formatUptime, escapeHtml } from '../ui.js';
import { i18n } from '../i18n.js';

let monitorInterval = null;

export async function renderMonitoring(root, state) {
  const serverId = state.activeServerId;

  root.innerHTML = `
    <div style="display:flex;flex-direction:column;gap:24px;">
      <div>
        <h3 style="font-size:18px;font-weight:700;color:var(--text-main);">${i18n.t('monitoring')}</h3>
        <p style="font-size:13px;color:var(--text-muted);">Real-time performance metrics and host statistics</p>
      </div>

      <!-- Host System Overview -->
      <div class="card">
        <div class="card-header">
          <h4 class="card-title">${i18n.t('systemHostMetrics')}</h4>
          <span class="badge badge-blue" id="mon-java-ver">Java</span>
        </div>

        <div class="grid-cols-3">
          <div style="background:var(--bg-subtle);padding:16px;border-radius:8px;">
            <div style="font-size:12px;font-weight:600;color:var(--text-muted);">${i18n.t('cpuUsage')}</div>
            <div style="font-size:24px;font-weight:700;color:var(--text-main);margin:6px 0;" id="mon-host-cpu">0.0%</div>
            <div style="width:100%;height:6px;background:#CBD5E1;border-radius:3px;overflow:hidden;">
              <div id="mon-host-cpu-bar" style="height:100%;width:0%;background:var(--brand-primary);transition:width 0.3s ease;"></div>
            </div>
          </div>

          <div style="background:var(--bg-subtle);padding:16px;border-radius:8px;">
            <div style="font-size:12px;font-weight:600;color:var(--text-muted);">${i18n.t('ramUsage')}</div>
            <div style="font-size:24px;font-weight:700;color:var(--text-main);margin:6px 0;" id="mon-host-ram">0 / 0 GB</div>
            <div style="width:100%;height:6px;background:#CBD5E1;border-radius:3px;overflow:hidden;">
              <div id="mon-host-ram-bar" style="height:100%;width:0%;background:var(--accent-emerald);transition:width 0.3s ease;"></div>
            </div>
          </div>

          <div style="background:var(--bg-subtle);padding:16px;border-radius:8px;">
            <div style="font-size:12px;font-weight:600;color:var(--text-muted);">${i18n.t('diskUsage')}</div>
            <div style="font-size:24px;font-weight:700;color:var(--text-main);margin:6px 0;" id="mon-host-disk">0 / 0 GB</div>
            <div style="width:100%;height:6px;background:#CBD5E1;border-radius:3px;overflow:hidden;">
              <div id="mon-host-disk-bar" style="height:100%;width:0%;background:var(--accent-amber);transition:width 0.3s ease;"></div>
            </div>
          </div>
        </div>
      </div>

      <!-- Server Instance Live Stats (if selected) -->
      ${serverId ? `
        <div class="card">
          <div class="card-header">
            <h4 class="card-title">Instance Telemetry</h4>
            <span class="badge badge-gray" id="mon-instance-status">STOPPED</span>
          </div>

          <div class="grid-cols-4">
            <div style="background:var(--bg-subtle);padding:14px;border-radius:8px;">
              <div style="font-size:12px;color:var(--text-muted);">Process CPU</div>
              <div style="font-size:20px;font-weight:700;" id="mon-inst-cpu">0.0%</div>
            </div>

            <div style="background:var(--bg-subtle);padding:14px;border-radius:8px;">
              <div style="font-size:12px;color:var(--text-muted);">JVM Memory</div>
              <div style="font-size:20px;font-weight:700;" id="mon-inst-ram">0 MB</div>
            </div>

            <div style="background:var(--bg-subtle);padding:14px;border-radius:8px;">
              <div style="font-size:12px;color:var(--text-muted);">Players Online</div>
              <div style="font-size:20px;font-weight:700;" id="mon-inst-players">0 / 0</div>
            </div>

            <div style="background:var(--bg-subtle);padding:14px;border-radius:8px;">
              <div style="font-size:12px;color:var(--text-muted);">Ping Latency</div>
              <div style="font-size:20px;font-weight:700;" id="mon-inst-ping">-</div>
            </div>
          </div>

          <div style="margin-top:16px;padding:12px;background:var(--bg-page);border-radius:6px;font-size:13px;display:flex;align-items:center;gap:10px;">
            <span style="font-weight:600;color:var(--text-muted);">MOTD:</span>
            <span id="mon-inst-motd" style="font-family:var(--font-mono);font-size:12px;">-</span>
          </div>
        </div>
      ` : ''}
    </div>
  `;

  if (monitorInterval) clearInterval(monitorInterval);

  async function update() {
    try {
      const sys = await api.get('/api/monitoring/system');
      const elJava = document.getElementById('mon-java-ver');
      const elCpu = document.getElementById('mon-host-cpu');
      const elCpuBar = document.getElementById('mon-host-cpu-bar');
      const elRam = document.getElementById('mon-host-ram');
      const elRamBar = document.getElementById('mon-host-ram-bar');
      const elDisk = document.getElementById('mon-host-disk');
      const elDiskBar = document.getElementById('mon-host-disk-bar');

      if (elJava) elJava.innerText = `Java ${sys.javaVersion} (${sys.availableProcessors} Cores)`;
      if (elCpu) elCpu.innerText = `${sys.systemCpuPercent.toFixed(1)}%`;
      if (elCpuBar) elCpuBar.style.width = `${Math.min(sys.systemCpuPercent, 100)}%`;

      const usedRamGb = (sys.usedPhysicalMemoryBytes / (1024 * 1024 * 1024)).toFixed(1);
      const totalRamGb = (sys.totalPhysicalMemoryBytes / (1024 * 1024 * 1024)).toFixed(1);
      const ramPct = (sys.totalPhysicalMemoryBytes > 0) ? (sys.usedPhysicalMemoryBytes * 100 / sys.totalPhysicalMemoryBytes) : 0;
      if (elRam) elRam.innerText = `${usedRamGb} / ${totalRamGb} GB`;
      if (elRamBar) elRamBar.style.width = `${Math.min(ramPct, 100)}%`;

      const usedDiskGb = (sys.usedDiskBytes / (1024 * 1024 * 1024)).toFixed(1);
      const totalDiskGb = (sys.totalDiskBytes / (1024 * 1024 * 1024)).toFixed(1);
      const diskPct = (sys.totalDiskBytes > 0) ? (sys.usedDiskBytes * 100 / sys.totalDiskBytes) : 0;
      if (elDisk) elDisk.innerText = `${usedDiskGb} / ${totalDiskGb} GB`;
      if (elDiskBar) elDiskBar.style.width = `${Math.min(diskPct, 100)}%`;

      if (serverId) {
        const inst = await api.get(`/api/servers/${serverId}/metrics`);
        const elInstStatus = document.getElementById('mon-instance-status');
        const elInstCpu = document.getElementById('mon-inst-cpu');
        const elInstRam = document.getElementById('mon-inst-ram');
        const elInstPlayers = document.getElementById('mon-inst-players');
        const elInstPing = document.getElementById('mon-inst-ping');
        const elInstMotd = document.getElementById('mon-inst-motd');

        if (elInstStatus) {
          elInstStatus.innerText = inst.status;
          elInstStatus.className = `badge ${inst.status === 'RUNNING' ? 'badge-green' : 'badge-gray'}`;
        }
        if (elInstCpu) elInstCpu.innerText = `${inst.cpuPercent.toFixed(1)}%`;
        if (elInstRam) elInstRam.innerText = `${inst.memoryUsedMb} / ${inst.memoryMaxMb} MB`;
        if (elInstPlayers) elInstPlayers.innerText = `${inst.playerCount} / ${inst.maxPlayers}`;
        if (elInstPing) elInstPing.innerText = inst.pingMs >= 0 ? `${inst.pingMs} ms` : 'Offline';
        if (elInstMotd) elInstMotd.innerText = inst.motd || 'CraftPanel Managed Server';
      }
    } catch (e) {}
  }

  update();
  monitorInterval = setInterval(update, 3000);
}
