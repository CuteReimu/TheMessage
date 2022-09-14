package com.fengsheng;

import com.fengsheng.network.ProtoServerChannelHandler;
import org.apache.log4j.Logger;

import java.io.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Recorder {
    private static final Logger log = Logger.getLogger(Recorder.class);
    private static final short initTocId = ProtoServerChannelHandler.stringHash("init_toc");
    private static final ExecutorService savePool = Executors.newSingleThreadExecutor();

    private final List<RecorderLine> list = new ArrayList<>();

    public void add(int messageId, byte[] messageBuf) {
        if (messageId == initTocId || list.size() > 0)
            list.add(new RecorderLine(System.nanoTime(), messageId, messageBuf));
    }

    public String save(Game g, Player p) {
        Date now = new Date();
        LocalDateTime localDateTime = now.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        String timeStr = localDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss"));
        StringBuilder sb = new StringBuilder();
        for (Player player : g.getPlayers()) {
            if (!sb.isEmpty()) sb.append("-");
            sb.append(player.getRoleName());
        }
        final String recordId = Long.toString((now.getTime() * 1000 + g.getId() % 100 + p.location()) % (36L * 36 * 36 * 36 * 36 * 36), Character.MAX_RADIX);
        final String fileName = "record/" + timeStr + "-" + sb + "-" + p.location() + "-" + recordId;
        savePool.submit(() -> {
            File file = new File("record/");
            if (!file.exists() && !file.isDirectory() && !file.mkdir())
                log.error("make dir failed: " + file.getName());
            try (ObjectOutputStream os = new ObjectOutputStream(new FileOutputStream(fileName))) {
                os.writeObject(list.toArray(new RecorderLine[0]));
                log.info("save record success" + recordId);
            } catch (IOException e) {
                log.error("save record failed", e);
            }
        });
        return recordId;
    }

    private record RecorderLine(long nanoTime, int messageId, byte[] messageBuf) implements Serializable {
        @Serial
        private static final long serialVersionUID = 7140606580772819765L;
    }
}
