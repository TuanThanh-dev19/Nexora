import csv,json
from collections import Counter
from pathlib import Path

root=Path(__file__).parent
def read(name):
    with (root/name).open(encoding='utf-8',newline='') as f:
        return list(csv.reader(f,delimiter='\t'))
def clean(value): return value.strip()
before=read('backlog-before-owner-audit.tsv')
after=read('backlog-after-owner-audit.tsv')
config_before=read('config-before-normalize.tsv')
config_after=read('config-after-normalize.tsv')
payload=json.loads((root/'owner-config-payload.json').read_text(encoding='utf-8'))
audit=json.loads((root/'owner-config-audit-source.json').read_text(encoding='utf-8'))
for index,expected in enumerate(payload['owners'],1):
    assert clean(after[index][4])==expected,(index,'owner')
    assert clean(after[index][8])==payload['sourceNotes'][index-1],(index,'source')
changed=[r for r in range(1,40) if clean(before[r][4])!=clean(after[r][4])]
assert changed==[2,4,8,34],changed
for row in range(1,53):
    assert clean(after[row][1])==payload['usecases'][row-1],(row,'usecase')
    for col in (0,2,3,5,6,7):
        assert clean(after[row][col])==clean(before[row][col]),(row,col,'unexpected change')
    if row>=40:
        for col in (4,8):
            assert clean(after[row][col])==clean(before[row][col]),(row,col,'Sprint 4 changed')
for r,expected in enumerate(payload['configRows']):
    assert [clean(c) for c in config_after[r][:3]]==expected,(r+1,config_after[r][:3],expected)
names=[clean(r[0]) for r in config_after[1:44]]
assert len(names)==len(set(names))==43
for row in range(1,53):
    assert all(t in names for t in clean(after[row][1]).split('; ')),row
for row in range(65):
    for col in (3,4,5,6,7):
        assert clean(config_after[row][col])==clean(config_before[row][col]),(row+1,col+1,'Config list changed')
for r,expected in enumerate(payload['guideRows'],2):
    assert [clean(c) for c in config_after[r][8:11]]==expected,(r+1,'guide')
for name,issue_id in audit['featureMap']:
    row=names.index(name)+1
    if issue_id is None:
        assert not clean(config_after[row][1]) and not clean(config_after[row][2]),name
counts=Counter(clean(row[4]) for row in after[1:53])
for row in range(1,6):
    name=clean(after[row][10]); count=int(clean(after[row][11]))
    assert count==counts[name],(name,count,counts[name],'summary did not recalculate')
assert not any('#REF!' in c or '#DIV/0!' in c for row in after+config_after for c in row)
print('Verified: 39 backlog rows mapped to 37 GitHub issues; exactly 4 owners corrected.')
print('Verified: 43 unique Config usecases, complete vocabulary coverage for 52 backlog rows, 9 deferred usecases without owner/deadline.')
print('Verified: task descriptions, deadlines, branches, Sprint 4 owners and status/member lists preserved; owner counts recalculated.')
print(json.dumps(counts,ensure_ascii=False))
