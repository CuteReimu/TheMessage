package com.fengsheng;

import com.fengsheng.phase.WaitForSelectRole;
import com.fengsheng.protos.Common;
import com.fengsheng.skill.RoleCache;
import org.apache.log4j.Logger;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class Statistics {
    private static final Statistics instance = new Statistics();
    private static final Logger log = Logger.getLogger(Statistics.class);

    public static Statistics getInstance() {
        return instance;
    }

    private final ExecutorService pool = Executors.newSingleThreadExecutor();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final Map<String, PlayerGameCount> playerGameCount = new ConcurrentHashMap<>();
    private final AtomicInteger totalWinCount = new AtomicInteger();
    private final AtomicInteger totalGameCount = new AtomicInteger();

    private Statistics() {
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT+8:00"));
    }

    public void add(List<Record> records) {
        pool.submit(() -> {
            String time = dateFormat.format(new Date());
            StringBuilder sb = new StringBuilder();
            for (Record r : records) {
                sb.append(r.role).append(',');
                sb.append(r.isWinner).append(',');
                sb.append(r.identity).append(',');
                sb.append(r.identity == Common.color.Black ? r.task.toString() : "").append(',');
                sb.append(r.totalPlayerCount).append(',');
                sb.append(time).append('\n');
            }
            try (FileOutputStream fileOutputStream = new FileOutputStream("stat.csv", true)) {
                fileOutputStream.write(sb.toString().getBytes());
            } catch (IOException e) {
                log.error("write file failed", e);
            }
        });
    }

    public void addPlayerGameCount(List<PlayerGameResult> playerGameResultList) {
        pool.submit(() -> {
            int win = 0;
            int game = 0;
            for (PlayerGameResult count : playerGameResultList) {
                if (count.isWin) win++;
                game++;
                playerGameCount.compute(count.deviceId, (k, v) -> {
                    int addWin = count.isWin ? 1 : 0;
                    if (v == null)
                        return new PlayerGameCount(addWin, 1);
                    return new PlayerGameCount(v.winCount + addWin, v.gameCount + 1);
                });
            }
            totalWinCount.addAndGet(win);
            totalGameCount.addAndGet(game);
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, PlayerGameCount> entry : playerGameCount.entrySet()) {
                PlayerGameCount count = entry.getValue();
                sb.append(count.winCount).append(',');
                sb.append(count.gameCount).append(',');
                sb.append(entry.getKey()).append('\n');
            }
            try (FileOutputStream fileOutputStream = new FileOutputStream("player.csv")) {
                fileOutputStream.write(sb.toString().getBytes());
            } catch (IOException e) {
                log.error("write file failed", e);
            }
        });
    }

    public PlayerGameCount getPlayerGameCount(String deviceId) {
        return playerGameCount.get(deviceId);
    }

    public PlayerGameCount getTotalPlayerGameCount() {
        return new PlayerGameCount(totalWinCount.get(), totalGameCount.get());
    }

    public void loadPlayerGameCount() throws IOException {
        int winCount = 0;
        int gameCount = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream("player.csv")))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] a = line.split(",", 3);
                String deviceId = a[2];
                int win = Integer.parseInt(a[0]);
                int game = Integer.parseInt(a[1]);
                PlayerGameCount oldCount = playerGameCount.put(deviceId, new PlayerGameCount(win, game));
                winCount += oldCount == null ? win : win - oldCount.winCount;
                gameCount += oldCount == null ? game : game - oldCount.gameCount;
            }
        } catch (FileNotFoundException ignored) {
        }
        totalWinCount.set(winCount);
        totalGameCount.set(gameCount);
    }

    public record Record(Common.role role, boolean isWinner, Common.color identity, Common.secret_task task,
                         int totalPlayerCount) {

    }

    public record PlayerGameResult(String deviceId, boolean isWin) {

    }

    public record PlayerGameCount(int winCount, int gameCount) {

    }

    public static void main(String[] args) throws IOException {
        EnumMap<Common.role, Integer> appearCount = new EnumMap<>(Common.role.class);
        EnumMap<Common.role, Integer> winCount = new EnumMap<>(Common.role.class);
        int totalCount = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream("stat.csv")))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] a = line.split(",");
                var role = Common.role.valueOf(a[0]);
                appearCount.compute(role, (k, v) -> v == null ? 1 : v + 1);
                if (Boolean.parseBoolean(a[1]))
                    winCount.compute(role, (k, v) -> v == null ? 1 : v + 1);
                totalCount++;
            }
        }
        if (totalCount == 0)
            return;
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("stat0.csv")))) {
            writer.write("角色,出场率,胜率");
            writer.newLine();
            for (Map.Entry<Common.role, Integer> entry : appearCount.entrySet()) {
                Common.role key = entry.getKey();
                float value = entry.getValue();
                String roleName = RoleCache.getRoleName(key);
                roleName = roleName == null ? WaitForSelectRole.getRoleName(key) : roleName;
                writer.write(Objects.requireNonNullElse(roleName, ""));
                writer.write(',');
                writer.write("%.1f%%".formatted(value * 100 / totalCount));
                writer.write(',');
                writer.write("%.1f%%".formatted(winCount.getOrDefault(key, 0) * 100 / value));
                writer.newLine();
            }
        }
    }
}
