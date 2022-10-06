package com.fengsheng;

import com.fengsheng.protos.Common;
import org.apache.log4j.Logger;

import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
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
                sb.append(r.task).append(',');
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

        public Record(Common.role role, boolean isWinner, Common.color identity, Common.secret_task task) {
            this.role = role;
            this.isWinner = isWinner;
            this.identity = identity;
            this.task = task;
        }
    }
}
