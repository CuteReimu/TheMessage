# FengSheng（风声）项目 - Copilot 编码指南

## 项目概述

本项目是卡牌游戏《风声》的服务端实现，使用Kotlin开发。这是一个基于WebSocket的多人在线卡牌游戏服务器，支持AI机器人对战。

- **主要语言**: Kotlin (约21000行代码，329个Kotlin文件)
- **Java版本**: JDK 17 (Temurin)
- **构建工具**: Gradle 8.7
- **框架**: Akka Actor、Netty、Protobuf
- **项目类型**: 服务端应用程序
- **主要功能**: 游戏逻辑、网络通信、AI机器人、统计排行、GM命令管理

**重要说明**:
- **本项目维护者主要是简体中文使用者，所有Issue、PR、评审和回复都应使用简体中文**
- 项目采用AGPLv3协议开源
- 详细游戏规则和文档位于: https://github.com/CuteReimu/fengsheng-doc (gh-pages分支)

## 编程风格特点

**关键要点**:
1. **函数式编程优先**: 大部分业务逻辑采用函数式编程风格，但部分"遗留代码"仍保留OOP习惯
2. **上下文敏感**: 阅读和修改代码时需要特别注意函数调用的上下文，理解函数式和OOP代码的混合使用
3. **AI逻辑**: 游戏AI采用传统硬编码逻辑，而非机器学习
4. **TODO标记**: 代码中存在TODO注释，标记了复杂且暂时未实现的逻辑（特别是AI相关），大概率不会实现

## 构建和验证步骤

### 环境要求

- **JDK 17** (必须，项目编译目标为JVM 17)
- **Gradle 8.7** (通过gradlew自动管理)
- **内存**: 至少4GB可用内存 (Gradle配置为-Xmx4g)

### 完整构建流程（按顺序执行）

#### 1. 清理项目
```bash
./gradlew clean
```
- 耗时: 约1秒
- 用途: 删除build目录，确保全新构建

#### 2. 运行测试
```bash
./gradlew test
```
- **首次编译耗时: 约100-110秒**
- 包含: Protobuf代码生成、Kotlin编译、Java编译、测试执行
- **必须通过**: 所有测试必须成功才能继续

#### 3. 代码格式检查
```bash
./gradlew ktlintCheck
```
- 耗时: 约1-2秒（已编译后）
- **必须通过**: 代码必须符合ktlint规范
- 使用IntelliJ IDEA代码风格，最大行长128

#### 4. 自动格式化代码
```bash
./gradlew ktlintFormat
```
- 耗时: 约14秒
- **重要**: 在提交前运行此命令自动修复格式问题
- **注意**: format.yml工作流会自动格式化PR代码并提交

#### 5. 完整构建
```bash
./gradlew build
```
- 耗时: 约27秒（已测试后）
- 输出: `build/libs/fengsheng-1.0-SNAPSHOT.jar` (约38MB)
- 包含: 编译、测试、格式检查、打包

### 运行和调试

#### 开发调试（不推荐生产环境）
```bash
./gradlew run
```
- **现象**: 执行后会停在88%左右，显示"> :run"是正常的
- **说明**: 此时服务已启动并监听端口，100%意味着运行结束
- 用途: 本地调试，方便IDE断点，但内存占用大

#### 生产部署
```bash
# 1. 编译
./gradlew build

# 2. 进入输出目录
cd build/libs

# 3. 运行jar包
java -jar fengsheng-1.0-SNAPSHOT.jar
```

### 常见构建问题和解决方案

#### 问题1: Gradle下载慢
修改 `gradle/wrapper/gradle-wrapper.properties`:
```properties
distributionUrl=https\://mirrors.cloud.tencent.com/gradle/gradle-8.7-bin.zip
```

#### 问题2: Maven依赖下载慢
修改 `build.gradle.kts`，在repositories块添加:
```kotlin
repositories {
    maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
    mavenCentral()
}
```

#### 问题3: IDEA"代码洞察功能不可用"
原因: role.proto生成的文件太大
解决: 编辑IDEA的 `idea.properties`:
```properties
idea.max.intellisense.filesize=2500
idea.max.content.load.filesize=20000
```

## 项目结构

### 根目录文件

```
.editorconfig           # EditorConfig配置（ktlint规则）
.gitignore             # Git忽略文件（包含*.dat, *.csv, build/, .idea/等）
LICENSE                # AGPLv3许可证
README.md              # 项目说明文档
build.gradle.kts       # Gradle构建配置（Kotlin DSL）
settings.gradle.kts    # Gradle项目设置
gradle.properties      # Gradle属性（kotlin.daemon.jvmargs=-Xmx4g）
gradlew / gradlew.bat  # Gradle Wrapper脚本
```

### 源代码结构

```
src/main/
├── kotlin/
│   ├── Game.kt                    # 游戏主类（游戏实例管理）
│   ├── Config.kt                  # 配置管理
│   ├── Player.kt                  # 玩家基类
│   ├── HumanPlayer.kt             # 人类玩家实现
│   ├── RobotPlayer.kt             # AI机器人实现
│   ├── GameExecutor.kt            # 游戏执行器（Akka Actor）
│   ├── Statistics.kt              # 统计数据管理
│   ├── ScoreFactory.kt            # 积分计算
│   ├── QQPusher.kt                # QQ消息推送（OneBot协议）
│   ├── Recorder.kt                # 录像功能
│   ├── Image.kt                   # 图片生成（排行榜、胜率）
│   ├── Fsm.kt                     # 有限状态机基类
│   ├── ProcessFsm.kt              # 处理流程状态机
│   ├── WaitingFsm.kt              # 等待状态机
│   ├── card/                      # 卡牌实现（16个文件）
│   │   ├── Card.kt                # 卡牌基类
│   │   ├── Deck.kt                # 牌堆
│   │   ├── ChengQing.kt           # 澄清卡
│   │   ├── MiLing.kt              # 密令卡
│   │   └── ... (其他卡牌类型)
│   ├── skill/                     # 技能实现（约200+个文件）
│   │   ├── AbstractSkill.kt       # 技能基类
│   │   ├── ChengFu.kt             # 城府技能
│   │   └── ... (各角色技能实现)
│   ├── phase/                     # 游戏阶段实现（21个文件）
│   │   ├── DrawPhase.kt           # 摸牌阶段
│   │   ├── SendPhaseStart.kt      # 传递阶段开始
│   │   ├── FightPhaseIdle.kt      # 争夺阶段空闲
│   │   ├── ReceivePhaseIdle.kt    # 接收阶段空闲
│   │   ├── NextTurn.kt            # 下一回合
│   │   └── ... (其他阶段)
│   ├── handler/                   # 协议处理器（50+个文件）
│   │   ├── ProtoHandler.kt        # 处理器接口
│   │   ├── JoinRoomTos.kt         # 加入房间
│   │   └── ... (各种操作处理)
│   ├── network/                   # 网络层（8个文件）
│   │   ├── Network.kt             # 网络管理
│   │   ├── WebSocketServerInitializer.kt
│   │   ├── ProtoServerInitializer.kt
│   │   └── ... (HTTP、WebSocket处理)
│   └── gm/                        # GM命令（25个文件）
│       ├── Init.kt                # GM命令初始化
│       ├── Addcard.kt             # 添加卡牌
│       ├── Addrobot.kt            # 添加机器人
│       └── ... (其他GM命令)
├── proto/                         # Protobuf定义（5个文件）
│   ├── fengsheng.proto            # 主协议
│   ├── role.proto                 # 角色定义（文件很大）
│   ├── common.proto               # 通用定义
│   ├── errcode.proto              # 错误码
│   └── record.proto               # 录像协议
└── resources/
    └── log4j2.xml                 # 日志配置

src/test/kotlin/
└── Test.kt                        # 测试用例

doc/
├── README.md                      # 游戏FAQ 2.0
└── Skill.md                       # 技能说明
```

### 关键架构组件

1. **Game类** (`Game.kt`): 游戏实例核心，管理玩家、牌堆、状态机、回合等
2. **Fsm状态机**: 使用有限状态机模式管理游戏流程
3. **Phase阶段**: 游戏分为摸牌、传递、争夺、接收等阶段
4. **Skill技能系统**: 每个角色技能独立实现
5. **Akka Actor**: 使用Actor模型处理并发游戏实例
6. **Protobuf通信**: 客户端-服务端使用Protobuf序列化

## GitHub Actions工作流

### 1. Build工作流 (`.github/workflows/build.yml`)

**触发条件**: 推送到kotlin分支或PR到kotlin分支

**执行步骤**:
1. 设置JDK 17 (Temurin发行版)
2. 设置Gradle
3. 执行 `./gradlew test` (GRADLE_OPTS='-Xmx4g')
4. 执行 `./gradlew build` (GRADLE_OPTS='-Xmx4g')

**重要**: 本地必须确保这两个命令都能成功执行

### 2. Format工作流 (`.github/workflows/format.yml`)

**触发条件**: 推送到kotlin分支或PR到kotlin分支

**执行步骤**:
1. 自动运行 `./gradlew ktlintFormat`
2. 如有格式变更，自动提交到PR分支
3. **注意**: 这是自动化流程，会自动修复格式问题

**最佳实践**: 提交前手动运行 `./gradlew ktlintFormat` 避免额外提交

### 3. Sync Proto工作流 (`.github/workflows/sync-proto.yml`)

用于同步Protobuf定义文件

## 代码规范和最佳实践

### ktlint配置 (`.editorconfig`)

```
- 代码风格: intellij_idea
- 最大行长: 128字符
- 禁用规则: 通配符导入、参数列表换行、多行if-else、尾随逗号等
```

### 编码建议

1. **遵循函数式编程**: 优先使用不可变数据和纯函数
2. **注意混合风格**: 理解哪些代码是函数式，哪些是OOP
3. **使用扩展函数**: Kotlin扩展函数广泛使用
4. **Protobuf集成**: 理解生成的protobuf代码使用方式
5. **并发安全**: 游戏实例通过Actor模型隔离，注意共享状态
6. **TODO处理**: 遇到TODO注释，特别是AI相关的，不要尝试实现复杂逻辑

### 修改代码前

1. **理解游戏规则**: 参考 `doc/README.md` 或 https://cutereimu.github.io/fengsheng-doc/
2. **查看相关技能**: `doc/Skill.md` 包含技能说明
3. **测试先行**: 修改后必须运行测试
4. **格式检查**: 修改后运行 `./gradlew ktlintFormat`

## 配置文件

### application.properties（运行时生成）

首次运行 `./gradlew run` 会在根目录生成此文件，包含:
- 文件服务器端口: 9091
- WebSocket监听端口: 9091
- GM命令端口: 9092
- 游戏规则配置（摸牌数、读条时间等）
- QQ推送配置（OneBot协议）

**注意**: 运行时可能自动更新此文件，不要在运行时手动修改

### log4j2配置

位置: `src/main/resources/log4j2.xml`

## 数据文件（运行时生成）

以下文件在`.gitignore`中被忽略:
- `*.dat`: 游戏数据文件
- `*.csv`: 统计数据（如PlayerInfo.csv）
- `records/`: 录像文件目录
- `application.properties`: 配置文件

## GM命令系统

通过HTTP GET访问 `http://127.0.0.1:9092/endpoint?param=value`

**常用命令**: addcard, addrobot, getscore, ranklist, winrate, resetseason等

详见README.md的GM命令表格

## 验证清单

修改代码后，按以下顺序验证:

1. ✅ 运行 `./gradlew clean` 清理
2. ✅ 运行 `./gradlew test` 确保测试通过（约100秒）
3. ✅ 运行 `./gradlew ktlintFormat` 自动格式化（约14秒）
4. ✅ 运行 `./gradlew build` 完整构建（约27秒）
5. ✅ 检查 `build/libs/fengsheng-1.0-SNAPSHOT.jar` 生成成功
6. ✅ （可选）运行 `./gradlew run` 验证服务启动

## 特别提醒

1. **信任本文档**: 除非发现文档错误或信息不完整，否则请直接遵循本文档指导，减少不必要的探索
2. **使用简体中文**: 所有交流、Issue、PR、代码注释请使用简体中文
3. **格式自动化**: format.yml会自动格式化代码，但建议提交前手动运行ktlintFormat
4. **内存要求**: Gradle配置需要4GB内存，确保环境满足要求
5. **Protobuf重要性**: role.proto文件很大，修改protobuf定义需要重新生成代码
6. **测试时间**: 首次测试需要约100秒，这是正常的，包含了代码生成和编译
7. **不要删除TODO**: 代码中的TODO标记了已知但暂不实现的复杂逻辑，保留它们

---

**文档版本**: 1.0
**最后更新**: 2025-11-09
**适用分支**: kotlin
