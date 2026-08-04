# AimeSimulator

AimeSimulator 是一个面向 Android 的 NFC-F / FeliCa Lite 卡片配置管理与 HCE-F 模拟工具。它可以保存多张本地配置，通过统一读卡入口读取 Amusement IC、普通 FeliCa 和旧式 MIFARE Aime 的可用信息，并由 Android `HostNfcFService` 向读卡端提供当前配置。

当前实验分支版本为 `2.2.8`。项目使用 Android 公开接口、协议资料和互操作实现作为开发依据；AIC/SPAD0 读取行为参考了 Project HINATA 的公开实现，具体来源见“实现参考、版权与第三方组件”。

> [!WARNING]
> 本项目会使用 HCE-F、LSPosed Hook、Root 命令和厂商 NFC HAL 注入。请仅在自己拥有或获准测试的设备与卡片上使用，并提前准备可恢复系统的手段。项目不保证兼容任何具体商业设备或服务。

## 快速开始（无 Root）

1. 从 [GitHub Releases](https://github.com/UmiSlat/AimeSimulator/releases) 安装最新 APK。
2. 打开应用，在 Android 系统界面中确认是否将 AimeSimulator 设为默认 NFC 应用。
3. 在“卡片”页选择“手动添加”或“读取卡片”，检查字段后保存配置。
4. 选中要使用的配置；首次尝试建议保持 PMm 补丁关闭。
5. 保持应用位于前台，将手机靠近读卡端测试。

无 Root 路径依赖设备原生支持 HCE-F，并正确接受应用声明的 NFCID2、System Code 和 PMm。若动态 IDm 被系统拒绝，可以先尝试“兼容模式”；只有原生路径确实失败时，才需要后文的 LSPosed 或 KernelSU 兼容组件。

状态页的“无 Root 兼容性”检测复用当前配置的真实 HCE-F 注册结果。要判断纯无 Root 路径，请先关闭 LSPosed、PMm Hook 和 KernelSU 模块；检测通过只代表 Android 接受了当前 IDm、`88B4` 和前台服务，PMm 仍需由实际读卡端验证。

> [!IMPORTANT]
> 将本应用设为默认 NFC 应用会替换现有的默认非接触式支付应用，可能暂时影响手机钱包。测试结束后可在 Android 的默认应用或非接触式付款设置中恢复原应用。

## 功能概览

- 管理多张本地卡片配置，可添加、编辑、删除和切换当前配置。
- 支持手动填写或从支持的实体卡读取 Access Code、IDm、S_PAD0 和 ID 块。
- 使用单一“读取卡片”入口自动识别 Amusement IC、普通 FeliCa 和旧式 MIFARE Aime。
- Amusement IC 可读取 IDm、加密 S_PAD0、ID 块并解密得到 Access Code。
- 普通 FeliCa 可尝试读取 IDm、S_PAD0 与 ID 块；旧式 MIFARE Aime 可读取 UID 与 Access Code。
- 提供正常模式与兼容模式两种 NFCID2 路由方式。
- 在状态页显示 NFC-F 注册的无 Root 兼容性结果，并区分 NFCID2、`88B4` 和服务启用失败。
- 提供独立的通用 `4000` 与静态 XML `88B4` 探针，用于定位 ROM 的 HCE-F 校验阶段。
- 进入应用时检查系统默认 NFC 支付应用；如果尚未选择本应用，则打开 Android 的系统确认界面。
- 响应 FeliCa Lite 的 Read Without Encryption 请求。
- 通过标准 HCE-F `t3tPmm-filter` 声明 PMm `00F1000000014300`，兼容的系统无需 Hook。
- 提供 PMm 兼容补丁：
  - Android 14 及以下：通过 libxposed API 101 和应用内原生 Hook 工作；
  - Android 15 及以上：通过独立 KernelSU 模块注入 NFC HAL。
- PMm 补丁启用时将对外配置替换为 `00F1000000014300`。
- 默认跟随系统浅色/深色主题，并允许在设置中手动选择浅色或深色。
- Android 12+ 默认使用 Material You 动态取色，可在设置中关闭。
- 使用“卡片 / 状态 / 设置”三页底部导航：卡片页负责配置管理，状态页集中展示 HCE-F 与 PMm，设置页管理外观和隐私。
- 应用不申请网络权限，不包含遥测或在线账户功能。

## 运行条件

### 基础功能

- Android 10 或更高版本（最低 API 29）。
- 设备必须具有 NFC，并由系统声明支持 NFC-F 主机卡模拟（HCE-F）。
- 使用模拟时需要保持 NFC 开启，并让 AimeSimulator 位于前台。

仅安装 APK 就可以管理配置，并通过系统原生 HCE-F 声明 NFCID2、System Code 和 PMm。若设备限制自定义 NFCID2，或厂商 NFC 栈忽略/替换标准 PMm，才需要下方对应的 Hook/Root 环境。

| 能力 | Root/LSPosed 要求 | 说明 |
| --- | --- | --- |
| 配置管理与实体卡读取 | 不需要 | 仍受手机 NFC 芯片和驱动支持范围限制 |
| 标准 HCE-F 模拟 | 不需要 | 设备需原生支持 HCE-F |
| 兼容模式固定 NFCID2 | 不需要 | 用于系统拒绝动态卡号的情况 |
| 放宽 NFCID2 / System Code 校验 | 按需使用 LSPosed | 作用域仅为 `com.android.nfc` |
| PMm 厂商兼容补丁 | 按需使用 Root | Android 15+ 还需要 KernelSU 模块 |

### 兼容性组件

| 系统版本 | LSPosed API 101 | Root | KernelSU 模块 | PMm 实现 |
| --- | --- | --- | --- | --- |
| Android 10–14 | 按需启用 | 使用 PMm 兜底补丁时需要 | 不需要 | 标准 HCE-F PMm；异常设备可在 `com.android.nfc` 中加载 `libpmm.so` |
| Android 15–16 | 按需启用 | 使用 PMm 兜底补丁时需要 | 异常设备需要 | 标准 HCE-F PMm；异常设备可向厂商 NFC HAL 注入 `libaimesim_pmm.so` |

LSPosed 模块的静态作用域只有 `com.android.nfc`。不需要、也不应勾选“系统框架”。它负责放宽 NFCID2 / System Code 的格式校验，并为 Android 14 及以下系统加载旧版 PMm Hook。

Android 15+ 的 PMm 路径具有设备相关性：

- 当前只构建 `arm64-v8a`。
- 注入器会寻找名称以 `android.hardware.nfc-service` 开头的 HAL 进程。
- PMm 数据改写目前针对已测试的 ST NFC HAL 符号和数据包格式。
- NXP、其他厂商 HAL 或经过大幅修改的系统即使进程名称能够被发现，也可能因为没有匹配符号而无法启用补丁。

## 安装

### 1. 安装 APK

安装构建得到的 `app-debug.apk`，或从 [GitHub Releases](https://github.com/UmiSlat/AimeSimulator/releases) 下载版本化 APK。更新安装会保留本应用包名下已经保存的配置；卸载应用通常会清除这些本地数据。

应用包名为：

```text
io.github.umislat.aimesimulator
```

### 2. 首次运行

1. 保持 LSPosed、PMm 补丁和 KernelSU 模块关闭。
2. 添加或读取一张卡片配置并选中它。
3. 在系统提示中确认默认 NFC 应用。
4. 先用正常模式测试；若系统拒绝动态 IDm，再尝试兼容模式。
5. 只有状态正常但外部读卡仍失败时，再继续配置兼容组件。

### 3A. 配置 LSPosed（按需）

1. 确认使用支持现代 libxposed API 101 的 LSPosed 版本。
2. 在 LSPosed 中启用 AimeSimulator 模块。
3. 作用域只选择“应用”中的 `com.android.nfc`。
4. 不要选择系统框架。
5. 重启设备，或按照所用框架的要求重新加载 NFC 进程。

如果系统原生接受所用 NFCID2，并正确采用 APK 中的 `t3tPmm-filter`，可以不启用 LSPosed 或 PMm 补丁。是否需要兜底组件应以实际读卡结果为准。

### 3B. Android 14 及以下的 PMm 兜底补丁（按需）

1. 完成 APK 和 LSPosed 配置。
2. 打开 AimeSimulator，进入“设置”。
3. 打开“启用 PMm 补丁”，并向应用授予 Root 权限。
4. 此路径会设置 `tmp.aimesim.pmm.enabled`，然后重启 NFC 服务，使原生 Hook 在 `com.android.nfc` 中加载。

因此 Android 14 及以下切换 PMm 时，NFC 会短暂不可用；应用会等待 NFC Binder 恢复并重试 HCE-F 注册。

### 3C. Android 15 及以上的 PMm 兜底补丁（按需）

1. 在 KernelSU 管理器中安装 `aimesim-pmm-ksu-v3.zip`。
2. 安装完成后重启一次设备。模块首次安装时默认保持关闭。
3. 打开 AimeSimulator，进入“设置”，向应用授予 Root 权限。
4. 打开“启用 PMm 补丁”，等待状态变为 `active`。

模块会把 Hook 保持在当前 NFC HAL 进程中，并通过 `vendor.aimesim.pmm.enabled` 在运行时控制是否改写 PMm。正常开关补丁不会重启 NFC HAL，也不会重启 `com.android.nfc`；如果 HAL 因系统原因自行重启，后台服务会检测新 PID 并重新注入。

## 使用方法

### 添加卡片配置

在主界面选择“手动添加”，填写：

| 字段 | 长度 | 说明 |
| --- | --- | --- |
| 配置名称 | 不限 | 留空时会根据 IDm 末四位自动生成 |
| Access Code | 20 位十进制数字 | 可选，记录卡面印刷号码，仅用于本地显示 |
| IDm | 16 个十六进制字符 | 必填，表示 8 字节 IDm |
| S_PAD0 | 32 个十六进制字符 | 可选，表示 16 字节块数据 |
| ID 块 | 32 个十六进制字符 | 可选，表示 16 字节块数据 |

输入会被规范化为大写十六进制；长度不正确或包含非十六进制字符时不会保存。

### 实体卡读取支持

| 卡片类型 | 手机侧技术 | 可读取内容 | 配置处理 |
| --- | --- | --- | --- |
| Amusement IC | NFC-F / FeliCa | IDm、System Code、加密 S_PAD0、ID 块、Access Code | 可直接带入编辑器 |
| 普通 FeliCa | NFC-F / FeliCa | IDm、System Code，并尝试读取 S_PAD0 与 ID 块 | 可创建配置，缺失块保持可选 |
| 旧式 Aime | NFC-A / MIFARE Classic | UID、Access Code | 需在保存前填写对应的 FeliCa IDm |

MIFARE Classic 能否读取还取决于手机 NFC 控制器和 Android 驱动。检测到 NFC-A 不代表设备一定提供 `MifareClassic` 接口。

### 读取实体卡

1. 在主界面选择“读取卡片”。
2. 将 FeliCa、Amusement IC 或旧式 MIFARE Aime 卡保持在手机 NFC 天线附近。
3. 应用自动判断卡片协议并读取可用字段。
4. Amusement IC 或旧式 Aime 识别成功后，点击“用于卡片配置”进入编辑器。
5. 检查自动填入的内容并保存。

- Amusement IC：从服务 `000B` 读取 Block `00` 与 `82`，解密 SPAD0 并提取 Access Code。
- 普通 FeliCa：读取 IDm、System Code、S_PAD0 与 ID 块；组合读取失败时分别重试。
- 旧式 Aime：使用 Key B 认证 MIFARE 扇区 0，从 Block 2 提取 Access Code；保存前仍需填写对应的 FeliCa IDm。

只有点击编辑器的“保存”后才会写入本地配置，读取流程不会修改实体卡。

### 选择并模拟

1. 点击某张配置卡片，使其成为当前配置。
2. 根据读卡端兼容性选择正常模式或兼容模式。
3. 确认状态栏显示“已准备模拟 …”。
4. 保持应用位于前台，将手机 NFC 天线靠近读卡器。

应用离开前台时会停用前台 HCE-F 服务；重新返回应用后会自动注册当前选择的配置。

### 默认 NFC 应用检查

进入主界面时，应用会检查 AimeSimulator 是否为 Android 当前的默认 NFC 支付应用。如果不是，系统会显示默认应用更改确认界面。只有用户在 Android 系统界面中确认后才会更改默认项；应用不会绕过系统授权直接修改。

为参与系统默认 NFC 应用选择，APK 声明了一个支付类别的最小 HCE-A 辅助服务和固定 AID `F0010203040506`。该服务不承担 Aime/FeliCa 模拟，收到 APDU 时只返回“不支持的指令”；实际卡片模拟仍完全由 HCE-F 服务完成。

选择本应用会替换设备原有的默认 NFC 支付应用，可能影响手机钱包的碰一碰支付。需要恢复时，请在 Android 的“默认应用”“非接触式付款”或钱包设置中重新选择原应用。一次应用会话中只请求一次，取消系统确认不会循环弹窗。

### 正常模式与兼容模式

| 模式 | 向 Android 注册的 NFCID2 | 模拟数据中的卡片 IDm | 适用场景 |
| --- | --- | --- | --- |
| 正常模式 | 当前配置的 IDm | 当前配置的 IDm | 系统和读卡端均接受动态 IDm |
| 兼容模式 | 固定为 `02FE001145141919` | 仍使用当前配置的 IDm | 系统或读卡端不接受动态卡号 |

兼容模式不会修改已经保存的配置，也不会改变 PMm 开关。它只改变交给 Android NFC 路由层的 NFCID2。

### 外观设置

“设置 → 外观”提供以下选项：

- **主题模式**：跟随系统、浅色或深色。默认跟随 Android 系统设置。
- **使用动态取色**：Android 12 及以上默认开启，颜色由系统根据壁纸生成；关闭后使用应用自带的 Material 3 配色。Android 11 及以下不支持动态取色，开关会显示为不可用。

外观选项会保存在本机，修改后立即应用，不影响卡片配置、HCE-F 状态或 PMm 补丁。

## 模拟协议行为

当前实现使用以下固定参数：

| 项目 | 值 |
| --- | --- |
| System Code | `88B4` |
| FeliCa Lite 只读服务 | `000B`（数据包中按小端序编码） |
| PMm 兼容值 | `00F1000000014300` |
| 兼容模式 NFCID2 | `02FE001145141919` |

PMm 通过 `host_nfcf_service.xml` 中的标准 `<t3tPmm-filter>` 静态声明。未声明该字段时，Android 默认使用 `FFFFFFFFFFFFFFFF`；本项目的 Root/Hook 补丁只负责兼容会忽略或覆盖标准声明的厂商 NFC 栈。

HCE-F 服务会：

- 校验帧长度后处理 Read Without Encryption（命令 `06`）。
- 返回命令 `07`、请求中的 NFCID2、两个零状态字节、块数量和对应块数据。
- 对 Write Without Encryption（命令 `08`）返回确认命令 `09`，但不会修改本地配置。
- 对不支持的命令返回兼容响应 `04 11 45 14`。
- 忽略无法安全解析的畸形帧。

默认卡片映像包含零填充的用户块、全 `FF` 的块 `0E`、块 `82` 中的配置 IDm、块 `83` 中的 PMm 元数据、块 `85` 中的 System Code，以及块 `86` 和 `88` 的固定兼容数据。配置中保存的 S_PAD0 和 ID 块会覆盖相应的默认块。

更精确的行为约定见 [`docs/FUNCTIONAL_SPEC.md`](docs/FUNCTIONAL_SPEC.md)。

## PMm 状态说明

Android 15+ 的模块将状态保存在 `/data/adb/aimesim_pmm/`。设置页可能显示：

| 状态 | 含义 |
| --- | --- |
| `disabled` | 用户已关闭补丁 |
| `waiting` | 已请求启用，正在等待 NFC HAL |
| `injecting` | 正在向当前 HAL 进程注入 |
| `active` | Hook 已加载，运行时 PMm 开关已启用 |
| `error` | Root、文件、属性、符号解析或注入过程失败 |

`active` 表示 Hook 已在目标进程中工作，不等同于某个外部读卡器已经验证通过。最终输出仍建议使用实际读卡器确认。

## 常见问题

### 不使用 Root 能否工作

可以先使用系统原生路径。设备需要支持 HCE-F，并接受 APK 声明的 System Code 和 PMm；动态 IDm 被限制时可尝试兼容模式。Root、LSPosed 和 KernelSU 都是异常设备的兼容方案，不是配置管理、实体卡读取或标准 HCE-F 模拟的前置条件。

实验版状态页会把失败分成动态 IDm、固定兼容 IDm、System Code `88B4` 和前台服务启用几类。请在关闭所有 Hook/Root 兼容组件后点击“检测 88B4”：只有此时 `88B4 注册已通过` 才能作为无 Root 注册通过的证据。

若 `88B4` 被拒绝，可以点击“测试 4000 通用 HCE-F”。成功后应用会显式启用固定兼容 IDm `02FE001145141919`、System Code `4000` 和声明的 PMm，供 HINATA 等通用工具核对手机实际暴露的数据。该模式只用于区分本应用与厂商 Beam/共享等其他 NFC-F 端点，不表示 AIME 读卡器能够发现；再次点击“检测 88B4”或离开应用会结束该诊断路径。

`2.2.8` 还提供“测试静态 88B4”：独立 `HostNfcFService` 在 XML 中直接声明固定 IDm `02FE001145141919`、System Code `88B4` 和 PMm，不调用动态 System Code 注册接口。应用会先读取 Android 解析后保留的 IDm 与 System Code；只有两者仍然正确且前台服务启用成功时，才提示使用 HINATA 实测。若解析阶段已经移除或替换 `88B4`，说明该 ROM 的静态与动态路径都受到相同限制。

### 实体卡靠近后没有反应

- 确认 Android NFC 已开启，并将卡片移开后重新贴近手机天线位置。
- 确认应用停留在“读取卡片”页面；同一张卡持续贴住时 Android 通常不会重复触发发现回调。
- Amusement IC 和普通 FeliCa 需要 NFC-F；旧式 Aime 需要手机驱动提供 MIFARE Classic。
- 取下手机壳或避免同时贴放多张 NFC 卡，以排除耦合距离和冲突问题。

### 检测到 Amusement IC，但 Block 0 读取失败

应用会使用服务 `000B` 读取 Block `00`，组合读取失败时再单独重试。请保持卡片静止并重新贴卡；如果持续失败，应记录手机型号、Android 版本和 NFC 日志。并非所有外观相似的 FeliCa 卡都符合 Amusement IC 的 IDm、PMm、System Code 和 SPAD0 数据格式。

### 为什么旧式 Aime 识别后仍需填写 FeliCa IDm

旧式 Aime 是 MIFARE Classic 卡，只能提供其 UID 和 Access Code；AimeSimulator 的模拟端使用 Android HCE-F，需要 8 字节 FeliCa IDm。应用不会把 MIFARE UID 伪装成 IDm，因此保存前必须由用户提供对应的 FeliCa 配置。

### 手机检测到 NFC-A，但无法读取 MIFARE Classic

部分手机 NFC 控制器或厂商驱动不公开 Android `MifareClassic` 接口。这种限制无法通过应用层密钥或重试修复，可改用支持 MIFARE Classic 的设备读取。

### 系统提示“不支持 NFC-F 主机卡模拟”

设备必须由系统声明 `android.hardware.nfc.hcef`。拥有普通 NFC 或能够读取 FeliCa 卡，不代表设备一定支持 HCE-F；应用无法通过 Root 或 Hook 补出缺失的硬件/系统实现。

### PMm 开关不可用或状态检查失败

- Android 15+：确认 KernelSU 模块已经安装并在安装后重启过一次。
- 确认 Root 管理器已经向 AimeSimulator 授权。
- 确认模块路径为 `/data/adb/modules/aimesim_pmm/`。
- 检查是否有其他 NFC 模块同时修改相同 HAL 或系统属性。

### LSPosed 中没有“推荐应用”或系统框架选项

这是预期行为。AimeSimulator 使用静态作用域，唯一作用域是 `com.android.nfc`；不包含系统框架。

### 开关 PMm 后 NFC 暂时不可用

- Android 14 及以下路径会主动重启 NFC 服务，这是旧版 Hook 加载方式的一部分。
- Android 15+ 当前实现只切换运行时属性，不应因正常开关而重启 HAL 或 NFC 应用。若仍发生重启，应检查模块状态和系统日志。

### 关闭补丁后仍看到 `00F1000000014300`

关闭补丁后，Hook 会将新的 CORE_SET_CONFIG 数据包原样传递给 HAL。读卡端仍看到相同值时，可能是厂商默认值、尚未发生新的 NFC-F 配置、或读卡端缓存；这时应结合 HAL 日志和重新激活后的实测判断，而不是只看 Android 框架中的缓存字段。

## 调试

下面的命令需要 Android Platform Tools；涉及模块状态的命令还需要 Root：

```bash
adb logcat -s AimeSimulator AimePmmPatch
adb shell su -c '/data/adb/modules/aimesim_pmm/service.sh status'
adb shell getprop vendor.aimesim.pmm.enabled
adb shell getprop tmp.aimesim.pmm.enabled
adb shell dumpsys nfc
```

Android 15+ 中，开启补丁后日志应出现类似 `patched ST HAL CORE_SET_CONFIG PMm`；关闭后应出现 `PMm patch disabled; passing through ST HAL CORE_SET_CONFIG`。日志可能包含设备实现细节，提交问题前请先移除卡号、序列号和其他敏感信息。

## 从源码构建

### 环境要求

- JDK 17
- Android SDK 34
- Android NDK（arm64 工具链）
- CMake 3.22.1
- Python 3

首次构建需要从 Google Maven 和 Maven Central 下载 AndroidX、Material Components、libxposed API 与 Dobby 等依赖。

### 构建与检查

Linux / macOS：

```bash
./gradlew clean testDebugUnitTest lintDebug assembleDebug
python3 tools/package_module.py
python3 tools/check_artifacts.py
```

Windows PowerShell：

```powershell
.\gradlew.bat clean testDebugUnitTest lintDebug assembleDebug
python tools\package_module.py
python tools\check_artifacts.py
```

生成文件：

- APK：`app/build/outputs/apk/debug/app-debug.apk`
- 当前稳定版本化 APK：`dist/AimeSimulator-2.2.5-debug.apk`
- 无 Root 静态探针 APK：`dist/AimeSimulator-2.2.8-static-88b4-probe-debug.apk`
- KernelSU 模块：`dist/aimesim-pmm-ksu-v3.zip`

`tools/check_artifacts.py` 会检查 APK 中的 libxposed API 101 元数据、静态作用域和 arm64 原生库，同时检查 KernelSU ZIP 的必要文件是否完整。版本化 APK 是发布交付副本；Gradle 默认仍输出 `app-debug.apk`。

## 项目结构

```text
app/
├─ src/main/java/.../data/   卡片配置与本地存储
├─ src/main/java/.../nfc/    HCE-F 会话、协议编解码和实体卡读取
├─ src/main/java/.../hook/   libxposed API 101 入口
├─ src/main/java/.../root/   PMm 状态与 Root 命令封装
├─ src/main/java/.../ui/     原生 Android 界面
└─ src/main/cpp/             PMm Hook 与 Android 15+ HAL 注入器
ksu-module/                  KernelSU 模块脚本和元数据
tools/                       模块打包与产物校验工具
docs/FUNCTIONAL_SPEC.md      可观察行为与协议约定
THIRD_PARTY_NOTICES.md       第三方依赖及其许可证
```

## 数据与隐私

- 卡片配置保存在应用私有目录的 `SharedPreferences` 中。
- Android 备份与数据提取已关闭。
- IDm 与卡面 Access Code 均有独立的显示开关；隐藏只影响界面，不会删除配置数据。
- 应用没有网络权限，不会主动上传卡片配置。
- Root、LSPosed 与 KernelSU 本身具有较高权限；请从可信来源安装并自行评估系统风险。

## 实现参考、版权与第三方组件

AIC 卡片指纹、FeliCa Block 0 读取和 SPAD0 变换行为参考并核对了 [Project-HINATA/hinata_go](https://github.com/Project-HINATA/hinata_go/tree/c56d8badc3a720e0ba9e2f721f3f73111f2f6d97) 的公开实现。项目不包含该应用的预编译二进制文件；具体参考范围和许可注意事项见 [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)。

Dobby、libxposed API、AndroidX、Material Components、Kotlin 和测试依赖分别遵循其自身许可证。

当前仓库未附带授予复制、修改或再发布项目自有代码权利的开源许可证；除第三方组件另有声明外，默认保留相应权利。

Aime 及相关名称和标识属于其各自权利人。本项目是非官方兼容性与研究工具，与相关厂商或运营方不存在隶属或背书关系。

## English summary

AimeSimulator is an Android HCE-F / FeliCa Lite profile manager and simulator. The 2.2.8 rootless experiment adds status-page checks for the actual NFCID2, System Code 88B4, and foreground-service registration result, plus explicit generic 4000 and static XML 88B4 probes for identifying the ROM validation stage. Version 2.2.5 remains the stable physical-card reader delivery. See the Chinese sections above for requirements, limitations, implementation references, and safety notes.
