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

## 核心业务逻辑

### 关键类和文件说明

#### Game.kt (531行) - 游戏实例核心
游戏的主控制类，管理整个游戏的生命周期和状态：
- **职责**: 管理玩家列表、牌堆、回合状态、游戏阶段转换
- **核心属性**:
  - `players`: 玩家列表
  - `deck`: 牌堆管理
  - `fsm`: 当前游戏阶段的状态机
  - `turn`/`realTurn`: 回合计数
  - `isEarly`: 判断是否为游戏早期（影响某些卡牌使用）
- **核心方法**:
  - `onPlayerJoinRoom()`: 玩家加入房间的处理
  - `start()`: 游戏开始，分配身份和角色
  - `resolve()`: 事件解析和状态机推进
  - `addEvent()`/`continueResolveEvent()`: 事件队列管理
- **重要机制**: 使用Akka Actor模型实现并发游戏实例隔离

#### Player.kt (351行) - 玩家基类
所有玩家的抽象基类，定义了玩家的基本属性和行为：
- **核心属性**:
  - `cards`: 手牌列表
  - `messageCards`: 情报区的情报牌
  - `identity`: 玩家身份（红色/蓝色/黑色）
  - `secretTask`: 神秘人任务（杀手/簒夺者/CP等）
  - `roleSkillsData`: 角色和技能数据
  - `alive`/`lose`: 存活和失败状态
- **系数机制**: `coefficientA`和`coefficientB`用于AI决策的随机性调整
- **技能系统集成**: 
  - `skills`: 角色技能列表
  - `findSkill()`: 查找特定技能
  - `addSkill()`/`deleteSkill()`: 动态增删技能
- **卡牌操作**: 
  - `deleteCard()`/`findCard()`: 手牌管理
  - `deleteMessageCard()`/`findMessageCard()`: 情报区管理

#### HumanPlayer.kt (533行) - 真人玩家实现
继承自Player，处理真实玩家的网络通信和交互：
- **网络层**: 通过Netty的Channel进行通信
- **协议发送**: 
  - `send(message)`: 发送Protobuf消息给客户端
  - `sendErrorMessage()`: 发送错误提示
- **超时机制**: 
  - `timeout`: 操作超时计时器
  - `timeoutCount`: 超时次数统计
  - 超时后自动托管（转为AI控制）
- **录像功能**: `recorder`记录游戏过程用于回放
- **托管系统**: `autoPlay`标志，超时后自动使用AI逻辑
- **限流保护**: `limiter`防止客户端恶意刷消息
- **重连处理**: `isReconnecting`标志，支持断线重连

#### RobotPlayer.kt (535行) - AI机器人实现
继承自Player，实现游戏AI逻辑（硬编码规则，非机器学习）：
- **AI决策架构**: 使用多个AI决策表
  - `aiMainPhase`: 出牌阶段决策
  - `aiSendPhaseStart`: 传递阶段决策
  - `aiFightPhase`: 争夺阶段决策
  - `aiSkillMainPhase1`/`aiSkillMainPhase2`: 技能使用决策
- **决策流程**:
  1. 出牌阶段：优先考虑【风云变幻】，然后按卡牌优先级使用
  2. 传递阶段：评估所有可能的传牌方案，选择价值最高的
  3. 争夺阶段：根据情报价值和游戏局势决定是否争夺
- **价值评估**: 调用`MessageCardValue.kt`中的函数计算局面价值
- **手牌保护**: 有【急送】技能时更保守地使用手牌
- **技能使用**: 不同阶段有不同的技能触发优先级
- **卡牌排序**: `sortCards()`按身份对手牌排序，优化出牌选择

#### MessageCardValue.kt (1007行) - 情报价值计算
AI决策的核心，计算情报牌对各玩家的价值：
- **核心函数**:
  - `calculateMessageCardValue()`: 计算某张情报对玩家的价值
  - `willWin()`: 判断玩家是否会获胜
  - `willDie()`: 判断玩家是否会死亡
  - `anyoneWillWinOrDie()`: 判断是否有人会赢或死
- **价值计算逻辑**:
  - 基础价值：根据身份和情报颜色（红/蓝/黑）
  - 胜利接近度：接近胜利条件的情报价值更高
  - 死亡风险：黑色情报导致死亡的情况需要特别计算
  - 身份推测：根据玩家行为推测其身份，影响价值判断
- **胜利条件判断**:
  - 镇压者：需要3张同色（红或蓝）情报
  - 神秘人：不同任务有不同胜利条件（杀手、簒夺者、CP等）
  - 搅局者：需要特定的情报配置
- **TODO标记**: 包含了一些复杂的CP胜利条件代码（已注释），暂不实现
- **AI权重**: 价值计算考虑了`coefficientA`和`coefficientB`参数增加随机性

### 游戏架构

#### 状态机模式 (Fsm.kt)
游戏使用状态机模式管理游戏流程：
- **Fsm接口**: 定义了`resolve()`方法，返回`ResolveResult`或`null`
- **ProcessFsm**: 处理流程状态机，用于技能和卡牌效果的处理
- **WaitingFsm**: 等待状态机，用于等待玩家操作
- **事件队列**: Game类维护事件队列，顺序处理游戏事件

#### 游戏阶段 (phase包，21个文件)
游戏流程被分解为多个阶段，每个阶段是一个独立的状态机：
- **DrawPhase**: 摸牌阶段，回合开始时摸牌
- **MainPhaseIdle**: 出牌阶段，当前玩家可以使用手牌或技能
- **SendPhaseStart**: 传递阶段开始，必须选择情报传出
- **FightPhaseIdle**: 争夺阶段，所有玩家可以使用【截获】等卡牌
- **ReceivePhaseIdle**: 接收阶段，情报接收者可能触发技能
- **NextTurn**: 回合结束，切换到下一个玩家
- **OnReceiveCard**: 接收情报时的处理（触发技能、检查胜利/死亡）
- 其他阶段处理各种游戏事件（死亡结算、技能触发等）

#### 卡牌系统 (card包，16个文件)
- **Card.kt**: 卡牌基类，定义卡牌的基本属性（id、类型、颜色）
- **Deck.kt**: 牌堆管理，洗牌、摸牌、弃牌堆
- 具体卡牌类型：
  - **ChengQing** (澄清): 救濒死玩家
  - **MiLing** (密令): 指定其他玩家传出情报
  - **WeiBi** (威逼): 查看手牌并获得一张
  - **ShiTan** (试探): 猜测身份，猜错要弃牌
  - **JieHuo** (截获): 争夺阶段转移情报方向
  - **DiaoBao** (调包): 用手牌替换待收情报
  - **WuDao** (误导): 更改情报传递方向
  - 其他卡牌（破译、平衡、风云变幻等）

#### 技能系统 (skill包，200+个文件)
每个角色有1-3个技能，每个技能是一个独立的类：
- **AbstractSkill**: 技能基类，定义技能ID和所属玩家
- **ActiveSkill**: 主动技能接口，玩家可以选择是否发动
- **技能分类**:
  - 摸牌阶段技能（如【城府】、【伺机】）
  - 出牌阶段技能（如【锦囊】、【明察】）
  - 传递阶段技能（如【急送】、【密电】）
  - 争夺阶段技能（如【能吏】、【假意】）
  - 被动触发技能（如【忍耐】、【反间】）
- **技能命名**: 按中文技能名拼音命名（如ChengFu.kt对应城府）
- **技能实现**: 每个技能重写对应阶段的处理方法

### 协议和网络层

#### Protobuf协议 (proto包，5个文件)
- **fengsheng.proto**: 主协议，定义所有客户端-服务端消息
- **role.proto**: 角色定义（文件很大，200+个角色）
- **common.proto**: 通用枚举（颜色、方向、卡牌类型等）
- **errcode.proto**: 错误码定义
- **record.proto**: 录像回放协议

#### 网络层 (network包，8个文件)
- **Network.kt**: 网络管理器，初始化服务器
- **WebSocketServerInitializer**: WebSocket服务器初始化
- **ProtoServerInitializer**: Protobuf协议服务器初始化
- **HttpServerInitializer**: HTTP服务器（用于文件下载和GM命令）
- **各种ChannelHandler**: 处理网络消息的接收和发送

#### 协议处理器 (handler包，50+个文件)
每个客户端操作对应一个处理器：
- **ProtoHandler**: 处理器接口
- **JoinRoomTos**: 加入房间
- **SendMessageCardTos**: 传出情报
- **SkillXxxTos**: 各种技能的使用请求
- **命名规范**: `XxxTos`表示客户端发送的请求（To Server）

### 辅助系统

#### 统计系统 (Statistics.kt)
- 玩家战绩统计（胜率、场次、积分）
- 排行榜生成
- 数据持久化到CSV文件

#### 积分系统 (ScoreFactory.kt)
- 根据游戏结果计算积分变化
- 考虑玩家等级和对手强度

#### GM命令系统 (gm包，25个文件)
通过HTTP GET请求执行管理命令，用于测试和管理：
- **Addcard.kt**: 给玩家添加手牌
- **Addrobot.kt**: 添加AI机器人
- **Ranklist.kt**: 查看排行榜
- **Resetseason.kt**: 重置赛季数据
- 其他管理命令

#### 推送系统 (QQPusher.kt)
支持通过OneBot协议将游戏结果推送到QQ群（可选功能）。

#### 图片生成 (Image.kt)
生成排行榜和胜率统计的图片（可选功能）。

#### 录像系统 (Recorder.kt)
记录游戏过程，支持回放功能。

## 项目结构概览

```
src/main/kotlin/
├── Game.kt                    # 游戏主类（531行）
├── Player.kt                  # 玩家基类（351行）
├── HumanPlayer.kt             # 真人玩家（533行）
├── RobotPlayer.kt             # AI机器人（535行）
├── MessageCardValue.kt        # 情报价值计算（1007行）★核心AI逻辑
├── Fsm.kt/ProcessFsm.kt/WaitingFsm.kt  # 状态机
├── GameExecutor.kt            # Akka Actor执行器
├── Config.kt                  # 配置管理
├── Statistics.kt              # 统计系统
├── ScoreFactory.kt            # 积分计算
├── Recorder.kt                # 录像系统
├── QQPusher.kt                # QQ推送（可选）
├── Image.kt                   # 图片生成（可选）
├── phase/                     # 游戏阶段（21个文件）
│   ├── DrawPhase.kt           # 摸牌阶段
│   ├── MainPhaseIdle.kt       # 出牌阶段
│   ├── SendPhaseStart.kt      # 传递阶段
│   ├── FightPhaseIdle.kt      # 争夺阶段
│   ├── ReceivePhaseIdle.kt    # 接收阶段
│   ├── NextTurn.kt            # 回合结束
│   └── ... (其他阶段处理)
├── card/                      # 卡牌系统（16个文件）
│   ├── Card.kt                # 卡牌基类
│   ├── Deck.kt                # 牌堆管理
│   ├── ChengQing.kt           # 澄清
│   ├── MiLing.kt              # 密令
│   ├── WeiBi.kt               # 威逼
│   └── ... (其他卡牌)
├── skill/                     # 技能系统（200+个文件）
│   ├── AbstractSkill.kt       # 技能基类
│   ├── ChengFu.kt             # 城府技能
│   └── ... (各角色技能)
├── handler/                   # 协议处理器（50+个文件）
│   ├── ProtoHandler.kt        # 处理器接口
│   ├── JoinRoomTos.kt         # 加入房间
│   └── ... (各种操作处理)
├── network/                   # 网络层（8个文件）
│   ├── Network.kt             # 网络管理
│   └── ... (服务器初始化)
└── gm/                        # GM命令（25个文件）
    ├── Init.kt                # 命令初始化
    ├── Addcard.kt             # 添加卡牌
    └── ... (其他GM命令)

src/main/proto/                # Protobuf协议（5个文件）
├── fengsheng.proto            # 主协议
├── role.proto                 # 角色定义（文件很大）
├── common.proto               # 通用定义
├── errcode.proto              # 错误码
└── record.proto               # 录像协议

src/main/resources/
└── log4j2.xml                 # 日志配置

doc/
├── README.md                  # 游戏规则FAQ
└── Skill.md                   # 技能说明
```

## 构建和验证

### 快速开始
确保环境满足：JDK 17、4GB内存

```bash
# 构建项目（包含测试和代码检查）
./gradlew build

# 如遇Gradle下载慢，修改gradle/wrapper/gradle-wrapper.properties使用国内镜像
```

### 代码规范
- 使用ktlint，IntelliJ IDEA代码风格，最大行长128
- 提交前运行 `./gradlew ktlintFormat` 自动格式化
- `.editorconfig`文件包含详细规则配置

## 重要提醒

1. **信任本文档**: 除非发现文档错误或信息不完整，否则请直接遵循本文档指导
2. **使用简体中文**: 所有交流、Issue、PR、代码注释请使用简体中文
3. **理解业务逻辑**: 修改代码前先理解游戏规则和相关技能
4. **函数式与OOP混合**: 注意代码中函数式和面向对象风格的混用
5. **AI逻辑复杂**: MessageCardValue.kt和RobotPlayer.kt包含复杂的AI决策逻辑
6. **不要删除TODO**: 代码中的TODO标记了已知但暂不实现的复杂逻辑，保留它们
7. **测试通过**: 修改后确保 `./gradlew build` 通过

---

**文档版本**: 2.0
**最后更新**: 2025-11-09
**适用分支**: kotlin
