(function (window, document) {
  'use strict';
  const Cms = window.Cms;

  function node(tag, className, text) {
    const element = document.createElement(tag);
    if (className) element.className = className;
    if (text != null) element.textContent = String(text);
    return element;
  }

  function idBadge(id) {
    return node('span', 'item-id', '#' + id);
  }

  function timeText(value, short = false) {
    if (typeof value !== 'string' || !value) return '—';
    return value.replace('T', ' ').slice(short ? 5 : 0, 16);
  }

  function typeBadge(item) {
    const found = item.itemType === 'FOUND';
    return node('span', 'cms-badge ' + (found ? 'cms-badge-positive' : 'cms-badge-solid'), found ? '招领' : '寻物');
  }

  function statusBadge(item) {
    const resolved = item.resolutionStatus === 'RESOLVED';
    const label = resolved ? '已结案' : (item.itemType === 'FOUND' ? '待认领' : '寻找中');
    return node('span', 'item-card-status' + (resolved ? ' is-resolved' : ''), label);
  }

  function card(item, index) {
    const link = node('a', 'cms-item-card');
    link.href = Cms.shell.link('pages/items/public-detail.html?' + new URLSearchParams({id: item.id}));
    link.style.setProperty('--item-order', String(Math.min(index, 9)));
    const badges = node('div', 'item-card-badges');
    const identifiers = node('span', 'item-card-identifiers');
    identifiers.append(typeBadge(item), idBadge(item.id));
    badges.append(identifiers, statusBadge(item));
    const meta = node('div', 'item-card-meta');
    const category = item.categoryName || item.categoryPath;
    if (category) meta.append(node('span', 'item-card-category', category));
    const place = node('span', 'item-card-place', item.location);
    if (item.location) place.title = item.location;
    meta.append(place);
    if (item.eventTime) {
      const time = node('time', '', timeText(item.eventTime, true));
      time.dateTime = item.eventTime;
      meta.append(time);
    }
    link.append(badges, node('h3', '', item.title), meta);
    return link;
  }

  Cms.itemView = { idBadge, timeText, typeBadge, statusBadge, card };
})(window, document);
