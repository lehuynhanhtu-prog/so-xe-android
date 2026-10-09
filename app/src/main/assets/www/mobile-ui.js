(() => {
  'use strict';
  ['expenseCar','expenseDesc','expenseAmount','expenseNote','carPlate','carName','carPowerType'].forEach(id => document.getElementById(id)?.closest('.field')?.classList.add('field-wide'));
  document.querySelectorAll('.nav button').forEach(button => {
    const text = button.textContent.trim(), space = text.indexOf(' ');
    if (space < 0) return;
    const icon = document.createElement('span'), label = document.createElement('span');
    icon.className = 'nav-icon'; icon.setAttribute('aria-hidden', 'true'); icon.textContent = text.slice(0, space);
    label.className = 'nav-label'; label.textContent = text.slice(space + 1);
    button.replaceChildren(icon, label);
  });
  function update() {
    document.querySelectorAll('.nav button').forEach(button => {
      if (button.classList.contains('active')) button.setAttribute('aria-current', 'page');
      else button.removeAttribute('aria-current');
    });
    document.querySelectorAll('#transactions table,.recent-table').forEach(table => {
      const labels = Array.from(table.querySelectorAll('thead th'), th => th.textContent.trim());
      table.querySelectorAll('tbody tr.recent-clickable').forEach(row => {
        Array.from(row.cells).forEach((cell, i) => { if (cell.dataset.label !== labels[i]) cell.dataset.label = labels[i] || ''; });
      });
    });
  }
  let scheduled = false;
  new MutationObserver(() => {
    if (scheduled) return;
    scheduled = true;
    requestAnimationFrame(() => { scheduled = false; update(); });
  }).observe(document.querySelector('.shell'), { subtree: true, childList: true, characterData: true });
  document.querySelector('.nav')?.addEventListener('click', () => setTimeout(update, 0));
  update();
})();
