# AimeSimulator

AimeSimulator 是一个面向 Android 的 NFC-F / FeliCa Lite 卡片配置管理与 HCE-F 模拟工具。它可以在本机保存多张卡片配置，读取实体 NFC-F 卡的部分公开数据，并通过 Android `HostNfcFService` 将当前配置提供给读卡端。

本项目依据公开接口和可观察行为独立实现，不包含旧项目的源码或预编译二进制文件。

> [!WARNING]
> 本项目会使用 HCE-F、LSPosed Hook、Root 命令和厂商 NFC HAL 注入。请仅在自己拥有或获准测试的设备与卡片上使用，并提前准备可恢复系统的手段。项目不保证兼容任何具体商业设备或服务。

## 功能概览

- 管理多张本地卡片配置，可添加、编辑、删除和切换当前配置。
- 支持手动填写 IDm、S_PAD0 和 ID 块。
- 支持读取实体 NFC-F 卡，并尝试获取 IDm、S_PAD0 与 ID 块。
- 提供正常模式与兼容模式两种 NFCID2 路由方式。
- 进入应用时检查系统默认 NFC 支付应用；如果尚未选择本应用，则打开 Android 的系统确认界面。
- 响应 FeliCa Lite 的 Read Without Encryption 请求。
- 提供 PMm 兼容补丁：
  - Android 14 及以下：通过 libxposed API 101 和应用内原生 Hook 工作；
  - Android 15 及以上：通过独立 KernelSU 模块注入 NFC HAL。
- PMm 补丁启用时将对外配置替换为 `00F1000000014300`。
- 默认跟随系统浅色/深色主题，并允许在设置中手动选择浅色或深色。
- Android 12+ 默认使用 Material You 动态取色，可在设置中关闭。
- 应用不申请网络权限，不包含遥测或在线账户功能。

## 运行条件

### 基础模拟

- Android 10 或更高版本（最低 API 29）。
- 设备必须具有 NFC，并由系统声明支持 NFC-F 主机卡模拟（HCE-F）。
- 使用模拟时需要保持 NFC 开启，并让 AimeSimulator 位于前台。

仅安装 APK 就可以管理配置并尝试使用系统原生 HCE-F。若设备限制自定义 NFCID2，或需要 PMm 兼容值，还需要下方对应的 Hook/Root 环境。

### 兼容性组件

| 系统版本 | LSPosed API 101 | Root | KernelSU 模块 | PMm 实现 |
| --- | --- | --- | --- | --- |
| Android 10–14 | 建议启用 | 切换 PMm 时需要 | 不需要 | 在 `com.android.nfc` 中加载 `libpmm.so` |
| Android 15–16 | 建议启用 | 需要 | 需要 | 向厂商 NFC HAL 注入 `libaimesim_pmm.so` |

LSPosed 模块的静态作用域只有 `com.android.nfc`。不需要、也不应勾选“系统框架”。它负责放宽 NFCID2 / System Code 的格式校验，并为 Android 14 及以下系统加载旧版 PMm Hook。

Android 15+ 的 PMm 路径具有设备相关性：

- 当前只构建 `arm64-v8a`。
- 注入器会寻找名称以 `android.hardware.nfc-service` 开头的 HAL 进程。
- PMm 数据改写目前针对已测试的 ST NFC HAL 符号和数据包格式。
- NXP、其他厂商 HAL 或经过大幅修改的系统即使进程名称能够被发现，也可能因为没有匹配符号而无法启用补丁。

## 安装

### 1. 安装 APK

安装构建得到的 `app-debug.apk`，或安装项目发布页提供的 APK。更新安装会保留本应用包名下已经保存的配置；卸载应用通常会清除这些本地数据。

应用包名为：

```text
io.github.umislat.aimesimulator
```

### 2. 配置 LSPosed

1. 确认使用支持现代 libxposed API 101 的 LSPosed 版本。
2. 在 LSPosed 中启用 AimeSimulator 模块。
3. 作用域只选择“应用”中的 `com.android.nfc`。
4. 不要选择系统框架。
5. 重启设备，或按照所用框架的要求重新加载 NFC 进程。

如果只使用兼容模式且系统原生允许注册固定 NFCID2，部分设备可能无需 LSPosed；但这不代表 PMm 兼容补丁也能在没有对应组件时工作。

### 3A. Android 14 及以下的 PMm 补丁

1. 完成 APK 和 LSPosed 配置。
2. 打开 AimeSimulator，进入“设置”。
3. 打开“启用 PMm 补丁”，并向应用授予 Root 权限。
4. 此路径会设置 `tmp.aimesim.pmm.enabled`，然后重启 NFC 服务，使原生 Hook 在 `com.android.nfc` 中加载。

因此 Android 14 及以下切换 PMm 时，NFC 会短暂不可用；应用会等待 NFC Binder 恢复并重试 HCE-F 注册。

### 3B. Android 15 及以上的 PMm 补丁

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
| IDm | 16 个十六进制字符 | 必填，表示 8 字节 IDm |
| S_PAD0 | 32 个十六进制字符 | 可选，表示 16 字节块数据 |
| ID 块 | 32 个十六进制字符 | 可选，表示 16 字节块数据 |

输入会被规范化为大写十六进制；长度不正确或包含非十六进制字符时不会保存。

### 读取实体卡

1. 在主界面选择“读取卡片”。
2. 将实体 NFC-F 卡保持在手机 NFC 天线附近。
3. 应用先读取 IDm 和 System Code，再尝试从只读服务 `000B` 一次读取块 `00` 与块 `82`。
4. 如果组合读取失败，会分别重试两个块；即使只能取得 IDm，仍可创建配置。
5. 检查自动填入的内容并保存。

“读取卡片”只读取构建模拟配置所需的数据，不会向实体卡写入内容。

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
- KernelSU 模块：`dist/aimesim-pmm-ksu-v3.zip`

`tools/check_artifacts.py` 会检查 APK 中的 libxposed API 101 元数据、静态作用域和 arm64 原生库，同时检查 KernelSU ZIP 的必要文件是否完整。

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
- “隐藏 IDm”只控制界面显示，不会删除配置中的 IDm。
- 应用没有网络权限，不会主动上传卡片配置。
- Root、LSPosed 与 KernelSU 本身具有较高权限；请从可信来源安装并自行评估系统风险。

## 版权与第三方组件

项目自有代码是独立实现。Dobby、libxposed API、AndroidX、Material Components、Kotlin 和测试依赖分别遵循其自身许可证，详见 [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)。

当前仓库未附带授予复制、修改或再发布项目自有代码权利的开源许可证；除第三方组件另有声明外，默认保留相应权利。

Aime 及相关名称和标识属于其各自权利人。本项目是非官方兼容性与研究工具，与相关厂商或运营方不存在隶属或背书关系。

## English summary

AimeSimulator is an independent Android HCE-F / FeliCa Lite profile simulator. It supports local profiles, limited physical-card capture, normal and compatibility NFCID2 routing, libxposed API 101 integration, and an experimental arm64 KernelSU PMm patch for tested ST NFC HAL implementations on Android 15+. See the Chinese sections above for requirements, installation steps, limitations, and safety notes.
