from pypdf import PdfReader

path = r"D:\javaProject\Training_Question_Bank_System\党建知识竞赛题库.pdf"
out = r"D:\javaProject\Training_Question_Bank_System\tools\pdf_dump.txt"
reader = PdfReader(path)
with open(out, "w", encoding="utf-8") as f:
    for i, page in enumerate(reader.pages):
        f.write(f"\n===== PAGE {i+1} =====\n")
        f.write(page.extract_text() or "")
print("done, total chars:", sum(len(p.extract_text() or "") for p in reader.pages))
