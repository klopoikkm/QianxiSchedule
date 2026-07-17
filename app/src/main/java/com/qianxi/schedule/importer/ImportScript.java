package com.qianxi.schedule.importer;

public final class ImportScript {
    private ImportScript() {}

    public static final String SCAN_PAGE = """
        (function () {
          const output = [];
          const seen = new Set();
          const docs = [document];
          for (const frame of document.querySelectorAll('iframe')) {
            try { if (frame.contentDocument) docs.push(frame.contentDocument); } catch (_) {}
          }

          function clean(value) {
            return String(value || '').replace(/\\u00a0/g, ' ').replace(/[ \\t]+/g, ' ')
              .replace(/\\n[ \\t]+/g, '\\n').replace(/\\n{3,}/g, '\\n\\n').trim();
          }
          function nodeText(node) {
            return clean(node.innerText || node.textContent || node.getAttribute('title') ||
              node.getAttribute('aria-label') || '');
          }
          function add(day, section, node) {
            day = Number(day); section = Number(section);
            if (!(day >= 1 && day <= 7) || !(section >= 1 && section <= 30)) return;
            const text = nodeText(node);
            if (!text || text.length < 2 || /^(无|暂无|--|-)$/.test(text)) return;
            const key = day + '|' + section + '|' + text;
            if (seen.has(key)) return;
            seen.add(key);
            output.push({ day, section, text,
              title: clean(node.getAttribute && (node.getAttribute('title') || '')) });
          }
          function addCell(day, section, cell) {
            const blocks = Array.from(cell.querySelectorAll(
              ':scope > .timetable_con, :scope > .kbcontent, :scope > .course, :scope > [class*="course-item"]'))
              .filter(node => nodeText(node).length > 1);
            if (blocks.length) blocks.forEach(node => add(day, section, node));
            else add(day, section, cell);
          }
          function sectionFrom(value, fallback) {
            const match = clean(value).match(/(?:第)?(\\d{1,2})(?:\\s*[-~至—]\\s*\\d{1,2})?\\s*节/);
            return match ? Number(match[1]) : fallback;
          }
          function scan(doc) {
            for (const cell of doc.querySelectorAll('td[id],div[id]')) {
              const id = cell.id || '';
              let match = id.match(/^(\\d)[-_](\\d{1,2})$/);
              if (match) addCell(Number(match[1]), Number(match[2]), cell);
              match = id.match(/^(\\d{1,2})[-_](\\d)$/);
              if (match && Number(match[1]) > 7) addCell(Number(match[2]), Number(match[1]), cell);
            }
            for (const cell of doc.querySelectorAll('[data-day],[data-weekday],[data-xq]')) {
              const day = cell.dataset.day || cell.dataset.weekday || cell.dataset.xq;
              const section = cell.dataset.section || cell.dataset.period || cell.dataset.jc ||
                sectionFrom(nodeText(cell), 1);
              addCell(day, section, cell);
            }
            for (const table of doc.querySelectorAll('table')) {
              const rows = Array.from(table.rows || []);
              rows.forEach((row, rowIndex) => {
                const cells = Array.from(row.cells || []);
                if (cells.length < 7) return;
                const offset = cells.length >= 8 ? cells.length - 7 : 0;
                const section = sectionFrom(nodeText(cells[0]), Math.max(1, rowIndex));
                for (let day = 1; day <= 7; day++) {
                  const cell = cells[offset + day - 1];
                  if (cell) addCell(day, section, cell);
                }
              });
            }
          }
          docs.forEach(scan);
          return JSON.stringify({ title: document.title || '', url: location.href, items: output });
        })();
        """;
}
