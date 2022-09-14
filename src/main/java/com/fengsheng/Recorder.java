package com.fengsheng;

import com.fengsheng.network.ProtoServerChannelHandler;
import com.fengsheng.protos.Errcode;
import com.fengsheng.protos.Fengsheng;
import org.apache.log4j.Logger;

import java.io.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Recorder {
    private static final Logger log = Logger.getLogger(Recorder.class);
    private static final short initTocId = ProtoServerChannelHandler.stringHash("init_toc");
    private static final ExecutorService saveLoadPool = Executors.newSingleThreadExecutor();

    private List<RecorderLine> list = new ArrayList<>();

    private int currentIndex;

    public void add(short messageId, byte[] messageBuf) {
        if (messageId == initTocId || list.size() > 0)
            list.add(new RecorderLine(System.nanoTime(), messageId, messageBuf));
    }

    public void save(Game g, final HumanPlayer p, boolean notify) {
        Date now = new Date();
        LocalDateTime localDateTime = now.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        String timeStr = localDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss"));
        StringBuilder sb = new StringBuilder();
        for (Player player : g.getPlayers()) {
            if (!sb.isEmpty()) sb.append("-");
            sb.append(player.getRoleName());
        }
        final String recordId = Long.toString((now.getTime() * 1000 + g.getId() % 100 + p.location()) % (36L * 36 * 36 * 36 * 36 * 36 * 36 * 36), Character.MAX_RADIX);
        final String fileName = timeStr + "-" + sb + "-" + p.location() + "-" + recordId;
        saveLoadPool.submit(() -> {
            File file = new File("records/");
            if (!file.exists() && !file.isDirectory() && !file.mkdir())
                log.error("make dir failed: " + file.getName());
            try (ObjectOutputStream os = new ObjectOutputStream(new FileOutputStream("records/" + fileName))) {
                os.writeInt(Config.ClientVersion);
                os.writeObject(list.toArray(new RecorderLine[0]));
                if (notify) p.send(Fengsheng.save_record_success_toc.newBuilder().setRecordId(recordId).build());
                log.info("save record success" + recordId);
            } catch (IOException e) {
                log.error("save record failed", e);
            }
        });
    }

    public void load(final int version, final String recordId, final HumanPlayer player) {
        File file = new File("records/");
        if (!file.exists() || !file.isDirectory()) {
            player.send(Errcode.error_code_toc.newBuilder()
                    .setCode(Errcode.error_code.record_not_exists).build());
            return;
        }
        File[] files = file.listFiles((dir, name) -> name.endsWith("-" + recordId));
        if (files == null || files.length == 0) {
            player.send(Errcode.error_code_toc.newBuilder()
                    .setCode(Errcode.error_code.record_not_exists).build());
            return;
        }
        final File recordFile = files[0];
        saveLoadPool.submit(() -> {
            try (ObjectInputStream is = new ObjectInputStream(new FileInputStream(recordFile))) {
                int recordVersion = is.readInt();
                if (version != recordVersion) {
                    player.send(Errcode.error_code_toc.newBuilder()
                            .setCode(Errcode.error_code.record_version_not_match)
                            .addIntParams(recordVersion).build());
                    return;
                }
                var lines = (RecorderLine[]) is.readObject();
                list = new ArrayList<>(Arrays.asList(lines));
                currentIndex = 0;
                log.info("load record success" + recordId);
                displayNext(player);
            } catch (IOException | ClassNotFoundException | ClassCastException e) {
                log.error("load record failed", e);
                player.send(Errcode.error_code_toc.newBuilder()
                        .setCode(Errcode.error_code.load_record_failed).build());
            }
        });
    }

    private void displayNext(final HumanPlayer player) {
        while (currentIndex < list.size()) {
            RecorderLine line = list.get(currentIndex);
            player.send(line.messageId, line.messageBuf);
            if (++currentIndex >= list.size())
                break;
            long diffNanoTime = list.get(currentIndex).nanoTime - line.nanoTime;
            if (diffNanoTime > 100000000) {
                GameExecutor.TimeWheel.newTimeout(timeout -> displayNext(player), Math.min(diffNanoTime, 2000000000), TimeUnit.NANOSECONDS);
                break;
            }
        }
    }

    private record RecorderLine(long nanoTime, short messageId, byte[] messageBuf) implements Serializable {
        @Serial
        private static final long serialVersionUID = 7140606580772819765L;
    }
}
