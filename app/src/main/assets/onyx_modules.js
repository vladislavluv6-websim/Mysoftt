(function () {
  'use strict';

  var state = { byKey: {}, modules: [] };
  var pending = {};
  var colorTarget = null;
  var dropdownTarget = null;
  var dropdownOptions = null;
  var dropdownCallback = null;

  function bridge(name) {
    try {
      if (window.OnyxBridge && typeof window.OnyxBridge[name] === 'function') {
        return window.OnyxBridge[name].apply(window.OnyxBridge, Array.prototype.slice.call(arguments, 1));
      }
    } catch (e) {}
    return null;
  }

  function esc(value) {
    return String(value == null ? '' : value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function num(value, fallback) {
    var parsed = parseFloat(value);
    return isFinite(parsed) ? parsed : fallback;
  }

  function normalizeHex(value, fallback) {
    var raw = String(value == null ? '' : value).trim();
    if (!raw) return fallback || '#FF4B7EFF';
    if (raw.charAt(0) !== '#') raw = '#' + raw;
    if (raw.length === 7) return '#FF' + raw.substring(1).toUpperCase();
    if (raw.length === 9) return raw.toUpperCase();
    if (raw.length === 4) {
      return '#FF' + raw.charAt(1) + raw.charAt(1) + raw.charAt(2) + raw.charAt(2) + raw.charAt(3) + raw.charAt(3);
    }
    return fallback || '#FF4B7EFF';
  }

  function hex6(argb) {
    var value = normalizeHex(argb);
    return '#' + value.substring(3);
  }

  function colorCss(argb) {
    var value = normalizeHex(argb);
    var a = parseInt(value.substring(1, 3), 16) / 255;
    var r = parseInt(value.substring(3, 5), 16);
    var g = parseInt(value.substring(5, 7), 16);
    var b = parseInt(value.substring(7, 9), 16);
    return 'rgba(' + r + ',' + g + ',' + b + ',' + a.toFixed(3) + ')';
  }

  function toArgb(hex) {
    var value = normalizeHex(hex);
    if (value.length === 9) return value;
    return '#FF' + value.substring(1);
  }

  function formatNumber(value, decimals) {
    if (decimals > 0) return Number(value).toFixed(decimals);
    return String(Math.round(value));
  }

  function categoryFor(mod) {
    if (mod.category) return mod.category;
    var id = String(mod.id || '').toLowerCase();
    if (mod.source === 'external') return 'misc';
    if (id === 'aim_assist' || id === 'trigger_bot' || id === 'target_esp' || id === 'swing' || id === 'friend') return 'combat';
    if (id === 'speed' || id === 'fly' || id === 'jesus' || id === 'scaffold' || id === 'auto_jump' || id === 'auto_sprint' || id === 'zoom' || id === 'snaplook' || id === 'gyro' || id === 'virtual_cursor' || id === 'quick_drop' || id === 'camera_perspective') return 'movement';
    if (id === 'esp' || id === 'name_tags' || id === 'fullbright' || id === 'toggle_hud' || id === 'fps_display' || id === 'cps_display') return 'visual';
    return 'misc';
  }

  function pageFor(category) {
    if (category === 'visual') return document.getElementById('page-visual');
    if (category === 'movement') return document.getElementById('page-movement');
    if (category === 'misc') return document.getElementById('page-misc');
    return document.getElementById('page-combat');
  }

  function configValue(mod, config) {
    if (config.value != null) return config.value;
    return config.default_value != null ? config.default_value : '';
  }

  function controlHtml(mod, config) {
    var value = configValue(mod, config);
    var type = config.type || 'toggle';
    if (type === 'toggle') {
      var on = value === true || value === 'true' || value === '1';
      return '<div class="nl-toggle' + (on ? ' active' : '') + '" data-role="cfg-toggle"><div class="nl-toggle-track"><div class="nl-toggle-thumb"></div></div></div>';
    }
    if (type === 'slider_int' || type === 'slider_float') {
      var min = num(config.min_value, 0);
      var max = num(config.max_value, 100);
      var current = num(value, min);
      var decimals = type === 'slider_float' ? (config.decimals != null ? config.decimals : 2) : 0;
      var ratio = max > min ? (current - min) / (max - min) : 0;
      ratio = Math.max(0, Math.min(1, ratio));
      return '<div class="nl-slider-container">' +
        '<div class="nl-slider-track" data-role="slider" data-min="' + min + '" data-max="' + max + '" data-decimals="' + decimals + '" data-val="' + current + '">' +
        '<div class="nl-slider-fill" style="width:' + (ratio * 100).toFixed(2) + '%"></div>' +
        '<div class="nl-slider-thumb" style="left:' + (ratio * 100).toFixed(2) + '%"></div></div>' +
        '<div class="nl-value-pill">' + esc(formatNumber(current, decimals)) + '</div></div>';
    }
    if (type === 'choice' || type === 'radio') {
      var options = config.options || [];
      var selected = null;
      for (var i = 0; i < options.length; i++) {
        if (String(options[i].value) === String(value)) selected = options[i];
      }
      if (!selected && options.length) selected = options[0];
      return '<div class="nl-select-btn" data-role="choice"><span class="label">' + esc(selected ? selected.label : '') + '</span><span>❯</span></div>';
    }
    if (type === 'color') {
      return '<div class="color-chip" data-role="color" style="background:' + colorCss(value) + '"></div>';
    }
    if (type === 'keybind') {
      return '<div class="nl-select-btn" data-role="keybind"><span class="label">' + esc(config.key_label || 'None') + '</span><span>❯</span></div>';
    }
    if (type === 'button') {
      return '<div class="nl-select-btn" data-role="button"><span class="label">' + esc(config.label || 'Open') + '</span><span>❯</span></div>';
    }
    return '<input class="nl-text-input" type="text" data-role="text" value="' + esc(value) + '">';
  }

  function rowHtml(mod, config) {
    var depends = config.depends ? ' data-depends="' + esc(config.depends) + '"' : '';
    return '<div class="setting-row" data-key="' + esc(config.key) + '"' + depends + '>' +
      '<span class="setting-label">' + esc(config.label) + '</span>' +
      controlHtml(mod, config) +
      '</div>';
  }

  function cardHtml(mod) {
    return '<div class="module-card" data-key="' + esc(mod.key) + '">' +
      '<div class="module-header">' +
        '<div class="module-left">' +
          '<div class="module-arrow">❯</div>' +
          '<span class="module-name">' + esc(mod.name) + '</span>' +
          '<span class="module-desc">' + esc(mod.desc) + '</span>' +
        '</div>' +
        '<div class="module-controls">' +
          '<div class="nl-toggle' + (mod.enabled ? ' active' : '') + '" data-role="module-toggle"><div class="nl-toggle-track"><div class="nl-toggle-thumb"></div></div></div>' +
        '</div>' +
      '</div>' +
      '<div class="module-settings"></div>' +
    '</div>';
  }

  function buildSettings(card, mod) {
    if (mod.built) return;
    mod.built = true;
    var settings = card.querySelector('.module-settings');
    if (!settings) return;
    var html = '';
    if (mod.shortcut_supported !== false) {
      html += '<div class="setting-row shortcut-row">' +
        '<span class="setting-label">Shortcut</span>' +
        '<input class="nl-checkbox" type="checkbox" data-role="shortcut"' + (mod.shortcut ? ' checked' : '') + '>' +
        '</div>';
    }
    var configs = mod.configs || [];
    for (var i = 0; i < configs.length; i++) html += rowHtml(mod, configs[i]);
    settings.innerHTML = html;

    var shortcut = settings.querySelector('[data-role="shortcut"]');
    if (shortcut) {
      shortcut.addEventListener('change', function () {
        mod.shortcut = shortcut.checked;
        bridge('setShortcut', mod.key, shortcut.checked);
      });
    }

    settings.querySelectorAll('.setting-row[data-key]').forEach(function (row) {
      var key = row.getAttribute('data-key');
      var config = null;
      for (var i = 0; i < mod.configs.length; i++) {
        if (mod.configs[i].key === key) { config = mod.configs[i]; break; }
      }
      if (!config) return;
      wireRow(row, mod, config);
    });

    updateDependencies(mod.key);
  }

  function ensureBuilt(card) {
    if (!card) return;
    var mod = state.byKey[card.getAttribute('data-key')];
    if (mod && !mod.built) buildSettings(card, mod);
  }

  function wireRow(row, mod, config) {
    var key = config.key;
    var toggle = row.querySelector('[data-role="cfg-toggle"]');
    if (toggle) {
      toggle.addEventListener('click', function (event) {
        event.stopPropagation();
        var on = !toggle.classList.contains('active');
        setToggleState(toggle, on);
        scheduleConfig(mod.key, key, on ? 'true' : 'false');
      });
    }

    var slider = row.querySelector('[data-role="slider"]');
    if (slider) wireSlider(slider, mod, config);

    var choice = row.querySelector('[data-role="choice"]');
    if (choice) {
      choice.addEventListener('click', function (event) {
        event.stopPropagation();
        var values = (config.options || []).map(function (option) {
          return { value: option.value, label: option.label };
        });
        openChoice(choice, values, function (selected) {
          scheduleConfig(mod.key, key, selected.value);
        });
      });
    }

    var chip = row.querySelector('[data-role="color"]');
    if (chip) {
      chip.addEventListener('click', function (event) {
        event.stopPropagation();
        openColor(chip, mod, config);
      });
    }

    var keybind = row.querySelector('[data-role="keybind"]');
    if (keybind) {
      keybind.addEventListener('click', function (event) {
        event.stopPropagation();
        bridge('captureKeybind', mod.key, key);
      });
    }

    var button = row.querySelector('[data-role="button"]');
    if (button) {
      button.addEventListener('click', function (event) {
        event.stopPropagation();
        bridge('setConfig', mod.key, key, '1');
      });
    }

    var text = row.querySelector('[data-role="text"]');
    if (text) {
      text.addEventListener('change', function () {
        scheduleConfig(mod.key, key, text.value);
      });
      text.addEventListener('click', function (event) { event.stopPropagation(); });
    }
  }

  function wireCard(card, mod) {
    card.querySelector('.module-header').addEventListener('click', function (event) {
      if (event.target.closest('.module-controls')) return;
      var expanded = card.classList.toggle('expanded');
      if (expanded) ensureBuilt(card);
    });

    var moduleToggle = card.querySelector('[data-role="module-toggle"]');
    moduleToggle.addEventListener('click', function (event) {
      event.stopPropagation();
      var on = !moduleToggle.classList.contains('active');
      setToggleState(moduleToggle, on);
      mod.enabled = on;
      bridge('setEnabled', mod.key, on);
    });
  }

  function flushConfig(modKey, configKey) {
    var id = modKey + '|' + configKey;
    var item = pending[id];
    if (!item) return;
    delete pending[id];
    bridge('setConfig', modKey, configKey, String(item.value));
  }

  function scheduleConfig(modKey, configKey, value) {
    var id = modKey + '|' + configKey;
    if (pending[id] && pending[id].timer) clearTimeout(pending[id].timer);
    var item = { value: value, timer: null };
    item.timer = setTimeout(function () { flushConfig(modKey, configKey); }, 70);
    pending[id] = item;
    var mod = state.byKey[modKey];
    if (mod && mod.configValues) mod.configValues[configKey] = String(value);
    updateDependencies(modKey);
  }

  function updateDependencies(modKey) {
    var mod = state.byKey[modKey];
    var card = document.querySelector('.module-card[data-key="' + cssEscape(modKey) + '"]');
    if (!mod || !card) return;
    card.querySelectorAll('[data-depends]').forEach(function (row) {
      var parent = row.getAttribute('data-depends');
      var on = mod.configValues && (mod.configValues[parent] === 'true' || mod.configValues[parent] === '1' || mod.configValues[parent] === true);
      row.style.display = on ? '' : 'none';
    });
  }

  function cssEscape(value) {
    if (window.CSS && typeof window.CSS.escape === 'function') return window.CSS.escape(value);
    return String(value).replace(/(["\\])/g, '\\$1');
  }

  function setToggleState(toggle, on) {
    if (on) toggle.classList.add('active');
    else toggle.classList.remove('active');
  }

  function wireSlider(track, mod, config) {
    var min = num(track.getAttribute('data-min'), 0);
    var max = num(track.getAttribute('data-max'), 100);
    var decimals = num(track.getAttribute('data-decimals'), 0);
    var active = false;

    function ratioFromEvent(event) {
      var rect = track.getBoundingClientRect();
      var clientX = event.touches && event.touches.length ? event.touches[0].clientX : event.clientX;
      var ratio = (clientX - rect.left) / rect.width;
      return Math.max(0, Math.min(1, ratio));
    }

    function apply(event) {
      var ratio = ratioFromEvent(event);
      var raw = min + (max - min) * ratio;
      var value = decimals > 0 ? Number(raw.toFixed(decimals)) : Math.round(raw);
      var percent = (ratio * 100).toFixed(2);
      var fill = track.querySelector('.nl-slider-fill');
      var thumb = track.querySelector('.nl-slider-thumb');
      var pill = track.closest('.nl-slider-container').querySelector('.nl-value-pill');
      if (fill) fill.style.width = percent + '%';
      if (thumb) thumb.style.left = percent + '%';
      if (pill) pill.textContent = formatNumber(value, decimals);
      track.setAttribute('data-val', String(value));
      scheduleConfig(mod.key, config.key, value);
    }

    function start(event) {
      event.preventDefault();
      event.stopPropagation();
      active = true;
      apply(event);
      window.addEventListener('mousemove', move);
      window.addEventListener('mouseup', end);
      window.addEventListener('touchmove', move, { passive: false });
      window.addEventListener('touchend', end);
    }

    function move(event) {
      if (!active) return;
      event.preventDefault();
      apply(event);
    }

    function end() {
      if (!active) return;
      active = false;
      flushConfig(mod.key, config.key);
      window.removeEventListener('mousemove', move);
      window.removeEventListener('mouseup', end);
      window.removeEventListener('touchmove', move);
      window.removeEventListener('touchend', end);
    }

    track.addEventListener('mousedown', start);
    track.addEventListener('touchstart', start, { passive: false });
  }

  function relocatePopups() {
    var dd = document.getElementById('modalDropdown');
    if (dd && dd.parentNode !== document.body) document.body.appendChild(dd);
    var cm = document.getElementById('colorPickerModal');
    if (cm && cm.parentNode !== document.body) document.body.appendChild(cm);
  }

  function openChoice(button, options, callback) {
    var dd = document.getElementById('modalDropdown');
    if (!dd) return;
    closeAllPopups();
    dropdownTarget = button;
    dropdownOptions = options;
    dropdownCallback = callback;
    var selected = button.querySelector('.label') ? button.querySelector('.label').textContent : '';
    var html = '';
    options.forEach(function (option, index) {
      var isSelected = option.label === selected;
      html += '<div class="dropdown-option' + (isSelected ? ' selected' : '') + '" data-index="' + index + '">' +
        '<span class="check">' + (isSelected ? '✓' : '') + '</span><span>' + esc(option.label) + '</span></div>';
    });
    dd.innerHTML = html;
    dd.style.left = '0px';
    dd.style.top = '0px';
    dd.classList.add('open');
    var rect = button.getBoundingClientRect();
    var w = dd.offsetWidth || 150;
    var h = dd.offsetHeight || 120;
    var left = Math.max(8, Math.min(rect.left, window.innerWidth - w - 8));
    var top = rect.bottom + 4;
    if (top + h > window.innerHeight - 8) top = rect.top - h - 4;
    if (top < 8) top = 8;
    dd.style.left = left + 'px';
    dd.style.top = top + 'px';
    dd.querySelectorAll('.dropdown-option').forEach(function (option) {
      option.addEventListener('click', function (event) {
        event.stopPropagation();
        selectChoice(parseInt(option.getAttribute('data-index'), 10));
      });
    });
  }

  function selectChoice(index) {
    if (!dropdownOptions || !dropdownOptions[index]) return;
    var option = dropdownOptions[index];
    if (dropdownTarget && dropdownTarget.querySelector('.label')) {
      dropdownTarget.querySelector('.label').textContent = option.label;
    }
    if (dropdownCallback) dropdownCallback(option);
    closeAllPopups();
  }

  function installColorHook() {
    if (window._onyxColorHookInstalled) return;
    window._onyxColorHookInstalled = true;
    var base = window.applyColor;
    window.applyColor = function (hex) {
      if (typeof base === 'function') base(hex);
      if (!colorTarget) return;
      var argb = toArgb(hex);
      colorTarget.chip.style.background = colorCss(argb);
      scheduleConfig(colorTarget.modKey, colorTarget.configKey, argb);
    };
  }

  function openColor(chip, mod, config) {
    installColorHook();
    colorTarget = { chip: chip, modKey: mod.key, configKey: config.key };
    if (typeof window.openColorPopup === 'function') {
      window.openColorPopup(chip, { stopPropagation: function () {} });
    }
    var value = configValue(mod, config);
    var six = hex6(value);
    var text = document.getElementById('colorHexInput');
    var native = document.getElementById('nativeColorPicker');
    if (text) text.value = six;
    if (native) native.value = six;
  }

  function render(modules) {
    state.modules = modules || [];
    state.byKey = {};
    var pages = {
      combat: document.getElementById('page-combat'),
      movement: document.getElementById('page-movement'),
      visual: document.getElementById('page-visual'),
      misc: document.getElementById('page-misc')
    };
    Object.keys(pages).forEach(function (key) {
      if (pages[key]) pages[key].innerHTML = '';
    });

    state.modules.forEach(function (mod) {
      mod.key = mod.key || mod.id;
      mod.configs = mod.configs || [];
      mod.configValues = {};
      mod.built = false;
      mod.configs.forEach(function (config) {
        mod.configValues[config.key] = String(configValue(mod, config));
      });
      state.byKey[mod.key] = mod;
      var page = pageFor(categoryFor(mod));
      if (!page) return;
      var holder = document.createElement('div');
      holder.innerHTML = cardHtml(mod);
      var card = holder.firstElementChild;
      page.appendChild(card);
      wireCard(card, mod);
    });

    var search = document.getElementById('nlSearchInput');
    if (search && search.value) window.handleSearch(search.value);
  }

  window.onyxInit = function (modules) {
    relocatePopups();
    render(Array.isArray(modules) ? modules : []);
  };

  window.onyxKeybindResult = function (modKey, configKey, label) {
    var card = document.querySelector('.module-card[data-key="' + cssEscape(modKey) + '"]');
    if (!card) return;
    var row = card.querySelector('.setting-row[data-key="' + cssEscape(configKey) + '"]');
    if (!row) return;
    var labelView = row.querySelector('.label');
    if (labelView) labelView.textContent = label || 'None';
  };

  var baseHandleSearch = window.handleSearch;
  window.handleSearch = function (query) {
    if (typeof baseHandleSearch === 'function') baseHandleSearch(query);
    document.querySelectorAll('.module-card.expanded').forEach(ensureBuilt);
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', relocatePopups);
  } else {
    relocatePopups();
  }

  window.toggleMenuVisibility = function () {
    bridge('hideMenu');
  };

  document.addEventListener('click', function (event) {
    if (event.target.closest('.nl-select-btn')) return;
    if (event.target.closest('.nl-modal-dropdown')) return;
    var dd = document.getElementById('modalDropdown');
    if (dd) dd.classList.remove('open');
  });
})();
