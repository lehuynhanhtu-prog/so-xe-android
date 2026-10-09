const {JSDOM}=require('jsdom'),fs=require('node:fs'),assert=require('node:assert/strict');
(async()=>{for(const folder of ['docs','app/src/main/assets/www']){
 const dom=new JSDOM(fs.readFileSync(folder+'/index.html','utf8'),{url:'https://soxe.test/',runScripts:'outside-only',pretendToBeVisual:true}),w=dom.window,alerts=[];
 w.HTMLDialogElement.prototype.showModal=function(){this.setAttribute('open','')};w.HTMLDialogElement.prototype.close=function(){this.removeAttribute('open')};w.alert=x=>alerts.push(x);w.confirm=()=>true;w.URL.createObjectURL=()=>'';
 w.localStorage.setItem('so-xe-language-v1','vi');w.localStorage.setItem('so-xe-data-v1',JSON.stringify({cars:[{id:'c',plate:'65A-12345',odo:5000,powerType:'fuel'}],expenses:[{id:'e',carId:'c',type:'insurance',date:'2026-08-01',insuranceStart:'2026-08-01',insuranceExpiry:'2027-07-31',odo:5000,amount:1000000}]}));
 for(const f of ['i18n.js','app.js','numeric-fields.js','mobile-ui.js'])w.eval(fs.readFileSync(folder+'/'+f,'utf8'));
 await new Promise(r=>setTimeout(r,30));
 assert.equal(w.document.querySelectorAll('input[type=date]').length,5);assert.equal(w.document.querySelectorAll('.date-dmy').length,0);
 for(const id of ['expenseDate','insuranceStart','insuranceExpiry','reportExportFrom','reportExportTo']){
  const el=w.document.getElementById(id);let opened=0;el.showPicker=()=>opened++;el.disabled=false;el.click();assert.equal(opened,1,id);
 }
 assert.equal(w.parseDateInput('2024-02-29'),'2024-02-29');assert.equal(w.parseDateInput('29/02/2024'),'2024-02-29');assert.equal(w.parseDateInput('2026-02-29'),'');
 w.editExpense('e');await new Promise(r=>setTimeout(r,30));assert.equal(w.document.getElementById('expenseDate').value,'2026-08-01');assert.equal(w.document.getElementById('insuranceExpiry').value,'2027-07-31');
 for(const [id,date] of Object.entries({expenseDate:'2026-10-09',insuranceStart:'2026-10-01',insuranceExpiry:'2027-09-30'})){const el=w.document.getElementById(id);el.value=date;el.dispatchEvent(new w.Event('change',{bubbles:true}));}
 w.document.getElementById('expenseForm').dispatchEvent(new w.Event('submit',{bubbles:true,cancelable:true}));
 const saved=JSON.parse(w.localStorage.getItem('so-xe-data-v1')).expenses[0];assert.equal(saved.date,'2026-10-09');assert.equal(saved.insuranceStart,'2026-10-01');assert.equal(saved.insuranceExpiry,'2027-09-30');
 w.document.getElementById('reportExportFrom').value='2026-10-01';w.document.getElementById('reportExportTo').value='2026-10-09';let criteria=w.excelCriteria();assert.equal(criteria.from,'2026-10-01');assert.equal(criteria.to,'2026-10-09');
 w.document.getElementById('reportExportTo').value='2026-09-30';assert.equal(w.excelCriteria(),null);assert.ok(alerts.pop().includes('Từ ngày'));
 w.document.getElementById('reportExportFrom').value='';w.document.getElementById('reportExportTo').value='';assert.equal(w.excelCriteria().from,'');
 w.showExpenseDetail('e');assert.ok(w.document.getElementById('expenseDetailModal').textContent.includes('09/10/2026'));
 console.log(folder+': all date pickers, leap dates, edit/save roundtrip, ISO filters, range validation and displayed dates PASS');dom.window.close();
}})().catch(e=>{console.error(e);process.exit(1)});
