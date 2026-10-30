from pathlib import Path
from zipfile import ZipFile
import xml.etree.ElementTree as ET

p = Path(__file__).parent / 'Nexora_Worklog_Sprint4_DaDoiChieu.xlsx'
ns = {'s': 'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}
with ZipFile(p) as archive:
    assert archive.testzip() is None
    book = ET.fromstring(archive.read('xl/workbook.xml'))
    sheets = [s.attrib['name'] for s in book.find('s:sheets', ns)]
    assert len(sheets) == 3
    sheet = ET.fromstring(archive.read('xl/worksheets/sheet1.xml'))
    cell = {c.attrib['r']: c for c in sheet.findall('.//s:sheetData/s:row/s:c', ns)}
    date_cell = cell['F2']
    if date_cell.attrib.get('t') == 'd':
        assert date_cell.find('s:v', ns).text.startswith('2026-10-16T')
    else:
        assert float(date_cell.find('s:v', ns).text) > 45000
    assert cell['F10'].find('s:v', ns) is None
    assert len(sheet.findall('s:dataValidations/s:dataValidation', ns)) == 2
    assert sum(1 for r in range(2, 13) if f'H{r}' in cell) == 11
    print('Verified export: 3 sheets, 11 branch cells, typed dates, 2 validations, intact ZIP.')
    print(f'File size: {p.stat().st_size} bytes')
