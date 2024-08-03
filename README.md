# FengSheng

![](https://img.shields.io/github/languages/top/CuteReimu/TheMessage "语言")
![](https://img.shields.io/badge/java%20version-17-informational "Java 17")
[![](https://img.shields.io/github/actions/workflow/status/CuteReimu/TheMessage/build.yml?branch=kotlin)](https://github.com/CuteReimu/TheMessage/actions/workflows/build.yml "代码分析")
[![](https://img.shields.io/github/issues/CuteReimu/TheMessage)](https://github.com/CuteReimu/TheMessage/issues "issues")
[![](https://img.shields.io/github/contributors/CuteReimu/TheMessage)](https://github.com/CuteReimu/TheMessage/graphs/contributors "贡献者")
[![](https://img.shields.io/github/license/CuteReimu/TheMessage)](https://github.com/CuteReimu/TheMessage/blob/kotlin/LICENSE "许可协议")

## 声明

- **本项目采用`AGPLv3`协议开源，任何直接、间接接触本项目的软件也要求使用`AGPLv3`协议开源**
- **本项目仅用于学习和测试。不鼓励，不支持一切商业用途**
- **本项目的作者由所有参与的开发者共同所有。请尊重各位开发者的劳动成果**
- **在使用前，使用者应该对本项目有充分的了解。任何由于使用本项目提供的接口、文档等造成的不良影响和后果与任何开发者无关**
- 由于本项目的特殊性，可能随时停止开发或删档
- 本项目为开源项目，不接受任何的催单和索取行为

## 运行

```shell
./gradlew run
```

## 配置

**游戏配置**

第一次运行会在根目录下生成一个`application.properties`，如下：

```properties
# 文件服务器监听端口
file_server_port=9091
# 服务端监听端口
listen_websocket_port=9091
# 播放录像时最大间隔时间（秒）
rule.record_max_interval=3
# 游戏开始时摸牌数
rule.hand_card_count_begin=3
# 每回合摸牌数
rule.hand_card_count_each_turn=3
# 最大房间数
room_count=200
# 默认的房间人数
player.total_count=5
# 默认的读条时间
waiting_second=20
# 需要的客户端最低版本号
client_version=1
# 客户端显示的录像列表中最多显示的对局数量
record_list_size=20
# 游戏公告内容
notice=公告
# GM命令的端口，不受下面的gm.enable影响
gm.listen_port=9092
# 如果为true，下面的gm.debug_roles才会生效，同时还会禁用各种房间人数、机器人等限制
gm.enable=false
# 测试时强制设置的角色，按进入房间的顺序安排，需要gm.enable=true才会生效
gm.debug_roles=22,26
## 以下是推送相关的配置，通过mirai的http-api向qq推送消息
# 是否开启推送
push.enable_push=true
# 机器人的qq号
push.robot_qq=12345678
# mirai的http-api的地址
push.mirai_http_url=http\://127.0.0.1\:8080
# mirai的http-api的verify_key
push.mirai_verify_key=AaBbCcDdEeFfGgHhIiJjKkLlMmNnOoPp
# 往哪个QQ群推送消息，可以是多个，用逗号分隔
push.push_qq_groups=12345678
```

请注意，游戏运行时有可能也会自动更新这个文件，所以最好不要在运行时手动修改这个文件。

**log4j配置**

见 [src/main/resources/log4j2.xml](src/main/resources/log4j2.xml)

## 关于GM命令

直接GET请求`http://127.0.0.1:9092/xxxx?a=1&b=2`即可使用。目前支持的GM命令有：

| 终结点               | 参数                               | 备注                                                                                     |
|-------------------|----------------------------------|----------------------------------------------------------------------------------------|
| /addcard          | player=0&card=1&count=1          | 其中player参数对应**服务器中的**玩家Id，card参数对应协议中的卡牌类型，count参数（非必填，默认1）为增加卡牌的个数                    |
| /addmessagecard   | player=0&card=1&colors=0&count=1 | 其中player参数对应**服务器中的**玩家Id，card参数对应协议中的卡牌类型，colors参数对应协议中的卡牌颜色，count参数（非必填，默认1）为增加卡牌的个数 |
| /addrobot         | count=1                          | 其中count参数对应想要增加机器人的个数，不填表示加满                                                           |
| /getscore         | name=aaa                         | 其中name参数是想要获取分数的玩家名字                                                                   |
| /ranklist         | 无                                | 获取排行榜                                                                                  |
| /resetpwd         | name=aaa                         | 其中name参数是想要重置密码的玩家名字（重置为空，玩家可以自行重新设置）                                                  |
| /forbidrole       | name=aaa                         | 禁用角色，禁用的角色不会再出现在角色池里，其中name参数是想要禁用的中文角色名                                               |
| /releaserole      | name=aaa                         | 启用角色，其中name参数是想要启用的中文角色名                                                               |
| /setnotice        | notice=aaa                       | 热更新公告，其中notice字段就是要更新成什么                                                               |
| /setversion       | version=1                        | 热更新客户端版本号，其中version字段就是要更新成多少                                                          |
| /register         | name=aaa                         | 注册，其中name是用户名                                                                          |
| /addnotify        | qq=12345678&when=0               | 开了喊我或者结束喊我的功能，qq是艾特的qq号，when为0是开了，1是结束（非必填，默认0）                                        |
| /updatewaitsecond | second=15                        | 修改默认出牌时间，其中second字段就是出牌时间（单位：秒），必须大于0                                                  |
| /forceend         | 无                                | 强制结束所有游戏，用于卡住了的情况                                                                      |
| /forbidplayer     | name=aaa&hour=72                 | 封号，其中name是用户名，hour是小时                                                                  |
| /releaseplayer    | name=aaa                         | 解封，其中name是用户名                                                                          |
| /winrate          | 无                                | 返回一张胜率统计的png图片                                                                         |
| /resetseason      | 无                                | 重置赛季，重置前请手动备份PlayerInfo.csv                                                            |
| /addenergy        | name=aaa&energy=1                | 增加精力                                                                                   |

## 把战绩推送到QQ群

将配置文件`application.properties`中的`push.enable_push`设置为`true`，就可以开启将战绩推送到QQ群的功能。

在使用推送功能之前，你应该首先自行搭建一个支持 [onebot-11](https://github.com/botuniverse/onebot-11) 接口的QQ机器人。例如：

- [NapCat](https://github.com/NapNeko/NapCatQQ) 基于NTQQ的无头Bot框架
- [OpenShamrock](https://github.com/whitechi73/OpenShamrock) 基于 Lsposed(Non-Riru) 实现 Kritor 标准的 QQ 机器人框架
- [Lagrange](https://github.com/LagrangeDev/Lagrange.Core) 一个基于纯C#的NTQQ协议实现，源自Konata.Core
- [LiteLoaderQQNT](https://github.com/LiteLoaderQQNT/LiteLoaderQQNT) QQNT 插件加载器
- [Gensokyo](https://github.com/Hoshinonyaruko/Gensokyo) 基于qq官方api开发的符合onebot标准的golang实现，轻量、原生跨平台
- [LLOneBot](https://github.com/LLOneBot/LLOneBot) LiteLoaderQQNT插件，使你的NTQQ支持OneBot11协议进行QQ机器人开发

> [!IMPORTANT]
> 本功能是基于onebot的正向http接口，因此你需要开启对应机器人项目的http监听。

*纯人机局不会推送，至少要有2名真人玩家时才会推送。*

## 关于文件服务器

提供了一个文件服务器端口，供客户端下载资源文件，`file_server_port`字段配成0就是不启用文件服务器。

启动后，访问`http://ip:port/`即可看到`files`文件夹（事先自行创建一个`files`文件夹）下的文件列表，点击文件名即可下载。（目前暂不支持嵌套文件夹）

## 开发相关

### gradle镜像

如果gradle下载太慢，可以修改`gradle/wrapper/gradle-wrapper.properties`中的`distributionUrl`：

```diff
- distributionUrl=https\://services.gradle.org/distributions/gradle-8.7-bin.zip
+ distributionUrl=https\://mirrors.cloud.tencent.com/gradle/gradle-8.7-bin.zip
```

### IDEA问题

如遇IDEA提示“代码洞察功能不可用”，是因为role.proto生成的协议文件太大导致的，在帮助菜单中编辑一下IDEA的自定义属性`idea.properties`
即可：

```properties
#---------------------------------------------------------------------
# Maximum file size (kilobytes) IDE should provide code assistance for.
# The larger file is the slower its editor works and higher overall system memory requests
# if code assistance is enabled. Remove this property or set to very large number if your
# code assistance for any files available regardless their size.
#---------------------------------------------------------------------
idea.max.intellisense.filesize=2500
#---------------------------------------------------------------------
# Maximum file size (kilobytes) IDE is able to open.
#---------------------------------------------------------------------
idea.max.content.load.filesize=20000
```

`idea.max.intellisense.filesize`表示IDEA的代码洞察功能支持缓存的单个文件大小。默认是2500，将它改大即可。若没有这个属性，自行添加并改大即可。

### 中文乱码问题

对于“排行”“胜率”这两个生成图片的功能，如遇Linux下中文乱码，请将字体文件放入`/usr/share/fonts`中，然后执行以下shell

```shell
# 刷新字体缓存
fc-cache
# 查看是否有宋体
fc-list :lang=zh | grep 宋体
```
