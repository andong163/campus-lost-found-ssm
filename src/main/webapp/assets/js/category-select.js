/*
 * 分类选择组件（SR R3）。
 *
 * 数据来自公开分类树 /api/public/categories：只包含自身与全部祖先都启用的分类，
 * 因此在这里选出来的值一定能绑定到内容，页面不必自己再判一次可绑定。
 *
 * 用法：
 *   var picker = Cms.CategorySelect.mount(document.getElementById('category-host'), {
 *       label: '所属分类', placeholder: '请选择分类',
 *       onChange: function (id) { ... }
 *   });
 *   picker.getValue();      // number|null
 *   picker.setValue(12);
 *   picker.reload();        // 重新拉取
 */
(function (global) {
    'use strict';

    var Cms = global.Cms = global.Cms || {};
    var TREE_PATH = '/api/public/categories';

    function flatten(nodes, depth, out) {
        (nodes || []).forEach(function (node) {
            out.push({ id: node.id, name: node.name, depth: depth });
            flatten(node.children, depth + 1, out);
        });
    }

    function indent(depth) {
        var pad = '';
        for (var i = 0; i < depth; i += 1) {
            pad += '\u3000';
        }
        return pad;
    }

    function loadTree(base) {
        return fetch(base + TREE_PATH + '?t=' + Date.now(), {
            headers: { Accept: 'application/json' }
        }).then(function (response) {
            if (!response.ok) {
                throw new Error('分类加载失败（' + response.status + '）');
            }
            return response.json();
        }).then(function (body) {
            var flat = [];
            flatten(body.data || [], 0, flat);
            return flat;
        });
    }

    Cms.CategorySelect = {
        /**
         * 把 host 渲染成一个带缩进的分类下拉（单选）。
         * @param {HTMLElement} host 容器
         * @param {{label?: string, name?: string, id?: string, required?: boolean,
         *          placeholder?: string, contextPath?: string, onChange?: function}} options
         * @returns {{element: HTMLSelectElement, getValue: function, setValue: function, reload: function}}
         */
        mount: function (host, options) {
            var settings = options || {};
            var base = settings.contextPath || '';
            var select = document.createElement('select');
            select.className = 'cms-category-select';
            if (settings.name) {
                select.name = settings.name;
            }
            if (settings.id) {
                select.id = settings.id;
            }
            if (settings.required) {
                select.required = true;
            }

            var empty = document.createElement('option');
            empty.value = '';
            empty.textContent = settings.placeholder || '请选择分类';
            select.appendChild(empty);

            if (settings.label) {
                var label = document.createElement('label');
                label.textContent = settings.label;
                label.appendChild(select);
                host.appendChild(label);
            } else {
                host.appendChild(select);
            }

            var picker = {
                element: select,
                getValue: function () {
                    return select.value === '' ? null : Number(select.value);
                },
                setValue: function (value) {
                    select.value = value == null ? '' : String(value);
                },
                reload: function () {
                    return loadTree(base).then(function (flat) {
                        var current = select.value;
                        while (select.options.length > 1) {
                            select.remove(1);
                        }
                        flat.forEach(function (item) {
                            var option = document.createElement('option');
                            option.value = item.id;
                            option.textContent = indent(item.depth) + item.name;
                            select.appendChild(option);
                        });
                        select.value = current;
                        if (typeof settings.onChange === 'function') {
                            settings.onChange(picker.getValue());
                        }
                        return flat;
                    }).catch(function (error) {
                        select.disabled = true;
                        select.title = error.message;
                        throw error;
                    });
                }
            };

            select.addEventListener('change', function () {
                if (typeof settings.onChange === 'function') {
                    settings.onChange(picker.getValue());
                }
            });

            picker.reload().catch(function (error) {
                console.error(error);
            });
            return picker;
        }
    };
})(window);
