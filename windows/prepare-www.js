const fs = require('fs');
const path = require('path');

const source = path.resolve(__dirname, '..', 'docs');
const destination = path.resolve(__dirname, 'www');

fs.rmSync(destination, { recursive: true, force: true });
fs.cpSync(source, destination, { recursive: true });
console.log(`Đã đóng gói giao diện từ ${source}`);
