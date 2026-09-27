(() => {
  'use strict';
  const STORAGE_KEY = 'so-xe-language-v1';
  const EN = {
    'Chi phí ô tô':'Vehicle expenses','Tổng quan':'Overview','Chi phí':'Expenses','Báo cáo':'Reports','Xe của tôi':'My vehicles','Cài đặt':'Settings',
    '● Chưa kết nối Drive':'● Drive not connected','Đồng bộ ngay':'Sync now','Tổng quan chi phí':'Expense overview','Tình hình xe trong tháng này':'Vehicle activity this month',
    '+ Thêm xe':'+ Add vehicle','+ Ghi chi phí':'+ Add expense','Chi phí và ODO từng xe trong tháng này':'Monthly expenses and odometer by vehicle',
    'Biển số':'License plate','Tên xe':'Vehicle name','Loại xe':'Vehicle type','Chi phí tháng này':'This month','ODO hiện tại':'Current odometer','Tổng cộng chi phí các xe':'Total vehicle expenses',
    'Chi phí 6 tháng gần nhất':'Expenses over the last 6 months','Sắp đến hạn bảo dưỡng':'Upcoming maintenance','Bảo hiểm TNDS của các xe':'Vehicle liability insurance',
    'Luôn hiển thị bảo hiểm hiện có và cảnh báo nổi bật trong vòng 45 ngày trước khi hết hạn.':'Current insurance is always shown, with a prominent warning during the final 45 days.',
    'Đăng kiểm và phí đường bộ của các xe':'Registration inspection and road-use fees','Luôn hiển thị kỳ hiện có và cảnh báo nổi bật trong vòng 45 ngày trước khi hết hạn.':'Current records are always shown, with a prominent warning during the final 45 days.',
    'Giao dịch gần đây':'Recent transactions','Nhấn vào một dòng để xem chi tiết':'Select a row to view details','Sắp xếp':'Sort','Theo thời gian':'By date','Theo xe':'By vehicle',
    'Ngày':'Date','Xe':'Vehicle','Loại':'Type','ODO':'Odometer','Thành tiền':'Amount','Sổ chi phí':'Expense log','Xăng, bảo dưỡng và các khoản khác':'Fuel, maintenance and other expenses',
    'Tìm nội dung...':'Search transactions...','Tất cả loại':'All types','Xăng':'Fuel','Sạc xe':'Charging','Thuê pin':'Battery rental','Bảo dưỡng':'Maintenance','Phụ tùng':'Parts',
    'Bảo hiểm TNDS':'Liability insurance','Đăng kiểm':'Vehicle inspection','Phí đường bộ':'Road-use fee','Khác':'Other','Nội dung':'Description','Số lượng':'Quantity',
    'Chi phí, ODO và mức tiêu hao tách riêng theo từng xe':'Expenses, odometer and consumption by vehicle','Quản lý ODO và lịch bảo dưỡng':'Manage odometer and maintenance schedules',
    'Cài đặt & dữ liệu':'Settings & data','Đồng bộ an toàn giữa máy tính và điện thoại':'Secure sync across computers and phones','Tải và cài ứng dụng Sổ Xe':'Download and install Sổ Xe',
    'Windows, Android và iPhone/iPad':'Windows, Android and iPhone/iPad','Google Drive':'Google Drive','Quyền riêng tư và điều khoản':'Privacy and terms','Chính sách quyền riêng tư':'Privacy Policy','Điều khoản sử dụng':'Terms of Use',
    'Ngôn ngữ':'Language','Ngôn ngữ hiển thị':'Display language','Thay đổi ngôn ngữ sử dụng trong ứng dụng.':'Change the language used in the app.',
    'Tài khoản Google':'Google account','Kết nối tài khoản Google':'Connect Google account','Ngắt kết nối':'Disconnect','Sao lưu thủ công':'Manual backup',
    'Tải bản dự phòng hoặc phục hồi dữ liệu khi chuyển thiết bị.':'Download a backup or restore data when moving to another device.','✓ Dữ liệu được bảo vệ':'✓ Data protection',
    'Sao lưu dữ liệu':'Back up data','Nên dùng bản ZIP đầy đủ để giữ cả ảnh và tệp đính kèm.':'Use a complete ZIP backup to retain photos and attachments.',
    'Tải bản sao lưu đầy đủ (.zip)':'Download complete backup (.zip)','Bao gồm dữ liệu JSON và các tệp trong thư mục Sổ xe. Cần kết nối Google Drive.':'Includes JSON data and all files in the Sổ xe Drive folder. Google Drive connection required.',
    'Chỉ cần dữ liệu cơ bản?':'Only need the basic data?','Tải riêng file JSON':'Download JSON only','Phục hồi dữ liệu':'Restore data','Hãy tạo một bản sao lưu hiện tại trước khi phục hồi.':'Create a current backup before restoring.',
    'Chọn bản sao lưu ZIP để phục hồi':'Choose ZIP backup to restore','Dữ liệu hiện tại sẽ được thay sau khi ảnh được tải lên Google Drive thành công.':'Current data will be replaced after attachments are successfully uploaded to Google Drive.',
    'File JSON cũ?':'Have an older JSON file?','Nhập file JSON':'Import JSON','Ghi chi phí':'Add expense','Đóng':'Close','Xe *':'Vehicle *','Ngày *':'Date *','Loại chi phí *':'Expense type *',
    'Đổ xăng':'Refueling','Biển số *':'License plate *','Tên xe':'Vehicle name','Loại xe *':'Vehicle type *','Xe xăng/dầu':'Petrol/Diesel','Xe điện':'Electric','Xe Điện-Xăng':'Hybrid',
    'Năm sản xuất':'Model year','Giấy chủ quyền xe (ảnh hoặc PDF, tối đa 15 MB/tệp)':'Vehicle ownership document (image or PDF, up to 15 MB/file)',
    'Có thể sửa giảm sau khi đã sửa các giao dịch nhập sai ODO.':'You can reduce this value after correcting transactions with an incorrect odometer.',
    'Hủy':'Cancel','Lưu xe':'Save vehicle','Lưu':'Save','Sửa':'Edit','Xóa':'Delete','Xem / tải':'View / download','Xem giấy chủ quyền':'View ownership document',
    'Chọn ngôn ngữ':'Choose language','Vui lòng chọn ngôn ngữ sử dụng. Bạn có thể thay đổi lại trong Cài đặt.':'Please choose your language. You can change it later in Settings.',
    'Quay lại Sổ Xe':'Back to Sổ Xe','Tải ứng dụng Sổ Xe':'Download Sổ Xe','Chọn phiên bản phù hợp với thiết bị. Dữ liệu có thể đồng bộ an toàn qua Google Drive.':'Choose the version for your device. Your data can be securely synced with Google Drive.',
    'Bản portable cho Windows 10/11, không cần cài đặt.':'Portable edition for Windows 10/11; no installation required.','Tải và giải nén tệp ZIP.':'Download and extract the ZIP file.',
    'Mở thư mục đã giải nén, chạy Chay-So-Xe.bat.':'Open the extracted folder and run Chay-So-Xe.bat.','Kết nối Google Drive để đồng bộ dữ liệu.':'Connect Google Drive to sync your data.',
    'Dành cho điện thoại và máy tính bảng Android 7.0 trở lên.':'For phones and tablets running Android 7.0 or later.','Tải và mở tệp APK.':'Download and open the APK file.',
    'Cho phép trình duyệt cài ứng dụng nếu được yêu cầu.':'Allow your browser to install the app if prompted.','Cài đè phiên bản cũ để giữ dữ liệu.':'Install over the existing version to retain your data.',
    'Cài Sổ Xe dưới dạng ứng dụng web toàn màn hình trên iPhone hoặc iPad.':'Install Sổ Xe as a full-screen web app on iPhone or iPad.',
    'Mở trang này bằng Safari và tải tệp cấu hình.':'Open this page in Safari and download the configuration profile.','Vào Cài đặt → Đã tải về hồ sơ → Cài đặt.':'Go to Settings → Profile Downloaded → Install.',
    'Mở Sổ Xe từ Màn hình chính và kết nối Google Drive.':'Open Sổ Xe from the Home Screen and connect Google Drive.','☁ Dữ liệu dùng chung an toàn':'☁ Secure shared data'
  };

  let language = localStorage.getItem(STORAGE_KEY) || 'vi';
  const textState = new WeakMap();
  const attrState = new WeakMap();
  let applying = false;

  function translate(value) {
    if (language !== 'en' || typeof value !== 'string') return value;
    const lead = value.match(/^\s*/)[0], tail = value.match(/\s*$/)[0];
    const body = value.slice(lead.length, value.length - tail.length);
    if (EN[body]) return lead + EN[body] + tail;
    const patterns = [
      [/^Tất cả xe$/, 'All vehicles'], [/^Không có dữ liệu$/, 'No data'], [/^Chưa có giao dịch$/, 'No transactions yet'],
      [/^Đã lưu xe$/, 'Vehicle saved'], [/^Đã lưu giao dịch$/, 'Transaction saved'], [/^Đã xóa xe$/, 'Vehicle deleted'], [/^Đã xóa giao dịch$/, 'Transaction deleted'],
      [/^Đang đồng bộ\.\.\.$/, 'Syncing...'], [/^Đã đồng bộ Google Drive$/, 'Google Drive synced'], [/^Có lỗi xảy ra$/, 'Something went wrong'],
      [/^Xóa giao dịch này\?$/, 'Delete this transaction?'], [/^Xóa xe này và toàn bộ dữ liệu liên quan\?$/, 'Delete this vehicle and all related data?'],
      [/^Tải Windows Portable ([\d.]+) \(\.zip\)$/, 'Download Windows Portable $1 (.zip)'], [/^Tải Sổ Xe Android ([\d.]+)$/, 'Download Sổ Xe Android $1']
    ];
    for (const [re, replacement] of patterns) if (re.test(body)) return lead + body.replace(re, replacement) + tail;
    return value;
  }

  function translateText(node) {
    let state = textState.get(node);
    if (!state || node.data !== state.applied) state = { original: node.data, applied: node.data };
    const next = translate(state.original);
    state.applied = next;
    textState.set(node, state);
    if (node.data !== next) node.data = next;
  }

  function translateAttrs(element) {
    const names = ['placeholder','title','aria-label'];
    let states = attrState.get(element) || {};
    names.forEach(name => {
      if (!element.hasAttribute(name)) return;
      const current = element.getAttribute(name);
      let state = states[name];
      if (!state || current !== state.applied) state = { original: current, applied: current };
      const next = translate(state.original);
      state.applied = next;
      states[name] = state;
      if (current !== next) element.setAttribute(name, next);
    });
    attrState.set(element, states);
  }

  function apply(root = document.body) {
    if (!root || applying) return;
    applying = true;
    const visit = node => {
      if (node.nodeType === Node.TEXT_NODE) return translateText(node);
      if (node.nodeType !== Node.ELEMENT_NODE || ['SCRIPT','STYLE','CODE'].includes(node.tagName)) return;
      translateAttrs(node);
      node.childNodes.forEach(visit);
    };
    visit(root);
    document.documentElement.lang = language;
    const select = document.getElementById('languageSelect');
    if (select && select.value !== language) select.value = language;
    applying = false;
  }

  function setLanguage(next) {
    language = next === 'en' ? 'en' : 'vi';
    localStorage.setItem(STORAGE_KEY, language);
    apply(document.body);
    window.dispatchEvent(new CustomEvent('soxe-language-change', { detail: { language } }));
  }

  function init() {
    apply(document.body);
    const select = document.getElementById('languageSelect');
    if (select) select.addEventListener('change', () => setLanguage(select.value));
    document.querySelectorAll('[data-language-choice]').forEach(button => button.addEventListener('click', () => {
      setLanguage(button.dataset.languageChoice);
      document.getElementById('languageModal')?.close();
    }));
    const modal = document.getElementById('languageModal');
    if (modal && !localStorage.getItem(STORAGE_KEY)) modal.showModal();
    new MutationObserver(records => {
      if (applying) return;
      records.forEach(record => {
        if (record.type === 'characterData') translateText(record.target);
        record.addedNodes.forEach(node => apply(node));
      });
    }).observe(document.body, { childList:true, subtree:true, characterData:true });
  }

  const nativeAlert = window.alert.bind(window);
  const nativeConfirm = window.confirm.bind(window);
  window.alert = message => nativeAlert(translate(String(message)));
  window.confirm = message => nativeConfirm(translate(String(message)));
  window.SoXeI18n = { setLanguage, getLanguage: () => language, translate, locale: () => language === 'en' ? 'en-US' : 'vi-VN', apply };
  window.tr = translate;
  document.readyState === 'loading' ? document.addEventListener('DOMContentLoaded', init) : init();
})();
