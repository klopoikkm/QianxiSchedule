package com.qianxi.schedule.importer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.qianxi.schedule.data.AppSettings;
import com.qianxi.schedule.data.Course;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class ImportParserTest {
    @Test
    public void parsesOddWeekRange() {
        long mask = ImportParser.extractWeeks("1-16周(单)");
        assertTrue((mask & 1L) != 0);
        assertTrue((mask & (1L << 14)) != 0);
        assertEquals(0L, mask & (1L << 1));
    }

    @Test
    public void parsesDiscontinuousWeeks() {
        long mask = ImportParser.extractWeeks("1-4,7,9-10周");
        assertTrue((mask & (1L << 0)) != 0);
        assertTrue((mask & (1L << 6)) != 0);
        assertTrue((mask & (1L << 9)) != 0);
        assertEquals(0L, mask & (1L << 7));
    }

    @Test
    public void parsesCompactEamsWeeksBeforeRoom() {
        long mask = ImportParser.extractWeeks(
                "人工智能导论(3030113067.01)(吕宪伟)n(4-15,工学馆307)");
        assertEquals(4, Long.numberOfTrailingZeros(mask) + 1);
        assertEquals(15, 64 - Long.numberOfLeadingZeros(mask));
        assertEquals(12, Long.bitCount(mask));
    }

    @Test
    public void missingWeeksAreNotSilentlyExpandedToWholeSemester() throws Exception {
        String payload = "{\"items\":[{\"name\":\"大学物理\",\"day\":1,"
                + "\"section\":1,\"endSection\":2}]}";
        ImportParser.ImportOutcome outcome = ImportParser.parseOutcome(payload, ImportAdapter.GENERIC);
        assertTrue(outcome.courses.isEmpty());
        assertEquals(1, outcome.skippedItems);
    }

    @Test
    public void parsesCapturedZhengfangCourse() throws Exception {
        String payload = "{\"items\":[{\"day\":2,\"section\":3,\"text\":"
                + "\"课程名称：高等数学\\n教师：张老师\\n上课地点：A101\\n1-16周(单)\\n第3-4节\"}]}";
        List<Course> courses = ImportParser.parseJavascriptResult(
                JSONObject.quote(payload), "正方教务");

        assertEquals(1, courses.size());
        Course course = courses.get(0);
        assertEquals("高等数学", course.name);
        assertEquals("张老师", course.teacher);
        assertEquals("A101", course.location);
        assertEquals(2, course.dayOfWeek);
        assertEquals(10 * 60, course.startMinute);
        assertEquals(11 * 60 + 40, course.endMinute);
        assertTrue(course.occursInWeek(15));
        assertTrue(!course.occursInWeek(16));
    }

    @Test
    public void migratesLegacySchoolPayloadWithoutApplyingSchoolTimes() throws Exception {
        String payload = "{\"adapter\":\"neu\",\"source\":\"neu-api\","
                + "\"term\":\"2026-2027-1\",\"campus\":\"浑南校区\",\"items\":[{"
                + "\"name\":\"编译原理\",\"teacher\":\"李老师\",\"location\":\"信息楼B201\","
                + "\"weeks\":\"1-16周(双)\",\"day\":3,\"section\":1,\"endSection\":2,"
                + "\"text\":\"编译原理\"}]}";
        ImportParser.ImportOutcome outcome = ImportParser.parseOutcome(
                JSONObject.quote(payload), ImportAdapter.NEU);

        assertEquals("2026-2027-1", outcome.term);
        assertEquals("浑南校区", outcome.campus);
        assertEquals(1, outcome.courses.size());
        Course course = outcome.courses.get(0);
        assertEquals(8 * 60, course.startMinute);
        assertEquals(9 * 60 + 40, course.endMinute);
        assertTrue(course.occursInWeek(2));
        assertTrue(!course.occursInWeek(3));
    }

    @Test
    public void mergesEquivalentSessionsAcrossWeekRanges() throws Exception {
        String payload = "{\"adapter\":\"neu\",\"items\":["
                + "{\"name\":\"大学物理\",\"day\":1,\"section\":3,\"endSection\":4,\"weeks\":\"1-4周\"},"
                + "{\"name\":\"大学物理\",\"day\":1,\"section\":3,\"endSection\":4,\"weeks\":\"5-8周\"}]}";
        List<Course> courses = ImportParser.parseJavascriptResult(
                JSONObject.quote(payload), ImportAdapter.NEU);

        assertEquals(1, courses.size());
        assertTrue(courses.get(0).occursInWeek(1));
        assertTrue(courses.get(0).occursInWeek(8));
    }

    @Test
    public void mergesRuntimeAndDomCopiesWithCampusLocationSuffix() throws Exception {
        String payload = "{\"items\":["
                + "{\"name\":\"军事理论\",\"teacher\":\"李雪扬\","
                + "\"location\":\"工学馆204(学校本部)\",\"day\":2,"
                + "\"section\":9,\"endSection\":10,\"weeks\":\"1-16周\"},"
                + "{\"name\":\"军事理论\",\"location\":\"工学馆204\",\"day\":2,"
                + "\"section\":9,\"endSection\":10,\"weeks\":\"1-16周\"}]}";

        List<Course> courses = ImportParser.parseJavascriptResult(
                JSONObject.quote(payload), ImportAdapter.GENERIC);

        assertEquals(1, courses.size());
        assertEquals("李雪扬", courses.get(0).teacher);
        assertEquals("工学馆204(学校本部)", courses.get(0).location);
    }

    @Test
    public void mergesSamePeriodNodesWhenPageClocksDiffer() throws Exception {
        String payload = "{\"items\":["
                + "{\"name\":\"电子技术基础\",\"teacher\":\"梁丽勤\","
                + "\"location\":\"综合楼507(学校本部)\",\"day\":3,"
                + "\"section\":1,\"endSection\":2,\"startTime\":\"0800\","
                + "\"endTime\":\"0935\",\"weeks\":\"1-16周\"},"
                + "{\"name\":\"电子技术基础\",\"teacher\":\"梁丽勤\","
                + "\"location\":\"综合楼507\",\"day\":3,"
                + "\"section\":1,\"endSection\":2,\"startTime\":\"0805\","
                + "\"endTime\":\"0940\",\"weeks\":\"1-16周\"}]}";

        List<Course> courses = ImportParser.parseJavascriptResult(
                JSONObject.quote(payload), ImportAdapter.GENERIC);

        assertEquals(1, courses.size());
        assertEquals(1, courses.get(0).startNode);
        assertEquals(2, courses.get(0).step);
    }

    @Test
    public void keepsSameNamedSessionsWithDifferentPeriodSpans() throws Exception {
        String payload = "{\"items\":["
                + "{\"name\":\"电子技术基础\",\"location\":\"综合楼507\",\"day\":3,"
                + "\"section\":1,\"endSection\":2,\"weeks\":\"1-16周\"},"
                + "{\"name\":\"电子技术基础\",\"location\":\"综合楼507\",\"day\":3,"
                + "\"section\":1,\"endSection\":3,\"weeks\":\"1-16周\"}]}";

        List<Course> courses = ImportParser.parseJavascriptResult(
                JSONObject.quote(payload), ImportAdapter.GENERIC);

        assertEquals(2, courses.size());
    }

    @Test
    public void keepsSameNamedSessionsInActuallyDifferentRooms() throws Exception {
        String payload = "{\"items\":["
                + "{\"name\":\"大学物理\",\"location\":\"A101\",\"day\":1,"
                + "\"section\":3,\"endSection\":4,\"weeks\":\"1-8周\"},"
                + "{\"name\":\"大学物理\",\"location\":\"B202\",\"day\":1,"
                + "\"section\":3,\"endSection\":4,\"weeks\":\"1-8周\"}]}";

        List<Course> courses = ImportParser.parseJavascriptResult(
                JSONObject.quote(payload), ImportAdapter.GENERIC);

        assertEquals(2, courses.size());
    }

    @Test
    public void extractsMultipleTeachersAndRoomFromEamsString() throws Exception {
        String payload = "{\"items\":[{\"day\":4,\"section\":5,\"endSection\":6,\"text\":"
                + "\"人工智能导论(3030113067.01)(吕宪伟,王强)(4-15,工学馆307)\"}]}";
        List<Course> courses = ImportParser.parseJavascriptResult(
                JSONObject.quote(payload), ImportAdapter.GENERIC);

        assertEquals(1, courses.size());
        Course course = courses.get(0);
        assertEquals("人工智能导论", course.name);
        assertEquals("吕宪伟,王强", course.teacher);
        assertEquals("工学馆307", course.location);
    }

    @Test
    public void populatesStartNodeAndStepFromSections() throws Exception {
        String payload = "{\"adapter\":\"neu\",\"items\":[{\"name\":\"大学物理\",\"day\":2,"
                + "\"section\":3,\"endSection\":5,\"weeks\":\"1-8周\"}]}";
        List<Course> courses = ImportParser.parseJavascriptResult(
                JSONObject.quote(payload), ImportAdapter.NEU);

        assertEquals(1, courses.size());
        Course course = courses.get(0);
        assertEquals(3, course.startNode);
        assertEquals(3, course.step);
        assertEquals(5, course.endNode());
    }

    @Test
    public void parsesBinaryWeekString() {
        // "0111111110" → weeks 2..9 (bit n = week n+1).
        long mask = ImportParser.extractWeeks("0111111110");
        assertEquals(0L, mask & 1L);              // week 1 off
        assertTrue((mask & (1L << 1)) != 0);      // week 2 on
        assertTrue((mask & (1L << 8)) != 0);      // week 9 on
        assertEquals(0L, mask & (1L << 9));       // week 10 off
    }

    @Test
    public void importsCourseWithBinaryWeeksField() throws Exception {
        String payload = "{\"items\":[{\"name\":\"汇编语言程序设计\",\"teacher\":\"张旭\","
                + "\"location\":\"综合楼1208\",\"day\":3,\"section\":3,\"endSection\":4,"
                + "\"weeks\":\"0111111111000000000\"}]}";
        List<Course> courses = ImportParser.parseJavascriptResult(
                JSONObject.quote(payload), ImportAdapter.NEUQ_EAMS);

        assertEquals(1, courses.size());
        Course course = courses.get(0);
        assertEquals("汇编语言程序设计", course.name);
        assertEquals("综合楼1208", course.location);
        assertEquals(2, course.startWeek);
        assertEquals(10, course.endWeek);
        assertTrue(!course.occursInWeek(1));
        assertTrue(course.occursInWeek(2));
        assertTrue(course.occursInWeek(10));
        assertTrue(!course.occursInWeek(11));
    }

    @Test
    public void parsesCompactApiClock() {
        assertEquals(8 * 60 + 30, ImportParser.parseClock("083000"));
        assertEquals(21 * 60 + 25, ImportParser.parseClock("21:25"));
        assertEquals(-1, ImportParser.parseClock("25:90"));
    }

    @Test
    public void preservesPageDiagnosticsForUnsupportedLayouts() throws Exception {
        String payload = "{\"adapter\":\"zhengfang\",\"layout\":\"zhengfang\",\"source\":\"page-dom\","
                + "\"sourceUrl\":\"https://jwgl.example.edu/kb\",\"pageTitle\":\"个人课表\","
                + "\"diagnostics\":{\"frames\":1,\"shadowRoots\":2,\"tables\":3,\"candidates\":2,"
                + "\"runtimeActivities\":4,\"recordRows\":5,"
                + "\"visualCandidates\":1,\"visualDays\":7,\"visualSections\":12,"
                + "\"visualNodes\":8,\"fingerprints\":[{\"id\":\"zhengfang\",\"score\":14,"
                + "\"evidence\":[\"课表表格\"]}]},\"items\":[]}";

        ImportParser.ImportOutcome outcome = ImportParser.parseOutcome(
                JSONObject.quote(payload), ImportAdapter.AUTO);

        assertEquals("个人课表", outcome.pageTitle);
        assertEquals("https://jwgl.example.edu/kb", outcome.sourceUrl);
        assertEquals("zhengfang", outcome.layout);
        assertEquals(1, outcome.frames);
        assertEquals(2, outcome.shadowRoots);
        assertEquals(3, outcome.tables);
        assertEquals(2, outcome.candidates);
        assertEquals(4, outcome.runtimeActivities);
        assertEquals(5, outcome.recordRows);
        assertEquals(1, outcome.visualCandidates);
        assertEquals(7, outcome.visualDays);
        assertEquals(12, outcome.visualSections);
        assertEquals(8, outcome.visualNodes);
        assertEquals("zhengfang (14): 课表表格", outcome.fingerprints.get(0));
    }

    @Test
    public void contentDetectionOverridesSelectedAdapterWhenPageIsGeneric() throws Exception {
        String payload = "{\"adapter\":\"generic\",\"layout\":\"table\",\"items\":[{"
                + "\"name\":\"离散数学\",\"day\":1,\"section\":1,\"endSection\":2,"
                + "\"weeks\":\"1-16周\"}]}";

        ImportParser.ImportOutcome outcome = ImportParser.parseOutcome(
                JSONObject.quote(payload), ImportAdapter.NEU);

        assertEquals(ImportAdapter.GENERIC, outcome.adapterId);
        assertEquals("table", outcome.layout);
        assertEquals(8 * 60, outcome.courses.get(0).startMinute);
    }

    @Test
    public void preservesCandidateSamplesForUnsupportedLayouts() throws Exception {
        String payload = "{\"items\":[],\"diagnostics\":{\"candidates\":2,\"samples\":["
                + "\"软件工程 / 1-8周 / 信息楼201\",\"大学物理\"]}}";

        ImportParser.ImportOutcome outcome = ImportParser.parseOutcome(
                JSONObject.quote(payload), ImportAdapter.NEU);

        assertEquals(2, outcome.candidateSamples.size());
        assertEquals("软件工程 / 1-8周 / 信息楼201", outcome.candidateSamples.get(0));
    }

    @Test
    public void weeklyWorkloadDoesNotOverrideZhengfangTableRow() throws Exception {
        String payload = "{\"adapter\":\"zhengfang\",\"items\":[{"
                + "\"day\":4,\"section\":1,\"endSection\":2,"
                + "\"text\":\"海洋气候学引论\\n秋[第1-8周]2节/周\\n胡春迪\\n舟山教学楼-234\"}]}";
        List<Course> courses = ImportParser.parseJavascriptResult(
                JSONObject.quote(payload), ImportAdapter.ZHENGFANG);

        assertEquals(1, courses.size());
        assertEquals(1, courses.get(0).startNode);
        assertEquals(2, courses.get(0).step);
    }

    @Test
    public void timetableTitleIsNeverImportedAsCourse() throws Exception {
        String payload = "{\"adapter\":\"zhengfang\",\"items\":[{"
                + "\"day\":1,\"section\":1,"
                + "\"text\":\"2026-2027学年秋学期杨文玉的课表\"}]}";
        List<Course> courses = ImportParser.parseJavascriptResult(
                JSONObject.quote(payload), ImportAdapter.ZHENGFANG);

        assertTrue(courses.isEmpty());
    }

    @Test
    public void parsesParityAttachedByZhengfangColumn() throws Exception {
        String payload = "{\"adapter\":\"zhengfang\",\"items\":[{"
                + "\"name\":\"海洋气候学引论\",\"day\":2,\"section\":1,\"endSection\":2,"
                + "\"weeks\":\"1-8周(单)\",\"text\":\"海洋气候学引论\\n1-8周\\n胡春迪\"}]}";

        List<Course> courses = ImportParser.parseJavascriptResult(
                JSONObject.quote(payload), ImportAdapter.ZHENGFANG);

        assertEquals(1, courses.size());
        assertTrue(courses.get(0).occursInWeek(1));
        assertTrue(!courses.get(0).occursInWeek(2));
        assertTrue(courses.get(0).occursInWeek(7));
    }

    @Test
    public void derivesMinutesFromUserClassTimes() throws Exception {
        // A school whose periods start at 08:20 with 50-minute lessons: the imported course must
        // land on the user's own 作息, not on the built-in 08:00 default.
        List<AppSettings.ClassTime> times = new ArrayList<>();
        times.add(new AppSettings.ClassTime(1, 8 * 60 + 20, 9 * 60 + 10));
        times.add(new AppSettings.ClassTime(2, 9 * 60 + 20, 10 * 60 + 10));
        times.add(new AppSettings.ClassTime(3, 10 * 60 + 30, 11 * 60 + 20));
        times.add(new AppSettings.ClassTime(4, 11 * 60 + 30, 12 * 60 + 20));

        String payload = "{\"adapter\":\"zhengfang\",\"items\":[{\"name\":\"线性代数\",\"day\":2,"
                + "\"section\":2,\"endSection\":3,\"weeks\":\"1-8周\"}]}";
        ImportParser.ImportOutcome outcome = ImportParser.parseOutcome(
                JSONObject.quote(payload), ImportAdapter.ZHENGFANG, times);

        Course course = outcome.courses.get(0);
        assertEquals(9 * 60 + 20, course.startMinute);
        assertEquals(11 * 60 + 20, course.endMinute);
        assertEquals(2, course.startNode);
        assertEquals(2, course.step);
    }

    @Test
    public void legacyAdapterUsesUserClassTimesAfterMigration() throws Exception {
        List<AppSettings.ClassTime> times = new ArrayList<>();
        times.add(new AppSettings.ClassTime(1, 7 * 60, 7 * 60 + 40));

        String payload = "{\"adapter\":\"neu\",\"items\":[{\"name\":\"编译原理\",\"day\":3,"
                + "\"section\":1,\"endSection\":1,\"weeks\":\"1-8周\"}]}";
        ImportParser.ImportOutcome outcome = ImportParser.parseOutcome(
                JSONObject.quote(payload), ImportAdapter.NEU, times);

        assertEquals(7 * 60, outcome.courses.get(0).startMinute);
        assertEquals(7 * 60 + 40, outcome.courses.get(0).endMinute);
    }

    @Test
    public void explicitClockWinsOverClassTimes() throws Exception {
        List<AppSettings.ClassTime> times = new ArrayList<>();
        times.add(new AppSettings.ClassTime(1, 8 * 60, 8 * 60 + 45));

        String payload = "{\"items\":[{\"name\":\"体育\",\"day\":5,\"section\":1,"
                + "\"startTime\":\"1330\",\"endTime\":\"1510\",\"weeks\":\"1-8周\"}]}";
        ImportParser.ImportOutcome outcome = ImportParser.parseOutcome(
                JSONObject.quote(payload), ImportAdapter.GENERIC, times);

        assertEquals(13 * 60 + 30, outcome.courses.get(0).startMinute);
        assertEquals(15 * 60 + 10, outcome.courses.get(0).endMinute);
    }

    @Test
    public void classTimesLookupMatchesDeclaredPeriodNumber() throws Exception {
        // A hand-edited 作息 that only covers the afternoon: "第6节" must find the slot the user
        // labelled 6, not the sixth row of the list.
        List<AppSettings.ClassTime> times = new ArrayList<>();
        times.add(new AppSettings.ClassTime(5, 13 * 60, 13 * 60 + 45));
        times.add(new AppSettings.ClassTime(6, 13 * 60 + 50, 14 * 60 + 35));
        times.add(new AppSettings.ClassTime(7, 14 * 60 + 45, 15 * 60 + 30));

        String payload = "{\"items\":[{\"name\":\"大学英语\",\"day\":1,\"section\":6,"
                + "\"endSection\":7,\"weeks\":\"1-8周\"}]}";
        ImportParser.ImportOutcome outcome = ImportParser.parseOutcome(
                JSONObject.quote(payload), ImportAdapter.GENERIC, times);

        assertEquals(13 * 60 + 50, outcome.courses.get(0).startMinute);
        assertEquals(15 * 60 + 30, outcome.courses.get(0).endMinute);
    }

    @Test
    public void parsesBigPeriodAsTwoConsecutiveNodes() throws Exception {
        String payload = "{\"items\":[{\"day\":2,\"section\":1,"
                + "\"text\":\"高等数学\\n第2大节\\n张老师\\n1-16周\"}]}";
        List<Course> courses = ImportParser.parseJavascriptResult(
                JSONObject.quote(payload), ImportAdapter.QIANGZHI);

        assertEquals(1, courses.size());
        assertEquals(3, courses.get(0).startNode);
        assertEquals(2, courses.get(0).step);
        assertEquals(4, courses.get(0).endNode());
    }

    @Test
    public void parsesCommaSeparatedSectionList() throws Exception {
        String payload = "{\"items\":[{\"day\":3,\"section\":1,"
                + "\"text\":\"机械设计\\n第3,4,5节\\n王老师\\n1-16周\"}]}";
        List<Course> courses = ImportParser.parseJavascriptResult(
                JSONObject.quote(payload), ImportAdapter.GENERIC);

        assertEquals(1, courses.size());
        assertEquals(3, courses.get(0).startNode);
        assertEquals(3, courses.get(0).step);
    }

    @Test
    public void appliesTrailingParityToMixedWeekList() {
        long mask = ImportParser.extractWeeks("1-5,9-16周(双)");
        assertEquals(0L, mask & (1L << 0));   // week 1 odd → off
        assertTrue((mask & (1L << 1)) != 0);  // week 2 on
        assertTrue((mask & (1L << 9)) != 0);  // week 10 on
        assertEquals(0L, mask & (1L << 8));   // week 9 odd → off
    }

    @Test
    public void parsesWeeksWrittenWithParityBeforeTheWeekWord() {
        long mask = ImportParser.extractWeeks("单周1-8周");
        assertTrue((mask & (1L << 0)) != 0);
        assertEquals(0L, mask & (1L << 1));
        assertTrue((mask & (1L << 6)) != 0);
    }

    @Test
    public void extractsRoomsInAlternateBuildingFormats() throws Exception {
        assertEquals("4#201", roomOf("电路原理(4#201)"));
        assertEquals("A-303", roomOf("大学物理(A-303)"));
        assertEquals("综合楼B区201", roomOf("高等数学(1-16,综合楼B区201)"));
    }

    private String roomOf(String text) throws Exception {
        String payload = "{\"items\":[{\"day\":1,\"section\":1,\"text\":"
                + JSONObject.quote(text) + ",\"weeks\":\"1-16周\"}]}";
        List<Course> courses = ImportParser.parseJavascriptResult(
                JSONObject.quote(payload), ImportAdapter.GENERIC);
        return courses.isEmpty() ? "" : courses.get(0).location;
    }

    @Test
    public void ignoresNonTeacherRoleWordsInParentheses() throws Exception {
        String payload = "{\"items\":[{\"day\":2,\"section\":1,\"endSection\":2,\"text\":"
                + "\"电子技术实验(3030113067.01)(实验员)(李明)(4-15,工学馆307)\"}]}";
        List<Course> courses = ImportParser.parseJavascriptResult(
                JSONObject.quote(payload), ImportAdapter.GENERIC);

        assertEquals("李明", courses.get(0).teacher);
    }

    @Test
    public void parsesQiangzhiWeeksWithoutSuffixAfterDomNormalization() throws Exception {
        String payload = "{\"adapter\":\"qiangzhi\",\"items\":[{"
                + "\"name\":\"程序设计\",\"teacher\":\"李老师\",\"location\":\"A201\","
                + "\"day\":1,\"section\":6,\"endSection\":7,\"weeks\":\"1-8\","
                + "\"text\":\"程序设计\\n李老师\\nA201\\n1-8(周)\"}]}";

        List<Course> courses = ImportParser.parseJavascriptResult(
                JSONObject.quote(payload), ImportAdapter.QIANGZHI);

        assertEquals(1, courses.size());
        assertEquals(6, courses.get(0).startNode);
        assertEquals(2, courses.get(0).step);
        assertTrue(courses.get(0).occursInWeek(1));
        assertTrue(courses.get(0).occursInWeek(8));
        assertTrue(!courses.get(0).occursInWeek(9));
    }

    @Test
    public void singleCellCourseWithoutRoomIsNotTreatedAsWeekMetadata() throws Exception {
        // A one-cell course string whose metadata group ends in 全周 has no room, so the whole
        // line ends with "周)". That must not be mistaken for a week descriptor row.
        String payload = "{\"items\":[{\"day\":5,\"section\":4,\"endSection\":5,"
                + "\"weeks\":\"1-16周\","
                + "\"text\":\"科技创新与挑战3D(李铁夫；限选；全周)\"}]}";

        List<Course> courses = ImportParser.parseJavascriptResult(
                JSONObject.quote(payload), ImportAdapter.GENERIC);

        assertEquals(1, courses.size());
        assertEquals("科技创新与挑战3D", courses.get(0).name);
        assertEquals(5, courses.get(0).dayOfWeek);
    }

    @Test
    public void pureWeekDescriptorLinesAreStillRejectedAsNames() throws Exception {
        // The counterpart of the case above: a line that really is only week wording must not
        // become a course name.
        String payload = "{\"items\":[{\"day\":1,\"section\":1,"
                + "\"weeks\":\"1-16周\",\"text\":\"1-16周(单)\"}]}";

        ImportParser.ImportOutcome outcome = ImportParser.parseOutcome(
                JSONObject.quote(payload), ImportAdapter.GENERIC);

        assertTrue(outcome.courses.isEmpty());
        assertEquals(1, outcome.skippedItems);
    }

    @Test
    public void parsesWeekRangesWrittenWithFullWidthTilde() {
        // 8:00～9:35 style pages use U+FF5E, not ASCII '~'.
        long mask = ImportParser.extractWeeks("1～8周");
        assertEquals(1, Long.numberOfTrailingZeros(mask) + 1);
        assertEquals(8, 64 - Long.numberOfLeadingZeros(mask));
        assertEquals(8, Long.bitCount(mask));
    }

    @Test
    public void parsesSectionRangeWrittenWithFullWidthTilde() throws Exception {
        String payload = "{\"items\":[{\"day\":3,\"section\":1,"
                + "\"weeks\":\"1-16周\",\"text\":\"大学物理\\n第3～4节\"}]}";

        List<Course> courses = ImportParser.parseJavascriptResult(
                JSONObject.quote(payload), ImportAdapter.GENERIC);

        assertEquals(1, courses.size());
        assertEquals(3, courses.get(0).startNode);
        assertEquals(2, courses.get(0).step);
    }

    /** The 11-小节 作息 from the reported device, against a 6-大节 page. */
    private static List<AppSettings.ClassTime> deviceClassTimes() {
        List<AppSettings.ClassTime> times = new ArrayList<>();
        times.add(new AppSettings.ClassTime(1, 8 * 60, 8 * 60 + 45));
        times.add(new AppSettings.ClassTime(2, 8 * 60 + 50, 9 * 60 + 35));
        times.add(new AppSettings.ClassTime(3, 9 * 60 + 55, 10 * 60 + 40));
        times.add(new AppSettings.ClassTime(4, 10 * 60 + 45, 11 * 60 + 30));
        times.add(new AppSettings.ClassTime(5, 11 * 60 + 35, 12 * 60 + 20));
        times.add(new AppSettings.ClassTime(6, 14 * 60, 14 * 60 + 45));
        times.add(new AppSettings.ClassTime(7, 14 * 60 + 50, 15 * 60 + 35));
        times.add(new AppSettings.ClassTime(8, 15 * 60 + 55, 16 * 60 + 40));
        times.add(new AppSettings.ClassTime(9, 16 * 60 + 45, 17 * 60 + 30));
        times.add(new AppSettings.ClassTime(10, 17 * 60 + 35, 18 * 60 + 20));
        times.add(new AppSettings.ClassTime(11, 19 * 60, 19 * 60 + 45));
        return times;
    }

    @Test
    public void pageSectionNumberingDecidesGridRowRegardlessOfLocalClassTimes() throws Exception {
        // 第6节 19:20~21:45 against an 11-小节 local table. The page's own numbering stays
        // authoritative: remapping the clock onto local rows is what made adjacent 大节 collide.
        // The mismatch is resolved by adopting the page's 作息 (see pagePeriods), not by guessing.
        String payload = "{\"items\":[{\"name\":\"高性能计算与计算机体系结构\",\"day\":1,"
                + "\"section\":6,\"startTime\":\"1920\",\"endTime\":\"2145\","
                + "\"weeks\":\"1-16周\"}]}";
        ImportParser.ImportOutcome outcome = ImportParser.parseOutcome(
                JSONObject.quote(payload), ImportAdapter.GENERIC, deviceClassTimes());

        Course course = outcome.courses.get(0);
        assertEquals(6, course.startNode);
        assertEquals(1, course.step);
        assertEquals(19 * 60 + 20, course.startMinute);
        assertEquals(21 * 60 + 45, course.endMinute);
    }

    @Test
    public void explicitClockAlwaysWinsOverLocalClassTimes() throws Exception {
        // 第1节 8:00~9:35 is a 大节; the local 第1节 ends at 8:45. The alarm follows the page.
        String payload = "{\"items\":[{\"name\":\"信号与系统\",\"day\":1,\"section\":1,"
                + "\"startTime\":\"0800\",\"endTime\":\"0935\",\"weeks\":\"1-16周\"}]}";
        ImportParser.ImportOutcome outcome = ImportParser.parseOutcome(
                JSONObject.quote(payload), ImportAdapter.GENERIC, deviceClassTimes());

        Course course = outcome.courses.get(0);
        assertEquals(1, course.startNode);
        assertEquals(8 * 60, course.startMinute);
        assertEquals(9 * 60 + 35, course.endMinute);
    }

    @Test
    public void pagePeriodsCarryTheStructureStatedByTheLabelColumn() throws Exception {
        // The scanner reports the page's own 作息 so the app can offer it instead of forcing a
        // 6-大节 timetable onto a table cut into 45-minute 小节.
        String payload = "{\"items\":[{\"name\":\"信号与系统\",\"day\":1,\"section\":1,"
                + "\"startTime\":\"0800\",\"endTime\":\"0935\",\"weeks\":\"1-16周\"}],"
                + "\"periods\":[{\"period\":1,\"startTime\":\"8:00\",\"endTime\":\"9:35\"},"
                + "{\"period\":2,\"startTime\":\"9:50\",\"endTime\":\"12:15\"},"
                + "{\"period\":6,\"startTime\":\"19:20\",\"endTime\":\"21:45\"}]}";
        ImportParser.ImportOutcome outcome = ImportParser.parseOutcome(
                JSONObject.quote(payload), ImportAdapter.GENERIC, deviceClassTimes());

        assertEquals(3, outcome.pagePeriods.size());
        assertEquals(1, outcome.pagePeriods.get(0).period);
        assertEquals(8 * 60, outcome.pagePeriods.get(0).startMinute);
        assertEquals(9 * 60 + 35, outcome.pagePeriods.get(0).endMinute);
        assertEquals(6, outcome.pagePeriods.get(2).period);
        assertEquals(19 * 60 + 20, outcome.pagePeriods.get(2).startMinute);
        assertEquals(21 * 60 + 45, outcome.pagePeriods.get(2).endMinute);
    }

    @Test
    public void malformedPagePeriodsAreDropped() throws Exception {
        String payload = "{\"items\":[{\"name\":\"信号与系统\",\"day\":1,\"section\":1,"
                + "\"weeks\":\"1-16周\"}],"
                + "\"periods\":[{\"period\":0,\"startTime\":\"8:00\",\"endTime\":\"9:35\"},"
                + "{\"period\":2,\"startTime\":\"9:50\",\"endTime\":\"9:50\"},"
                + "{\"period\":3,\"startTime\":\"\",\"endTime\":\"15:05\"},"
                + "{\"period\":4,\"startTime\":\"15:20\",\"endTime\":\"16:55\"}]}";
        ImportParser.ImportOutcome outcome = ImportParser.parseOutcome(
                JSONObject.quote(payload), ImportAdapter.GENERIC, null);

        assertEquals(1, outcome.pagePeriods.size());
        assertEquals(4, outcome.pagePeriods.get(0).period);
    }

    @Test
    public void pagesWithoutPeriodLabelsReportNoStructure() throws Exception {
        String payload = "{\"items\":[{\"name\":\"信号与系统\",\"day\":1,\"section\":1,"
                + "\"weeks\":\"1-16周\"}]}";
        ImportParser.ImportOutcome outcome = ImportParser.parseOutcome(
                JSONObject.quote(payload), ImportAdapter.GENERIC, null);

        assertTrue(outcome.pagePeriods.isEmpty());
    }

    @Test
    public void parsesExplicitClockWrittenWithFullWidthTilde() throws Exception {
        String payload = "{\"items\":[{\"day\":1,\"section\":1,"
                + "\"weeks\":\"1-16周\",\"text\":\"信号与系统\\n8:00～9:35\"}]}";

        List<Course> courses = ImportParser.parseJavascriptResult(
                JSONObject.quote(payload), ImportAdapter.GENERIC);

        assertEquals(1, courses.size());
        assertEquals(8 * 60, courses.get(0).startMinute);
        assertEquals(9 * 60 + 35, courses.get(0).endMinute);
    }
}
