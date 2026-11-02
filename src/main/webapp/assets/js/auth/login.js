/*
 * 登录 / 注册页。依赖公共 shell（导航、会话状态）与 api.js（Result 解包、CSRF）。
 * 登录成功后先让 api.js 丢弃旧令牌，再让 shell 重新拉取会话，最后按角色回跳。
 */
(function () {
  'use strict';

  const alertBox = document.getElementById('alert');
  const loginForm = document.getElementById('form-login');
  const registerForm = document.getElementById('form-register');
  const tabLogin = document.getElementById('tab-login');
  const tabRegister = document.getElementById('tab-register');
  const USERNAME = /^[A-Za-z0-9_]{4,30}$/;

  function showAlert(message, ok) {
    alertBox.textContent = message;
    alertBox.className = 'cms-alert is-visible ' + (ok ? 'is-ok' : 'is-error');
  }

  function hideAlert() {
    alertBox.className = 'cms-alert';
    alertBox.textContent = '';
  }

  function switchTab(register) {
    hideAlert();
    tabLogin.setAttribute('aria-selected', String(!register));
    tabRegister.setAttribute('aria-selected', String(register));
    loginForm.hidden = register;
    registerForm.hidden = !register;
    (register ? document.getElementById('reg-username') : document.getElementById('login-username')).focus();
  }

  tabLogin.addEventListener('click', function () { switchTab(false); });
  tabRegister.addEventListener('click', function () { switchTab(true); });

  /** 只接受本应用内的回跳地址，避免被构造成开放重定向。 */
  function nextUrl() {
    const raw = new URLSearchParams(window.location.search).get('next');
    if (raw) {
      try {
        const target = new URL(raw, window.location.href);
        if (target.origin === window.location.origin && target.href.indexOf(Cms.api.root) === 0) {
          return target.href;
        }
      } catch (ignored) {
        // 非法地址按无回跳处理。
      }
    }
    if (Cms.shell.hasPermission('user:manage')) return Cms.shell.link('pages/auth/users.html');
    if (Cms.shell.hasPermission('role:manage')) return Cms.shell.link('pages/auth/roles.html');
    if (Cms.shell.hasPermission('item:view') || Cms.shell.user) return Cms.shell.link('pages/index.html');
    return Cms.shell.link('pages/index.html');
  }

  async function enter() {
    Cms.api.invalidateSession();
    await Cms.shell.refreshSession();
    window.location.replace(nextUrl());
  }

  loginForm.addEventListener('submit', async function (event) {
    event.preventDefault();
    hideAlert();
    const username = loginForm.username.value.trim();
    const password = loginForm.password.value;
    if (!username || !password) {
      showAlert('请输入用户名和密码');
      return;
    }
    const button = document.getElementById('login-submit');
    button.disabled = true;
    try {
      await Cms.api.request('/api/auth/login', {
        method: 'POST',
        body: { username: username, password: password }
      });
      loginForm.password.value = '';
      await enter();
    } catch (error) {
      showAlert(error.message || '登录失败，请稍后重试');
      button.disabled = false;
    }
  });

  registerForm.addEventListener('submit', async function (event) {
    event.preventDefault();
    hideAlert();
    const username = registerForm.username.value.trim();
    const displayName = registerForm.displayName.value.trim();
    const password = registerForm.password.value;
    if (!USERNAME.test(username)) {
      showAlert('用户名需为 4~30 个字母、数字或下划线');
      return;
    }
    if (displayName.length < 2 || displayName.length > 20) {
      showAlert('昵称需为 2~20 个字符');
      return;
    }
    if (password.length < 8 || password.length > 64) {
      showAlert('密码需为 8~64 个字符');
      return;
    }
    if (password !== registerForm.confirm.value) {
      showAlert('两次输入的密码不一致');
      return;
    }
    const button = document.getElementById('register-submit');
    button.disabled = true;
    try {
      await Cms.api.request('/api/auth/register', {
        method: 'POST',
        body: { username: username, displayName: displayName, password: password }
      });
      // 注册成功直接登录，省掉一次手工输入。
      await Cms.api.request('/api/auth/login', {
        method: 'POST',
        body: { username: username, password: password }
      });
      await enter();
    } catch (error) {
      showAlert(error.message || '注册失败，请稍后重试');
      button.disabled = false;
    }
  });

  // 已登录用户不必再次登录；直接按角色回跳。
  Cms.shell.ready.then(function () {
    if (Cms.shell.user) {
      window.location.replace(nextUrl());
    }
  });
})();
