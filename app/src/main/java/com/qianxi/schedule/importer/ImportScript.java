package com.qianxi.schedule.importer;

public final class ImportScript {
    private ImportScript() {}

    public static String forAdapter(String adapterId) {
        // NEUQ/NEU fetch the timetable directly through the EAMS/JWAPP API, so they work even when
        // the portal page itself fails to render (e.g. jQuery missing → bare skeleton). Only the
        // generic adapter depends on the visible DOM.
        if (ImportAdapter.NEUQ_EAMS.equals(adapterId)) return NEUQ_EAMS_API;
        if (ImportAdapter.NEU.equals(adapterId)) return NEU_API;
        return GENERIC_PAGE;
    }

    private static final String GENERIC_PAGE = """
        (function () {
          window.__qianxiImportState = 'loading';
          window.__qianxiImportPayload = null;
          window.__qianxiImportError = '';
          try {
            const output = [];
            const seen = new Set();
            let structuredNodes = 0;
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
            function dayNumber(value) {
              const numeric = Number(value);
              if (numeric >= 1 && numeric <= 7) return numeric;
              const match = clean(value).match(/[一二三四五六日天]/);
              if (!match) return 0;
              if (match[0] === '日' || match[0] === '天') return 7;
              return '一二三四五六'.indexOf(match[0]) + 1;
            }
            function sectionNumber(value) {
              const numeric = Number(value);
              if (numeric >= 1 && numeric <= 30) return numeric;
              const match = clean(value).match(/\\d{1,2}/);
              return match ? Number(match[0]) : 0;
            }
            function addText(day, section, endSection, text, title) {
              day = dayNumber(day); section = sectionNumber(section);
              endSection = sectionNumber(endSection || section) || section;
              text = clean(text);
              if (!(day >= 1 && day <= 7) || !(section >= 1 && section <= 30)
                  || !text || text.length < 2 || /^(无|暂无|--|-)$/.test(text)) return;
              // Filter out common noise patterns: UI elements, metadata, pure names
              const compact = text.replace(/\\s/g, '');
              if (/^(操作|编辑|删除|查看|详情|添加|修改|保存|取消|确定|提交)$/.test(compact)) return;
              if (/^(实验辅导?老师|辅导教师|助教|监考老师)/.test(compact)) return;
              if (/^(代码|编号|序号|课程号|教学班号|上课时间|上课地点)$/.test(compact)) return;
              if (/^(授课小结|教学小结|课程小结|备注|说明|注释)/.test(compact)) return;
              // Filter single short words that are likely just names or labels
              if (compact.length <= 4 && !/[\\d()（）]/.test(compact) &&
                  !/课|程|学|实验|设计|理论|基础|导论|概论|原理|技术|方法/.test(compact)) return;
              const key = day + '|' + section + '|' + endSection + '|' + text;
              if (seen.has(key)) return;
              seen.add(key);
              output.push({ day, section, endSection: Math.max(section, endSection), text,
                title: clean(title || '') });
            }
            function add(day, section, endSection, node) {
              const text = nodeText(node);
              addText(day, section, endSection, text,
                node && node.getAttribute ? node.getAttribute('title') : '');
            }
            function addCell(day, section, cell) {
              const endSection = Number(section) + Math.max(1, Number(cell.rowSpan || 1)) - 1;
              const blocks = Array.from(cell.querySelectorAll(
                ':scope > .timetable_con, :scope > .kbcontent, :scope > .course, ' +
                ':scope > [class*="course-item"], :scope > [class*="CourseItemInfo"]'))
                .filter(node => nodeText(node).length > 1);
              if (blocks.length) blocks.forEach(node => add(day, section, endSection, node));
              else add(day, section, endSection, cell);
            }
            function sectionFrom(value, fallback) {
              const match = clean(value).match(/(?:第)?(\\d{1,2})(?:\\s*[-~至—]\\s*\\d{1,2})?\\s*节/);
              return match ? Number(match[1]) : fallback;
            }
            function field(value, names) {
              if (!value || typeof value !== 'object') return '';
              for (const name of names) {
                if (value[name] !== undefined && value[name] !== null && String(value[name]).trim()) {
                  return value[name];
                }
              }
              return '';
            }
            function addStructured(value) {
              if (!value || typeof value !== 'object' || Array.isArray(value)) return;
              const day = field(value, ['dayOfWeek', 'weekday', 'weekDay', 'day', 'xq', 'week_day']);
              const section = field(value, ['beginSection', 'startSection', 'section', 'period', 'jc', 'startJc']);
              if (!day || !section) return;
              const name = field(value, ['courseName', 'course_name', 'name', 'courseTitle', 'title']);
              const teacher = field(value, ['teacherName', 'teacher', 'teachers', 'instructor']);
              const location = field(value, ['roomName', 'location', 'classroom', 'room']);
              const weeks = field(value, ['weekDescription', 'weeks', 'weekText', 'week']);
              const text = [name, teacher, location, weeks].filter(Boolean).join('\\n');
              addText(day, section, field(value, ['endSection', 'finishSection', 'endJc', 'endPeriod']) || section,
                text || value.text || value.content || '', field(value, ['title', 'courseName']));
            }
            function scanStructured(value, depth) {
              if (!value || depth > 6) return;
              if (++structuredNodes > 5000) return;
              if (Array.isArray(value)) {
                for (const item of value) scanStructured(item, depth + 1);
                return;
              }
              if (typeof value !== 'object') return;
              addStructured(value);
              for (const key of Object.keys(value)) {
                if (key !== 'html' && key !== 'style' && key !== 'children') {
                  scanStructured(value[key], depth + 1);
                }
              }
            }
            function headerDay(value) {
              const compact = clean(value).replace(/\\s+/g, '');
              let match = compact.match(/^(?:星期|周)([一二三四五六日天])/);
              if (!match) match = compact.match(/^([一二三四五六日天])$/);
              return match ? dayNumber(match[1]) : 0;
            }
            function scanTable(table) {
              const rows = Array.from(table.rows || []);
              const grid = [];
              rows.forEach((row, rowIndex) => {
                if (!grid[rowIndex]) grid[rowIndex] = [];
                let column = 0;
                for (const cell of Array.from(row.cells || [])) {
                  while (grid[rowIndex][column]) column++;
                  const rowSpan = Math.max(1, Number(cell.rowSpan || 1));
                  const colSpan = Math.max(1, Number(cell.colSpan || 1));
                  for (let rowOffset = 0; rowOffset < rowSpan; rowOffset++) {
                    const targetRow = rowIndex + rowOffset;
                    if (!grid[targetRow]) grid[targetRow] = [];
                    for (let colOffset = 0; colOffset < colSpan; colOffset++) {
                      grid[targetRow][column + colOffset] = {cell, originRow: rowIndex};
                    }
                  }
                  column += colSpan;
                }
              });
              const maxColumns = grid.reduce((max, row) => Math.max(max, row.length), 0);
              if (maxColumns < 7) return;
              const dayColumns = {};
              for (let row = 0; row < Math.min(4, grid.length); row++) {
                for (let column = 0; column < grid[row].length; column++) {
                  const entry = grid[row][column];
                  const day = entry ? headerDay(nodeText(entry.cell)) : 0;
                  if (day && dayColumns[day] === undefined) dayColumns[day] = column;
                }
              }
              if (Object.keys(dayColumns).length !== 7) {
                const startColumn = Math.max(0, maxColumns - 7);
                for (let day = 1; day <= 7; day++) dayColumns[day] = startColumn + day - 1;
              }
              grid.forEach((logicalRow, rowIndex) => {
                const labels = logicalRow.slice(0, Math.min(4, logicalRow.length))
                  .filter(Boolean).map(entry => nodeText(entry.cell)).join(' ');
                const section = sectionFrom(labels, Math.max(1, rowIndex));
                for (let day = 1; day <= 7; day++) {
                  const entry = logicalRow[dayColumns[day]];
                  if (entry && entry.originRow === rowIndex) addCell(day, section, entry.cell);
                }
              });
            }
            function scan(doc) {
              for (const cell of doc.querySelectorAll('td[id],div[id]')) {
                const id = cell.id || '';
                let match = id.match(/^(\\d)[-_](\\d{1,2})$/);
                if (match) addCell(Number(match[1]), Number(match[2]), cell);
                match = id.match(/^(\\d{1,2})[-_](\\d)$/);
                if (match && Number(match[1]) > 7) addCell(Number(match[2]), Number(match[1]), cell);
                match = id.match(/(?:day|d|xq)[-_]?([1-7])[^0-9]+(?:section|s|jc)?[_-]?(\\d{1,2})/i);
                if (match) addCell(Number(match[1]), Number(match[2]), cell);
              }
              for (const cell of doc.querySelectorAll(
                  '[data-day],[data-weekday],[data-xq],[data-day-of-week],[data-course-day]')) {
                const day = cell.dataset.day || cell.dataset.weekday || cell.dataset.xq ||
                  cell.dataset.dayOfWeek || cell.dataset.courseDay;
                const section = cell.dataset.section || cell.dataset.period || cell.dataset.jc ||
                  cell.dataset.startSection || cell.dataset.periodIndex ||
                  sectionFrom(nodeText(cell), 1);
                addCell(day, section, cell);
              }
              for (const script of doc.querySelectorAll('script[type="application/json"],script[data-course-data]')) {
                try { scanStructured(JSON.parse(script.textContent || ''), 0); } catch (_) {}
              }
              for (const table of doc.querySelectorAll('table')) scanTable(table);
            }
            docs.forEach(scan);
            const html = document.documentElement ? document.documentElement.innerHTML : '';
            let detected = 'generic';
            if (/zfsoft|正方软件|jwglxt/i.test(html)) detected = 'zhengfang';
            else if (/强智|qiangzhi|jw\\.glite/i.test(html)) detected = 'qiangzhi';
            else if (/青果|kingosoft|URP综合教务/i.test(html)) detected = 'kingosoft';
            window.__qianxiImportPayload = {
              adapter: detected,
              source: 'page-dom',
              sourceUrl: location.href,
              pageTitle: document.title || '',
              term: '',
              diagnostics: {
                frames: docs.length - 1,
                tables: document.querySelectorAll('table').length,
                candidates: output.length
              },
              items: output
            };
            window.__qianxiImportState = 'done';
          } catch (error) {
            window.__qianxiImportError = String(error && error.message ? error.message : error);
            window.__qianxiImportState = 'error';
          }
        })();
        """;

    private static final String NEUQ_EAMS_API = """
        (function () {
          window.__qianxiImportState = 'loading';
          window.__qianxiImportPayload = null;
          window.__qianxiImportError = '';

          const output = [];
          const seen = new Set();
          let scannedTables = 0;

          function clean(value) {
            return String(value || '').replace(/\\u00a0/g, ' ').replace(/[ \\t]+/g, ' ')
              .replace(/\\n[ \\t]+/g, '\\n').replace(/\\n{3,}/g, '\\n\\n').trim();
          }
          async function fetchText(url, options) {
            const controller = new AbortController();
            const timer = setTimeout(() => controller.abort(), 15000);
            try {
              const response = await fetch(url, Object.assign({
                credentials: 'include', signal: controller.signal,
                headers: {'X-Requested-With': 'XMLHttpRequest'}
              }, options || {}));
              if (!response.ok) throw new Error('EAMS 请求失败（HTTP ' + response.status + '）');
              return {text: await response.text(), url: response.url};
            } finally {
              clearTimeout(timer);
            }
          }
          function isLoginPage(result) {
            return /authserver\\/login|name=["']username["']|统一身份认证/i.test(
              (result.url || '') + ' ' + (result.text || ''));
          }
          function documentOf(html) {
            return new DOMParser().parseFromString(html, 'text/html');
          }
          function namedValue(doc, name) {
            const element = doc.querySelector('[name="' + name + '"]');
            if (!element) return '';
            if (element.tagName === 'SELECT') {
              const selected = element.querySelector('option:checked,option[selected]');
              return clean((selected && selected.value) || element.value || '');
            }
            return clean(element.value || element.getAttribute('value') || '');
          }
          function firstNumber(html, patterns) {
            for (const pattern of patterns) {
              const match = html.match(pattern);
              if (match) return match[1];
            }
            return '';
          }
          function discoverContext(html) {
            const doc = documentOf(html);
            const ids = namedValue(doc, 'ids') || namedValue(doc, 'student.id') ||
              firstNumber(html, [
                /name=["']ids["'][^>]*value=["'](\\d+)/i,
                /["']ids["']\\s*,\\s*["']?(\\d+)/i,
                /(?:studentId|stdId|ids)\\s*[:=]\\s*["']?(\\d+)/i
              ]);
            const semester = namedValue(doc, 'semester.id') ||
              firstNumber(html, [
                /name=["']semester\\.id["'][^>]*value=["'](\\d+)/i,
                /["']semester\\.id["']\\s*,\\s*["']?(\\d+)/i,
                /semester\\.id\\s*[:=]\\s*["']?(\\d+)/i
              ]);
            return {ids, semester};
          }
          function nodeText(node) {
            return clean(node.innerText || node.textContent ||
              (node.getAttribute && (node.getAttribute('title') || node.getAttribute('aria-label'))) || '');
          }
          function dayFromHeader(value) {
            const match = clean(value).replace(/\\s+/g, '').match(/^(?:星期|周)?([一二三四五六日天])$/);
            if (!match) return 0;
            if (match[1] === '日' || match[1] === '天') return 7;
            return '一二三四五六'.indexOf(match[1]) + 1;
          }
          function sectionFrom(value, fallback) {
            const match = clean(value).match(/(?:第)?(\\d{1,2})(?:\\s*[-~至—]\\s*\\d{1,2})?\\s*节/);
            return match ? Number(match[1]) : fallback;
          }
          function add(day, section, endSection, node) {
            day = Number(day); section = Number(section); endSection = Number(endSection || section);
            const text = nodeText(node);
            if (!(day >= 1 && day <= 7) || !(section >= 1 && section <= 30)
                || text.length < 2 || /^(无|暂无|--|-)$/.test(text)) return;
            const key = [day, section, endSection, text].join('|');
            if (seen.has(key)) return;
            seen.add(key);
            output.push({day, section, endSection: Math.max(section, endSection), text,
              title: clean(node.getAttribute && (node.getAttribute('title') || ''))});
          }
          function addCell(day, section, cell) {
            const endSection = section + Math.max(1, Number(cell.rowSpan || 1)) - 1;
            const blocks = Array.from(cell.querySelectorAll(
              ':scope > .kbcontent,:scope > .course,:scope > .activity,' +
              ':scope > [class*="course"],:scope > [class*="activity"]'))
              .filter(node => nodeText(node).length > 1);
            if (blocks.length) blocks.forEach(node => add(day, section, endSection, node));
            else add(day, section, endSection, cell);
          }
          function scanTable(table) {
            scannedTables++;
            const rows = Array.from(table.rows || []);
            const grid = [];
            rows.forEach((row, rowIndex) => {
              if (!grid[rowIndex]) grid[rowIndex] = [];
              let column = 0;
              for (const cell of Array.from(row.cells || [])) {
                while (grid[rowIndex][column]) column++;
                const rowSpan = Math.max(1, Number(cell.rowSpan || 1));
                const colSpan = Math.max(1, Number(cell.colSpan || 1));
                for (let r = 0; r < rowSpan; r++) {
                  if (!grid[rowIndex + r]) grid[rowIndex + r] = [];
                  for (let c = 0; c < colSpan; c++) {
                    grid[rowIndex + r][column + c] = {cell, originRow: rowIndex};
                  }
                }
                column += colSpan;
              }
            });
            const width = grid.reduce((max, row) => Math.max(max, row.length), 0);
            if (width < 7) return;
            const columns = {};
            for (let row = 0; row < Math.min(5, grid.length); row++) {
              for (let column = 0; column < grid[row].length; column++) {
                const entry = grid[row][column];
                const day = entry ? dayFromHeader(nodeText(entry.cell)) : 0;
                if (day && columns[day] === undefined) columns[day] = column;
              }
            }
            if (Object.keys(columns).length !== 7) {
              const start = Math.max(0, width - 7);
              for (let day = 1; day <= 7; day++) columns[day] = start + day - 1;
            }
            grid.forEach((row, rowIndex) => {
              const labels = row.slice(0, Math.min(4, row.length)).filter(Boolean)
                .map(entry => nodeText(entry.cell)).join(' ');
              const section = sectionFrom(labels, Math.max(1, rowIndex));
              for (let day = 1; day <= 7; day++) {
                const entry = row[columns[day]];
                if (entry && entry.originRow === rowIndex) addCell(day, section, entry.cell);
              }
            });
          }
          function scanDocument(doc) {
            for (const table of doc.querySelectorAll('table')) scanTable(table);
          }

          // Beangle EAMS ("courseTableForStd") does NOT put the week range in the rendered cell
          // text — only a compact "第几节" label. The real schedule lives in inline JS at the bottom
          // of the returned HTML, emitted like this (one activity, bound to every unit it occupies):
          //   var actTeacherId = "..."; var actTeacherName = "张旭";
          //   var activity = new TaskActivity(actTeacherId,actTeacherName,
          //       "课程号(课程名)","高等数学","roomId","综合楼1208","001111111100000000000",null,null);
          //   index =2*unitCount+2;  table0.activities[index][...] = activity;
          //   index =2*unitCount+3;  table0.activities[index][...] = activity;   // multi-period span
          // The grid index is the VARIABLE `index`, not a literal — the real day/unit are in the
          // `index = day*unitCount + unit` expression (both 0-based). The 7th constructor argument
          // is a binary week string (position n = week n has class). Parsing this gives the true
          // weeks, weekday and unit that the rendered table cannot.
          function extractArgList(html, startIndex) {
            // startIndex points just after the opening '(' of new TaskActivity(. Walk to the
            // matching ')', respecting quoted strings so commas/parens inside them don't confuse us.
            let i = startIndex, depth = 1, quote = '';
            while (i < html.length && depth > 0) {
              const ch = html[i];
              if (quote) {
                if (ch === '\\\\') { i += 2; continue; }
                if (ch === quote) quote = '';
              } else if (ch === '"' || ch === "'") {
                quote = ch;
              } else if (ch === '(') {
                depth++;
              } else if (ch === ')') {
                depth--;
              }
              i++;
            }
            return {argStr: html.slice(startIndex, i - 1), end: i};
          }
          function parseTaskActivities(html, unitCount) {
            const items = [];
            const uc = unitCount > 0 ? unitCount : 12;
            const marker = 'new TaskActivity(';
            let pos = 0;
            while (true) {
              const at = html.indexOf(marker, pos);
              if (at < 0) break;
              const parsed = extractArgList(html, at + marker.length);
              const args = splitArgs(parsed.argStr);
              // Every `index = day*unitCount+unit; table0.activities[index]...` binding for this
              // activity sits between it and the next `new TaskActivity(` (cap the window so a
              // trailing activity without a successor cannot run away over the whole document).
              const next = html.indexOf(marker, parsed.end);
              const regionEnd = next < 0 ? Math.min(html.length, parsed.end + 4000) : next;
              const region = html.slice(parsed.end, regionEnd);
              pos = next < 0 ? html.length : next;
              if (!args || args.length < 7) continue;
              const courseName = stripQuotes(args[3]) || stripQuotes(args[2]);
              const room = stripQuotes(args[5]);
              const weekBin = stripQuotes(args[6]);
              const teacher = stripQuotes(args[1]);
              if (!courseName || !/^[01]+$/.test(weekBin)) continue;
              const idxRe = /index\\s*=\\s*(\\d+)\\s*\\*\\s*(?:unitCount|\\d+)\\s*\\+\\s*(\\d+)/g;
              let mm;
              while ((mm = idxRe.exec(region)) !== null) {
                const day = Number(mm[1]) + 1;        // 0-based weekday → 1-based (Mon=1)
                const startUnit = Number(mm[2]) + 1;  // 0-based unit → 1-based node
                if (day >= 1 && day <= 7 && startUnit >= 1 && startUnit <= 30) {
                  items.push({courseName, teacher, room, weekBin, day, startUnit});
                }
              }
            }
            return items;
          }
          function splitArgs(str) {
            // Split a JS argument list on top-level commas, respecting quotes.
            const out = [];
            let cur = '', quote = '';
            for (let i = 0; i < str.length; i++) {
              const ch = str[i];
              if (quote) {
                if (ch === quote && str[i - 1] !== '\\\\') quote = '';
                cur += ch;
              } else if (ch === '"' || ch === "'") {
                quote = ch; cur += ch;
              } else if (ch === ',') {
                out.push(cur.trim()); cur = '';
              } else cur += ch;
            }
            if (cur.trim()) out.push(cur.trim());
            return out;
          }
          function stripQuotes(v) {
            if (v == null) return '';
            return clean(String(v).replace(/^\\s*["']|["']\\s*$/g, ''));
          }
          function weekBinToText(bin) {
            // beangle's vaildWeeks string is 1-INDEXED: position 0 is a placeholder and is always
            // '0'; position n (n >= 1) means "week n has class". Pushing `i + 1` was off-by-one and
            // shifted every course forward one week (e.g. real 1-14 → shown as 2-15). Push `i`.
            const weeks = [];
            for (let i = 0; i < bin.length; i++) if (bin[i] === '1') weeks.push(i);
            if (!weeks.length) return '';
            const parts = [];
            let s = weeks[0], p = weeks[0];
            for (let i = 1; i < weeks.length; i++) {
              if (weeks[i] === p + 1) { p = weeks[i]; continue; }
              parts.push(s === p ? '' + s : s + '-' + p);
              s = weeks[i]; p = weeks[i];
            }
            parts.push(s === p ? '' + s : s + '-' + p);
            return parts.join(',') + '周';
          }
          function unitCountOf(html) {
            const m = html.match(/unitCount\\s*=\\s*(\\d+)/);
            return m ? Number(m[1]) : 0;
          }
          function emitActivities(activities) {
            // Adjacent units of the same course/week/day are one multi-period class; merge them
            // into a section..endSection range so a 2-node lecture shows as one tall card.
            const byKey = {};
            for (const a of activities) {
              const key = [a.courseName, a.teacher, a.room, a.weekBin, a.day].join('\\u0000');
              (byKey[key] || (byKey[key] = [])).push(a);
            }
            for (const key of Object.keys(byKey)) {
              const group = byKey[key].sort((x, y) => x.startUnit - y.startUnit);
              let run = [group[0]];
              const flush = () => {
                const first = run[0], last = run[run.length - 1];
                const weeks = weekBinToText(first.weekBin);
                output.push({
                  day: first.day,
                  section: first.startUnit,
                  endSection: last.startUnit,
                  name: first.courseName,
                  teacher: first.teacher,
                  location: first.room,
                  weeks: weeks,
                  text: [first.courseName, first.teacher, first.room, weeks].filter(Boolean).join('\\n'),
                  title: first.courseName
                });
              };
              for (let i = 1; i < group.length; i++) {
                if (group[i].startUnit === run[run.length - 1].startUnit + 1) run.push(group[i]);
                else { flush(); run = [group[i]]; }
              }
              flush();
            }
          }
          // The real fix for the "courseName+\\"" / "actTeacherName.join(',')" symptom: NEUQ's
          // `new TaskActivity(...)` is called with JS EXPRESSIONS (variables, string concatenation,
          // .join()), not literals — the constructor source text can never resolve them, only a JS
          // runtime can. But we don't need to re-execute anything: when the user taps "拉取课表" while
          // the EAMS timetable page is open in the WebView, its TaskActivity_01.js has already run
          // and populated `window.table0.activities` with fully evaluated activity objects whose
          // fields are real strings (courseName, teachers, room, vaildWeeks). Reading those beats
          // any parse/replay strategy. Same-origin iframes discovered on the page (e.g. an EAMS
          // sub-frame that hosts the actual timetable) are searched too.
          //
          // Beangle's TaskActivity constructor sets fields like:
          //   this.teacherId, this.teachers, this.courseId, this.courseName,
          //   this.roomId, this.room, this.vaildWeeks (sic — "vaild", not "valid")
          // Slots in activities[] are Beangle "OpaqueArray" objects: numeric indices 0..count-1
          // hold TaskActivity instances, and .count is the occupancy for that unit.
          function harvestFromWindow(win) {
            const items = [];
            let table = null;
            let unitCount = 0;
            try { unitCount = Number(win.unitCount) || 0; } catch (e) {}
            try {
              for (const k in win) {
                try {
                  if (/^table\\d+$/.test(k) && win[k] && win[k].activities) {
                    table = win[k]; break;
                  }
                } catch (e) {}
              }
            } catch (e) {}
            if (!table) {
              try { if (win.table0 && win.table0.activities) table = win.table0; } catch (e) {}
            }
            if (!table || !table.activities) return items;
            if (!unitCount) unitCount = 12;
            const acts = table.activities;
            for (let i = 0; i < acts.length; i++) {
              const slot = acts[i];
              if (!slot) continue;
              const count = typeof slot.count === 'number'
                ? slot.count : (Array.isArray(slot) ? slot.length : 0);
              const entries = [];
              for (let j = 0; j < count; j++) {
                try { if (slot[j]) entries.push(slot[j]); } catch (e) {}
              }
              // Some Beangle builds store activities directly in a flat array without a .count.
              if (!entries.length && slot && typeof slot === 'object'
                  && (slot.courseName || slot.__qx)) {
                entries.push(slot);
              }
              for (const a of entries) {
                if (!a) continue;
                let courseName = '';
                let teacher = '';
                let room = '';
                let weekBin = '';
                // First choice: the constructed object's real string fields.
                try {
                  courseName = clean(String(a.courseName || a.course || ''));
                  teacher = clean(String(a.teachers || a.teacherName || a.teacher || ''));
                  room = clean(String(a.room || a.roomName || ''));
                  weekBin = String(a.vaildWeeks || a.validWeeks || a.weeks || '');
                } catch (e) {}
                // Second choice: our capture shim stored the original arguments on __qx.
                if ((!courseName || !weekBin) && a.__qx) {
                  const args = a.__qx;
                  if (!courseName) {
                    courseName = clean(String(args[3] != null ? args[3] : ''))
                      || clean(String(args[2] != null ? args[2] : ''));
                  }
                  if (!teacher) teacher = clean(String(args[1] != null ? args[1] : ''));
                  if (!room) room = clean(String(args[5] != null ? args[5] : ''));
                  if (!weekBin) weekBin = String(args[6] != null ? args[6] : '');
                }
                if (!courseName || !/^[01]+$/.test(weekBin)) continue;
                const day = Math.floor(i / unitCount) + 1;
                const startUnit = (i % unitCount) + 1;
                if (day >= 1 && day <= 7 && startUnit >= 1 && startUnit <= 30) {
                  items.push({courseName, teacher, room, weekBin, day, startUnit});
                }
              }
            }
            return items;
          }
          function harvestFromCurrentPage() {
            // Read the live EAMS page the WebView is showing: this is the only place TaskActivity
            // arguments are already evaluated, no matter how they were written in source.
            const wins = [window];
            try {
              for (const frame of document.querySelectorAll('iframe')) {
                try { if (frame.contentWindow) wins.push(frame.contentWindow); } catch (e) {}
              }
            } catch (e) {}
            for (const w of wins) {
              const out = harvestFromWindow(w);
              if (out.length) return out;
            }
            return [];
          }
          // Fallback when the user hasn't opened the timetable page: fetch it ourselves and inject
          // it into a same-origin iframe via document.write. `about:blank` inherits our origin, so
          // TaskActivity_01.js loads normally, cookies attach, and the inline scripts evaluate.
          // (srcdoc puts the iframe under an opaque origin — subresource loading and cross-frame
          // property access misbehave there, which is why the 1.17.2/1.17.3 srcdoc path silently
          // produced no activities.)
          function injectCapture(html) {
            // Belt-and-braces: swap in a TaskActivity shim BEFORE the real one loads, so if any
            // caller passes literal expressions we still record them for the fallback read path.
            const cap = '<script>(function(){var R=window.TaskActivity;'
              + 'function T(){try{if(R)R.apply(this,arguments);}catch(e){}'
              + 'try{this.__qx=Array.prototype.slice.call(arguments);}catch(e){}return this;}'
              + 'try{if(R&&R.prototype)T.prototype=R.prototype;}catch(e){}window.TaskActivity=T;})();</script>';
            const low = html.toLowerCase();
            let at = low.indexOf('taskactivity_01.js');
            if (at >= 0) {
              const close = low.indexOf('</script>', at);
              if (close >= 0) {
                const ins = close + 9;
                return html.slice(0, ins) + cap + html.slice(ins);
              }
            }
            at = html.indexOf('new TaskActivity(');
            if (at >= 0) {
              const start = low.lastIndexOf('<script', at);
              if (start >= 0) return html.slice(0, start) + cap + html.slice(start);
            }
            return cap + html;
          }
          function renderAndHarvest(html) {
            return new Promise(resolve => {
              const frame = document.createElement('iframe');
              frame.style.cssText =
                'position:absolute;left:-9999px;top:0;width:1200px;height:2400px;visibility:hidden;';
              // about:blank inherits the parent's origin — scripts inside see same-origin cookies
              // and can load TaskActivity_01.js off the EAMS server normally.
              frame.src = 'about:blank';
              let finished = false;
              const finish = () => {
                if (finished) return;
                finished = true;
                let out = [];
                try { out = harvestFromWindow(frame.contentWindow || {}); } catch (e) {}
                try { frame.remove(); } catch (e) {}
                resolve(out);
              };
              document.body.appendChild(frame);
              const write = () => {
                try {
                  const doc = frame.contentWindow.document;
                  const base = '<base href="' + location.origin + '/eams/">';
                  let payload = injectCapture(html);
                  payload = /<head[^>]*>/i.test(payload)
                    ? payload.replace(/<head[^>]*>/i, match => match + base)
                    : base + payload;
                  doc.open();
                  doc.write(payload);
                  doc.close();
                } catch (e) {
                  finish();
                  return;
                }
                // Give TaskActivity_01.js + inline bootstrap time to run.
                setTimeout(finish, 4000);
              };
              // about:blank load is usually synchronous but wait a tick to be safe.
              setTimeout(write, 60);
              setTimeout(finish, 12000);
            });
          }
          function renderAndScan(html) {
            return new Promise(resolve => {
              const frame = document.createElement('iframe');
              frame.style.display = 'none';
              let finished = false;
              const done = () => {
                if (finished) return;
                finished = true;
                try { if (frame.contentDocument) scanDocument(frame.contentDocument); } catch (_) {}
                frame.remove();
                resolve();
              };
              frame.onload = () => setTimeout(done, 1200);
              const base = '<base href="' + location.origin + '/eams/">';
              frame.srcdoc = /<head[^>]*>/i.test(html)
                ? html.replace(/<head[^>]*>/i, match => match + base) : base + html;
              document.body.appendChild(frame);
              setTimeout(done, 5000);
            });
          }

          (async function () {
            try {
              if (!/^jwxt\\.neuq\\.edu\\.cn$/i.test(location.hostname)) {
                throw new Error('东北大学教务适配器无法识别当前域名');
              }
              const currentHtml = document.documentElement ? document.documentElement.outerHTML : '';
              const landing = await fetchText('/eams/courseTableForStd.action');
              if (isLoginPage(landing)) throw new Error('登录状态已失效，请重新登录统一身份认证');
              let context = discoverContext(landing.text);
              if (!context.ids || !context.semester) {
                const current = discoverContext(currentHtml);
                context = {ids: context.ids || current.ids, semester: context.semester || current.semester};
              }
              if (!context.ids) {
                throw new Error('未获取到学生课表 ID，请先在教务菜单中打开一次“我的课表”');
              }
              if (!context.semester) {
                throw new Error('未获取到当前学期，请在“我的课表”页面选择学期后重试');
              }

              const form = new URLSearchParams();
              form.append('ignoreHead', '1');
              form.append('showPrintAndExport', '1');
              form.append('setting.kind', 'std');
              form.append('ids', context.ids);
              form.append('semester.id', context.semester);
              form.append('startWeek', '1');
              const result = await fetchText('/eams/courseTableForStd!courseTable.action', {
                method: 'POST', body: form,
                headers: {
                  'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8',
                  'X-Requested-With': 'XMLHttpRequest'
                }
              });
              if (isLoginPage(result)) throw new Error('登录状态已失效，请重新登录统一身份认证');

              // Strategy, in order of fidelity:
              // 1. Read `table0.activities` from the LIVE page/frames the user has already loaded —
              //    TaskActivity has run there, so courseName/teachers/room/vaildWeeks are real strings.
              // 2. Render the fetched HTML in a same-origin about:blank iframe and read the same
              //    fields — used when the user tapped 拉取 from a non-timetable page.
              // 3. Text-parse the constructor source (works only for literal-argument deployments).
              // 4. DOM scan the rendered timetable cells as last resort.
              const unitCount = unitCountOf(result.text);
              let activities = harvestFromCurrentPage();
              if (!activities.length) {
                try { activities = await renderAndHarvest(result.text); } catch (e) { activities = []; }
              }
              if (!activities.length) activities = parseTaskActivities(result.text, unitCount);
              if (activities.length) {
                emitActivities(activities);
              } else {
                scanDocument(documentOf(result.text));
                if (!output.length) await renderAndScan(result.text);
              }

              window.__qianxiImportPayload = {
                adapter: 'neuq-eams',
                source: 'neuq-eams',
                sourceUrl: location.origin + '/eams/courseTableForStd.action',
                pageTitle: '东北大学秦皇岛个人课表',
                term: context.semester,
                campus: '东北大学秦皇岛分校',
                diagnostics: {frames: 0, tables: scannedTables, candidates: output.length},
                items: output
              };
              window.__qianxiImportState = 'done';
            } catch (error) {
              window.__qianxiImportError = String(error && error.message ? error.message : error);
              window.__qianxiImportState = 'error';
            }
          })();
        })();
        """;

    private static final String NEU_API = """
        (function () {
          window.__qianxiImportState = 'loading';
          window.__qianxiImportPayload = null;
          window.__qianxiImportError = '';

          function clean(value) {
            return String(value || '').replace(/\\u00a0/g, ' ').replace(/\\s+/g, ' ').trim();
          }
          async function fetchJson(url, options) {
            const controller = new AbortController();
            const timer = setTimeout(() => controller.abort(), 12000);
            try {
              const response = await fetch(url, Object.assign({
                credentials: 'include', signal: controller.signal
              }, options || {}));
              if (!response.ok) throw new Error('教务接口请求失败（HTTP ' + response.status + '）');
              const raw = await response.text();
              try {
                return JSON.parse(raw);
              } catch (_) {
                if (/^\\s*</.test(raw)) {
                  throw new Error('登录状态已失效，请重新登录东北大学教务系统');
                }
                throw new Error('教务接口返回了无法识别的数据');
              }
            } finally {
              clearTimeout(timer);
            }
          }
          function firstArray(value) {
            if (Array.isArray(value)) return value;
            if (!value || typeof value !== 'object') return [];
            for (const key of ['campusList', 'rows', 'data', 'list']) {
              if (Array.isArray(value[key])) return value[key];
            }
            for (const key of Object.keys(value)) {
              if (Array.isArray(value[key])) return value[key];
            }
            return [];
          }
          function teacherOf(item) {
            const raw = clean(item.weeksAndTeachers || item.teacherName || item.teachers || '');
            if (!raw) return '';
            const parts = raw.split('/');
            return clean(parts[parts.length - 1].replace(/\\[主讲\\]/g, ''));
          }
          function detailOf(raw, item) {
            const text = clean(raw);
            const match = text.match(/^([0-9\\s,，、\\-~至—()（）单双周]+)\\s*(.*)$/);
            return {
              weeks: match ? clean(match[1]) : clean(item.weekDescription || item.weeks || ''),
              location: match ? clean(match[2]) : clean(item.roomName || item.location || '')
            };
          }

          (async function () {
            try {
              if (!/jwxt\\.neu\\.edu\\.cn$/i.test(location.hostname)) {
                throw new Error('东北大学适配器只能在 https://jwxt.neu.edu.cn/ 内运行');
              }
              const userData = await fetchJson('/jwapp/sys/homeapp/api/home/currentUser.do');
              const datas = userData && userData.datas ? userData.datas : {};
              const welcome = datas.welcomeInfo || datas;
              const termCode = clean(welcome.xnxqdm || welcome.termCode || welcome.semesterCode || '');
              if (!termCode) throw new Error('未获取到当前学期，请先进入教务首页并选择学期');

              const campusData = await fetchJson(
                '/jwapp/sys/homeapp/api/home/student/getMyScheduledCampus.do?termCode=' +
                encodeURIComponent(termCode));
              const campuses = firstArray(campusData ? campusData.datas : null);
              if (!campuses.length) throw new Error('未获取到校区信息，请确认当前账号已有排课数据');

              const output = [];
              const seen = new Set();
              for (const campus of campuses) {
                const campusCode = clean(campus.id || campus.code || campus.campusCode || '');
                if (!campusCode) continue;
                const form = new URLSearchParams();
                form.append('termCode', termCode);
                form.append('campusCode', campusCode);
                form.append('type', 'term');
                const scheduleData = await fetchJson(
                  '/jwapp/sys/homeapp/api/home/student/getMyScheduleDetail.do', {
                    method: 'POST', body: form,
                    headers: {'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8'}
                  });
                const scheduleDatas = scheduleData && scheduleData.datas ? scheduleData.datas : {};
                const arranged = Array.isArray(scheduleDatas.arrangedList)
                  ? scheduleDatas.arrangedList : firstArray(scheduleDatas);
                for (const item of arranged) {
                  const details = Array.isArray(item.titleDetail) && item.titleDetail.length > 1
                    ? item.titleDetail.slice(1) : [item.weekDescription || item.weeks || ''];
                  for (const rawDetail of details) {
                    const detail = detailOf(rawDetail, item);
                    const record = {
                      name: clean(item.courseName || item.name || ''),
                      teacher: teacherOf(item),
                      location: detail.location,
                      weeks: detail.weeks,
                      day: Number(item.dayOfWeek),
                      section: Number(item.beginSection),
                      endSection: Number(item.endSection || item.beginSection),
                      startTime: clean(item.beginTime || item.startTime || ''),
                      endTime: clean(item.endTime || item.finishTime || ''),
                      text: clean(item.courseName || item.name || ''),
                      title: clean(rawDetail)
                    };
                    const key = [record.name, record.day, record.section, record.endSection,
                      record.teacher, record.location, record.weeks].join('|');
                    if (record.name && record.day && record.section && !seen.has(key)) {
                      seen.add(key);
                      output.push(record);
                    }
                  }
                }
              }
              window.__qianxiImportPayload = {
                adapter: 'neu',
                source: 'neu-api',
                sourceUrl: location.origin,
                pageTitle: document.title || '',
                term: termCode,
                campus: campuses.map(c => clean(c.name || c.text || c.label || c.id)).join('、'),
                diagnostics: { campuses: campuses.length, candidates: output.length },
                items: output
              };
              window.__qianxiImportState = 'done';
            } catch (error) {
              window.__qianxiImportError = String(error && error.message ? error.message : error);
              window.__qianxiImportState = 'error';
            }
          })();
        })();
        """;
}
