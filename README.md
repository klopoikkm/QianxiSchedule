# 潜溪课表 Qianxi Schedule

潜溪课表是一款本地优先的 Android 课表应用。它提供周课表、教务页面导入和按上课时间自动静音，应用不要求注册账号，也不会把教务页面或课程数据上传到第三方服务器。

## 功能

- 周课表与当前时间指示，支持课程重叠显示
- 课程增删改、单双周和不连续周次
- 正方、强智、青果及通用表格教务页面导入
- 导入前预览，支持替换或合并现有课表
- 上课自动静音，连续课和重叠课程结束后才恢复
- 恢复上课前的铃声模式，尊重上课期间的手动调整
- 开机、时区变化和应用更新后自动重建闹钟

## 构建

要求 JDK 17、Android SDK 35 和可访问 Google Maven 的网络环境。

```powershell
./gradlew.bat assembleDebug
```

生成的 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。正式发布流程与 GitHub Actions Secrets 配置参见 [`RELEASING.md`](RELEASING.md)。

## 教务导入流程

1. 在应用中打开“导入”。
2. 选择教务系统类型并填写学校教务网址。
3. 在学校页面完成登录、验证码或统一身份认证。
4. 进入当前学期的个人周课表，点击“导入当前课表”。
5. 检查识别结果，选择替换或合并。

导入通过一段只读 JavaScript 扫描当前页面 DOM，数据直接返回本机 Java 代码解析。应用没有账号密码接口，也没有远程数据服务。不同学校会定制教务页面；未识别的页面可在 `ImportScript.java` 中增加 DOM 选择规则，或在 `ImportParser.java` 中增加文本规则。

## 自动静音

Android 6 及以上需要用户授予“勿扰权限”；Android 12 及以上建议同时授予“精确闹钟”权限。权限只能由用户在系统设置中确认。

静音状态使用课程实例令牌管理。当多门课时间重叠时，任一课程仍在进行都会保持静音。最后一个令牌退出后，应用恢复上课前保存的铃声模式。

## 主要结构

```text
app/src/main/java/com/qianxi/schedule/
├── data/       SQLite 数据、设置与周次计算
├── importer/   WebView DOM 扫描和课程解析
├── silence/    AlarmManager 调度、权限与铃声状态机
└── ui/         周课表、编辑、导入和设置界面
```

## 许可证

MIT License。
