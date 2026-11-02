(function (window, document) {
  'use strict';
  const Cms = window.Cms;
  const target = document.querySelector('#public-categories');
  async function load() {
    try {
      const categories = await Cms.api.request('/api/public/categories');
      target.replaceChildren();
      if (!Array.isArray(categories) || categories.length === 0) {
        const message = document.createElement('span'); message.className = 'empty-text'; message.textContent = '分类整理中，敬请期待。'; target.append(message); return;
      }
      categories.slice(0, 8).forEach(category => { const tag = document.createElement('span'); tag.className = 'tag'; tag.textContent = String(category.name); target.append(tag); });
    } catch (error) {
      target.textContent = '分类暂时无法读取。';
      const retry = document.createElement('button'); retry.type = 'button'; retry.className = 'button button-quiet'; retry.textContent = '重新读取'; retry.addEventListener('click', load); target.append(retry);
    }
  }
  window.addEventListener('cms:authchange', event => { document.querySelector('[data-login]').hidden = !!event.detail.user; });
  load();
})(window, document);
