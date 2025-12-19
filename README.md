# GeLab Agent (Android)

一个基于无障碍服务的移动端自动化 Agent。应用通过 LLM 的 Chat Completions 接口获取动作指令（JSON），再在设备上执行点击、滑动、输入、打开应用等操作，并在需要用户补充信息时通过悬浮层提示输入。

## 功能概览
- LLM 驱动的动作编排（tap / swipe / input / hotkey / wait 等）
- 无障碍服务执行手势与全局按键
- 悬浮层提示用户补充信息（验证码、确认等）
- 任务完成后通知提示
- 配置持久化（DataStore）

## 快速开始
1. 确保已安装 Android SDK 34 和 Java 17，并在 `local.properties` 配置 `sdk.dir`。
2. 构建并安装：
   ```bash
   ./gradlew assembleDebug
   ./gradlew installDebug
   ```
3. 在设备上打开应用，填写：
   - `API Base`：OpenAI 风格的 `chat/completions` 接口地址（见下方示例）。
   - `API Key`：接口访问密钥。
   - `Model`：模型名称（默认 `gpt-4o-mini`）。
   - `Instruction`：你希望 Agent 完成的任务描述。
4. 根据提示开启：悬浮窗权限、无障碍服务、通知权限。
5. 点击 `Start` 开始执行，应用会退到后台运行。

### API Base 示例
`AgentApi` 会自动补齐 `/v1/chat/completions`：
- `https://api.example.com/v1`
- `https://api.example.com/v1/chat/completions`
- `https://api.example.com`

## 权限说明
应用需要以下权限才能正常运行：
- `INTERNET`：访问 LLM API。
- `SYSTEM_ALERT_WINDOW`：显示悬浮输入提示。
- `POST_NOTIFICATIONS`：任务完成后通知提醒（Android 13+）。
- 无障碍服务：执行手势与全局按键动作。

## 主要模块
- `app/src/main/java/com/gelabzero/app/MainActivity.kt`：配置 UI 与状态展示。
- `app/src/main/java/com/gelabzero/app/agent/`：Agent 协议解析、循环控制、API 请求。
- `app/src/main/java/com/gelabzero/app/accessibility/`：无障碍服务与动作执行器。
- `app/src/main/java/com/gelabzero/app/overlay/`：悬浮层输入提示。
- `app/src/main/java/com/gelabzero/app/data/`：DataStore 配置持久化。
- `app/src/main/java/com/gelabzero/app/notifications/`：完成通知。

## Agent 协议简述
LLM 必须返回单个 JSON 对象（无额外文本），例如：
```json
{"type":"action","actions":[{"type":"tap","x":120,"y":300}]}
```
支持的动作类型与字段在 `app/src/main/java/com/gelabzero/app/agent/AgentProtocol.kt` 中定义。

## 开发与构建
- 构建 APK：`./gradlew assembleDebug`
- 安装到设备：`./gradlew installDebug`
- Lint：`./gradlew lint`

## 常见问题
- 启动后立刻报错 `Missing config`：请检查 API Base / Key / Instruction 是否填写。
- 无法执行动作：确认无障碍服务已启用，且悬浮窗权限已开启。
- 无通知：Android 13+ 需要手动授予通知权限。

## 备注
- 项目使用 Kotlin + Jetpack Compose，目标 JVM 17。
