package com.fengsheng;

import com.fengsheng.phase.WaitForSelectRole;
import com.fengsheng.protos.Common;
import com.fengsheng.protos.Fengsheng;
import com.fengsheng.skill.RoleCache;
import org.apache.log4j.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
    private final Map<String, PlayerInfo> playerInfoMap = new ConcurrentHashMap<>();
    private final AtomicInteger totalWinCount = new AtomicInteger();
    private final AtomicInteger totalGameCount = new AtomicInteger();
    private final Map<String, Long> trialStartTime = new ConcurrentHashMap<>();
    private final Map<Integer, com.fengsheng.protos.Record.player_order> orderMap = new HashMap<>();
    private final Map<String, List<com.fengsheng.protos.Record.player_order>> deviceOrderMap = new ConcurrentHashMap<>();
    private int orderId;

    private Statistics() {
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT+8:00"));
    }

    public void add(List<Record> records) {
        pool.submit(() -> {
            try {
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
                writeFile("stat.csv", sb.toString().getBytes(), true);
            } catch (Exception e) {
                log.error("execute task failed", e);
            }
        });
    }

    public void addPlayerGameCount(List<PlayerGameResult> playerGameResultList) {
        pool.submit(() -> {
            try {
                int win = 0;
                int game = 0;
                boolean updateTrial = false;
                for (PlayerGameResult count : playerGameResultList) {
                    if (count.isWin) {
                        win++;
                        if (trialStartTime.remove(count.player.getDevice()) != null)
                            updateTrial = true;
                    }
                    game++;
                    playerInfoMap.computeIfPresent(count.player.getPlayerName(), (k, v) -> {
                        int addWin = count.isWin ? 1 : 0;
                        return new PlayerInfo(v.name, v.deviceId, v.password, v.winCount + addWin, v.gameCount + 1);
                    });
                }
                totalWinCount.addAndGet(win);
                totalGameCount.addAndGet(game);
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<String, PlayerInfo> entry : playerInfoMap.entrySet()) {
                    PlayerInfo info = entry.getValue();
                    sb.append(info.winCount).append(',');
                    sb.append(info.gameCount).append(',');
                    sb.append(info.name).append(',');
                    sb.append(info.deviceId).append(',');
                    sb.append(info.password).append('\n');
                }
                writeFile("playerInfo.csv", sb.toString().getBytes());
                if (updateTrial) {
                    sb = new StringBuilder();
                    for (Map.Entry<String, Long> entry : trialStartTime.entrySet()) {
                        sb.append(entry.getValue()).append(',');
                        sb.append(entry.getKey()).append('\n');
                    }
                    writeFile("trial.csv", sb.toString().getBytes());
                }
            } catch (Exception e) {
                log.error("execute task failed", e);
            }
        });
    }

    public PlayerInfo login(String name, String deviceId, String pwd) {
        final String password;
        try {
            password = pwd == null || pwd.isEmpty() ? "" : md5(name + pwd);
        } catch (NoSuchAlgorithmException e) {
            log.error("md5加密失败", e);
            return null;
        }
        PlayerInfo playerInfo = playerInfoMap.get(name);
        if (playerInfo == null) {
            playerInfo = new PlayerInfo(name, deviceId, password, 0, 0);
            PlayerInfo playerInfo2 = playerInfoMap.putIfAbsent(name, playerInfo);
            if (playerInfo2 != null) playerInfo = playerInfo2;
        }
        if (playerInfo.password == null || playerInfo.password.isEmpty())
            playerInfo = new PlayerInfo(playerInfo.name, playerInfo.deviceId, password, playerInfo.winCount, playerInfo.gameCount);
        if (!password.equals(playerInfo.password))
            return null;
        // 对旧数据进行兼容
        final PlayerInfo[] finalPlayerInfo = {playerInfo};
        playerGameCount.computeIfPresent(deviceId, (k, v) -> {
            int gameCount = finalPlayerInfo[0].gameCount + v.gameCount;
            int winCount = finalPlayerInfo[0].winCount + v.winCount;
            finalPlayerInfo[0] = new PlayerInfo(name, deviceId, password, gameCount, winCount);
            return null;
        });
        if (finalPlayerInfo[0] != playerInfo) {
            playerInfoMap.put(name, finalPlayerInfo[0]);
            pool.submit(() -> {
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<String, PlayerInfo> entry : playerInfoMap.entrySet()) {
                    PlayerInfo info = entry.getValue();
                    sb.append(info.winCount).append(',');
                    sb.append(info.gameCount).append(',');
                    sb.append(info.name).append(',');
                    sb.append(info.deviceId).append(',');
                    sb.append(info.password).append('\n');
                }
                writeFile("playerInfo.csv", sb.toString().getBytes());
                sb = new StringBuilder();
                for (Map.Entry<String, PlayerGameCount> entry : playerGameCount.entrySet()) {
                    PlayerGameCount count = entry.getValue();
                    sb.append(count.winCount).append(',');
                    sb.append(count.gameCount).append(',');
                    sb.append(entry.getKey()).append('\n');
                }
                writeFile("player.csv", sb.toString().getBytes());
            });
        }
        return playerInfo;
    }

    public PlayerGameCount getPlayerGameCount(String name) {
        PlayerInfo playerInfo = playerInfoMap.get(name);
        if (playerInfo == null) return null;
        return new PlayerGameCount(playerInfo.winCount, playerInfo.gameCount);
    }

    public PlayerGameCount getTotalPlayerGameCount() {
        return new PlayerGameCount(totalWinCount.get(), totalGameCount.get());
    }

    public void load() throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream("player.csv")))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] a = line.split(",", 3);
                String deviceId = a[2];
                int win = Integer.parseInt(a[0]);
                int game = Integer.parseInt(a[1]);
                playerGameCount.put(deviceId, new PlayerGameCount(win, game));
            }
        } catch (FileNotFoundException ignored) {
        }
        int winCount = 0;
        int gameCount = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream("playerInfo.csv")))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] a = line.split(",", 5);
                String password = a[4];
                String deviceId = a[3];
                String name = a[2];
                int win = Integer.parseInt(a[0]);
                int game = Integer.parseInt(a[1]);
                if (playerInfoMap.put(name, new PlayerInfo(name, deviceId, password, win, game)) != null)
                    throw new RuntimeException("数据错误，有重复的玩家name");
                winCount += win;
                gameCount += game;
            }
        } catch (FileNotFoundException ignored) {
        }
        totalWinCount.set(winCount);
        totalGameCount.set(gameCount);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream("trial.csv")))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] a = line.split(",", 2);
                trialStartTime.put(a[1], Long.parseLong(a[0]));
            }
        } catch (FileNotFoundException ignored) {
        }
        try (FileInputStream is = new FileInputStream("order.dat")) {
            var playerOrders = com.fengsheng.protos.Record.player_orders.parseFrom(is.readAllBytes());
            orderMap.putAll(playerOrders.getOrdersMap());
            orderId = playerOrders.getOrderId();
            for (var order : playerOrders.getOrdersMap().values())
                deviceOrderMap.computeIfAbsent(order.getDevice(), k -> new ArrayList<>()).add(order);
        } catch (FileNotFoundException ignored) {
        }
    }

    public long getTrialStartTime(String deviceId) {
        return trialStartTime.getOrDefault(deviceId, 0L);
    }

    public void setTrialStartTime(String device, long time) {
        pool.submit(() -> {
            try {
                trialStartTime.put(device, time);
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<String, Long> entry : trialStartTime.entrySet()) {
                    sb.append(entry.getValue()).append(',');
                    sb.append(entry.getKey()).append('\n');
                }
                writeFile("trial.csv", sb.toString().getBytes());
            } catch (Exception e) {
                log.error("execute task failed", e);
            }
        });
    }

    public List<Fengsheng.pb_order> getOrders(String deviceId) {
        final long now = System.currentTimeMillis() / 1000 + 8 * 3600;
        final Set<com.fengsheng.protos.Record.player_order> set = new TreeSet<>((o1, o2) -> {
            if (o1.getTime() == o2.getTime()) return Integer.compare(o1.getId(), o2.getId());
            return o1.getTime() < o2.getTime() ? -1 : 1;
        });
        var myOrders = deviceOrderMap.get(deviceId);
        if (myOrders != null) {
            myOrders.forEach(o -> {
                if (o.getTime() > now - 1800)
                    set.add(o);
            });
        }
        orderMap.forEach((k, o) -> {
            if (o.getTime() > now - 1800)
                set.add(o);
        });
        List<Fengsheng.pb_order> list = new ArrayList<>();
        for (var o : set) {
            list.add(playerOrderToPbOrder(deviceId, o));
            if (list.size() >= 20) break;
        }
        return list;
    }

    public void addOrder(String device, String name, long time) {
        final long now = System.currentTimeMillis() / 1000 + 8 * 3600;
        if (time <= now - 1800)
            return;
        pool.submit(() -> {
            try {
                var orders1 = deviceOrderMap.get(device);
                orders1 = orders1 == null ? new ArrayList<>() : new ArrayList<>(orders1);
                var order = com.fengsheng.protos.Record.player_order.newBuilder().setId(++orderId).setDevice(device).setName(name).setTime(time).build();
                orders1.add(order);
                orders1.removeIf(o -> {
                    if (o.getTime() <= now - 1800) {
                        orderMap.remove(o.getId());
                        return true;
                    }
                    return false;
                });
                if (orders1.size() > 3)
                    orders1.subList(0, orders1.size() - 3).clear();
                deviceOrderMap.put(device, orders1);
                orderMap.put(order.getId(), order);
                List<Integer> removeList = new ArrayList<>();
                for (var entry : orderMap.entrySet()) {
                    var o = entry.getValue();
                    if (o.getTime() <= now - 1800) {
                        removeList.add(entry.getKey());
                        List<com.fengsheng.protos.Record.player_order> orders2 = new ArrayList<>();
                        for (var o2 : deviceOrderMap.get(o.getDevice())) {
                            if (o2.getId() != o.getId())
                                orders2.add(o2);
                        }
                        deviceOrderMap.put(o.getDevice(), orders2);
                    }
                }
                removeList.forEach(orderMap::remove);
                var buf = com.fengsheng.protos.Record.player_orders.newBuilder().setOrderId(orderId).putAllOrders(orderMap).build().toByteArray();
                writeFile("order.dat", buf);
            } catch (Exception e) {
                log.error("execute task failed", e);
            }
        });
    }

    private static Fengsheng.pb_order playerOrderToPbOrder(String deviceId, com.fengsheng.protos.Record.player_order order) {
        return Fengsheng.pb_order.newBuilder().setId(order.getId()).setName(order.getName()).setTime(order.getTime()).setIsMine(deviceId.equals(order.getDevice())).build();
    }

    private static void writeFile(String fileName, byte[] buf) {
        writeFile(fileName, buf, false);
    }

    private static void writeFile(String fileName, byte[] buf, boolean append) {
        try (FileOutputStream fileOutputStream = new FileOutputStream(fileName, append)) {
            fileOutputStream.write(buf);
        } catch (IOException e) {
            log.error("write file failed", e);
        }
    }

    public void displayRecordList(HumanPlayer player) {
        pool.submit(() -> {
            var builder = Fengsheng.get_record_list_toc.newBuilder();
            File dir = new File("records");
            String[] files = dir.list();
            if (files != null) {
                Arrays.sort(files);
                String lastPrefix = null;
                int j = 0;
                for (int i = files.length - 1; i >= 0; i--) {
                    if (files[i].length() < 19)
                        continue;
                    if (lastPrefix == null || !files[i].startsWith(lastPrefix)) {
                        if (++j > Config.RecordListSize) break;
                        lastPrefix = files[i].substring(0, 19);
                    }
                    builder.addRecords(files[i]);
                }
            }
            player.send(builder.build());
        });
    }

    public record Record(Common.role role, boolean isWinner, Common.color identity, Common.secret_task task,
                         int totalPlayerCount) {

    }

    public record PlayerGameResult(HumanPlayer player, boolean isWin) {

    }

    public record PlayerGameCount(int winCount, int gameCount) {

    }

    public record PlayerInfo(String name, String deviceId, String password, int winCount, int gameCount) {

    }

    public static void main(String[] args) throws IOException {
        EnumMap<Common.role, Integer> appearCount = new EnumMap<>(Common.role.class);
        EnumMap<Common.role, Integer> blackAppearCount = new EnumMap<>(Common.role.class);
        EnumMap<Common.role, Integer> winCount = new EnumMap<>(Common.role.class);
        EnumMap<Common.role, Integer> blackWinCount = new EnumMap<>(Common.role.class);
        Set<String> timeSet = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream("stat.csv")))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] a = line.split(",");
                var role = Common.role.valueOf(a[0]);
                appearCount.compute(role, (k, v) -> v == null ? 1 : v + 1);
                if (Boolean.parseBoolean(a[1]))
                    winCount.compute(role, (k, v) -> v == null ? 1 : v + 1);
                if ("Black".equals(a[2])) {
                    blackAppearCount.compute(role, (k, v) -> v == null ? 1 : v + 1);
                    if (Boolean.parseBoolean(a[1]))
                        blackWinCount.compute(role, (k, v) -> v == null ? 1 : v + 1);
                }
                timeSet.add(a[5]);
            }
        }
        if (timeSet.isEmpty())
            return;
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("stat0.csv")))) {
            writer.write("角色,场次,胜率,军潜胜率,神秘人胜率");
            writer.newLine();
            for (Map.Entry<Common.role, Integer> entry : appearCount.entrySet()) {
                Common.role key = entry.getKey();
                int value = entry.getValue();
                String roleName = RoleCache.getRoleName(key);
                roleName = roleName == null ? WaitForSelectRole.getRoleName(key) : roleName;
                writer.write(Objects.requireNonNullElse(roleName, ""));
                writer.write(',');
                writer.write(Integer.toString(value));
                writer.write(',');
                writer.write("%.2f%%".formatted(winCount.getOrDefault(key, 0) * 100.0 / value));
                writer.write(',');
                int blackAppear = blackAppearCount.getOrDefault(key, 0);
                int nonBlackAppear = value - blackAppear;
                if (nonBlackAppear > 0)
                    writer.write("%.2f%%".formatted((winCount.getOrDefault(key, 0) - blackWinCount.getOrDefault(key, 0)) * 100.0 / nonBlackAppear));
                else
                    writer.write("0.00%");
                writer.write(',');
                if (blackAppear > 0)
                    writer.write("%.2f%%".formatted(blackWinCount.getOrDefault(key, 0) * 100.0 / blackAppearCount.get(key)));
                else
                    writer.write("0.00%");
                writer.newLine();
            }
        }
    }

    private final static char[] hexDigests = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    private static String md5(String s) throws NoSuchAlgorithmException {
        byte[] in = s.getBytes(StandardCharsets.UTF_8);
        MessageDigest messageDigest = MessageDigest.getInstance("md5");
        messageDigest.update(in);
        // 获得密文
        byte[] md = messageDigest.digest();
        // 将密文转换成16进制字符串形式
        int j = md.length;
        char[] str = new char[j * 2];
        int k = 0;
        for (byte b : md) {
            str[k++] = hexDigests[b >>> 4 & 0xf]; // 高4位
            str[k++] = hexDigests[b & 0xf]; // 低4位
        }
        return new String(str);
    }
}
