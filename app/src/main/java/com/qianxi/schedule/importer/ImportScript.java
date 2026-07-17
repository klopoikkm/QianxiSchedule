package com.qianxi.schedule.importer;

public final class ImportScript {
    private ImportScript() {}

    public static String forAdapter(String adapterId) {
        return ImportAdapter.NEU.equals(adapterId) ? NEU_API : GENERIC_PAGE;
    }

    private static final String GENERIC_PAGE = """
        (function () {
          window.__qianxiImportState = 'loading';
          window.__qianxiImportPayload = null;
          window.__qianxiImportError = '';
          try {
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
            function add(day, section, endSection, node) {
              day = Number(day); section = Number(section); endSection = Number(endSection || section);
              if (!(day >= 1 && day <= 7) || !(section >= 1 && section <= 30)) return;
              const text = nodeText(node);
              if (!text || text.length < 2 || /^(无|暂无|--|-)$/.test(text)) return;
              const key = day + '|' + section + '|' + endSection + '|' + text;
              if (seen.has(key)) return;
              seen.add(key);
              output.push({ day, section, endSection: Math.max(section, endSection), text,
                title: clean(node.getAttribute && (node.getAttribute('title') || '')) });
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
