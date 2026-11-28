(function (window, document) {
  'use strict';
  const Cms = window.Cms;
  const categories = document.getElementById('public-categories');
  const latest = document.getElementById('latest-items');
  const openCount = document.getElementById('public-open-count');

  function element(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
  }

  function showState(container, message, action) {
    const state = element('div', 'home-state');
    state.append(element('p', 'empty-text', message));
    if (action) state.append(action);
    container.replaceChildren(state);
  }

  function retry(label, handler) {
    const button = element('button', 'button button-quiet', label);
    button.type = 'button';
    button.addEventListener('click', handler);
    return button;
  }

  async function loadOverview() {
    try {
      const data = await Cms.api.request('/api/public/items?page=1&size=1&resolutionStatus=OPEN');
      const count = element('strong', 'overview-number', String(data.total));
      openCount.replaceChildren(count, document.createTextNode(' 条公开信息未结案'));
    } catch (_) {
      openCount.textContent = '暂时无法读取未结案数量';
    }
  }

  async function loadItems() {
    showState(latest, '正在加载物品…');
    try {
      const data = await Cms.api.request('/api/public/items?page=1&size=6');
      if (!data.records.length) {
        const link = element('a', 'button button-quiet', '发布寻物 / 招领');
        link.href = Cms.shell.link('pages/items/create.html');
        showState(latest, '暂无公开物品。可以发布一条寻物或招领信息。', link);
        return;
      }
      latest.replaceChildren(...data.records.map(Cms.itemView.card));
    } catch (_) {
      showState(latest, '无法加载物品。', retry('重新加载物品', loadItems));
    }
  }

  async function loadCategories() {
    showState(categories, '正在加载分类…');
    try {
      const list = await Cms.api.request('/api/public/categories');
      if (!list.length) {
        showState(categories, '暂无可用分类。');
        return;
      }
      categories.replaceChildren(...list.map(category => {
        const tag = element('a', 'tag', category.name);
        tag.href = Cms.shell.link('pages/items/public.html?' + new URLSearchParams({categoryId: category.id}));
        return tag;
      }));
    } catch (_) {
      showState(categories, '无法加载分类。', retry('重新加载分类', loadCategories));
    }
  }

  loadItems();
  loadOverview();
  loadCategories();
})(window, document);
