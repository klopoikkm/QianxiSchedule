package com.qianxi.schedule.importer;

public final class ImportScript {
    private ImportScript() {}

    public static String forAdapter(String adapterId) {
        return GENERIC_PAGE;
    }

    public static String forAdapter(String adapterId, String pageUrl) {
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
            let runtimeActivities = 0;
            let visualCandidates = 0;
            let visualDays = 0;
            let visualSections = 0;
            let visualNodes = 0;
            let recordRows = 0;
            let missingWeekCandidates = 0;
            let selectedSource = 'page-dom';
            let domPassSkipped = 0;
            let qiangzhiFallback = 0;
            let structuredCandidates = 0;
            let vueRoots = 0;
            const docs = [];
            const seenDocs = new Set();
            const roots = [];
            const seenRoots = new Set();
            const seenStructured = new WeakSet();
            function collectDocuments(doc, depth) {
              if (!doc || depth > 4 || seenDocs.has(doc)) return;
              seenDocs.add(doc);
              docs.push(doc);
              for (const frame of doc.querySelectorAll('iframe,frame')) {
                try { collectDocuments(frame.contentDocument, depth + 1); } catch (_) {}
              }
            }
            function collectQueryRoots(root, depth) {
              if (!root || depth > 6 || seenRoots.has(root) || roots.length >= 96) return;
              seenRoots.add(root);
              roots.push(root);
              let nodes = [];
              try { nodes = root.querySelectorAll ? root.querySelectorAll('*') : []; } catch (_) {}
              for (const node of nodes) {
                try {
                  if (node.shadowRoot) collectQueryRoots(node.shadowRoot, depth + 1);
                } catch (_) {}
              }
            }
            collectDocuments(document, 0);
            docs.forEach(doc => collectQueryRoots(doc, 0));

            function clean(value) {
              return String(value || '').replace(/\\u00a0/g, ' ').replace(/[ \\t]+/g, ' ')
                .replace(/\\n[ \\t]+/g, '\\n').replace(/\\n{3,}/g, '\\n\\n').trim();
            }
            function nodeText(node) {
              if (node == null) return '';
              const get = typeof node.getAttribute === 'function'
                ? name => node.getAttribute(name) : () => '';
              return clean(node.innerText || node.textContent || get('title') ||
                get('aria-label') || (typeof node === 'string' ? node : ''));
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
            function attribute(node, names) {
              if (!node || typeof node.getAttribute !== 'function') return '';
              for (const name of names) {
                const value = node.getAttribute(name);
                if (value !== null && clean(value)) return clean(value);
              }
              return '';
            }
            function childText(node, selectors) {
              if (!node || typeof node.querySelector !== 'function') return '';
              for (const selector of selectors) {
                const child = node.querySelector(selector);
                const value = child ? nodeText(child) : '';
                if (value) return value;
              }
              return '';
            }
            function labeledText(text, labels) {
              for (const line of clean(text).split('\\n')) {
                for (const label of labels) {
                  const prefix = new RegExp('^\\\\s*' + label + '\\\\s*[:：]\\\\s*(.+)$', 'i');
                  const match = line.match(prefix);
                  if (match && clean(match[1])) return clean(match[1]);
                }
              }
              return '';
            }
            function compactEamsWeeks(value) {
              const match = clean(value).match(/[（(]\\s*((?:\\d{1,2}\\s*(?:[-~至—]\\s*\\d{1,2})?)(?:\\s*[,，、]\\s*(?:\\d{1,2}\\s*(?:[-~至—]\\s*\\d{1,2})?))*)\\s*([单双])?\\s*[,，]/);
              if (!match) return '';
              return clean(match[1]) + '周' + (match[2] ? '(' + match[2] + ')' : '');
            }
            function courseFields(node, text) {
              const lines = clean(text).split('\\n').map(clean).filter(Boolean);
              const weekLine = lines.findIndex(line =>
                /\\d{1,2}(?:\\s*[-~至—]\\s*\\d{1,2})?.*周/.test(line));
              let name = attribute(node, ['data-course-name', 'data-kcmc', 'data-name']) ||
                childText(node, ['.course-name', '.kcmc', '.title', '[class*="courseName"]',
                  '[class*="course-name"]', '[class*="kcmc"]']) ||
                labeledText(text, ['课程名称', '课程名', '科目']);
              let teacher = attribute(node, ['data-teacher', 'data-teacher-name', 'data-jsxm']) ||
                childText(node, ['[title="教师"] font', '[title="老师"]', '.teacher', '.jsxm',
                  '[class*="teacherName"]',
                  '[class*="teacher-name"]', '[class*="jsxm"]']) ||
                labeledText(text, ['任课教师', '授课教师', '主讲教师', '教师', '老师']);
              let location = attribute(node, ['data-location', 'data-room', 'data-classroom', 'data-cdmc']) ||
                childText(node, ['[title="上课地点"] font', '[title="教室"]', '.location',
                  '.room', '.classroom', '.cdmc',
                  '[class*="classroom"]', '[class*="location"]', '[class*="cdmc"]']) ||
                labeledText(text, ['上课地点', '地点', '教室', '校区']);
              let weeks = attribute(node, ['data-weeks', 'data-week', 'data-week-text', 'data-zcd']) ||
                childText(node, ['[title="节/周"] font', '[title="周次(节次)"]', '.weeks',
                  '.week', '.zcd', '[class*="weekText"]',
                '[class*="week-text"]', '[class*="zcd"]']) ||
                labeledText(text, ['上课周次', '周次', '周数']);
              if (!weeks) weeks = compactEamsWeeks(text);
              if (!name && lines.length && !/^(?:第?[一二三四五六七八九十\\d]+节|上午|下午|晚上|早晨)$/.test(lines[0])
                  && !/\\d{1,2}(?:\\s*[-~至—]\\s*\\d{1,2})?.*周/.test(lines[0])) {
                name = lines[0];
              }
              if (!weeks && weekLine >= 0) {
                const match = lines[weekLine].match(/(?:第\\s*)?\\d{1,2}(?:\\s*[-~至—]\\s*\\d{1,2})?(?:\\s*[,，、]\\s*\\d{1,2}(?:\\s*[-~至—]\\s*\\d{1,2})?)*\\s*周/);
                if (match) weeks = clean(match[0]);
              }
              if (!teacher && weekLine >= 0 && lines[weekLine + 1] &&
                  /^[\\u4e00-\\u9fa5A-Za-z·,，、\\s]{2,24}$/.test(lines[weekLine + 1]) &&
                  !/(楼|馆|教室|校区|中心|实验室|操场|场|室)/.test(lines[weekLine + 1])) {
                teacher = lines[weekLine + 1];
              }
              if (!location) {
                location = lines.find(line =>
                  /(楼|馆|教室|校区|中心|实验室|操场|球场|机房)\\s*[-A-Za-z]?\\d*$/i.test(line) ||
                  /^[A-Za-z]\\d{2,4}$/.test(line)) || '';
              }
              const weekMarker = weeks.search(/[（(]周[）)]/);
              if (weekMarker >= 0) weeks = clean(weeks.slice(0, weekMarker));
              const describedWeeks = weeks.match(/(?:第\\s*)?\\d{1,2}(?:\\s*[-~至—]\\s*\\d{1,2})?(?:\\s*[,，、]\\s*\\d{1,2}(?:\\s*[-~至—]\\s*\\d{1,2})?)*\\s*周(?:\\s*[（(]?[单双]周?[）)]?)?/);
              if (describedWeeks) weeks = clean(describedWeeks[0]);
              return {name, teacher, location, weeks};
            }
            function addText(day, section, endSection, text, title, fields, parity) {
              day = dayNumber(day); section = sectionNumber(section);
              endSection = sectionNumber(endSection || section) || section;
              text = clean(text);
              if (!(day >= 1 && day <= 7) || !(section >= 1 && section <= 30)
                  || !text || text.length < 2 || /^(无|暂无|--|-)$/.test(text)) return;
              const compact = text.replace(/\\s/g, '');
              if (/^\\d+(?:\\.\\d+)?$/.test(compact)) return;
              if (/^(操作|编辑|删除|查看|详情|添加|修改|保存|取消|确定|提交)$/.test(compact)) return;
              if (/^(实验辅导?老师|辅导教师|助教|监考老师)/.test(compact)) return;
              if (/^(代码|编号|序号|课程号|教学班号|上课时间|上课地点)$/.test(compact)) return;
              if (/^(授课小结|教学小结|课程小结|备注|说明|注释)/.test(compact)) return;
              if (/^\\d{4}\\s*[-—]\\s*\\d{4}学年.*学期.*(?:的)?课表$/.test(compact)) return;
              if (/^(时间|节次|星期|周次|上午|下午|晚上|早晨)$/.test(compact)) return;
              fields = fields || {};
              if (!clean(fields.name || '') && compact.length <= 4 && !/[\\d()（）]/.test(compact) &&
                  !/课|程|学|实验|设计|理论|基础|导论|概论|原理|技术|方法/.test(compact)) return;
              let weeks = clean(fields.weeks || '');
              if (!weeks) {
                const weekMatch = text.match(/(?:第\\s*)?\\d{1,2}(?:\\s*[-~至—]\\s*\\d{1,2})?(?:\\s*[,，、]\\s*\\d{1,2}(?:\\s*[-~至—]\\s*\\d{1,2})?)*\\s*周/);
                if (weekMatch) weeks = clean(weekMatch[0]);
              }
              if (!weeks) weeks = compactEamsWeeks(text);
              if (parity && !/[单双]/.test(weeks)) {
                if (weeks) weeks += '(' + parity + ')';
              }
              if (!weeks) { missingWeekCandidates++; return; }
              const key = day + '|' + section + '|' + endSection + '|' + text + '|' + weeks;
              if (seen.has(key)) return;
              seen.add(key);
              const item = {day, section, endSection: Math.max(section, endSection), text,
                title: clean(title || '')};
              if (clean(fields.name || '')) item.name = clean(fields.name);
              if (clean(fields.teacher || '')) item.teacher = clean(fields.teacher);
              if (clean(fields.location || '')) item.location = clean(fields.location);
              if (weeks) item.weeks = weeks;
              if (clean(fields.startTime || '')) item.startTime = clean(fields.startTime);
              if (clean(fields.endTime || '')) item.endTime = clean(fields.endTime);
              output.push(item);
            }
            function add(day, section, endSection, node, parity) {
              const text = nodeText(node);
              if (/\\{[^}]*第?\\d{1,2}(?:\\s*[-~至—]\\s*\\d{1,2})?周[^}]*\\}/.test(text)) {
                addCourseBlock(day, section, endSection, node, parity);
                return;
              }
              addText(day, section, endSection, text,
                node && typeof node.getAttribute === 'function' ? node.getAttribute('title') : '',
                courseFields(node, text), parity);
            }
            function addCourseBlock(day, section, endSection, node, parity) {
              const text = nodeText(node);
              const tokens = text.split(/\\s+/).filter(Boolean);
              const timeIndexes = [];
              tokens.forEach((token, index) => {
                if (/\\{[^}]*第?\\d{1,2}(?:\\s*[-~至—]\\s*\\d{1,2})?周[^}]*\\}/.test(token)) {
                  timeIndexes.push(index);
                }
              });
              if (timeIndexes.length) {
                const property = /^(?:必修课?|选修课?|必修|选修|专基|专选|公必|公选|通识必修|通识选修|公共基础|专业基础课|专业必修|专业选修|方向)$/;
                for (let item = 0; item < timeIndexes.length; item++) {
                  const index = timeIndexes[item];
                  const limit = item + 1 < timeIndexes.length ? timeIndexes[item + 1] : tokens.length;
                  let nameIndex = index - 1;
                  if (nameIndex >= 0 && property.test(tokens[nameIndex])) nameIndex--;
                  const name = nameIndex >= 0 ? tokens[nameIndex] : '';
                  const teacher = index + 1 < limit ? tokens[index + 1] : '';
                  const location = index + 2 < limit ? tokens[index + 2] : '';
                  const weeks = (tokens[index].match(/\\d{1,2}(?:\\s*[-~至—]\\s*\\d{1,2})?(?:\\s*[,，、]\\s*\\d{1,2}(?:\\s*[-~至—]\\s*\\d{1,2})?)*\\s*周(?:\\s*[（(]?[单双]周?[）)]?)?/) || [])[0] || '';
                  const itemText = [name, teacher, location, weeks, tokens[index]]
                    .filter(Boolean).join('\\n');
                  addText(day, section, endSection, itemText, '', {
                    name, teacher, location, weeks
                  }, parity);
                }
                return;
              }
              const html = String(node && node.innerHTML || '');
              const parts = html.split(/-{5,}/).filter(part =>
                clean(String(part == null ? '' : part).replace(/<[^>]+>/g, ' ')));
              if (parts.length <= 1) {
                addText(day, section, endSection, text,
                  node && typeof node.getAttribute === 'function' ? node.getAttribute('title') : '',
                  courseFields(node, text), parity);
                return;
              }
              for (const part of parts) {
                const holder = (node.ownerDocument || document).createElement('div');
                holder.innerHTML = String(part == null ? '' : part);
                try { add(day, section, endSection, holder, parity); } catch (_) {}
              }
            }
            function addCell(day, section, cell, parity, explicitEndSection) {
              const endSection = Number(explicitEndSection) || Number(section) +
                Math.max(1, Number(cell.rowSpan || 1)) - 1;
              let blocks = Array.from(cell.querySelectorAll('.kbcontent'))
                .filter(node => nodeText(node).length > 1);
              if (!blocks.length) {
                blocks = Array.from(cell.querySelectorAll(
                  '.timetable_con, .course, [class*="course-item"], [class*="CourseItemInfo"]'))
                  .filter(node => nodeText(node).length > 1);
              }
              if (!blocks.length) {
                blocks = Array.from(cell.querySelectorAll('div')).filter(node =>
                  Array.from(node.children || []).some(child =>
                    child.classList && child.classList.contains('title')));
              }
              if (blocks.length) {
                blocks.forEach(node => addCourseBlock(day, section, endSection, node, parity));
              }
              else add(day, section, endSection, cell, parity);
            }
            function chineseNumber(value) {
              const digits = {'一': 1, '二': 2, '三': 3, '四': 4, '五': 5,
                '六': 6, '七': 7, '八': 8, '九': 9};
              value = clean(value);
              if (value === '十') return 10;
              if (value.indexOf('十') >= 0) {
                const parts = value.split('十');
                return (parts[0] ? digits[parts[0]] : 1) * 10 + (parts[1] ? digits[parts[1]] : 0);
              }
              return digits[value] || 0;
            }
            function sectionFrom(value, fallback) {
              const text = clean(value);
              let match = text.match(/(?:第)?(\\d{1,2})(?:\\s*[-~至—]\\s*\\d{1,2})?\\s*节(?!\\s*\\/\\s*周)/);
              if (match) return Number(match[1]);
              match = text.match(/^\\s*(\\d{1,2})\\s*[-~至—、,，]\\s*(\\d{1,2})\\s*$/);
              if (match) return Number(match[1]);
              match = text.match(/(?:第)?\\s*([一二三四五六七八九十]+)\\s*节/);
              if (match) return chineseNumber(match[1]) || fallback;
              match = text.match(/^\\s*(\\d{1,2})\\s*$/);
              return match ? Number(match[1]) : fallback;
            }
            function endSectionFrom(value, fallback) {
              const text = clean(value);
              let match = text.match(/(?:第)?\\s*\\d{1,2}\\s*[-~至—、,，]\\s*(?:第)?\\s*(\\d{1,2})\\s*节?/);
              if (!match) match = text.match(/^\\s*\\d{1,2}\\s*[-~至—、,，]\\s*(\\d{1,2})\\s*$/);
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
              const day = field(value, ['dayOfWeek', 'weekday', 'weekDay', 'day', 'xq', 'week_day',
                'xqj', 'XQJ', 'xqmc', 'XQMC', 'skxq', 'SKXQ', 'XQ', 'weekDayCode']);
              const section = field(value, ['beginSection', 'startSection', 'section', 'period', 'jc',
                'startJc', 'ksjc', 'KSJC', 'djj', 'DJJ', 'jcs', 'JCS', 'startUnit', 'startTimeUnit']);
              if (!day || !section) return;
              const name = field(value, ['courseName', 'course_name', 'name', 'courseTitle', 'title',
                'kcmc', 'KCMC', 'kcm', 'KCM', 'kcmcDisplay', 'KCMC_DISPLAY']);
              let teacher = field(value, ['teacherName', 'teacher', 'teachers', 'instructor',
                'jsmc', 'JSMC', 'jsxm', 'JSXM', 'skjs', 'SKJS', 'rkjs', 'RKJS', 'xm']);
              const teacherSummary = field(value, ['weeksAndTeachers', 'teacherWeekText',
                'SKJSZC', 'skjszc']);
              if (!teacher && teacherSummary) {
                const teacherParts = clean(teacherSummary).split('/');
                teacher = clean(teacherParts[teacherParts.length - 1]).replace(/\\[主讲\\]/g, '');
              }
              let location = field(value, ['roomName', 'location', 'classroom', 'room',
                'jasmc', 'JASMC', 'cdmc', 'CDMC', 'jxcdmc', 'JXCDMC', 'skdd', 'SKDD']);
              let weeks = field(value, ['weekDescription', 'weeks', 'weekText', 'week',
                'zcmc', 'ZCMC', 'skzc', 'SKZC', 'zcd', 'ZCD']);
              const firstWeek = Number(field(value,
                ['startWeek', 'beginWeek', 'qszc', 'QSZC']));
              const lastWeek = Number(field(value,
                ['endWeek', 'finishWeek', 'jszc', 'JSZC']));
              if (!weeks && firstWeek > 0) {
                weeks = firstWeek + (lastWeek >= firstWeek ? '-' + lastWeek : '') + '周';
              }
              const startTime = field(value, ['beginTime', 'startTime', 'kssj', 'KSSJ']);
              const endTime = field(value, ['endTime', 'finishTime', 'jssj', 'JSSJ']);
              let endSection = field(value, ['endSection', 'finishSection', 'endJc', 'endPeriod',
                'jsjc', 'JSJC', 'mjj', 'MJJ', 'endUnit', 'endTimeUnit']);
              if (!endSection) {
                const sectionRange = clean(section).match(/\\d{1,2}\\s*[-~至—]\\s*(\\d{1,2})/);
                if (sectionRange) endSection = sectionRange[1];
              }
              const titleDetails = field(value, ['titleDetail', 'scheduleDetails',
                'courseDetails', 'SKXQXQ']);
              let detailed = false;
              if (Array.isArray(titleDetails)) {
                const candidates = titleDetails.length > 1 ? titleDetails.slice(1) : titleDetails;
                for (const rawDetail of candidates) {
                  const detail = clean(rawDetail);
                  const match = detail.match(
                    /^([0-9\\s,，、\\-~至—()（）单双周]+)\\s*(.*)$/);
                  if (!match) continue;
                  const detailWeeks = clean(match[1]) || clean(weeks);
                  const detailLocation = clean(match[2]) || clean(location);
                  const detailText = [name, teacher, detailLocation, detailWeeks]
                    .filter(Boolean).join('\\n');
                  addText(day, section, endSection || section, detailText, detail, {
                    name, teacher, location: detailLocation, weeks: detailWeeks,
                    startTime, endTime
                  }, '');
                  detailed = true;
                }
              }
              if (detailed) return;
              const text = [name, teacher, location, weeks].filter(Boolean).join('\\n');
              addText(day, section, endSection || section,
                text || value.text || value.content || '', field(value, ['title', 'courseName']),
                {name, teacher, location, weeks, startTime, endTime}, '');
            }
            function scanStructured(value, depth) {
              if (!value || depth > 6) return;
              if (typeof value === 'object') {
                if (seenStructured.has(value)) return;
                seenStructured.add(value);
              }
              if (++structuredNodes > 5000) return;
              if (Array.isArray(value)) {
                for (const item of value) scanStructured(item, depth + 1);
                return;
              }
              if (typeof value !== 'object') return;
              addStructured(value);
              for (const key of Object.keys(value)) {
                if (key !== 'html' && key !== 'style') {
                  scanStructured(value[key], depth + 1);
                }
              }
            }
            function scanVueRoot(root) {
              if (!root || typeof root.querySelectorAll !== 'function') return;
              let inspected = 0;
              for (const node of root.querySelectorAll('*')) {
                if (++inspected > 4000) break;
                const instances = [];
                try { if (node.__vue__) instances.push(node.__vue__); } catch (_) {}
                try { if (node.__vueParentComponent) instances.push(node.__vueParentComponent); } catch (_) {}
                try { if (node.__vue_app__) instances.push(node.__vue_app__); } catch (_) {}
                for (const instance of instances) {
                  vueRoots++;
                  for (const value of [instance.$data, instance._data, instance.$props,
                      instance.props, instance.data, instance.setupState,
                      instance.ctx, instance.proxy && instance.proxy.$data]) {
                    try { scanStructured(value, 0); } catch (_) {}
                  }
                }
              }
            }
            function scanStructuredSources(root) {
              for (const script of root.querySelectorAll(
                  'script[type="application/json"],script[data-course-data]')) {
                try { scanStructured(JSON.parse(script.textContent || ''), 0); } catch (_) {}
              }
              if (root.nodeType === 9) {
                for (const name of ['__INITIAL_STATE__', '__INITIAL_DATA__', '__NEXT_DATA__',
                    '__NUXT__', '__PAGE_DATA__', 'pageData', 'scheduleData', 'scheduleDatas',
                    'currentSchedule', 'arrangedList', 'courseData', 'kbData', 'tableData',
                    'viewModel']) {
                  try { scanStructured(root.defaultView && root.defaultView[name], 0); } catch (_) {}
                }
              }
              scanVueRoot(root);
            }
            function activityValue(value) {
              if (value == null) return '';
              if (Array.isArray(value)) {
                return clean(value.map(item => {
                  if (item && typeof item === 'object') {
                    return item.name || item.teacherName || item.fullName || item.label || '';
                  }
                  return item;
                }).filter(Boolean).join(','));
              }
              if (typeof value === 'object') {
                return clean(value.name || value.teacherName || value.fullName ||
                  value.label || value.text || '');
              }
              return clean(String(value));
            }
            function binaryWeeks(value) {
              const bits = String(value == null ? '' : value).trim();
              if (!/^[01]+$/.test(bits)) return '';
              const weeks = [];
              // Beangle's vaildWeeks is 1-indexed: bit 0 is a placeholder, bit n is week n.
              for (let index = 1; index < bits.length; index++) {
                if (bits[index] === '1') weeks.push(index);
              }
              if (!weeks.length) return '';
              const ranges = [];
              let start = weeks[0];
              let end = start;
              for (let index = 1; index < weeks.length; index++) {
                if (weeks[index] === end + 1) {
                  end = weeks[index];
                } else {
                  ranges.push(start === end ? String(start) : start + '-' + end);
                  start = end = weeks[index];
                }
              }
              ranges.push(start === end ? String(start) : start + '-' + end);
              return ranges.join(',') + '周';
            }
            function activityEntries(slot) {
              if (!slot) return [];
              const entries = [];
              const count = Math.max(0, Number(slot.count || 0));
              for (let index = 0; index < count; index++) {
                try { if (slot[index]) entries.push(slot[index]); } catch (_) {}
              }
              if (!entries.length && Array.isArray(slot)) {
                for (const item of slot) if (item) entries.push(item);
              }
              if (!entries.length && typeof slot === 'object' &&
                  (slot.courseName || slot.course || slot.__qx)) {
                entries.push(slot);
              }
              return entries;
            }
            function scanBeangleRuntime(win) {
              if (!win) return;
              const tables = [];
              const tableSet = new Set();
              function keepTable(table) {
                if (!table || !table.activities || tableSet.has(table)) return;
                tableSet.add(table);
                tables.push(table);
              }
              try { keepTable(win.table0); } catch (_) {}
              try {
                let checked = 0;
                for (const name in win) {
                  if (++checked > 5000) break;
                  if (/^table\\d+$/.test(name)) {
                    try { keepTable(win[name]); } catch (_) {}
                  }
                }
              } catch (_) {}
              for (const table of tables) {
                const activities = table.activities;
                let unitCount = 0;
                try { unitCount = Number(win.unitCount || table.unitCount || 0); } catch (_) {}
                let length = Number(activities && activities.length || 0);
                if (!length && activities && typeof activities === 'object') {
                  try {
                    const numericKeys = Object.keys(activities)
                      .filter(key => /^\\d+$/.test(key)).map(Number);
                    if (numericKeys.length) length = Math.max(...numericKeys) + 1;
                  } catch (_) {}
                }
                if (!unitCount && length >= 7 && length % 7 === 0) unitCount = length / 7;
                if (!(unitCount >= 1 && unitCount <= 30) || !length) continue;
                const found = [];
                for (let slotIndex = 0; slotIndex < length; slotIndex++) {
                  let slot = null;
                  try { slot = activities[slotIndex]; } catch (_) {}
                  for (const activity of activityEntries(slot)) {
                    let name = activityValue(activity.courseName || activity.course);
                    let teacher = activityValue(activity.teachers || activity.teacherName ||
                      activity.teacher);
                    let location = activityValue(activity.room || activity.roomName);
                    let weekBits = String(activity.vaildWeeks || activity.validWeeks ||
                      activity.weeks || '');
                    if ((!name || !weekBits) && activity.__qx) {
                      const args = activity.__qx;
                      if (!name) name = activityValue(args[3] != null ? args[3] : args[2]);
                      if (!teacher) teacher = activityValue(args[1]);
                      if (!location) location = activityValue(args[5]);
                      if (!weekBits) weekBits = String(args[6] == null ? '' : args[6]);
                    }
                    const weeks = binaryWeeks(weekBits);
                    const day = Math.floor(slotIndex / unitCount) + 1;
                    const section = slotIndex % unitCount + 1;
                    if (!name || !weeks || day < 1 || day > 7) continue;
                    found.push({name, teacher, location, weeks, day, section});
                  }
                }
                const grouped = new Map();
                for (const item of found) {
                  const key = [item.name, item.teacher, item.location, item.weeks, item.day]
                    .join('\\u0000');
                  if (!grouped.has(key)) grouped.set(key, []);
                  grouped.get(key).push(item);
                }
                for (const group of grouped.values()) {
                  group.sort((left, right) => left.section - right.section);
                  const unique = group.filter((item, index) =>
                    index === 0 || item.section !== group[index - 1].section);
                  let run = [unique[0]];
                  function emitRun() {
                    if (!run[0]) return;
                    const first = run[0];
                    const last = run[run.length - 1];
                    const text = [first.name, first.teacher, first.location, first.weeks]
                      .filter(Boolean).join('\\n');
                    const before = output.length;
                    addText(first.day, first.section, last.section, text, first.name, {
                      name: first.name, teacher: first.teacher,
                      location: first.location, weeks: first.weeks
                    }, '');
                    if (output.length > before) runtimeActivities++;
                  }
                  for (let index = 1; index < unique.length; index++) {
                    if (unique[index].section === run[run.length - 1].section + 1) {
                      run.push(unique[index]);
                    } else {
                      emitRun();
                      run = [unique[index]];
                    }
                  }
                  emitRun();
                }
              }
            }
            function headerDay(value) {
              const compact = clean(value).replace(/\\s+/g, '');
              let match = compact.match(/^(?:星期|周)([一二三四五六日天])/);
              if (!match) match = compact.match(/^([一二三四五六日天])$/);
              return match ? dayNumber(match[1]) : 0;
            }
            function scanTable(table, mode) {
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
                      grid[targetRow][column + colOffset] = {
                        cell, originRow: rowIndex, originColumn: column
                      };
                    }
                  }
                  column += colSpan;
                }
              });
              const maxColumns = grid.reduce((max, row) => Math.max(max, row.length), 0);
              if (maxColumns < 7) return;
              const columnDays = {};
              const columnParity = {};
              for (let row = 0; row < Math.min(6, grid.length); row++) {
                for (let column = 0; column < grid[row].length; column++) {
                  const entry = grid[row][column];
                  const day = entry ? headerDay(nodeText(entry.cell)) : 0;
                  if (day) columnDays[column] = day;
                  const header = entry ? nodeText(entry.cell).replace(/\\s+/g, '') : '';
                  if (/^(单|双)(周)?$/.test(header)) columnParity[column] = header.charAt(0);
                }
              }
              if (new Set(Object.values(columnDays)).size < 5) {
                const startColumn = Math.max(0, maxColumns - 7);
                for (let day = 1; day <= 7; day++) columnDays[startColumn + day - 1] = day;
              }
              const mappedColumns = Object.keys(columnDays).map(Number).sort((a, b) => a - b);
              if (!mappedColumns.length) return;
              const firstDayColumn = mappedColumns[0];
              let nextSection = 1;
              let groupedRow = 0;
              grid.forEach((logicalRow, rowIndex) => {
                const originalEntries = logicalRow.filter((entry, column) => entry &&
                  entry.originRow === rowIndex && entry.originColumn === column);
                const isHeaderRow = originalEntries.some(entry => {
                  const value = nodeText(entry.cell).replace(/\\s+/g, '');
                  return headerDay(value) || /^(单|双)(周)?$/.test(value);
                }) || (originalEntries.length === 1 &&
                  Number(originalEntries[0].cell.colSpan || 1) >= 5 &&
                  /(?:学年|学期|课表)/.test(nodeText(originalEntries[0].cell)));
                if (isHeaderRow) return;
                groupedRow++;
                const labelEntries = logicalRow.slice(0, firstDayColumn)
                  .filter((entry, column) => entry && entry.originColumn === column);
                let section = 0;
                let labeledEndSection = 0;
                for (const entry of labelEntries) {
                  const label = nodeText(entry.cell);
                  section = sectionFrom(label, section);
                  labeledEndSection = endSectionFrom(label, labeledEndSection);
                  if (section) break;
                }
                let explicitEndSection = labeledEndSection;
                if (!section && mode === 'qiangzhi') {
                  section = groupedRow <= 3 ? groupedRow * 2 - 1 : groupedRow * 2 - 2;
                  explicitEndSection = section + ((section === 5 || section === 12) ? 0 : 1);
                }
                if (!section) section = nextSection;
                const before = output.length;
                for (const column of mappedColumns) {
                  const entry = logicalRow[column];
                  if (!entry || entry.originRow !== rowIndex || entry.originColumn !== column) continue;
                  const parity = Number(entry.cell.colSpan || 1) === 1
                    ? columnParity[column] || '' : '';
                  addCell(columnDays[column], section, entry.cell, parity, explicitEndSection);
                }
                if (output.length > before) nextSection = Math.max(nextSection, section + 1);
              });
            }
            // WakeUp's strong智 parser walks the vendor's actual #kbtable instead of relying on
            // generic header detection. Keep this as a fallback for deployments whose table has
            // no recognizable day headers but still exposes the standard kbcontent metadata.
            function scanQiangzhiWakeup(table) {
              if (!table) return;
              const rows = Array.from(table.querySelectorAll('tr'));
              let nodeCount = 0;
              for (const row of rows) {
                const cells = Array.from(row.cells || []);
                if (!cells.length) continue;
                nodeCount++;
                const firstLooksLikeLabel = cells.length >= 8;
                const first = firstLooksLikeLabel ? 1 : 0;
                for (let index = first; index < cells.length && index < first + 7; index++) {
                  const day = index - first + 1;
                  const cell = cells[index];
                  let blocks = Array.from(cell.querySelectorAll('.kbcontent'))
                    .filter(node => nodeText(node).length > 1);
                  if (!blocks.length) blocks = [cell];
                  for (const block of blocks) {
                    const html = String(block.innerHTML || '');
                    const parts = html.split(/-{5,}/).filter(part =>
                      clean(String(part).replace(/<[^>]+>/g, ' ')));
                    const pieces = parts.length ? parts : [html || nodeText(block)];
                    for (const part of pieces) {
                      const holder = (block.ownerDocument || document).createElement('div');
                      holder.innerHTML = String(part || '');
                      const text = nodeText(holder);
                      if (!text) continue;
                      const fields = courseFields(holder, text);
                      addText(day, nodeCount <= 3 ? nodeCount * 2 - 1 : nodeCount * 2 - 2,
                        nodeCount <= 3 ? nodeCount * 2 - 2 : nodeCount * 2 - 1,
                        text, '', fields, '');
                    }
                  }
                }
              }
            }
            function recordRole(value) {
              const text = clean(value).replace(/[：:]/g, '').replace(/\\s+/g, '');
              if (/^(?:课程名称|课程名|科目名称|科目|课程)$/.test(text)) return 'name';
              if (/^(?:星期|上课星期|周几|星期几)$/.test(text)) return 'day';
              if (/^(?:节次|上课节次|课节|上课节数)$/.test(text)) return 'section';
              if (/^(?:上课时间|授课时间|时间)$/.test(text)) return 'time';
              if (/^(?:周次|上课周次|授课周次|周数)$/.test(text)) return 'weeks';
              if (/^(?:教师|任课教师|授课教师|主讲教师|老师)$/.test(text)) return 'teacher';
              if (/^(?:上课地点|授课地点|教室|场地|地点)$/.test(text)) return 'location';
              return '';
            }
            function scanRecordTable(table) {
              const rows = Array.from(table.rows || []);
              let headerIndex = -1;
              let roles = {};
              for (let rowIndex = 0; rowIndex < Math.min(rows.length, 8); rowIndex++) {
                const candidate = {};
                Array.from(rows[rowIndex].cells || []).forEach((cell, column) => {
                  const role = recordRole(nodeText(cell));
                  if (role && candidate[role] === undefined) candidate[role] = column;
                });
                const hasPosition = candidate.time !== undefined ||
                  (candidate.day !== undefined && candidate.section !== undefined);
                if (candidate.name !== undefined && hasPosition && Object.keys(candidate).length >= 3) {
                  headerIndex = rowIndex;
                  roles = candidate;
                  break;
                }
              }
              if (headerIndex < 0) return;
              for (let rowIndex = headerIndex + 1; rowIndex < rows.length; rowIndex++) {
                const cells = Array.from(rows[rowIndex].cells || []);
                const value = role => roles[role] === undefined || !cells[roles[role]]
                  ? '' : nodeText(cells[roles[role]]);
                const name = value('name');
                const time = value('time');
                const dayText = value('day') || time;
                const sectionText = value('section') || time;
                const day = dayNumber(dayText);
                const section = sectionFrom(sectionText, 0);
                const endSection = endSectionFrom(sectionText, section);
                if (!name || !day || !section) continue;
                const teacher = value('teacher');
                const location = value('location');
                const weeks = value('weeks');
                const before = output.length;
                addText(day, section, endSection, [name, teacher, location, weeks]
                  .filter(Boolean).join('\\n'), '', {name, teacher, location, weeks}, '');
                if (output.length > before) recordRows++;
              }
            }
            function visibleRect(node) {
              try {
                const rect = node.getBoundingClientRect();
                const view = node.ownerDocument && node.ownerDocument.defaultView;
                const style = view && view.getComputedStyle ? view.getComputedStyle(node) : null;
                if (!rect || rect.width < 8 || rect.height < 8 || !style ||
                    style.display === 'none' || style.visibility === 'hidden' ||
                    Number(style.opacity || 1) === 0) return null;
                return rect;
              } catch (_) {
                return null;
              }
            }
            function scanVisualTimetable(doc) {
              // Layout capability is the gate: school domains only select known system/time
              // profiles and never decide whether a timetable can be parsed.
              const elements = Array.from(doc.querySelectorAll('th,td,div,span,li'));
              const dayAnchors = {};
              for (const node of elements) {
                const value = nodeText(node).replace(/\\s+/g, '');
                if (!/^(?:星期|周)[一二三四五六日天]$/.test(value)) continue;
                const day = headerDay(value);
                const rect = visibleRect(node);
                if (!day || !rect) continue;
                const area = rect.width * rect.height;
                if (!dayAnchors[day] || area < dayAnchors[day].area) {
                  dayAnchors[day] = {day, rect, area};
                }
              }
              const days = Object.values(dayAnchors).sort((a, b) =>
                (a.rect.left + a.rect.width / 2) - (b.rect.left + b.rect.width / 2));
              visualDays = Math.max(visualDays, days.length);
              if (days.length < 5) return;
              const firstDayCenter = days[0].rect.left + days[0].rect.width / 2;
              const sectionAnchors = {};
              function keepSection(node, explicit) {
                const value = nodeText(node).replace(/\\s+/g, '');
                if (explicit && !/^(?:第)?(?:\\d{1,2}|[一二三四五六七八九十]+)节$/.test(value)) return;
                if (!explicit && !/^\\d{1,2}$/.test(value)) return;
                const section = sectionFrom(value, 0);
                const rect = visibleRect(node);
                if (!section || section > 30 || !rect) return;
                if (!explicit && rect.right >= firstDayCenter) return;
                const area = rect.width * rect.height;
                if (!sectionAnchors[section] || area < sectionAnchors[section].area) {
                  sectionAnchors[section] = {section, rect, area};
                }
              }
              elements.forEach(node => keepSection(node, true));
              if (Object.keys(sectionAnchors).length < 2) {
                elements.forEach(node => keepSection(node, false));
              }
              const sections = Object.values(sectionAnchors).sort((a, b) =>
                (a.rect.top + a.rect.height / 2) - (b.rect.top + b.rect.height / 2));
              visualSections = Math.max(visualSections, sections.length);
              if (sections.length < 2) return;

              const dayCenters = days.map(anchor =>
                anchor.rect.left + anchor.rect.width / 2);
              const dayGaps = dayCenters.slice(1).map((value, index) => value - dayCenters[index])
                .filter(value => value > 4);
              const columnWidth = dayGaps.length
                ? dayGaps.sort((a, b) => a - b)[Math.floor(dayGaps.length / 2)]
                : days[0].rect.width;
              const sectionTops = sections.map(anchor => anchor.rect.top);
              const sectionGaps = sectionTops.slice(1)
                .map((value, index) => value - sectionTops[index]).filter(value => value > 4);
              const rowHeight = sectionGaps.length
                ? sectionGaps.sort((a, b) => a - b)[Math.floor(sectionGaps.length / 2)]
                : sections[0].rect.height;
              const left = dayCenters[0] - columnWidth * 0.6;
              const right = dayCenters[dayCenters.length - 1] + columnWidth * 0.6;
              const top = sectionTops[0] - rowHeight * 0.25;
              const bottom = sectionTops[sectionTops.length - 1] + rowHeight * 1.25;
              const candidates = [];
              for (const node of doc.querySelectorAll('td,div,li')) {
                const text = nodeText(node);
                const compact = text.replace(/\\s/g, '');
                if (compact.length < 5 || compact.length > 600 ||
                    !/[\\u4e00-\\u9fa5A-Za-z]/.test(compact)) continue;
                if (/^\\d{4}[-—]\\d{4}学年.*课表$/.test(compact) ||
                    /(?:版权所有|Copyright|本科教务管理系统|我的课表)$/.test(compact)) continue;
                const rect = visibleRect(node);
                if (!rect || rect.width > columnWidth * 1.8 || rect.height > rowHeight * 8) continue;
                const centerX = rect.left + rect.width / 2;
                const centerY = rect.top + rect.height / 2;
                if (centerX < left || centerX > right || centerY < top || centerY > bottom) continue;
                const className = String(node.className || '');
                const hasWeek = /\\d{1,2}(?:\\s*[-~至—]\\s*\\d{1,2})?.*周/.test(text);
                const multiLine = text.split('\\n').filter(line => clean(line)).length >= 2;
                const courseClass = /course|lesson|schedule|timetable|kb|activity|task|card/i
                  .test(className);
                if (!hasWeek && !multiLine && !courseClass) continue;
                const score = (hasWeek ? 8 : 0) + (multiLine ? 4 : 0) +
                  (courseClass ? 3 : 0);
                candidates.push({node, text, rect, score,
                  area: rect.width * rect.height});
              }
              visualNodes = Math.max(visualNodes, candidates.length);
              candidates.sort((a, b) => b.score - a.score || a.area - b.area);
              const accepted = [];
              for (const candidate of candidates) {
                const centerX = candidate.rect.left + candidate.rect.width / 2;
                let dayAnchor = days[0];
                for (const anchor of days) {
                  const anchorX = anchor.rect.left + anchor.rect.width / 2;
                  const bestX = dayAnchor.rect.left + dayAnchor.rect.width / 2;
                  if (Math.abs(anchorX - centerX) < Math.abs(bestX - centerX)) dayAnchor = anchor;
                }
                let startAnchor = sections[0];
                for (const anchor of sections) {
                  if (Math.abs(anchor.rect.top - candidate.rect.top) <
                      Math.abs(startAnchor.rect.top - candidate.rect.top)) startAnchor = anchor;
                }
                let endSection = startAnchor.section;
                for (const anchor of sections) {
                  if (anchor.rect.top >= startAnchor.rect.top - 2 &&
                      anchor.rect.top < candidate.rect.bottom - rowHeight * 0.2) {
                    endSection = Math.max(endSection, anchor.section);
                  }
                }
                const duplicate = accepted.some(item =>
                  item.day === dayAnchor.day && item.section === startAnchor.section &&
                  (item.text === candidate.text || item.text.includes(candidate.text) ||
                    candidate.text.includes(item.text)));
                if (duplicate) continue;
                const before = output.length;
                add(dayAnchor.day, startAnchor.section, endSection, candidate.node, '');
                if (output.length > before) {
                  accepted.push({day: dayAnchor.day, section: startAnchor.section,
                    text: candidate.text});
                  visualCandidates++;
                }
              }
            }
            function scan(root) {
              for (const cell of root.querySelectorAll('td[id],div[id]')) {
                const id = cell.id || '';
                let match = id.match(/^(\\d)[-_](\\d{1,2})$/);
                if (match) addCell(Number(match[1]), Number(match[2]), cell);
                match = id.match(/^(\\d{1,2})[-_](\\d)$/);
                if (match && Number(match[1]) > 7) addCell(Number(match[2]), Number(match[1]), cell);
                match = id.match(/(?:day|d|xq)[-_]?([1-7])[^0-9]+(?:section|s|jc)?[_-]?(\\d{1,2})/i);
                if (match) addCell(Number(match[1]), Number(match[2]), cell);
              }
              for (const cell of root.querySelectorAll(
                  '[data-day],[data-weekday],[data-xq],[data-day-of-week],[data-course-day]')) {
                const day = cell.dataset.day || cell.dataset.weekday || cell.dataset.xq ||
                  cell.dataset.dayOfWeek || cell.dataset.courseDay;
                const section = cell.dataset.section || cell.dataset.period || cell.dataset.jc ||
                  cell.dataset.startSection || cell.dataset.periodIndex ||
                  sectionFrom(nodeText(cell), 1);
                addCell(day, section, cell);
              }
              const zhengfang = root.querySelector('#kbgrid_table_0,#table1,#Table1');
              const qiangzhi = root.querySelector('#kbtable');
              for (const table of root.querySelectorAll('table')) scanRecordTable(table);
              if (zhengfang) scanTable(zhengfang, 'zhengfang');
              if (qiangzhi && qiangzhi !== zhengfang) scanTable(qiangzhi, 'qiangzhi');
              if (qiangzhi && !output.length) {
                const before = output.length;
                try { scanQiangzhiWakeup(qiangzhi); } catch (_) {}
                if (output.length > before) {
                  qiangzhiFallback++;
                  selectedSource = 'qiangzhi-wakeup';
                }
              }
              for (const table of root.querySelectorAll('table')) {
                if (table !== zhengfang && table !== qiangzhi) scanTable(table, 'generic');
              }
            }
            docs.forEach(doc => {
              try { scanBeangleRuntime(doc.defaultView); } catch (_) {}
            });
            // WakeUp routes one page through one vendor parser. Beangle's evaluated activity
            // table is its authoritative model; scanning the rendered cells afterwards reads the
            // same schedule a second time with slightly different labels and clock metadata.
            if (runtimeActivities > 0) {
              selectedSource = 'eams-runtime';
              domPassSkipped = 1;
            } else {
              const beforeStructured = output.length;
              roots.forEach(root => {
                try { scanStructuredSources(root); } catch (_) {}
              });
              structuredCandidates = output.length - beforeStructured;
              if (structuredCandidates > 0) {
                selectedSource = vueRoots > 0 ? 'vue-runtime' : 'structured-data';
                domPassSkipped = 1;
              } else {
                roots.forEach(scan);
              }
            }
            if (!output.length) {
              roots.forEach(scanVisualTimetable);
              if (output.length) selectedSource = 'visual-grid';
            }
            const signature = docs.map(doc => {
              let href = '';
              try { href = doc.location && doc.location.href || ''; } catch (_) {}
              return href + ' ' + (doc.title || '') + ' ' +
                String(doc.documentElement && doc.documentElement.innerHTML || '').slice(0, 120000);
            }).join('\\n');
            const fingerprintMap = new Map();
            function fingerprint(id, score, evidence) {
              let item = fingerprintMap.get(id);
              if (!item) item = {id, score: 0, evidence: []};
              item.score += score;
              if (evidence && item.evidence.indexOf(evidence) < 0) item.evidence.push(evidence);
              fingerprintMap.set(id, item);
            }
            if (/zfsoft|正方软件|\\/jwglxt\\//i.test(signature)) {
              fingerprint('zhengfang', 6, '厂商脚本或路径');
            }
            if (/强智|qiangzhi|jw\\.glite|\\/jsxsd\\//i.test(signature)) {
              fingerprint('qiangzhi', 6, '厂商脚本或路径');
            }
            if (/青果|kingosoft|URP综合教务|\\/urp\\//i.test(signature)) {
              fingerprint('kingosoft', 6, '厂商脚本或路径');
            }
            if (/\\/jwapp\\/sys\\/|publicfrreport|emap/i.test(signature)) {
              fingerprint('emap', 6, 'EMAP 页面结构');
            }
            if (/\\/eams\\/|TaskActivity|beangle/i.test(signature)) {
              fingerprint('eams', 6, 'Beangle 页面结构');
            }
            if (runtimeActivities > 0) {
              fingerprint('eams', 12, '运行时活动对象');
            }
            for (const root of roots) {
              if (root.querySelector('#kbgrid_table_0,#table1,#Table1')) {
                fingerprint('zhengfang', 8, '课表表格');
              }
              if (root.querySelector('#kbtable .kbcontent,#kbtable')) {
                fingerprint('qiangzhi', 8, 'kbtable 课表');
              }
              if (root.querySelector('[data-kcmc],[data-jsxm],[data-zcd]')) {
                fingerprint('emap', 4, '课程字段');
              }
              if (root.querySelector('.Nsb_table,.Nsb_r_list,[class*="kingosoft"],table[id*="kb"]')) {
                fingerprint('kingosoft', 5, '课表节点');
              }
              if (root.querySelector('[id*="courseTableForStd"],[class*="TaskActivity"]')) {
                fingerprint('eams', 5, '活动课表节点');
              }
            }
            const fingerprints = Array.from(fingerprintMap.values())
              .sort((a, b) => b.score - a.score || a.id.localeCompare(b.id));
            const strongest = fingerprints.length && fingerprints[0].score >= 4
              ? fingerprints[0] : null;
            let detected = 'generic';
            let layout = 'generic';
            if (strongest) {
              layout = strongest.id;
              if (['zhengfang', 'qiangzhi', 'kingosoft', 'eams', 'emap'].includes(strongest.id)) {
                detected = strongest.id;
              }
            } else if (visualCandidates > 0) {
              layout = 'visual-grid';
            } else if (output.length > 0) {
              layout = 'table';
            }
            window.__qianxiImportPayload = {
              adapter: detected,
              layout,
              source: selectedSource,
              sourceUrl: location.href,
              pageTitle: document.title || '',
              term: '',
              diagnostics: {
                frames: docs.length - 1,
                shadowRoots: Math.max(0, roots.length - docs.length),
                tables: roots.reduce((count, root) => count + root.querySelectorAll('table').length, 0),
                candidates: output.length,
                missingWeekCandidates,
                qiangzhiFallback,
                structuredCandidates,
                vueRoots,
                domPassSkipped,
                runtimeActivities,
                recordRows,
                visualCandidates,
                visualDays,
                visualSections,
                visualNodes,
                fingerprints,
                samples: output.slice(0, 3).map(item => clean(item.name || item.text || '')
                  .replace(/\\n+/g, ' / ').slice(0, 96))
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

}
