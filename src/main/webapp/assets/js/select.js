/* Progressive single-select enhancement. Native names, values and FormData remain authoritative. */
(function (window, document) {
  'use strict';
  let sequence = 0;
  let opened = null;
  class Select {
    constructor(select) {
      if (select.multiple || select.dataset.cmsSelectReady) return;
      this.select = select;
      this.active = -1;
      this.opened = false;
      this.search = '';
      this.searchTime = 0;
      const id = 'cms-select-' + (++sequence);
      this.wrapper = document.createElement('div');
      this.wrapper.className = 'cms-select';
      this.trigger = document.createElement('button');
      this.trigger.type = 'button';
      this.trigger.id = id;
      this.trigger.className = 'cms-select-trigger';
      this.trigger.setAttribute('role', 'combobox');
      this.trigger.setAttribute('aria-haspopup', 'listbox');
      this.trigger.setAttribute('aria-expanded', 'false');
      this.trigger.setAttribute('aria-controls', id + '-list');
      this.value = document.createElement('span');
      this.value.id = id + '-value';
      this.value.className = 'cms-select-value';
      const arrow = document.createElement('span');
      arrow.className = 'cms-select-arrow';
      arrow.textContent = '▾';
      arrow.setAttribute('aria-hidden', 'true');
      this.trigger.append(this.value, arrow);
      this.menu = document.createElement('div');
      this.menu.id = id + '-list';
      this.menu.className = 'cms-select-menu';
      this.menu.setAttribute('role', 'listbox');
      this.menu.hidden = true;
      this.labels = Array.from(select.labels || []);
      for (const [index, label] of this.labels.entries()) {
        if (!label.id) label.id = id + '-label-' + index;
        label.htmlFor = id;
      }
      const labelIds = this.labels.map(label => label.id).join(' ');
      if (labelIds) {
        this.trigger.setAttribute('aria-labelledby', labelIds + ' ' + this.value.id);
        this.menu.setAttribute('aria-labelledby', labelIds);
      } else {
        const label = select.getAttribute('aria-label') || select.name;
        this.trigger.setAttribute('aria-label', label);
        this.menu.setAttribute('aria-label', label);
      }
      select.before(this.wrapper);
      this.wrapper.append(select, this.trigger);
      document.body.append(this.menu); // Portal: parent overflow must not clip the expanded menu.
      select.hidden = true;
      select.dataset.cmsSelectReady = 'true';
      this.click = () => this.opened ? this.close() : this.open();
      this.keydown = event => this.onKeydown(event);
      this.outside = event => {
        if (this.opened && !this.wrapper.contains(event.target) && !this.menu.contains(event.target)) this.close(false);
      };
      this.focusOutside = event => {
        if (this.opened && !this.wrapper.contains(event.target) && !this.menu.contains(event.target)) this.close(false);
      };
      this.reposition = event => {
        if (this.opened && !this.menu.contains(event.target)) this.position();
      };
      this.reset = () => window.requestAnimationFrame(() => { this.close(false); this.sync(); });
      this.changed = () => this.sync();
      this.trigger.addEventListener('click', this.click);
      this.trigger.addEventListener('keydown', this.keydown);
      select.addEventListener('change', this.changed);
      select.form?.addEventListener('reset', this.reset);
      document.addEventListener('pointerdown', this.outside);
      document.addEventListener('focusin', this.focusOutside);
      window.addEventListener('resize', this.reposition);
      window.addEventListener('scroll', this.reposition, true);
      this.observer = new MutationObserver(() => this.sync());
      this.observer.observe(select, { childList: true, subtree: true, attributes: true, characterData: true,
        attributeFilter: ['disabled', 'selected', 'label', 'value', 'hidden', 'aria-invalid'] });
      this.sync();
    }
    sync() {
      this.trigger.disabled = this.select.disabled;
      this.trigger.setAttribute('aria-required', String(this.select.required));
      if (this.select.hasAttribute('aria-invalid')) this.trigger.setAttribute('aria-invalid', this.select.getAttribute('aria-invalid'));
      else this.trigger.removeAttribute('aria-invalid');
      const selected = this.select.selectedOptions[0];
      this.value.textContent = selected ? selected.label : '请选择';
      this.options = Array.from(this.select.options).filter(option => !option.hidden);
      this.menu.replaceChildren();
      this.rows = this.options.map((option, index) => {
        const row = document.createElement('div');
        row.id = this.menu.id + '-option-' + index;
        row.className = 'cms-select-option';
        row.setAttribute('role', 'option');
        row.setAttribute('aria-selected', String(option.selected));
        row.setAttribute('aria-disabled', String(this.disabled(option)));
        const label = document.createElement('span');
        label.textContent = option.label;
        const check = document.createElement('span');
        check.className = 'cms-select-check';
        check.textContent = option.selected ? '✓' : '';
        check.setAttribute('aria-hidden', 'true');
        row.append(label, check);
        row.addEventListener('mousedown', event => event.preventDefault());
        row.addEventListener('pointermove', () => { if (!this.disabled(option)) this.activate(index, false); });
        row.addEventListener('click', () => this.choose(index));
        this.menu.append(row);
        return row;
      });
      if (this.select.disabled) this.close(false);
      else if (this.opened) {
        this.activate(this.options.findIndex(option => option.selected));
        this.position();
      }
    }
    disabled(option) { return option.disabled || (option.parentElement.tagName === 'OPTGROUP' && option.parentElement.disabled); }
    open(edge) {
      if (this.select.disabled) return;
      if (opened && opened !== this) opened.close(false);
      opened = this;
      this.sync(); // Also pick up external select.value assignments before opening.
      this.opened = true;
      this.menu.hidden = false;
      this.trigger.setAttribute('aria-expanded', 'true');
      let index = edge === 'first' ? this.options.findIndex(option => !this.disabled(option))
        : edge === 'last' ? this.options.map(option => !this.disabled(option)).lastIndexOf(true)
        : this.options.findIndex(option => option.selected && !this.disabled(option));
      if (index < 0) index = this.options.findIndex(option => !this.disabled(option));
      this.position();
      this.activate(index);
    }
    close(restoreFocus = true) {
      if (!this.opened) return;
      this.opened = false;
      this.menu.hidden = true;
      this.trigger.setAttribute('aria-expanded', 'false');
      this.trigger.removeAttribute('aria-activedescendant');
      this.search = '';
      if (opened === this) opened = null;
      if (restoreFocus) this.trigger.focus({ preventScroll: true });
    }
    activate(index, scroll = true) {
      this.active = index;
      this.rows.forEach((row, current) => row.dataset.active = String(current === index));
      const row = this.rows[index];
      if (row) {
        this.trigger.setAttribute('aria-activedescendant', row.id);
        if (scroll) row.scrollIntoView({ block: 'nearest' });
      }
    }
    move(direction) {
      for (let index = this.active + direction; index >= 0 && index < this.options.length; index += direction) {
        if (!this.disabled(this.options[index])) { this.activate(index); return; }
      }
    }
    choose(index) {
      const option = this.options[index];
      if (!option || this.disabled(option)) return;
      const changed = this.select.value !== option.value;
      this.select.value = option.value;
      this.close();
      this.sync();
      if (changed) {
        this.select.dispatchEvent(new Event('input', { bubbles: true }));
        this.select.dispatchEvent(new Event('change', { bubbles: true }));
      }
    }
    onKeydown(event) {
      const key = event.key;
      if (key === 'Tab') { this.close(false); return; }
      if (key === 'Escape') {
        if (this.opened) { event.preventDefault(); event.stopPropagation(); this.close(); }
        return;
      }
      if (['ArrowDown', 'ArrowUp', 'Home', 'End', 'Enter', ' '].includes(key)) {
        event.preventDefault();
        event.stopPropagation();
        if (key === 'Home' || key === 'End') {
          if (!this.opened) this.open(key === 'Home' ? 'first' : 'last');
          else this.activate(key === 'Home' ? this.options.findIndex(option => !this.disabled(option))
            : this.options.map(option => !this.disabled(option)).lastIndexOf(true));
        } else if (key === 'ArrowDown' || key === 'ArrowUp') {
          if (!this.opened) this.open(); else this.move(key === 'ArrowDown' ? 1 : -1);
        } else if (this.opened) this.choose(this.active); else this.open();
      } else if (key.length === 1 && !event.ctrlKey && !event.metaKey && !event.altKey) {
        event.preventDefault();
        if (!this.opened) this.open();
        const now = Date.now();
        this.search = now - this.searchTime < 700 ? this.search + key : key;
        this.searchTime = now;
        const prefix = this.search.toLocaleLowerCase();
        const index = this.options.findIndex(option => !this.disabled(option) && option.label.trim().toLocaleLowerCase().startsWith(prefix));
        if (index >= 0) this.activate(index);
      }
    }
    position() {
      const box = this.trigger.getBoundingClientRect();
      const gap = 6, inset = 8, width = document.documentElement.clientWidth, height = window.innerHeight;
      this.menu.style.width = Math.min(box.width, width - inset * 2) + 'px';
      this.menu.style.left = Math.max(inset, Math.min(box.left, width - box.width - inset)) + 'px';
      const below = height - box.bottom - gap - inset, above = box.top - gap - inset;
      const up = below < Math.min(200, this.menu.scrollHeight) && above > below;
      this.menu.style.maxHeight = Math.max(38, Math.min(260, up ? above : below)) + 'px';
      this.menu.style.top = Math.max(inset, up ? box.top - gap - this.menu.offsetHeight : box.bottom + gap) + 'px';
    }
    destroy() {
      this.close(false);
      this.observer.disconnect();
      this.trigger.removeEventListener('click', this.click);
      this.trigger.removeEventListener('keydown', this.keydown);
      this.select.removeEventListener('change', this.changed);
      this.select.form?.removeEventListener('reset', this.reset);
      document.removeEventListener('pointerdown', this.outside);
      document.removeEventListener('focusin', this.focusOutside);
      window.removeEventListener('resize', this.reposition);
      window.removeEventListener('scroll', this.reposition, true);
      this.labels.forEach(label => label.htmlFor = this.select.id);
      this.wrapper.before(this.select);
      this.wrapper.remove(); this.menu.remove();
      this.select.hidden = false;
      delete this.select.dataset.cmsSelectReady;
    }
  }
  const Cms = window.Cms = window.Cms || {};
  Cms.Select = Select;
  document.querySelectorAll('select[data-cms-select]').forEach(select => new Select(select));
})(window, document);
