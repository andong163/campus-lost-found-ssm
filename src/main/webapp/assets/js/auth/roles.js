/*
 * 角色权限管理页：角色列表、新建、改名与权限勾选。
 * 权限清单来自 /api/permissions，未登记的权限码不会出现在页面上，也不会被提交。
 */
(function () {
  'use strict';

  const REQUIRED = 'role:manage';
  const el = (id) => document.getElementById(id);
  const alertBox = el('alert');
  let roles = [];
  let permissions = [];
  let selected = null;

  function showAlert(message, ok) {
    alertBox.textContent = message;
    alertBox.className = 'cms-alert is-visible ' + (ok ? 'is-ok' : 'is-error');
  }

  function renderRoles() {
    const tbody = el('role-rows');
    tbody.textContent = '';
    for (const role of roles) {
      const tr = document.createElement('tr');

      const code = document.createElement('td');
      code.textContent = role.code;
      tr.append(code);

      const name = document.createElement('td');
      name.textContent = role.name || '—';
      tr.append(name);

      const count = document.createElement('td');
      count.textContent = String((role.permissions || []).length);
      tr.append(count);

      const actions = document.createElement('td');
      actions.className = 'actions';
      const edit = document.createElement('button');
      edit.type = 'button';
      edit.className = 'cms-btn-ghost';
      edit.textContent = '维护权限';
      edit.addEventListener('click', () => select(role));
      actions.append(edit);
      tr.append(actions);

      tbody.append(tr);
    }
  }

  function groupOf(code) {
    const index = code.indexOf(':');
    return index > 0 ? code.slice(0, index) : '其他';
  }

  function renderPermissionGroups(role) {
    const host = el('permission-groups');
    host.textContent = '';
    const checked = new Set(role.permissions || []);
    const groups = new Map();
    for (const permission of permissions) {
      const key = groupOf(permission.code);
      if (!groups.has(key)) groups.set(key, []);
      groups.get(key).push(permission);
    }
    for (const [key, items] of groups) {
      const wrapper = document.createElement('div');
      wrapper.className = 'cms-permission-group';
      const title = document.createElement('h3');
      title.textContent = key;
      const boxes = document.createElement('div');
      boxes.className = 'cms-checkboxes';
      for (const permission of items) {
        const label = document.createElement('label');
        const box = document.createElement('input');
        box.type = 'checkbox';
        box.value = permission.code;
        box.checked = checked.has(permission.code);
        label.append(box, document.createTextNode(permission.name || permission.code));
        label.title = permission.code;
        boxes.append(label);
      }
      wrapper.append(title, boxes);
      host.append(wrapper);
    }
    if (!permissions.length) {
      const hint = document.createElement('p');
      hint.className = 'cms-hint';
      hint.textContent = '尚未登记任何权限点。';
      host.append(hint);
    }
  }

  function select(role) {
    selected = role;
    el('editor').hidden = false;
    el('editor-title').textContent = '维护权限 · ' + (role.name || role.code);
    el('edit-name').value = role.name || '';
    el('editor-hint').textContent = '角色代码 ' + role.code + '，当前 ' + (role.permissions || []).length + ' 个权限';
    renderPermissionGroups(role);
  }

  async function loadAll() {
    try {
      const loaded = await Cms.api.request('/api/roles');
      roles = loaded || [];
      try {
        permissions = (await Cms.api.request('/api/permissions')) || [];
      } catch (error) {
        permissions = [];
      }
      renderRoles();
      if (selected) {
        const again = roles.filter((item) => item.id === selected.id)[0];
        if (again) select(again);
      }
    } catch (error) {
      showAlert(error.message || '角色列表加载失败');
    }
  }

  async function createRole(event) {
    event.preventDefault();
    const code = el('new-code').value.trim().toUpperCase();
    const name = el('new-name').value.trim();
    if (!/^[A-Z][A-Z0-9_]{1,39}$/.test(code)) {
      showAlert('角色代码需为大写字母开头的大写字母、数字或下划线');
      return;
    }
    if (name.length < 2 || name.length > 80) {
      showAlert('角色名称需为 2~80 个字符');
      return;
    }
    const button = el('create-submit');
    button.disabled = true;
    try {
      await Cms.api.request('/api/roles', { method: 'POST', body: { code: code, name: name } });
      el('new-code').value = '';
      el('new-name').value = '';
      showAlert('已创建角色 ' + code, true);
      await loadAll();
    } catch (error) {
      showAlert(error.message || '创建角色失败');
    } finally {
      button.disabled = false;
    }
  }

  async function saveName() {
    if (!selected) return;
    const name = el('edit-name').value.trim();
    if (name.length < 2 || name.length > 80) {
      showAlert('角色名称需为 2~80 个字符');
      return;
    }
    try {
      await Cms.api.request('/api/roles/' + selected.id, { method: 'PUT', body: { name: name } });
      showAlert('已更新角色名称', true);
      await loadAll();
    } catch (error) {
      showAlert(error.message || '更新角色失败');
    }
  }

  async function savePermissions() {
    if (!selected) return;
    const codes = Array.from(el('permission-groups').querySelectorAll('input[type=checkbox]'))
      .filter((box) => box.checked)
      .map((box) => box.value);
    const button = el('save-permissions');
    button.disabled = true;
    try {
      await Cms.api.request('/api/roles/' + selected.id + '/permissions', {
        method: 'PUT',
        body: { permissionCodes: codes }
      });
      showAlert('已保存 ' + selected.code + ' 的 ' + codes.length + ' 个权限', true);
      await loadAll();
    } catch (error) {
      showAlert(error.message || '保存权限失败');
      button.disabled = false;
    }
  }

  async function start() {
    await Cms.shell.ready;
    if (!Cms.shell.user) {
      window.location.replace(Cms.shell.link('pages/auth/login.html?next=' +
        encodeURIComponent(window.location.pathname + window.location.search)));
      return;
    }
    if (!Cms.shell.hasPermission(REQUIRED)) {
      el('denied').hidden = false;
      return;
    }
    el('workspace').hidden = false;
    el('create-form').addEventListener('submit', createRole);
    el('save-name').addEventListener('click', saveName);
    el('save-permissions').addEventListener('click', savePermissions);
    await loadAll();
  }

  window.addEventListener('cms:authchange', function (event) {
    if (!event.detail || !event.detail.user) {
      el('workspace').hidden = true;
      el('editor').hidden = true;
      el('denied').hidden = true;
    }
  });

  start();
})();
