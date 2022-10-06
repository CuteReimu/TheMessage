package com.fengsheng;

import com.fengsheng.card.Card;
import com.fengsheng.card.Deck;
import com.fengsheng.network.Network;
import com.fengsheng.phase.WaitForSelectRole;
import com.fengsheng.protos.Common;
import com.fengsheng.protos.Fengsheng;
import com.fengsheng.skill.RoleCache;
import com.fengsheng.skill.RoleSkillsData;
import com.fengsheng.skill.TriggeredSkill;
import com.google.protobuf.GeneratedMessageV3;
import org.apache.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;

public final class Game {
    private static final Logger log = Logger.getLogger(Game.class);
    public static final ConcurrentMap<Integer, Game> GameCache = new ConcurrentHashMap<>();
    public static final ConcurrentMap<String, HumanPlayer> deviceCache = new ConcurrentHashMap<>();
    private static int increaseId = 0;
    private static Game newGame;

    private final int id;
    private volatile boolean started;
    private volatile boolean ended;
    private Player[] players;
    private Deck deck;
    private Fsm fsm;
    private final List<TriggeredSkill> listeningSkills = new ArrayList<>();

    /**
     * 用于王田香技能禁闭
     */
    private Player jinBiPlayer;

    private Game(int totalPlayerCount) {
        // 调用构造函数时加锁了，所以increaseId无需加锁
        id = ++increaseId;
        players = new Player[totalPlayerCount];
    }

    /**
     * 不是线程安全的
     */
    public static void newInstance() {
        newGame = new Game(Math.max(newGame != null ? newGame.getPlayers().length : 0, Config.TotalPlayerCount));
    }

    /**
     * 不是线程安全的
     */
    public static Game getInstance() {
        return newGame;
    }

    /**
     * 玩家进入房间时调用
     */
    public void onPlayerJoinRoom(Player player) {
        int unready = -1;
        for (int index = 0; index < players.length; index++) {
            if (players[index] == null && ++unready == 0) {
                players[index] = player;
                player.setLocation(index);
            }
        }
        var msg = Fengsheng.join_room_toc.newBuilder().setName(player.getPlayerName()).setPosition(player.location()).build();
        for (Player p : players) {
            if (p != player && p instanceof HumanPlayer) {
                ((HumanPlayer) p).send(msg);
            }
        }
        if (unready == 0) {
            log.info(player.getPlayerName() + "加入了。已加入" + players.length + "个人，游戏开始。。。");
            started = true;
            GameExecutor.post(this, this::start);
            newInstance();
        } else {
            log.info(player.getPlayerName() + "加入了。已加入" + (players.length - unready) + "个人，等待" + unready + "人加入。。。");
        }
    }

    public boolean isStarted() {
        return started;
    }

    public void setStarted(boolean started) {
        this.started = started;
    }

    public void start() {
        Random random = ThreadLocalRandom.current();
        List<Common.color> identities = new ArrayList<>();
        switch (players.length) {
            case 2:
                identities = switch (random.nextInt(4)) {
                    case 0 -> Arrays.asList(Common.color.Red, Common.color.Blue);
                    case 1 -> Arrays.asList(Common.color.Red, Common.color.Black);
                    case 2 -> Arrays.asList(Common.color.Blue, Common.color.Black);
                    default -> Arrays.asList(Common.color.Black, Common.color.Black);
                };
                break;
            case 9:
                identities.add(Common.color.Red);
                identities.add(Common.color.Blue);
                identities.add(Common.color.Black);
            case 4:
                identities.add(Common.color.Red);
                identities.add(Common.color.Blue);
            case 3:
                identities.add(Common.color.Red);
                identities.add(Common.color.Blue);
                identities.add(Common.color.Black);
                identities.add(Common.color.Black);
                while (identities.size() > players.length)
                    identities.remove(random.nextInt(identities.size()));
                break;
            default:
                for (int i = 0; i < (players.length - 1) / 2; i++) {
                    identities.add(Common.color.Red);
                    identities.add(Common.color.Blue);
                }
                identities.add(Common.color.Black);
                if (players.length % 2 == 0) identities.add(Common.color.Black);
        }
        Collections.shuffle(identities, random);
        List<Common.secret_task> tasks = Arrays.asList(Common.secret_task.Killer, Common.secret_task.Stealer, Common.secret_task.Collector);
        Collections.shuffle(tasks, random);
        int secretIndex = 0;
        for (int i = 0; i < players.length; i++) {
            var identity = identities.get(i);
            var task = identity == Common.color.Black ? tasks.get(secretIndex++) : Common.secret_task.forNumber(0);
            players[i].setIdentity(identity);
            players[i].setSecretTask(task);
            players[i].setOriginIdentity(identity);
            players[i].setOriginSecretTask(task);
        }
        RoleSkillsData[] roleSkillsDataArray = Config.IsGmEnable
                ? RoleCache.getRandomRolesWithSpecific(players.length * 2, Config.DebugRoles)
                : RoleCache.getRandomRoles(players.length * 2);
        resolve(new WaitForSelectRole(this, roleSkillsDataArray));
    }

    public boolean isEnd() {
        return ended;
    }

    public void end(List<Player> winners) {
        ended = true;
        GameCache.remove(id);
        boolean isHumanGame = true;
        for (Player p : players) {
            if (p instanceof HumanPlayer humanPlayer) {
                humanPlayer.saveRecord();
                deviceCache.remove(humanPlayer.getDevice());
            } else {
                isHumanGame = false;
            }
        }
        if (winners != null && isHumanGame && players.length >= 5) {
            List<Statistics.Record> records = new ArrayList<>(players.length);
            for (Player p : players)
                records.add(new Statistics.Record(p.getRole(), winners.contains(p), p.getOriginIdentity(), p.getOriginSecretTask()));
            Statistics.getInstance().add(records);
        }
    }

    public int getId() {
        return id;
    }

    public Player[] getPlayers() {
        return players;
    }

    public void setPlayers(Player[] players) {
        this.players = players;
    }

    public Deck getDeck() {
        return deck;
    }

    public void setDeck(Deck deck) {
        this.deck = deck;
    }

    /**
     * 用于王田香技能禁闭
     */
    public Player getJinBiPlayer() {
        return jinBiPlayer;
    }

    /**
     * 用于王田香技能禁闭
     */
    public void setJinBiPlayer(Player jinBiPlayer) {
        this.jinBiPlayer = jinBiPlayer;
    }

    /**
     * 玩家弃牌
     */
    public void playerDiscardCard(Player player, Card... cards) {
        if (cards.length == 0) return;
        for (Card card : cards) {
            player.deleteCard(card.getId());
        }
        log.info(player + "弃掉了" + Arrays.toString(cards) + "，剩余手牌" + player.getCards().size() + "张");
        deck.discard(cards);
        for (Player p : players) {
            if (p instanceof HumanPlayer) {
                var builder = Fengsheng.discard_card_toc.newBuilder().setPlayerId(p.getAlternativeLocation(player.location()));
                for (Card card : cards) {
                    builder.addCards(card.toPbCard());
                }
                ((HumanPlayer) p).send(builder.build());
            }
        }
    }

    public void playerSetRoleFaceUp(Player player, boolean faceUp) {
        if (faceUp) {
            if (player.isRoleFaceUp())
                log.error(player + "本来就是正面朝上的", new RuntimeException());
            else
                log.info(player + "将角色翻至正面朝上");
            player.setRoleFaceUp(true);
        } else {
            if (!player.isRoleFaceUp())
                log.error(player + "本来就是背面朝上的", new RuntimeException());
            else
                log.info(player + "将角色翻至背面朝上");
            player.setRoleFaceUp(false);
        }
        for (Player p : players) {
            if (p instanceof HumanPlayer) {
                var builder = Fengsheng.notify_role_update_toc.newBuilder().setPlayerId(p.getAlternativeLocation(player.location()));
                builder.setRole(player.isRoleFaceUp() ? player.getRole() : Common.role.unknown);
                ((HumanPlayer) p).send(builder.build());
            }
        }
    }

    /**
     * 继续处理当前状态机
     */
    public void continueResolve() {
        GameExecutor.post(this, () -> {
            ResolveResult result = fsm.resolve();
            if (result != null) {
                fsm = result.next();
                if (result.continueResolve()) {
                    continueResolve();
                }
            }
        });
    }

    /**
     * 对于{@link WaitingFsm}，当收到玩家协议时，继续处理当前状态机
     */
    public void tryContinueResolveProtocol(final Player player, final GeneratedMessageV3 pb) {
        GameExecutor.post(this, () -> {
            if (!(fsm instanceof WaitingFsm)) {
                log.error("时机错误，当前时点为：" + fsm);
                return;
            }
            ResolveResult result = ((WaitingFsm) fsm).resolveProtocol(player, pb);
            if (result != null) {
                fsm = result.next();
                if (result.continueResolve()) {
                    continueResolve();
                }
            }
        });
    }

    /**
     * 更新一个新的状态机并结算，只能由游戏所在线程调用
     */
    public void resolve(Fsm fsm) {
        this.fsm = fsm;
        continueResolve();
    }

    public Fsm getFsm() {
        return fsm;
    }

    /**
     * 增加一个新的需要监听的技能。仅用于接收情报时、使用卡牌时、死亡时的技能
     */
    public void addListeningSkill(TriggeredSkill skill) {
        listeningSkills.add(skill);
    }

    /**
     * 遍历监听列表，结算技能
     */
    public ResolveResult dealListeningSkill() {
        for (TriggeredSkill skill : listeningSkills) {
            ResolveResult result = skill.execute(this);
            if (result != null) return result;
        }
        return null;
    }

    public static void main(String[] args) {
        synchronized (Game.class) {
            newInstance();
        }
        Network.init();
    }
}
