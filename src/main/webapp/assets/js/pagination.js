(function (window) {
  'use strict';
  const Cms = window.Cms = window.Cms || {};
  function state(value) {
    const page = Number(value.page), size = Number(value.size), total = Number(value.total);
    if (![page, size, total].every(Number.isSafeInteger) || page < 1 || size < 1 || size > 100 || total < 0) throw new Error('分页结果无效');
    const pages = Math.max(1, Math.ceil(total / size));
    return { page, size, total, pages, previous: page > 1, next: page < pages };
  }
  class Pagination {
    constructor(element, onChange, sizes = [5, 10, 20, 50]) {
      this.element = element; this.onChange = onChange;
      this.sizes = sizes.filter(n => Number.isSafeInteger(n) && n > 0 && n <= 100);
      this.value = { page: 1, size: 10, total: 0 }; this.busy = false;
      this.controls = new Map();
    }
    render(value = this.value, busy = false) {
      const current = state(value); this.value = current; this.busy = busy;
      const doc = this.element.ownerDocument;
      this.element.replaceChildren(); this.element.classList.add('pagination');
      this.element.setAttribute('aria-label', '分页');
      this.element.setAttribute('aria-busy', String(busy)); this.controls.clear();
      const summary = doc.createElement('span'); summary.className = 'pagination-summary';
      summary.setAttribute('role', 'status'); summary.setAttribute('aria-atomic', 'true');
      summary.textContent = `共 ${current.total} 条 · 第 ${current.page} / ${current.pages} 页`;
      const select = doc.createElement('select'); select.setAttribute('aria-label', '每页条数'); select.disabled = busy;
      [...new Set([...this.sizes, current.size])].sort((a,b) => a-b).forEach(size => {
        const option = doc.createElement('option'); option.value = String(size); option.textContent = `${size} 条 / 页`; select.append(option);
      });
      select.value = String(current.size);
      this.controls.set('size', select);
      select.addEventListener('change', () => this.change(1, Number(select.value)));
      const controls = doc.createElement('div'); controls.className = 'pagination-controls';
      const button = (text, page, enabled, key) => {
        const el = doc.createElement('button'); el.type = 'button'; el.textContent = text;
        el.className = 'button button-quiet'; el.disabled = busy || !enabled;
        el.addEventListener('click', () => this.change(page, current.size)); this.controls.set(key, el); return el;
      };
      controls.append(select, button('上一页', current.page - 1, current.previous, 'previous'), button('下一页', current.page + 1, current.next, 'next'));
      this.element.append(summary, controls);
    }
    change(page, size) {
      if (this.busy) return;
      const doc = this.element.ownerDocument;
      const focusKey = [...this.controls].find(([, element]) => element === doc.activeElement)?.[0];
      this.render(this.value, true);
      Promise.resolve().then(() => this.onChange({ page, size })).catch(error => {
        if (Cms.shell?.report) Cms.shell.report(error);
      }).finally(() => {
        this.render(this.value, false);
        const active = doc.activeElement;
        if (focusKey && !this.element.hidden && (!active || active === doc.body || [...this.controls.values()].includes(active))) {
          const requested = this.controls.get(focusKey);
          (requested?.disabled ? this.controls.get('size') : requested)?.focus();
        }
      });
    }
  }
  Pagination.state = state;
  Cms.Pagination = Pagination;
})(window);
