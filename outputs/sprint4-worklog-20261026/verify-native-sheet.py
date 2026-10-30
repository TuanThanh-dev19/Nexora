import csv
import json
from pathlib import Path
from datetime import datetime, timedelta

root = Path(__file__).parent
with (root / 'google-sheet-before.tsv').open(encoding='utf-8', newline='') as f:
    before = list(csv.reader(f, delimiter='\t'))
with (root / 'google-sheet-after.tsv').open(encoding='utf-8', newline='') as f:
    after = list(csv.reader(f, delimiter='\t'))
payload = json.loads((root / 'native-sheet-payload.json').read_text(encoding='utf-8'))
for row in range(40):
    for col in list(range(7)) + [8, 10]:
        assert before[row][col].strip() == after[row][col].strip(), (row+1,col+1,before[row][col],after[row][col])
for row, branch in enumerate(payload['branches'], 1):
    assert after[row][7].strip() == branch, (row+1, after[row][7], branch)
for offset, expected in enumerate(payload['rows']):
    actual = after[40+offset]
    for col, value in enumerate(expected):
        if col == 5 and isinstance(value, int):
            value = (datetime(1899,12,30) + timedelta(days=value)).strftime('%d/%m/%Y')
        assert str(value).strip() == actual[col].strip(), (41+offset,col+1,value,actual[col])
sprint4 = [r for r in after[1:] if len(r)>6 and r[6].strip()=='4']
assert len(sprint4) == 11
assert all(r[3].strip() == 'Done' and r[7].strip() for r in sprint4)
assert not any('<issue-number>' in c for r in after for c in r)
assert all(not c.strip() for c in after[53][:9])
assert all(after[r][6].strip()=='' for r in (51,52))
print('Verified live sheet: 11 completed Sprint 4 issues, 50 populated branch cells, 2 deferred items outside Sprint 4.')
print('Earlier sprint data and member list preserved; deadlines display dd/mm/yyyy; no branch placeholders remain.')
