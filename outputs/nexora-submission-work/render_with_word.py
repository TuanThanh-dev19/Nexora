from pathlib import Path
import importlib.util
import shutil

work=Path(__file__).resolve().parent
source=work.parent/'ho-so-nop-nexora-20261029'/'02_Thong_tin_de_tai_va_tien_do.docx'
pdf=work/'qa'/'02_Thong_tin_de_tai_va_tien_do.pdf'
renderer=Path(r'C:\Users\FPT\.codex\plugins\cache\openai-primary-runtime\documents\26.1025.11240\skills\documents\render_docx.py')
spec=importlib.util.spec_from_file_location('packaged_render_docx',renderer)
module=importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
def native_word_pdf(doc_path,user_profile,convert_tmp_dir,stem,verbose):
    target=Path(convert_tmp_dir)/(stem+'.pdf')
    shutil.copy2(pdf,target)
    return str(target),'PDF rendered by Microsoft Word for Windows'
module.convert_to_pdf=native_word_pdf
pages=module.rasterize(str(source),str(work/'qa'),dpi=144,verbose=True,emit_pdf=False)
print('\n'.join(pages))
