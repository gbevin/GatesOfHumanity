#!/usr/bin/env python3
"""Checks AppStoreListing.md and makes a page of it on which every value has a button that
copies it, to paste into App Store Connect field by field.

    ios/Store/listing_page.py OUT.html

The listing is read as it's written: a `## ` heading starts a section, a `### ` heading a field
whose value is the block between ``` lines below it, with `Limit: N` above the block when the
field has one, and the text after the block as its note. A value over its limit, or a field
without a value, stops it."""
import html
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))


def parse(text):
    """The title, the introduction and the sections: each a name, its parts of text and its fields."""
    title, intro, sections = "", [], []
    parts, field = intro, None
    lines = text.split("\n")
    at = 0
    while at < len(lines):
        line = lines[at]
        at += 1
        if line.startswith("# "):
            title = line[2:].strip()
        elif line.startswith("## "):
            sections.append({"name": line[3:].strip(), "parts": [], "fields": []})
            parts, field = sections[-1]["parts"], None
        elif line.startswith("### "):
            field = {"label": line[4:].strip(), "limit": None, "value": None, "parts": []}
            sections[-1]["fields"].append(field)
            parts = field["parts"]
        elif field is not None and field["value"] is None and re.fullmatch(r"Limit: \d+", line):
            field["limit"] = int(line[7:])
        elif line == "```":
            end = lines.index("```", at)
            if field is None or field["value"] is not None:
                sys.exit(f"line {at}: a block that isn't the value of a field")
            field["value"] = "\n".join(lines[at:end])
            at = end + 1
        elif re.match(r"(- |\d+\. )", line):
            kind = "ul" if line.startswith("- ") else "ol"
            if not parts or parts[-1][0] != kind or lines[at - 2] == "":
                parts.append((kind, []))
            parts[-1][1].append(re.sub(r"^(- |\d+\. )", "", line))
        elif line.startswith("  ") and parts and parts[-1][0] in ("ul", "ol"):
            parts[-1][1][-1] += " " + line.strip()
        elif line.strip():
            if parts and parts[-1][0] == "p" and lines[at - 2] != "":
                parts[-1][1][-1] += " " + line.strip()
            else:
                parts.append(("p", [line.strip()]))
    return title, intro, sections


def check(sections):
    wrong = []
    for section in sections:
        for field in section["fields"]:
            if field["value"] is None:
                wrong.append(f"{field['label']} has no value")
            elif field["limit"] is not None and len(field["value"]) > field["limit"]:
                wrong.append(f"{field['label']} is {len(field['value'])} characters, {len(field['value']) - field['limit']} over its limit")
            if field["value"] and re.search("—|–|--", field["value"]):
                wrong.append(f"{field['label']} has a dash")
    return wrong


def inline(text):
    return re.sub(r"`([^`]+)`", r"<code>\1</code>", html.escape(text, quote=False))


def prose(parts):
    out = []
    for kind, items in parts:
        if kind == "p":
            out.append(f"<p>{inline(items[0])}</p>")
        else:
            out.append(f"<{kind}>" + "".join(f"<li>{inline(item)}</li>" for item in items) + f"</{kind}>")
    return "\n".join(out)


def slug(name):
    return re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-")


def page(title, intro, sections):
    with open(os.path.join(HERE, "listing_page.html")) as f:
        template = f.read()
    paste = [s for s in sections if s["fields"]]
    body = []
    for section in sections:
        body.append(f'<section id="{slug(section["name"])}"><h2>{html.escape(section["name"])}</h2>')
        body.append(prose(section["parts"]))
        for field in section["fields"]:
            count = len(field["value"])
            limit = f'<span class="count{" full" if field["limit"] == count else ""}">{count:,} / {field["limit"]:,}</span>' if field["limit"] else ""
            rows = "long" if field["value"].count("\n") > 3 or count > 300 else "short"
            key = slug(section["name"] + " " + field["label"])
            body.append(f'''<article class="field" data-key="{key}">
<header><h3>{html.escape(field["label"])}</h3>{limit}
<button type="button" class="copy" aria-label="Copy {html.escape(field["label"], quote=True)}">Copy</button></header>
<pre class="value {rows}" tabindex="0">{html.escape(field["value"], quote=False)}</pre>
{('<div class="note">' + prose(field["parts"]) + '</div>') if field["parts"] else ""}
<label class="entered"><input type="checkbox" id="entered-{key}"> Entered in App Store Connect</label>
</article>''')
        body.append("</section>")
    nav = "".join(f'<a href="#{slug(s["name"])}">{html.escape(s["name"])}</a>' for s in sections)
    fields = sum(len(s["fields"]) for s in paste)
    return (template.replace("<!--title-->", html.escape(title)).replace("<!--intro-->", prose(intro[:1]))
            .replace("<!--nav-->", nav).replace("<!--sections-->", "\n".join(body)).replace("<!--fields-->", str(fields)))


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    with open(os.path.join(HERE, "AppStoreListing.md")) as f:
        title, intro, sections = parse(f.read())
    wrong = check(sections)
    if wrong:
        sys.exit("the listing is wrong: " + "; ".join(wrong))
    with open(sys.argv[1], "w") as f:
        f.write(page(title, intro, sections))
    print(f"{sum(len(s['fields']) for s in sections)} values within their limits, written to {sys.argv[1]}")


if __name__ == "__main__":
    main()
