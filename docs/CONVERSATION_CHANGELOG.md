# AimeSimulator 对话更改记录

本文记录 Codex 任务 `019fb2c2-5b6a-73f2-b763-a9af42b50630` 及其在当前任务中的续接工作。记录范围覆盖项目基础重构、NFC-F/HCE-F 与 PMm 兼容、界面与外观、卡面 Access Code、文档、构建验证及仓库操作。

- 时间范围：2026-08-01 至 2026-08-04
- 当前项目：`UmiSlat/AimeSimulator`
- 当前分支：`main`
- 当前应用版本：`2.2.5`

## 1. 仓库与项目基础

- 将项目整理为 `AimeSimulator`，包名统一为 `io.github.umislat.aimesimulator`。
- 建立新的 Android、Kotlin、Java 与 C/C++ 工程结构。
- 建立卡片配置、持久化存储、十六进制编解码、FeliCa 数据包处理、实体卡读取和 HCE-F 会话管理等模块。
- 增加中英文资源、Material 3 主题、自适应图标与 Android 13+ 单色主题图标。
- 增加单元测试、功能规格、第三方声明、Gradle Wrapper、APK/模块检查与 KernelSU 模块打包工具。
- 新仓库使用 `main` 分支；旧仓库 `UmiSlat/AICEmu_rebuild` 作为归档项目，新仓库地址统一为 `UmiSlat/AimeSimulator`。
- Git 历史最终整理为新的项目提交链，并移除了旧仓库远程引用。

## 2. 卡片配置与 HCE-F

- 支持保存多个本地卡片配置，并使用稳定 ID 标识配置。
- 支持设置当前卡片、编辑、删除以及隐私隐藏 IDm。
- 支持手动输入卡片数据和从实体 NFC-F 卡读取必要数据。
- 实体卡读取包含 IDm、System Code、S_PAD0 和 ID 块。
- 实现 FeliCa Lite 读取命令解析、服务码和块描述校验以及模拟响应。
- 正常模式使用卡片自身 NFCID2；兼容模式使用固定兼容 NFCID2 路由。
- HCE-F 会话增加注册回滚和状态一致性处理。
- NFC 服务重启期间识别 `DeadObjectException`，重新获取 NFC 适配器并有限重试，避免短暂 Binder 失效导致崩溃或永久配置失败。

## 3. LSPosed 与作用域

- LSPosed 模块迁移到现代 API 101 格式和入口。
- 补齐模块元数据、入口和推荐作用域声明。
- 最终推荐作用域仅保留 `com.android.nfc`。
- 移除不需要的 `system`/系统框架作用域，避免扩大注入范围。
- NFCID2 和 System Code 的兼容校验 Hook 仅在 NFC 服务进程中工作。
- 产物检查会验证 APK 内的 Xposed 入口和作用域清单。

## 4. PMm 与 KernelSU 兼容

### 4.1 标准 HCE-F PMm

- 在 `host_nfcf_service.xml` 中补充标准声明：

  ```xml
  <t3tPmm-filter android:name="00F1000000014300" />
  ```

- 修复关闭 PMm Hook 时 Android 回退为 `FFFFFFFFFFFFFFFF` 的问题。
- 在遵循标准 HCE-F 元数据的设备上，关闭 Hook 后也可以使用固定 PMm `00F1000000014300`。
- PMm Hook 保留为厂商 NFC 栈忽略或覆盖标准值时的兼容方案。
- APK 产物检查增加 PMm 元数据断言。

### 4.2 Android 15/16 KernelSU 模块

- 增加 arm64 KernelSU 模块、HAL 注入器和原生 PMm Hook。
- 修复 PMm 库文件 SELinux 标签错误，将目标库按 vendor 库上下文安装，避免 `hal_nfc_default` 拒绝加载。
- 针对实机 ST NFC HAL 恢复 GOT Hook；Dobby 仅保留给适用的旧框架路径。
- 补充实机 HAL 进程名称识别和注入重试。
- 重写注入器，移除会修改共享指令并触发 `SIGTRAP` 的 `BRK` 方案，改用单线程 ptrace 单步和 syscall 返回哨兵。
- 增加 HAL PID、NFC Binder、Root 命令超时与恢复状态检查。
- 最终改为运行时属性控制旁路：关闭补丁时 Hook 保持加载但原样放行，不再重启 NFC HAL 或 `com.android.nfc`。
- 解决反复开启/关闭 PMm 时 Binder 缓存失效、HCE-F 重新配置超时和 NFC 服务重启问题。
- 实机完成“开启 → 关闭 → 再开启 → 恢复关闭”回归；HAL 和 NFC 进程 PID 保持不变，开关日志和实际改写日志符合预期。

## 5. 默认 NFC 应用检查

- 增加进入主界面时的默认 NFC 应用检查。
- 增加最小 HCE-A 辅助服务与支付类别 AID `F0010203040506`，用于参与 Android 默认非接触式应用选择。
- 辅助服务不处理支付数据，收到 APDU 时统一返回不支持；Aime/FeliCa 模拟仍由 HCE-F 服务完成。
- 如果应用不是默认 NFC 应用，会打开 Android 系统确认界面。
- 每次应用会话最多请求一次；NFC 关闭时暂不弹出，恢复后重新进入前台再检查。
- README 增加替换默认支付应用的影响和恢复说明。

## 6. 主题、动态取色与图标

- 支持跟随系统、浅色和深色三种主题模式。
- Android 12+ 支持 Material You 动态取色，并提供独立开关。
- 主题和动态取色选择持久保存，修改后立即应用。
- 主题重建时保存并恢复 PMm 状态快照，避免重复查询 KernelSU 模块状态。
- 修复设置页首次打开时重复查询 PMm 状态的问题。
- 将应用图标替换为“极简触碰星芒”方案：深蓝背景、白色卡片、蓝色触碰轨迹和珊瑚红星芒。
- 同步制作 Android 13+ 单色主题图标。
- 将前景图缩入自适应图标安全区，改善圆形和厂商蒙版下的裁切。

## 7. 2.2.0 界面重构

- 将主界面重构为单 Activity 下的三页底部导航：卡片、状态、设置。
- 卡片页包含紧凑模拟状态、配置列表、当前配置标记和添加按钮。
- 状态页包含当前卡片详情、HCE-F 状态、兼容模式、System Code、PMm 和兼容补丁状态。
- 设置页包含主题、动态取色、隐私选项和关于入口。
- 移除独立 `SettingsActivity`，主题重建时保留当前页面和 PMm 状态。
- 新增独立 Material 3 关于页面，包含应用信息、版本、能力、隐私、风险说明和 GitHub 入口。
- 压缩主页面、关于页和读卡页标题栏，移除重复标题信息。
- 修复卡片“当前”标签与勾选角标重叠。
- 编辑、删除改为轻量按钮；主题选择改为描边分段控件；关于入口改为描边按钮。
- PMm 显示格式调整为 `00F1 0000 0001 4300`。

## 8. 添加卡片与表单

- 将添加方式由普通对话框改为带图标和说明的底部操作页。
- 将卡片编辑改为可滚动的高位底部表单，并固定取消/保存操作。
- IDm、S_PAD0 和 ID 块使用 Material 3 描边输入框，显示固定标签和长度说明。
- 长十六进制字段支持横向滚动，并支持粘贴包含空格的数据。
- 输入错误会在对应字段就地显示。
- 修复 `TextInputLayout` 使用错误 `LayoutParams` 引发的 `ClassCastException`；手动添加和读卡回填不再在创建输入框时崩溃。

## 9. 卡面 Access Code

- 为卡片配置增加可选的 20 位卡面 Access Code 字段。
- Access Code 作为本地资料手动输入，不从 IDm、S_PAD0 或实体卡读取结果推算。
- 增加数字格式和长度校验。
- 卡片页与状态页按 `4 × 5` 分组显示。
- 设置页增加独立的 Access Code 隐私开关。
- Access Code 不参与 HCE-F 模拟，也不改变 NFC 数据响应。
- 旧配置缺少该字段时仍可无损读取，存储格式保持向后兼容。
- README、功能规格和单元测试同步更新。

### 9.1 MIFARE Access Code 识别测试

- `2.2.1` 首版将识别复用在原读取入口中，因缺少可见入口而不便确认测试模式。
- `2.2.2` 在“添加卡片”底部页增加独立的“识别 Access Code（测试）”入口，并将版本号单独提升以便安装确认。
- 测试入口只启用 NFC-A 与 MIFARE Classic 只读识别，原“读取卡片”入口继续只处理 NFC-F/FeliCa。
- 使用 `WCCFv2` 对应的 6 字节密钥尝试认证扇区 0，通过后读取 Block 2。
- 从 Block 2 的第 7–16 字节提取 10 字节 packed-decimal 数据，并仅接受可表示为 20 位十进制数字的结果。
- 识别成功后在读卡页显示 UID 和按 `4 × 5` 分组的 Access Code。
- 当前测试阶段不会把识别结果回传给卡片编辑器，也不会写入 `CardStore` 或实体卡。
- 对认证失败、无效数据、读取失败以及设备不提供 MIFARE Classic 访问分别显示明确提示。
- 新增纯数据解码单元测试；正式接入配置写入前仍需完成实体卡与手机 NFC 芯片兼容性验证。

### 9.2 HINATA 官方实现核对与 Amusement IC 识别

- 用户实测 `2.2.2` 后反馈“和上一版本没有任何区别”，并提供 HINATA 官网 `https://hinata.neri.moe/` 作为实现参考。
- 核对 HINATA 官网及官方仓库 `Project-HINATA/hinata_go`，分析提交 `c56d8badc3a720e0ba9e2f721f3f73111f2f6d97` 的 NFC 读取实现。
- 确认 HINATA 对卡片采用两条独立路径：Amusement IC 使用 NFC-F/FeliCa，旧式 Aime 使用 NFC-A/MIFARE Classic。
- 找到上一测试版无反应的直接原因：独立识别入口只启用了 `FLAG_READER_NFC_A`，因此 Amusement IC 不会触发回调。
- `2.2.3` 将识别入口改为同时监听 NFC-A 和 NFC-F；原“读取卡片”入口仍只处理配置读取，不改变既有行为。
- FeliCa 路径按官方逻辑校验 IDm `01 2E` 前缀、PMm `00F1000000014300` 和 System Code `88B4`（也兼容未知或零值）。
- 使用 FeliCa Read Without Encryption 命令、只读服务码 `000B` 读取 Block 0，并校验响应命令、IDm、状态码、块数量和长度。
- 新增 SPAD0 解密实现；解密后要求第 6 字节为 `00`、Access Code 首字节高半字节为 `5`，再从第 7–16 字节提取 10 字节 Access Code。
- 识别成功页显示卡片类型、IDm/UID、System Code（FeliCa）和分组后的 Access Code；仍不回传编辑器、不写入 `CardStore`、不写实体卡。
- MIFARE 路径改为和官方一致，直接使用 `WCCFv2` 对应 Key B 认证 Block 2，不再先尝试 Key A。
- 新增 HINATA SPAD0 固定向量、加密 AIC Block 0、AIC 指纹和异常输入单元测试。
- README 与功能规格补充双协议识别入口，并明确识别结果尚不写入配置。
- 应用版本提升为 `2.2.3 (23)`，新 APK 单独输出，避免与 `2.2.2` 混装后无法辨别。

### 9.3 实体卡验证与配置写入流程

- 用户使用实体 Amusement IC 验证 `2.2.3`，确认 Access Code 识别成功。
- 识别成功页增加“用于卡片配置”按钮，不再自动结束或静默保存，用户可以先核对识别结果。
- Amusement IC 会把 IDm、原始加密 S_PAD0 和 Access Code 通过 Activity Result 带入卡片编辑器。
- 旧式 MIFARE Aime 只返回 Access Code；编辑器会提示用户在保存前填写对应的 FeliCa IDm。
- 主界面的识别入口改为使用现有 Activity Result 启动器，并在编辑器中预填返回字段。
- 实体卡验证通过后，入口标题移除“测试”标记，作为正式的 Access Code 识别流程。
- 只有用户点击编辑器“保存”且全部字段校验通过后才写入 `CardStore`；取消编辑不会改变配置。
- 实体卡读取、识别和本地配置保存流程始终不会向实体卡写入数据。
- 应用版本提升为 `2.2.4 (24)`，与已验证的 `2.2.3` 测试版分开交付。

### 9.4 读取与识别入口合并

- 用户提出合并“读取卡片”和“识别 Access Code”两个入口。
- “添加卡片”底部页现在只保留一个“读取卡片”入口，不再要求用户预先判断卡片协议。
- 统一 Reader Mode 同时监听 NFC-F 和 NFC-A，并自动分派 Amusement IC、普通 FeliCa 与旧式 MIFARE Aime。
- Amusement IC 在同一次连接中优先组合读取 Block `00` 与 `82`，组合失败时分别重试，因此同时保留 Access Code 识别和原 ID 块采集能力。
- 非 Amusement IC 的普通 FeliCa 卡继续使用原配置采集流程；旧式 MIFARE Aime 继续使用 Key B 认证与 Access Code 提取。
- 移除识别专用 Activity 模式、Intent 参数和重复入口文案，结果统一进入同一配置编辑器。
- 应用版本提升为 `2.2.5 (25)`。

## 10. 关于页面最终文案调整

- 从中英文项目说明中移除“独立实现”相关措辞。
- 将关于页的“独立实现 / Independent implementation”信息卡替换为“适用范围 / Scope”。
- 中文文案说明本应用用于个人设备上的配置管理、兼容性测试和本地调试，并保留非官方、无隶属或背书关系及名称标识归属说明。
- 英文文案同步表达相同范围和免责声明。
- 代码资源引用从 `about_independence_*` 更新为 `about_scope_*`，项目源码中不再存在旧资源名和旧介绍。

涉及的当前未提交文件：

- `app/src/main/java/io/github/umislat/aimesimulator/ui/AboutActivity.kt`
- `app/src/main/res/values-zh-rCN/strings.xml`
- `app/src/main/res/values/strings.xml`

## 11. 文档与交付

- README 重写为中文主文档，并保留简短英文摘要。
- README 补充功能范围、兼容性、安装、正常/兼容模式、PMm、Root/LSPosed/KernelSU 风险、使用流程、调试、构建和常见问题。
- `docs/FUNCTIONAL_SPEC.md` 持续同步 HCE-F、PMm、界面和 Access Code 行为。
- `THIRD_PARTY_NOTICES.md` 记录第三方依赖和工具。
- `tools/check_artifacts.py`、`tools/module_layout.py` 和 `tools/package_module.py` 用于检查 APK、模块结构和打包结果。
- `dist/` 是项目约定的 APK 与 KernelSU 模块交付输出目录；Gradle 的 `app/build/outputs/` 仅保存临时构建产物。
- 生成过多个版本化 Debug APK 和 KernelSU 模块 ZIP；`dist/` 与构建目录均保持不纳入 Git。

## 12. 版本与提交时间线

| 版本/阶段 | 主要内容 | Git 状态 |
| --- | --- | --- |
| 2.0.0 | 建立当前项目基础、数据层、HCE-F、实体卡读取、现代 LSPosed、KernelSU 与测试工具 | 汇总进入初始实现提交 |
| 2.1.1 | 移除系统框架作用域，仅保留 `com.android.nfc` | 中间构建 |
| 2.1.2 | 修复 NFC 服务重启期间的 `DeadObjectException` 与 HCE-F 配置竞态 | 中间构建 |
| 2.1.3 | 修复 vendor 库标签、HAL 识别和 ST HAL GOT Hook | 中间构建 |
| 2.1.4 | 重写 ptrace 注入返回检测，移除共享代码 `BRK` | 中间构建 |
| 2.1.5 | 扩展 NFC/HAL 恢复重试；实机发现厂商子 Binder 缓存问题 | 中间构建 |
| 2.1.6 | 改为 PMm 运行时旁路，无需重启 NFC；完成实机反复开关回归 | `23a3717` |
| 2.1.6 | 替换“极简触碰星芒”图标 | 后续汇总提交 |
| 2.1.7 | 增加主题设置、动态取色控制和默认 NFC 应用检查 | 后续汇总提交 |
| 2.1.8 | 修正图标安全区和主题重建导致的 PMm 状态刷新 | `e476258` |
| 2.1.9 | 声明标准 HCE-F PMm，关闭 Hook 时不再回退全 `FF` | `d4e4cff` |
| 2.2.0 | 三页 UI、关于页、底部表单、崩溃修复与 Access Code | `1c9a619` |
| 2.2.0 当前工作区 | 关于页将“独立实现”替换为“适用范围” | 尚未提交 |
| 2.2.1 识别测试版 | 增加 MIFARE Classic Access Code 只读识别，尚未写入配置 | 尚未提交、待实机验证 |
| 2.2.2 识别测试版 | 增加独立且可见的识别入口，与 FeliCa 配置读取完全分离 | 尚未提交、待实机验证 |
| 2.2.3 识别测试版 | 按 HINATA 官方流程增加 Amusement IC/FeliCa Access Code 识别，并修正 MIFARE Key B 认证 | 尚未提交、待实机验证 |
| 2.2.4 配置接入版 | 实体卡识别验证成功；识别结果经用户确认后写入本地卡片配置 | 尚未提交、待实机验证 |
| 2.2.5 统一读卡版 | 合并读取与 Access Code 识别入口，按卡片技术自动分派 | 尚未提交、待实机验证 |

当前仓库提交：

| 提交 | 日期 | 内容 |
| --- | --- | --- |
| `2d7d2a3` | 2026-08-01 | Initial AimeSimulator implementation |
| `23a3717` | 2026-08-02 | Stabilize PMm runtime patching |
| `3ccf870` | 2026-08-02 | Expand Chinese README |
| `e476258` | 2026-08-03 | Add appearance and default NFC controls |
| `d4e4cff` | 2026-08-03 | Declare standard HCE-F PMm |
| `1c9a619` | 2026-08-03 | Redesign UI and add printed access codes |

## 13. 验证记录

对主要版本和最终改动执行过以下验证：

- `testDebugUnitTest`
- `lintDebug`
- `assembleDebug`
- Kotlin、Java 与 C/C++ 编译
- APK 签名、Manifest、HCE-F 元数据和 Xposed 入口检查
- KernelSU 模块布局、原生库和 ZIP 产物检查
- `git diff --check`
- Android 16 实机 SSH 日志、NFC Binder、HAL PID、PMm 开关和应用界面回归

本次关于页文案、统一实体卡读取、Access Code 识别和配置编辑器接入已使用 JDK 17 完成：

```text
testDebugUnitTest lintDebug assembleDebug
BUILD SUCCESSFUL
```

当前正式交付的 Debug APK：

```text
dist/AimeSimulator-2.2.5-debug.apk
SHA-256: 9A115D79F54F34F3F3694EFEE71D509F597C576ACA52F513673408838D8C4DDB
```

## 14. 当前状态

- 已推送的最新提交：`1c9a619`
- 当前未提交源码改动：About 文案、统一实体卡读取与 Access Code 识别、配置编辑器回填、SPAD0 解密、测试与相关资源
- 本记录文件：`docs/CONVERSATION_CHANGELOG.md`
- 当前未执行新的提交或推送
