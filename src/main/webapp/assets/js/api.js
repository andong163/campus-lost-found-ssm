(function (window) {
  'use strict';
  const Cms = window.Cms = window.Cms || {};
  const root = new URL('../../', window.document.currentScript.src);
  const messages = Object.freeze({
    0: '网络连接失败，请检查网络后重试。',
    400: '填写的信息有误，请检查后再提交。',
    401: '请先登录；原登录状态可能已失效。',
    403: '暂时无法执行此操作，请确认权限或重新登录。',
    404: '未找到这条内容，可能已被移除。',
    405: '暂不支持此操作。',
    409: '内容或状态已发生变化，请刷新后重试。',
    415: '提交的内容格式不受支持。',
    500: '系统暂时无法处理请求，请稍后重试。',
    502: '服务返回异常，请稍后重试。',
    503: '服务暂时不可用，请稍后再试。',
    504: '请求超时，请稍后重试。'
  });
  class ApiError extends Error {
    constructor(status) { super(messages[status] || messages[500]); this.name = 'ApiError'; this.status = status; }
  }
  let token = null, tokenRequest = null, generation = 0;
  function invalidateSession() { generation++; token = null; tokenRequest = null; }
  function url(path) {
    if (typeof path !== 'string' || !/^\/api\//.test(path) || /[\\#]/.test(path)) throw new ApiError(400);
    const destination = new URL(root.pathname.replace(/\/$/, '') + path, root.origin);
    if (destination.origin !== root.origin || !destination.pathname.startsWith(root.pathname + 'api/')) throw new ApiError(400);
    return destination;
  }
  async function request(path, options = {}) {
    const destination = url(path);
    const method = (options.method || 'GET').toUpperCase();
    if (!['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD'].includes(method)) throw new ApiError(400);
    const startGeneration = generation;
    const headers = new Headers({ Accept: 'application/json' });
    if (options.body !== undefined) headers.set('Content-Type', 'application/json');
    if (!['GET', 'HEAD'].includes(method) && !['/api/auth/login', '/api/auth/register'].includes(path)) {
      headers.set('X-CSRF-Token', await csrf());
    }
    const controller = new AbortController();
    const abort = () => controller.abort();
    if (options.signal?.aborted) throw new DOMException('Aborted', 'AbortError');
    options.signal?.addEventListener('abort', abort, { once: true });
    let timedOut = false;
    const timeout = window.setTimeout(() => { timedOut = true; controller.abort(); }, options.timeout || 15000);
    try {
      const response = await window.fetch(destination.href, {
        method, credentials: 'same-origin', cache: 'no-store', redirect: 'error', headers,
        body: options.body === undefined ? undefined : JSON.stringify(options.body), signal: controller.signal
      });
      if (!response.ok) {
        if (response.status === 401 && startGeneration === generation) {
          invalidateSession();
          window.dispatchEvent(new CustomEvent('cms:unauthorized'));
        }
        if (response.status === 403 && startGeneration === generation) { token = null; tokenRequest = null; }
        throw new ApiError(response.status);
      }
      let result;
      try { result = await response.json(); } catch (_) { throw new ApiError(502); }
      if (!result || result.code !== response.status || !Object.prototype.hasOwnProperty.call(result, 'data')) throw new ApiError(502);
      return result.data;
    } catch (error) {
      if (error instanceof ApiError) throw error;
      if (error.name === 'AbortError' && !timedOut) throw error;
      throw new ApiError(timedOut ? 504 : 0);
    } finally {
      window.clearTimeout(timeout);
      options.signal?.removeEventListener('abort', abort);
    }
  }
  async function csrf() {
    if (token) return token;
    if (!tokenRequest) {
      const epoch = generation;
      const pending = request('/api/auth/csrf').then(value => {
        if (epoch !== generation) throw new ApiError(401);
        if (typeof value !== 'string' || !/^[A-Za-z0-9_-]{20,256}$/.test(value)) throw new ApiError(502);
        token = value;
        return token;
      });
      tokenRequest = pending;
      pending.finally(() => { if (tokenRequest === pending) tokenRequest = null; }).catch(() => {});
    }
    return tokenRequest;
  }
  Cms.api = Object.freeze({ request, csrf, invalidateSession, ApiError, messages, root: root.href });
})(window);
