package com.fengsheng;

import com.fengsheng.card.Deck;
import com.fengsheng.network.Network;
import com.fengsheng.protos.Fengsheng;
import com.fengsheng.skill.Skill;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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
        GameCache.put(newGame.id, newGame);
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
