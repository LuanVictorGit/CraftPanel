export const api = {
  async request(method, endpoint, body = null, isFormData = false) {
    const options = {
      method,
      headers: {}
    };

    if (body) {
      if (isFormData) {
        options.body = body;
      } else {
        options.headers['Content-Type'] = 'application/json';
        options.body = JSON.stringify(body);
      }
    }

    const response = await fetch(endpoint, options);

    if (response.status === 401 && !endpoint.includes('/api/auth/login') && !endpoint.includes('/api/setup')) {
      window.dispatchEvent(new CustomEvent('auth:unauthorized'));
      throw new Error('Sessão expirada ou não autenticada');
    }

    const contentType = response.headers.get('content-type');
    let data = null;
    if (contentType && contentType.includes('application/json')) {
      data = await response.json();
    } else {
      data = await response.text();
    }

    if (!response.ok) {
      const errorMsg = data?.error || data?.title || (typeof data === 'string' && data ? data : `Erro ${response.status}`);
      throw new Error(errorMsg);
    }

    return data;
  },

  get(endpoint, params = null) {
    let url = endpoint;
    if (params) {
      const q = new URLSearchParams();
      for (const [k, v] of Object.entries(params)) {
        if (v !== undefined && v !== null && v !== '') {
          q.append(k, v);
        }
      }
      const qs = q.toString();
      if (qs) url += (url.includes('?') ? '&' : '?') + qs;
    }
    return this.request('GET', url);
  },

  post(endpoint, body) {
    return this.request('POST', endpoint, body);
  },

  put(endpoint, body) {
    return this.request('PUT', endpoint, body);
  },

  delete(endpoint, params = null) {
    let url = endpoint;
    if (params) {
      const q = new URLSearchParams();
      for (const [k, v] of Object.entries(params)) {
        if (v !== undefined && v !== null && v !== '') {
          q.append(k, v);
        }
      }
      const qs = q.toString();
      if (qs) url += (url.includes('?') ? '&' : '?') + qs;
    }
    return this.request('DELETE', url, params);
  },

  upload(endpoint, formData) {
    return this.request('POST', endpoint, formData, true);
  },

  async waitForTask(taskId, onProgress = null) {
    const deadline = Date.now() + 5 * 60 * 1000;
    let failures = 0;
    while (Date.now() < deadline) {
      await new Promise(r => setTimeout(r, 600));
      let task;
      try {
        task = await this.get(`/api/tasks/${taskId}`);
        failures = 0;
      } catch (e) {
        if (++failures > 5) throw e;
        continue;
      }
      if (onProgress) onProgress(task);
      if (task.state === 'COMPLETED') return task;
      if (task.state === 'FAILED' || task.state === 'CANCELLED') {
        throw new Error(task.error || task.message || 'Task failed');
      }
    }
    throw new Error('Timed out waiting for the task to finish');
  }
};
