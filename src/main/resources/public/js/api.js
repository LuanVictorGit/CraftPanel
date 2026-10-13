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
      const errorMsg = (data && data.error) ? data.error : (typeof data === 'string' && data ? data : `Erro ${response.status}`);
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
    return this.request('DELETE', url);
  },

  upload(endpoint, formData) {
    return this.request('POST', endpoint, formData, true);
  }
};
