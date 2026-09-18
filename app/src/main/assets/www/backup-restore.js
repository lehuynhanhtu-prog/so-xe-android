function restoreStatus(message){document.querySelector('#restoreStatus').textContent=message}
window.nativeBackupRestored=json=>{
  try{
    const restored=JSON.parse(json);
    if(!validateImported(restored))throw new Error('Dữ liệu khôi phục không hợp lệ');
    data=restored;localStorage.setItem(KEY,JSON.stringify(data));localStorage.removeItem(DIRTY_KEY);dirty=false;render();
    setSync('Đã khôi phục và đồng bộ Google Drive',true);
    restoreStatus('Đã khôi phục dữ liệu và tệp đính kèm từ ZIP.');
  }catch(error){restoreStatus(error.message||'Không đọc được dữ liệu khôi phục')}
  finally{window.restoreBusy=false}
};
window.nativeBackupRestoreError=message=>{window.restoreBusy=false;restoreStatus(message||'Khôi phục thất bại');toast(message||'Khôi phục thất bại')};
document.querySelector('#restoreBackupBtn').onclick=()=>{
  if(window.restoreBusy)return;
  if(!navigator.onLine||!window.AndroidDrive?.hasDriveToken()){restoreStatus('Hãy kết nối Google Drive và Internet trước khi khôi phục.');return}
  if(syncRunning){restoreStatus('Đang đồng bộ dữ liệu. Hãy thử lại sau.');return}
  if(!confirm('Khôi phục dữ liệu từ ZIP sẽ thay dữ liệu hiện tại trên máy và Google Drive. Hãy sao lưu trước khi tiếp tục.'))return;
  window.restoreBusy=true;restoreStatus('Đang kiểm tra ZIP và khôi phục tệp lên Drive…');
  try{window.AndroidDrive.restoreBackup()}catch(error){window.nativeBackupRestoreError(error.message)}
};
