import { api } from './api.js';

export class PanelWebSocket {
  constructor(endpoint, onMessage, onOpen = null, onClose = null) {
    this.endpoint = endpoint;
    this.onMessage = onMessage;
    this.onOpen = onOpen;
    this.onClose = onClose;
    this.ws = null;
    this.reconnectTimer = null;
    this.closedExplicitly = false;
  }

  async connect() {
    this.closedExplicitly = false;
    try {
      const ticketRes = await api.post('/api/auth/ws-ticket');
      const ticket = ticketRes.ticket;

      const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
      const host = window.location.host;
      const url = `${protocol}//${host}${this.endpoint}?ticket=${ticket}`;

      this.ws = new WebSocket(url);

      this.ws.onopen = () => {
        if (this.onOpen) this.onOpen();
      };

      this.ws.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);
          this.onMessage(data);
        } catch (e) {
          this.onMessage({ type: 'raw', data: event.data });
        }
      };

      this.ws.onclose = () => {
        if (this.onClose) this.onClose();
        if (!this.closedExplicitly) {
          this.scheduleReconnect();
        }
      };

      this.ws.onerror = () => {
        if (this.ws) this.ws.close();
      };
    } catch (e) {
      if (!this.closedExplicitly) {
        this.scheduleReconnect();
      }
    }
  }

  scheduleReconnect() {
    if (this.reconnectTimer) clearTimeout(this.reconnectTimer);
    this.reconnectTimer = setTimeout(() => {
      this.connect();
    }, 3000);
  }

  send(payload) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(typeof payload === 'string' ? payload : JSON.stringify(payload));
    }
  }

  close() {
    this.closedExplicitly = true;
    if (this.reconnectTimer) clearTimeout(this.reconnectTimer);
    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
  }
}
