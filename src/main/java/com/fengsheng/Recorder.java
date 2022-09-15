package com.fengsheng;

import com.fengsheng.network.ProtoServerChannelHandler;
import com.fengsheng.protos.Errcode;
import com.fengsheng.protos.Fengsheng;
import com.fengsheng.protos.Record;
import com.google.protobuf.ByteString;
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
import java.util.concurrent.TimeUnit;

public class Recorder {
    private static final Logger log = Logger.getLogger(Recorder.class);
    private static final short initTocId = ProtoServerChannelHandler.stringHash("init_toc");
    private static final ExecutorService saveLoadPool = Executors.newSingleThreadExecutor();

    private List<Record.recorder_line> list = new ArrayList<>();

    private int currentIndex;

    private volatile boolean loading = false;

    public void add(short messageId, byte[] messageBuf) {
        if (!loading && (messageId == initTocId || list.size() > 0))
            list.add(Record.recorder_line.newBuilder().setNanoTime(System.nanoTime()).setMessageId(messageId)
                    .setMessageBuf(ByteString.copyFrom(messageBuf)).build());
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
        final String recordId = Long.toString((now.getTime() / 1000 * 1000 + g.getId() % 100 * 10 + p.location()) % (36L * 36 * 36 * 36 * 36 * 36), Character.MAX_RADIX);
        final String fileName = timeStr + "-" + sb + "-" + p.location() + "-" + recordId;
        var builder = Record.record_file.newBuilder();
        builder.setClientVersion(Config.ClientVersion);
        builder.addAllLines(list);
        Record.record_file recordFile = builder.build();
        saveLoadPool.submit(() -> {
            File file = new File("records/");
            if (!file.exists() && !file.isDirectory() && !file.mkdir())
                log.error("make dir failed: " + file.getName());
            try (DataOutputStream os = new DataOutputStream(new FileOutputStream("records/" + fileName))) {
                os.write(recordFile.toByteArray());
                if (notify) p.send(Fengsheng.save_record_success_toc.newBuilder().setRecordId(recordId).build());
                log.info("save record success: " + recordId);
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
        File[] files = recordId.length() == 6 ? file.listFiles((dir, name) -> name.endsWith("-" + recordId)) : null;
        if (files == null || files.length == 0) {
            player.send(Errcode.error_code_toc.newBuilder()
                    .setCode(Errcode.error_code.record_not_exists).build());
            return;
        }
        final File recordFile = files[0];
        loading = true;
        saveLoadPool.submit(() -> {
            try (DataInputStream is = new DataInputStream(new FileInputStream(recordFile))) {
                Record.record_file pb = Record.record_file.parseFrom(is.readAllBytes());
                int recordVersion = pb.getClientVersion();
                if (version < recordVersion) {
                    player.send(Errcode.error_code_toc.newBuilder()
                            .setCode(Errcode.error_code.record_version_not_match)
                            .addIntParams(recordVersion).build());
                    loading = false;
                    return;
                }
                list = pb.getLinesList();
                currentIndex = 0;
                log.info("load record success: " + recordId);
                displayNext(player);
            } catch (IOException e) {
                log.error("load record failed", e);
                player.send(Errcode.error_code_toc.newBuilder()
                        .setCode(Errcode.error_code.load_record_failed).build());
                loading = false;
            }
        });
    }

    private void displayNext(final HumanPlayer player) {
        while (player.isActive()) {
            if (currentIndex >= list.size()) {
                player.send(Fengsheng.display_record_end_toc.getDefaultInstance());
                list = new ArrayList<>();
                loading = false;
                break;
            }
            Record.recorder_line line = list.get(currentIndex);
            player.send((short) line.getMessageId(), line.getMessageBuf().toByteArray());
            if (++currentIndex >= list.size()) {
                player.send(Fengsheng.display_record_end_toc.getDefaultInstance());
                list = new ArrayList<>();
                loading = false;
                break;
            }
            long diffNanoTime = list.get(currentIndex).getNanoTime() - line.getNanoTime();
            if (diffNanoTime > 100000000) {
                GameExecutor.TimeWheel.newTimeout(timeout -> displayNext(player), Math.min(diffNanoTime, 2000000000), TimeUnit.NANOSECONDS);
                break;
            }
        }
    }

    public boolean loading() {
        return loading;
    }
}
