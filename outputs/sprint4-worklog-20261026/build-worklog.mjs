import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync, spawnSync } from 'node:child_process';
import { Workbook, SpreadsheetFile } from '@oai/artifact-tool';

const dir = path.dirname(fileURLToPath(import.meta.url));
const repo = 'D:/FA2026/SBA301/campusfix';
const git = 'C:/Users/FPT/.cache/codex-runtimes/codex-primary-runtime/dependencies/native/git/cmd/git.exe';
const data = JSON.parse(await fs.readFile(path.join(dir, 'github-source.json'), 'utf8'));
const members = {
  Huytran2903: [1, 'Trần Phan Nhật Huy', '#ACFFAB'],
  'thoaithanh-web': [2, 'Lâm Thoại Thanh', '#FFDD91'],
  'TuanThanh-dev19': [3, 'Phan Tuấn Thành', '#F6B8F3'],
  OanhPhiTo: [4, 'Tô Phi Oanh', '#ACFFD6'],
  tranthaigin: [5, 'Trần Quốc Thái', '#A9E8FB'],
};
const byId = Object.fromEntries(data.issues.map(x => [x.number, x]));
const remote = execFileSync(git, ['ls-remote', '--heads', 'origin'], { cwd: repo, encoding: 'utf8' });
const heads = Object.fromEntries(remote.trim().split(/\r?\n/).map(line => {
  const [sha, ref] = line.split(/\s+/); return [ref.replace('refs/heads/', ''), sha];
}));
const develop = execFileSync(git, ['rev-parse', 'origin/develop'], {cwd: repo, encoding:'utf8'}).trim();
if (develop !== heads.develop) throw new Error('Local develop is not the current remote develop.');
const proof = [];
for (const issue of data.issues) {
  if (issue.state !== 'closed' || issue.state_reason !== 'completed') throw new Error(`Issue ${issue.number} is not completed.`);
  if (issue.assignees.length !== 1 || !members[issue.assignees[0]]) throw new Error(`Unknown owner for ${issue.number}.`);
  const sha = heads[issue.branch];
  if (!sha) throw new Error(`Remote branch missing: ${issue.branch}`);
  const check = spawnSync(git, ['merge-base', '--is-ancestor', sha, develop], { cwd: repo });
  if (check.status !== 0) throw new Error(`Branch not contained in develop: ${issue.branch}`);
  const pr = data.prs.find(p => p.head === issue.branch);
  if (pr && !pr.merged) throw new Error(`PR ${pr.number} is not merged.`);
  proof.push({ issue: issue.number, branch: issue.branch, sha, inDevelop: true, pr: pr?.number ?? null });
}
await fs.writeFile(path.join(dir, 'branch-verification.json'), JSON.stringify({develop, proof}, null, 2));

function dateValue(value) {
  if (!value) return null;
  const [d,m,y] = value.split('/').map(Number);
  return new Date(Date.UTC(y,m-1,d));
}
function bangkokValue(value) { return value ? new Date(new Date(value).getTime() + 7*3600*1000) : null; }
const workbook = Workbook.create();
const main = workbook.worksheets.add('Worklog Sprint 4');
const compare = workbook.worksheets.add('Đối chiếu thay đổi');
const sources = workbook.worksheets.add('Nguồn GitHub');
function base(sheet, lastColumn, lastRow, widths, headerRow=1) {
  sheet.showGridLines = false;
  const used = sheet.getRange(`A1:${lastColumn}${lastRow}`);
  used.format.font = {name:'Arial', size:10, color:'#233C4F'};
  used.format.verticalAlignment = 'center';
  used.format.rowHeight = 38;
  widths.forEach((width,i) => sheet.getRangeByIndexes(0,i,lastRow,1).format.columnWidthPx = width);
  const h = sheet.getRange(`A${headerRow}:${lastColumn}${headerRow}`);
  h.format.fill = '#274D62';
  h.format.font = {name:'Arial',size:10,bold:true,color:'#FFFFFF'};
  h.format.horizontalAlignment = 'center';
  h.format.wrapText = true;
  h.format.rowHeight = 38;
  sheet.freezePanes.freezeRows(headerRow);
}
const order = [80,78,76,74,73,79,75,81,88,77,72];
const features = {
  80:'Submit Ticket',78:'Submit Ticket',76:'View and Download Ticket Attachments',
  74:'View Technician Profiles',73:'Assign Technician: Check Eligibility',
  79:'Review Ticket; Assign Technician; View Current Assignment',
  75:'View Assigned Work; Start Ticket Work',81:'Record Work Log; Resolve Ticket',
  88:'Upload Work Result Images',77:'Close Ticket; Shared Foundation and Integration',
  72:'View Ticket Dashboard; Monitor Ticket SLA',
};
const mainRows = order.map((n,i) => {
  const x=byId[n];
  return [40+i,features[n],x.title.replace(/^\[NEX-\d+\]\s*/,''),'Done',members[x.assignees[0]][1],dateValue(x.plannedEnd),4,x.branch];
});
main.getRange('A1:H12').values = [['STT','Chức năng','Công việc','Trạng thái','Người phụ trách','Hạn hoàn thành\n(kế hoạch)','Sprint','Branch-name'],...mainRows];
base(main,'H',12,[48,335,485,100,190,135,60,335]);
main.getRange('A2:H12').format.rowHeight = 62;
main.getRange('A2:C12').format.fill = '#DDF2E5';
main.getRange('B2:C12').format.wrapText = true;
main.getRange('A1:H12').format.borders = {preset:'all', style:'thin', color:'#9BAAA3'};
main.getRange('A2:A12').format.horizontalAlignment='center';
main.getRange('D2:G12').format.horizontalAlignment='center';
main.getRange('F2:F12').setNumberFormat('dd/mm/yyyy');
main.getRange('F2:F12').format.font.color='#D40000';
main.getRange('D2:D12').dataValidation={rule:{type:'list',values:['To Do','In Progress','Done']}};
main.getRange('D2:D12').conditionalFormats.addCustom('D2="Done"',{fill:'#D7EBBA',font:{bold:true,color:'#233C18'}});
main.getRange('E2:E12').dataValidation={rule:{type:'list',values:Object.values(members).map(m=>m[1])}};
for (const [, name, color] of Object.values(members)) {
  main.getRange('E2:E12').conditionalFormats.addCustom(`E2="${name}"`,{fill:color});
}
main.getRange('F10').format.fill='#FFF2C8';
main.tabColor='#274D62';
main.getRange('J1:J10').format.font={name:'Arial',size:10,color:'#536879'};
main.getRange('J1:J10').format.columnWidthPx=760;
main.getRange('J1:J10').format.wrapText=false;
main.getRange('J1').values=[['Phạm vi và ngày đối chiếu']];
main.getRange('J1').format.font.bold=true;
main.getRange('J2').values=[['Đối chiếu ngày 26/10/2026, múi giờ Asia/Bangkok.']];
main.getRange('J3').values=[['11 issue Sprint 4. STT được đánh lại từ 40 theo khối trong ảnh.']];
main.getRange('J4').values=[['Cột F giữ hạn kế hoạch trong issue; ngày đóng issue và merge nằm ở tab Nguồn GitHub.']];
main.getRange('J5').values=[['Issue #88 không ghi hạn kế hoạch, nên F10 để trống.']];
main.getRange('J6').values=[['Reject, cancel, reassign, unassign, reopen, comments và feedback được hoãn ngoài Sprint 4.']];
main.getRange('J7').values=[['Upload ảnh kết quả đã được bổ sung lại vào scope qua issue #88 và PR #89.']];
main.getRange('J8').values=[['Bản này chỉ gồm Sprint 4 từ ảnh cung cấp; chưa đồng bộ vào Google Sheets gốc.']];
main.getRange('J9').values=[['Giữ các sprint khác. Tham chiếu issue/branch để ghép dữ liệu, không ghép chỉ theo STT cũ.']];
main.getRange('J10').values=[['Tab Đối chiếu thay đổi giải thích từng dòng cũ và phần đã gộp hoặc tách.']];

const oldRows=[
 [40,'Validation dynamic values/evidence','Trần Quốc Thái','22/10/2026','feature/48-ticket-submission-validation',[80],'Đổi người phụ trách, hạn kế hoạch và branch.'],
 [41,'API tạo Ticket với sequence, SLA và ảnh','Trần Quốc Thái','22/10/2026','feature/49-create-ticket-api',[78],'Đổi người phụ trách, hạn kế hoạch và branch.'],
 [42,'API đọc attachment có phân quyền','Lâm Thoại Thanh','22/10/2026','feature/51-ticket-attachment-content-api',[76],'Đổi người phụ trách, hạn kế hoạch và branch.'],
 [43,'API danh sách Technician','Lâm Thoại Thanh','15/10/2026','feature/57-technician-list-api',[74],'Đổi người phụ trách, hạn kế hoạch và branch.'],
 [44,'Nghiệm thu API thật và cập nhật tài liệu','Tô Phi Oanh','22/10/2026','feature/58-real-api-integration',[77,88],'Gộp scope demo/nghiệm thu vào #77; phần tài liệu hoàn thiện trong PR #89 của #88. Không có branch real-api-integration trên remote.'],
 [45,'Review; reject; cancel Ticket','Tô Phi Oanh','23/10/2026','',[79],'Giữ review, gộp cùng assign/current assignment. Hoãn reject và cancel.'],
 [46,'Assign; reassign; unassign; eligibility','Phan Tuấn Thành','21/10/2026','',[73,79],'Tách eligibility (#73) và review/assign (#79). Hoãn reassign, unassign và override.'],
 [47,'API công việc hiện tại của Technician','Phan Tuấn Thành','23/10/2026','',[75],'Gộp My Work với start; đổi phụ trách sang Tô Phi Oanh.'],
 [48,'API start và Work Log','Trần Phan Nhật Huy','28/10/2026','',[75,81],'Start thuộc #75; Work Log thuộc #81 cùng resolve. Cả hai do Tô Phi Oanh phụ trách.'],
 [49,'API ảnh kết quả và resolve','Trần Phan Nhật Huy','28/10/2026','',[88,81],'Tách upload ảnh (#88, Phan Tuấn Thành) khỏi Work Log/resolve (#81, Tô Phi Oanh).'],
 [50,'API close và reopen','Phan Tuấn Thành','28/10/2026','',[77],'Giữ close của reporter và phạm vi demo. Hoãn reopen/force-close.'],
 [51,'API comments Ticket','Phan Tuấn Thành','30/10/2026','',[],'Hoãn ngoài Sprint 4; không giữ To Do trong khối Sprint 4 mới. #77 và #88 xác nhận comments chưa thuộc demo.'],
 [52,'API feedback Ticket','Phan Tuấn Thành','30/10/2026','',[],'Hoãn ngoài Sprint 4; không giữ To Do trong khối Sprint 4 mới. #77 và #88 xác nhận feedback chưa thuộc demo.'],
 [53,'API Dashboard summary theo role','Trần Phan Nhật Huy','30/10/2026','',[72],'Đổi người phụ trách sang Trần Quốc Thái, cập nhật Done, hạn kế hoạch và branch thật.'],
];
const changes=oldRows.map(([stt,desc,owner,due,branch,ids,note])=>[
  stt,desc,owner,dateValue(due),branch||null,
  ids.length?ids.map(n=>`#${n}`).join('\n'):'Hoãn ngoài Sprint 4',
  ids.length?ids.map(n=>members[byId[n].assignees[0]][1]).filter((v,i,a)=>a.indexOf(v)===i).join('\n'):null,
  note,ids.length?ids.map(n=>byId[n].branch).join('\n'):null,
]);
compare.getRange('A1:I15').values=[['STT cũ','Nội dung cũ (từ ảnh)','Phụ trách cũ','Hạn cũ','Branch cũ','Issue hiện tại','Phụ trách hiện tại','Điều chỉnh','Branch hiện tại'],...changes];
base(compare,'I',15,[65,255,180,110,320,130,185,480,335]);
compare.getRange('A2:I15').format.wrapText=true;
compare.getRange('A2:I15').format.rowHeight=86;
compare.getRange('D2:D15').setNumberFormat('dd/mm/yyyy');
compare.getRange('A2:A15').format.horizontalAlignment='center';
compare.getRange('D2:D15').format.horizontalAlignment='center';
compare.getRange('F2:F15').format.horizontalAlignment='center';
compare.getRange('F13:F14').format.fill='#FFF2C8';
compare.getRange('A1:I15').format.borders={preset:'all',style:'thin',color:'#C9D2D8'};

const sourceRows=order.map(n=>{
  const x=byId[n], login=x.assignees[0], p=proof.find(p=>p.issue===n), pr=data.prs.find(q=>q.head===x.branch);
  let note = n===88?'Không có ngày bắt đầu/hạn kế hoạch trong issue.':null;
  if(n===78) note='Issue đóng 18/10, PR merge 19/10; giữ hai mốc riêng.';
  if(n===79) note='PR merge 19/10, issue đóng 22/10; giữ hai mốc riêng.';
  if(n===72) note='Issue/PR hoàn tất sớm hơn mốc kế hoạch trong nội dung issue.';
  return [`#${n}`,'closed / completed',login,members[login][1],dateValue(x.plannedStart),dateValue(x.plannedEnd),bangkokValue(x.closed_at),pr?bangkokValue(pr.merged_at):null,pr?`PR #${pr.number} đã merge develop`:`Commit ${p.sha.slice(0,7)} có trong develop`,x.url,pr?.url??`https://github.com/${data.repository}/commit/${p.sha}`,note];
});
sources.getRange('A1').values=[['Nguồn GitHub và mốc thời gian']];
sources.getRange('A2').values=[['Chốt 26/10/2026. Ngày đóng issue và merge dùng Asia/Bangkok (+07:00). Hạn kế hoạch giữ theo nội dung issue.']];
sources.getRange('A3').values=[['Branch kiểm tra trực tiếp trên remote; cả 11 branch đều có commit nằm trong develop tại thời điểm đối chiếu.']];
sources.getRange('A5:L16').values=[['Issue','Trạng thái GitHub','GitHub login','Thành viên','Bắt đầu KH','Hạn KH','Đóng issue (+07)','Merge PR (+07)','Minh chứng develop','Nguồn issue','Nguồn PR / commit','Ghi chú'],...sourceRows];
base(sources,'L',16,[65,145,155,185,110,110,150,150,250,400,445,360],5);
sources.getRange('A1').format.font={name:'Arial',size:14,bold:true,color:'#233C4F'};
sources.getRange('A2:A3').format.font={name:'Arial',size:10,color:'#536879'};
sources.getRange('A1:L3').format.rowHeight=26;
sources.getRange('A2:A3').format.wrapText=false;
sources.getRange('A6:L16').format.wrapText=true;
sources.getRange('A6:L16').format.rowHeight=65;
sources.getRange('E6:F16').setNumberFormat('dd/mm/yyyy');
sources.getRange('G6:H16').setNumberFormat('dd/mm/yyyy hh:mm');
sources.getRange('E6:H16').format.horizontalAlignment='center';
sources.getRange('A5:L16').format.borders={preset:'all',style:'thin',color:'#C9D2D8'};
sources.getRange('J6:K16').format.font.color='#116DB1';
sources.getRange('A19:D24').values=[['Thành viên','Họ và tên','GitHub login','Nguồn ánh xạ'],...Object.entries(members).sort((a,b)=>a[1][0]-b[1][0]).map(([login,[num,name]])=>[`Member ${num}`,name,login,'Tên do người dùng cung cấp; login đối chiếu assignee GitHub.'])];
sources.getRange('A19:D24').format.font={name:'Arial',size:10,color:'#233C4F'};
sources.getRange('A19:D24').format.wrapText=true;
sources.getRange('A19:D24').format.rowHeight=54;
sources.getRange('A19:D19').format.fill='#E7EEF2';
sources.getRange('A19:D19').format.font.bold=true;
sources.getRange('A27').values=[['Nguồn dữ liệu cũ: ảnh worklog STT 40–53 do người dùng cung cấp. Không truy cập được nội dung Google Sheets gốc (HTTP 401).']];
sources.getRange('A28').values=[['Không suy ngày merge từ ngày commit cho các issue chưa tìm thấy PR; ô Merge PR để trống.']];
sources.getRange('A27:A28').format.font={name:'Arial',size:10,color:'#536879'};
sources.getRange('A27:A28').format.wrapText=false;

workbook.recalculate();
const expected = main.getRange('A2:H12').values;
if(expected.length!==11 || expected.some(r=>!r[4]||!r[7]||r[3]!=='Done')) throw new Error('Invalid worklog rows.');
const inspected=await workbook.inspect({kind:'table',range:"'Worklog Sprint 4'!A1:H12",include:'values,formulas',tableMaxRows:12,tableMaxCols:8,maxChars:7000});
await fs.writeFile(path.join(dir,'worklog-inspection.ndjson'),inspected.ndjson);
const errors=await workbook.inspect({kind:'match',searchTerm:'#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A|#NUM!|#NULL!|#SPILL!|#CALC!',options:{useRegex:true,maxResults:30},summary:'Final formula error scan',maxChars:2000});
await fs.writeFile(path.join(dir,'error-scan.ndjson'),errors.ndjson);
for(const [sheetName,range,name] of [
  ['Worklog Sprint 4','A1:H12','worklog-preview.png'],
  ['Đối chiếu thay đổi','A1:I15','comparison-preview.png'],
  ['Nguồn GitHub','A1:I16','sources-preview.png'],
  ['Nguồn GitHub','J5:L16','source-links-preview.png'],
  ['Nguồn GitHub','A19:D24','members-preview.png'],
  ['Worklog Sprint 4','J1:J10','notes-preview.png'],
]){
  const preview=await workbook.render({sheetName,range,scale:1,format:'png'});
  await fs.writeFile(path.join(dir,name),new Uint8Array(await preview.arrayBuffer()));
}
const xlsx=await SpreadsheetFile.exportXlsx(workbook);
await xlsx.save(path.join(dir,'Nexora_Worklog_Sprint4_DaDoiChieu.xlsx'));
console.log(JSON.stringify({output:path.join(dir,'Nexora_Worklog_Sprint4_DaDoiChieu.xlsx'),rows:mainRows.length,verifiedBranches:proof.length,completedIssues:data.issues.length,errors:errors.ndjson},null,2));
