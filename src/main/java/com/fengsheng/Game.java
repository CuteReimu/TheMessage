package com.fengsheng;

import com.fengsheng.card.Deck;
import com.fengsheng.network.Network;
import com.fengsheng.protos.Common;
import com.fengsheng.protos.Fengsheng;
import com.fengsheng.skill.Skill;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;

public final class Game {
    private static final Logger log = Logger.getLogger(Game.class);
    public static final ConcurrentMap<Integer, Game> GameCache = new ConcurrentHashMap<>();
    private static int increaseId = 0;
    private static Game newGame;

    private final int id;
    private boolean started;
    private final Player[] players;
    private final Deck deck = new Deck(this);
    private Runnable fsm;
    private final List<Skill> listeningSkills = new ArrayList<>();

    private Game(int totalPlayerCount) {
        // 调用构造函数时加锁了，所以increaseId无需加锁
        id = ++increaseId;
        players = new Player[totalPlayerCount];
    }

    /**
     * 不是线程安全的
     */
    private static void newInstance() {
        newGame = new Game(Config.TotalPlayerCount);
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
        var msg = Fengsheng.join_room_toc.newBuilder().setName(player.toString()).setPosition(player.location()).build();
        for (Player p : players) {
            if (p != player && p instanceof HumanPlayer) {
                ((HumanPlayer) p).send(msg);
            }
        }
        if (unready == 0) {
            log.info(player + "加入了。已加入" + players.length + "个人，游戏开始。。。");
            GameExecutor.post(this, this::start);
            newInstance();
        } else {
            log.info(player + "加入了。已加入" + (players.length - unready) + "个人，等待" + unready + "人加入。。。");
        }
    }

    public boolean isStarted() {
        return started;
    }

    private void start() {
        started = true;
        Random random = ThreadLocalRandom.current();
        List<Common.color> identities = new ArrayList<>();
        for (int i = 0; i < (players.length - 1) / 2; i++) {
            identities.add(Common.color.Red);
            identities.add(Common.color.Blue);
        }
        identities.add(Common.color.Black);
        if (players.length % 2 == 0) identities.add(Common.color.Black);
        Collections.shuffle(identities, random);
        List<Common.secret_task> tasks = new ArrayList<>(List.of(Common.secret_task.Killer, Common.secret_task.Stealer, Common.secret_task.Collector));
        Collections.shuffle(tasks, random);
        // TODO 随机分配角色
        int secretIndex = 0;
        for (int i = 0; i < players.length; i++) {
            var identity = identities.get(i);
            var task = identity == Common.color.Black ? tasks.get(secretIndex++) : Common.secret_task.forNumber(0);
            players[i].init(identity, task, null);
        }
        GameCache.put(id, this);
        int whoseTurn = random.nextInt(players.length);
        for (int i = 0; i < players.length; i++) {
            players[(whoseTurn + i) % players.length].draw(Config.HandCardCountBegin);
        }
        // TODO 进入第一个人的出牌阶段
    }

    public void end() {
        GameCache.remove(id);
    }

    int getId() {
        return id;
    }

    public Player[] getPlayers() {
        return players;
    }

    public Deck getDeck() {
        return deck;
    }

    public Runnable getFsm() {
        return fsm;
    }

    public static void main(String[] args) {
        synchronized (Game.class) {
            newInstance();
        }
        Network.init();
    }
}
