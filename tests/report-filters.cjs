const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
for(const path of ['docs/app.js','app/src/main/assets/www/app.js']){
 const source=fs.readFileSync(path,'utf8');
 const names=['matchesReportCriteria','monthlyReportForCar','fuelIntervals','electricCostIntervals','exportVisibleReportsExcel'];
 const context={data:{cars:[{id:'a',plate:'A'},{id:'b',plate:'B'}],expenses:[
 {id:'1',carId:'a',type:'fuel',date:'2026-09-01',amount:100,odo:100,liters:10},
 {id:'2',carId:'a',type:'fuel',date:'2026-09-10',amount:200,odo:200,liters:10},
 {id:'3',carId:'a',type:'maintenance',date:'2026-09-15',amount:500,odo:250},
 {id:'4',carId:'a',type:'fuel',date:'2026-09-25',amount:300,odo:300,liters:10},
 {id:'5',carId:'b',type:'fuel',date:'2026-09-10',amount:999,odo:1000,liters:10},
 ]},supportsFuel:()=>true,supportsElectric:()=>true,window:{},dateVN:x=>x,expenseTypeLabel:x=>x,today:()=>'',excelCriteria:()=>context.criteria,
 excelSheet:(name,headers,rows)=>({name,headers,rows}),excelWorkbook:x=>x,downloadExcel:x=>context.result=x,toast:x=>context.message=x};
 vm.createContext(context);vm.runInContext(source.split('\n').filter(l=>names.some(n=>l.startsWith('function '+n+'('))).join('\n'),context);
 context.criteria={carId:'a',type:'fuel',from:'2026-09-10',to:'2026-09-15'};
 const months=context.monthlyReportForCar('a',context.criteria);assert.equal(months.length,1);assert.equal(months[0].total,200);assert.equal(months[0].maintenance,0);assert.equal(months[0].distance,100);
 context.exportVisibleReportsExcel();assert.equal(context.result[0].rows.length,1);assert.equal(context.result[0].rows[0][13][0],200);assert.equal(context.result[1].rows.length,1);assert.equal(context.result[1].rows[0][1],'2026-09-01');
 context.criteria={carId:'a',type:'maintenance',from:'2026-09-10',to:'2026-09-15'};context.exportVisibleReportsExcel();assert.equal(context.result.length,1);assert.equal(context.result[0].rows[0][13][0],500);
 context.criteria={from:'2026-09-10',to:'2026-09-10'};context.exportVisibleReportsExcel();assert.equal(context.result[0].rows.length,2);assert.equal(context.result[0].rows.reduce((s,r)=>s+r[13][0],0),1199);
 context.result=null;context.criteria={from:'2026-10-01'};context.exportVisibleReportsExcel();assert.equal(context.result,null);assert.ok(context.message);
 console.log(path+': vehicle/type/date boundaries, interval baseline, totals and empty result PASS');
}
