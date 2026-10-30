import csv
import json
from pathlib import Path

root=Path(__file__).parent
audit=json.loads((root/'owner-config-audit-source.json').read_text(encoding='utf-8'))
with (root/'backlog-before-owner-audit.tsv').open(encoding='utf-8',newline='') as f:
    rows=list(csv.reader(f,delimiter='\t'))
with (root/'config-before-normalize.tsv').open(encoding='utf-8',newline='') as f:
    config=list(csv.reader(f,delimiter='\t'))
owner_changes=[]
owner_values=[]
source_notes=[]
for index,issue_id in enumerate(audit['issueIds'],1):
    issue=audit['issues'][str(issue_id)]
    assert len(issue['assignees'])==1
    owner=audit['owners'][issue['assignees'][0]['login']]
    owner_values.append(owner)
    if rows[index][4].strip()!=owner:
        owner_changes.append({'stt':index,'row':index+1,'issue':issue_id,'before':rows[index][4].strip(),'after':owner})
    note=issue['url']
    if index in (4,12): note+=' — Cùng issue với STT '+str(12 if index==4 else 4)+' (tách usecase, không phải issue độc lập).'
    if index in (8,11): note+=' — Cùng issue với STT '+str(11 if index==8 else 8)+' (tách usecase, không phải issue độc lập).'
    if rows[index][8].strip(): note=rows[index][8].strip()+' '+note
    source_notes.append(note)
aliases={
 'All Use Cases (shared support)':'Shared Foundation and Integration',
 'Incident Category and Dynamic Form Management':'Manage Dynamic Form Drafts; View Form Versions',
 'Location Management':'Manage Locations; Manage Equipment Types',
 'Chưa phân loại':'Shared Foundation and Integration',
}
canonical={x[0] for x in audit['featureConfig']}
usecase_values=[]
for index in range(1,53):
    text=rows[index][1].strip()
    text=aliases.get(text,text)
    tokens=[v.strip() for v in text.split(';') if v.strip()]
    assert all(t in canonical for t in tokens),(index,tokens)
    usecase_values.append('; '.join(dict.fromkeys(tokens)))
config_rows=[['Chức năng','Phụ trách hiện tại','Hạn kế hoạch']]+audit['featureConfig']
guide_rows=[
 [config[2][8].strip(),config[2][9].strip(),"Tên usecase chuẩn tại Config!A2:A44. Mỗi tên một dòng; backlog nhiều usecase phân tách bằng '; '."],
 [config[3][8].strip(),config[3][9].strip(),"Tên issue gốc hoặc mô tả đúng scope của issue. Không xem usecase là một issue độc lập."],
 [config[4][8].strip(),config[4][9].strip(),"Done khi issue GitHub closed/completed. To Do, In Progress, Blocked theo tiến độ đã xác nhận. Việc hoãn ghi rõ trong Ghi chú."],
 [config[5][8].strip(),config[5][9].strip(),"Dùng GitHub assignee và ánh xạ Member 1–5 do nhóm cung cấp; không thay assignee bằng tác giả commit/PR."],
 [config[6][8].strip(),config[6][9].strip(),"Hạn kế hoạch trong nội dung issue, không phải ngày đóng/merge. Thiếu hạn thì để trống. Hiển thị DD/MM/YYYY."],
 [config[7][8].strip(),config[7][9].strip(),"URL issue GitHub, scope hoãn và quan hệ giữa các dòng cùng issue. Đối chiếu ngày 26/10/2026."],
 [config[8][8].strip(),config[8][9].strip(),"Sprint theo phạm vi nhóm đã chốt. Công việc hoãn để trống Sprint, không tính vào Sprint 4."],
 [config[9][8].strip(),config[9][9].strip(),"Dùng tên branch thực tế đã kiểm tra trên GitHub/Git hoặc head branch của PR. Không giữ placeholder <issue-number>."],
 [config[10][8].strip(),config[10][9].strip(),"Nguồn danh mục: usecase ở cột A, trạng thái E2:E5, thành viên G2:G6. Giữ tên chuẩn và không chèn dòng trống giữa danh sách."],
 [config[11][8].strip(),'Google Sheets Table',"Thêm công việc trong ProductBacklogTable, giữ dropdown và định dạng. Mỗi dòng phải có issue tham chiếu hoặc ghi rõ phần việc được tách."],
 [config[12][8].strip(),config[12][9].strip(),"Backlog cột I lưu nguồn GitHub. ConfigUsecase B/C theo issue triển khai chính hiện tại; không dùng để ghi đè các issue nền tảng. Usecase hoãn để trống B/C."],
]
payload={'owners':owner_values,'ownerChanges':owner_changes,'sourceNotes':source_notes,'usecases':usecase_values,'configRows':config_rows,'guideRows':guide_rows}
(root/'owner-config-payload.json').write_text(json.dumps(payload,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'ownerChanges':owner_changes,'auditedRows':39,'distinctIssues':len(audit['issues']),'configUsecases':len(config_rows)-1,'canonicalCoverage':len(usecase_values)},ensure_ascii=False,indent=2))
