# -*- coding: utf-8 -*-
"""
把“题目-选项-答案标记”格式的题库 PDF 批量转换成系统通用导入 JSON。

识别格式(来自 党建知识竞赛题库.pdf):
    1. 题干……（） [单选题] *
    A、选项一
    B、选项二(正确答案)
    C、选项三

支持题型标记: [单选题] [多选题] [判断题] [简答题](大小写/全角空格/换行均可容忍)。
答案标记: (正确答案) / （正确答案）, 可被换行拆开。
选项前缀: A、 A. A． (A-H)。
用法:
    python tools/parse_pdf.py <in.pdf> <out.json> [--kp 知识点名称] [--sheet-name 标签]
"""
import json
import re
import sys

TYPE_MAP = {
    "单选题": "SINGLE",
    "多选": "MULTIPLE",
    "判断题": "JUDGE",
    "简答题": "SHORT",
    "简答": "SHORT",
}

# [单选题] / [ 单选\n题] / [单\n选题] ...
TYPE_RE = re.compile(r"\[\s*((?:单|多)\s*选\s*题|判\s*断\s*题|简\s*答\s*题|(?:单|多)选|判断|简答)\s*\]")
# 题号开头的块: 行首 1-3 位数字 + . /、 后跟内容
QNUM_RE = re.compile(r"(?m)^\s*(\d{1,4})\s*[.、．]\s*")
OPT_LETTER_RE = re.compile(r"(?m)(^|\n)\s*([A-Ha-h])\s*[、.．]\s*")
ANSWER_TAG_RE = re.compile(r"[（(]\s*正\s*确\s*答\s*案\s*[)）]")


def clean_space(s: str) -> str:
    s = s.replace("\u3000", " ").replace("\xa0", " ")
    s = re.sub(r"\s+", " ", s)
    return s.strip()


def parse_pdf_text(text: str, kp: str):
    # 去掉分页标记, 得到连续文本
    text = re.sub(r"=====\s*PAGE\s*\d+\s*=====", "", text)
    # 去掉每行行首空格之类
    # 用题号切块(题号行开始到下一题号行为止)
    matches = list(QNUM_RE.finditer(text))
    if not matches:
        raise RuntimeError("未在 PDF 中找到题号, 无法解析")
    questions = []
    seen = set()
    for idx, m in enumerate(matches):
        end = matches[idx + 1].start() if idx + 1 < len(matches) else len(text)
        block = text[m.end():end]
        qno = int(m.group(1))
        if qno in seen:
            continue
        seen.add(qno)
        q = parse_one_block(qno, block, kp)
        if q:
            questions.append(q)
    return questions


def parse_one_block(qno: int, block: str, kp: str):
    tm = TYPE_RE.search(block)
    if not tm:
        print(f"!! 第{qno}题 未找到题型标记, 跳过: {block[:60]!r}")
        return None
    raw_type = re.sub(r"\s+", "", tm.group(1))
    qtype = TYPE_MAP.get(raw_type)
    if not qtype:
        print(f"!! 第{qno}题 未知题型标记 {raw_type!r}, 跳过")
        return None
    stem = block[:tm.start()]
    rest = block[tm.end():]

    # 题干部分: 去掉题号与多余符号
    stem = clean_space(stem)
    stem = stem.lstrip("0123456789.、． ")

    # ---- 选项解析 ----
    options = []
    answer_letters = []
    if qtype in ("SINGLE", "MULTIPLE"):
        opt_matches = list(OPT_LETTER_RE.finditer(rest))
        if not opt_matches:
            # 没有选项字母 -> 视为题干内容合并
            stem = clean_space(stem + " " + rest)
        else:
            for i, om in enumerate(opt_matches):
                letter = om.group(2).upper()
                seg_start = om.end()
                seg_end = opt_matches[i + 1].start() if i + 1 < len(opt_matches) else len(rest)
                seg = rest[seg_start:seg_end]
                seg = re.sub(r"(?m)^\s*", "", seg)  # 每行行首空白
                had_answer = ANSWER_TAG_RE.search(seg) is not None
                # 去掉答案标记(可能跨行, 先去除内部换行)
                seg = ANSWER_TAG_RE.sub("", seg)
                seg = re.sub(r"\s*\*\s*$", "", seg)
                seg = clean_space(seg)
                options.append(seg)
                if had_answer:
                    answer_letters.append(letter)
            # 无选项文本的条目剔除(页眉残留等)
            if not options:
                stem = clean_space(stem + " " + rest)
    else:
        # 判断/简答: 无选项; 可能题干中直接有 答案:xxx 或 (对/错)
        leftover = clean_space(rest)
        # 尝试 "答案[:：] xxx" 模式
        am = re.search(r"答\s*案\s*[:：]\s*([^\n]+)", rest)
        if am:
            answer_letters.append(clean_space(am.group(1)).strip("()（）"))
            rest_clean = re.sub(r"答\s*案\s*[:：]\s*([^\n]+)", "", rest)
            stem = clean_space(stem + " " + rest_clean)
        elif leftover and ("(对)" in leftover or "(错)" in leftover or "（对）" in leftover or "（错）" in leftover):
            ans_txt = "对" if ("(对)" in leftover or "（对）" in leftover) else "错"
            answer_letters.append(ans_txt)
        elif leftover:
            stem = clean_space(stem + " " + leftover)

    stem = re.sub(r"\s+", " ", stem).strip()
    if not stem:
        print(f"!! 第{qno}题 题干为空, 跳过")
        return None

    # ---- 答案整理 ----
    if qtype in ("SINGLE", "JUDGE", "SHORT"):
        if not answer_letters and qtype == "JUDGE":
            answer_letters = [""]  # 标记待补充
        answer = "".join(answer_letters) if answer_letters else ""
        if not answer and qtype != "SHORT":
            print(f"!! 第{qno}题 未找到答案标记, 答案留空")
    else:  # MULTIPLE
        answer = ",".join(sorted(set(answer_letters))) if answer_letters else ""

    if qtype == "JUDGE" and answer not in ("对", "错"):
        # 兼容 True/False/√/×
        t = answer.strip().upper()
        answer = "对" if t in ("对", "T", "TRUE", "√", "A", "正确", "YES", "1") else ("错" if t in ("错", "F", "FALSE", "×", "X", "B", "错误", "NO", "0") else "")

    analysis = ""
    if answer and qtype in ("SINGLE", "MULTIPLE"):
        analysis = "正确答案：" + answer
    return {
        "knowledgePoint": kp,
        "type": qtype,
        "stem": stem,
        "options": options if options else None,
        "answer": answer,
        "analysis": analysis,
        "difficulty": 3,
    }


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    kp = "党史党建"
    if "--kp" in sys.argv:
        kp = sys.argv[sys.argv.index("--kp") + 1]
    if len(args) < 2:
        print(__doc__)
        sys.exit(1)
    from pypdf import PdfReader
    pdf_path, out_path = args[0], args[1]
    reader = PdfReader(pdf_path)
    full = "\n".join((p.extract_text() or "") for p in reader.pages)
    qs = parse_pdf_text(full, kp)
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(qs, f, ensure_ascii=False, indent=2)
    types = {}
    for q in qs:
        types[q["type"]] = types.get(q["type"], 0) + 1
    print(f"OK 解析 {len(qs)} 题 -> {out_path}")
    print("题型分布:", types)
    no_answer = [q["stem"][:40] for q in qs if not q["answer"]]
    if no_answer:
        print("WARN 无答案:", len(no_answer))
        for s in no_answer[:10]:
            print("   -", s)


if __name__ == "__main__":
    main()
