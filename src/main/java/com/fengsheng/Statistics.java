package com.fengsheng;

import com.fengsheng.phase.WaitForSelectRole;
import com.fengsheng.protos.Common;
import com.fengsheng.skill.RoleCache;
import org.apache.log4j.Logger;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Statistics {
    private static final Statistics instance = new Statistics();
    private static final Logger log = Logger.getLogger(Statistics.class);

    public static Statistics getInstance() {
        return instance;
    }

    private final ExecutorService saveLoadPool = Executors.newSingleThreadExecutor();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private Statistics() {
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT+8:00"));
    }

    public void add(List<Record> records) {
        saveLoadPool.submit(() -> {
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

    public static class Record {
        private final Common.role role;
        private final boolean isWinner;
        private final Common.color identity;
        private final Common.secret_task task;
        private final int totalPlayerCount;

        public Record(Common.role role, boolean isWinner, Common.color identity, Common.secret_task task, int totalPlayerCount) {
            this.role = role;
            this.isWinner = isWinner;
            this.identity = identity;
            this.task = task;
            this.totalPlayerCount = totalPlayerCount;
        }
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
