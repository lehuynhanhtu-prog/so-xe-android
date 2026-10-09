(() => {
  'use strict';
  const fields = new Map();
  const locale = () => window.SoXeI18n?.locale() || 'vi-VN';
  function separators(lang) {
    const parts = new Intl.NumberFormat(lang).formatToParts(1234.5);
    return { group: parts.find(p => p.type === 'group').value, decimal: parts.find(p => p.type === 'decimal').value };
  }
  function parse(text, precision = 0, lang = locale()) {
    let value = String(text).replace(/\s/g, '');
    if (!value) return { raw: '', trailing: false };
    if (!/^-?[\d.,]+$/.test(value)) return null;
    const { group, decimal } = separators(lang);
    // Decimal keyboards may send a dot even with a Vietnamese locale.
    if (precision && decimal === ',' && !value.includes(',') && value.includes('.')) {
      const pieces = value.split('.');
      if (pieces.length === 2 && pieces[1].length <= precision) value = value.replace('.', ',');
    }
    value = value.split(group).join('');
    const parts = value.split(decimal);
    if (parts.length > 2 || (!precision && parts.length > 1)) return null;
    if (!/^-?\d+$/.test(parts[0]) || (parts.length === 2 && !/^\d*$/.test(parts[1]))) return null;
    if (parts[1]?.length > precision) return null;
    return { raw: parts[0] + (parts.length === 2 ? '.' + parts[1] : ''), trailing: parts.length === 2 && !parts[1] };
  }
  function format(raw, lang = locale(), grouped = true) {
    if (raw === '') return '';
    const [whole, fraction] = String(raw).split('.');
    if (!Number.isFinite(Number(raw))) return String(raw);
    const integer = new Intl.NumberFormat(lang, { useGrouping: grouped, maximumFractionDigits: 0 }).format(Number(whole));
    return integer + (fraction === undefined ? '' : separators(lang).decimal + fraction);
  }
  function message(kind, limit) {
    const en = locale() === 'en-US';
    if (kind === 'invalid') return en ? 'Enter a valid number.' : 'Vui lòng nhập số hợp lệ.';
    if (kind === 'min') return en ? `Minimum value: ${format(limit)}.` : `Giá trị tối thiểu: ${format(limit)}.`;
    if (kind === 'max') return en ? `Maximum value: ${format(limit)}.` : `Giá trị tối đa: ${format(limit)}.`;
    return en ? 'Check the number of decimal places.' : 'Kiểm tra số chữ số thập phân.';
  }
  function validate(input, state) {
    let error = '';
    if (state.raw === null) error = message('invalid');
    else if (state.raw !== '') {
      const value = Number(state.raw);
      if (!Number.isFinite(value)) error = message('invalid');
      else if (state.min !== null && value < state.min) error = message('min', state.min);
      else if (state.max !== null && value > state.max) error = message('max', state.max);
      else if (state.step) {
        const units = (value - (state.min ?? 0)) / state.step;
        if (Math.abs(units - Math.round(units)) > 1e-7) error = message('step');
      }
    }
    input.setCustomValidity(error);
    input.setAttribute('aria-invalid', error ? 'true' : 'false');
  }
  function refresh() {
    fields.forEach((state, input) => {
      // Values assigned by edit forms and fuel calculations are canonical numbers.
      if (input.value !== state.display) state.raw = input.value;
      if (state.raw !== null) {
        input.value = format(state.raw, locale(), state.grouped);
        state.display = input.value;
      }
      validate(input, state);
    });
  }
  function read(id) {
    const input = document.getElementById(id), state = fields.get(input);
    if (!state) return Number(input.value);
    return Number(input.value === state.display ? state.raw ?? NaN : input.value);
  }
  document.querySelectorAll('input[type="number"]').forEach(input => {
    const step = input.getAttribute('step') || '1';
    const state = { raw: input.value, display: input.value, precision: step.includes('.') ? step.split('.')[1].length : 0,
      step: step === 'any' ? 0 : Number(step), min: input.hasAttribute('min') ? Number(input.min) : null,
      max: input.hasAttribute('max') ? Number(input.max) : null, grouped: input.id !== 'carYear' };
    fields.set(input, state);
    input.type = 'text';
    input.inputMode = state.precision ? 'decimal' : 'numeric';
    input.classList.add('numeric-input');
    input.autocomplete = 'off';
    const label = input.closest('.field')?.querySelector('label');
    if (label && !label.htmlFor) label.htmlFor = input.id;
    input.addEventListener('input', () => {
      const value = input.value, caret = input.selectionStart ?? value.length;
      const digitCount = value.slice(0, caret).replace(/\D/g, '').length;
      const parsed = parse(value, state.precision);
      state.raw = parsed?.raw ?? null;
      if (parsed) {
        input.value = format(parsed.raw, locale(), state.grouped);
        let position = input.value.length, digits = 0;
        if (caret !== value.length) {
          position = 0;
          while (position < input.value.length && digits < digitCount) if (/\d/.test(input.value[position++])) digits++;
        }
        input.setSelectionRange(position, position);
      }
      state.display = input.value;
      validate(input, state);
    }, true);
    input.addEventListener('blur', () => {
      if (state.raw !== null && state.raw !== '') state.raw = String(Number(state.raw));
      refresh();
    });
  });
  // Bubble after existing fuel calculation listeners, preserving their updates.
  document.addEventListener('input', refresh);
  document.addEventListener('reset', () => {
    fields.forEach(state => { state.display = null; });
    Promise.resolve().then(refresh);
  });
  window.addEventListener('soxe-language-change', refresh);
  document.querySelectorAll('dialog').forEach(dialog => new MutationObserver(refresh).observe(dialog, { attributes: true, attributeFilter: ['open'] }));
  window.SoXeNumbers = { read, parse, format, refresh };
  refresh();
})();
