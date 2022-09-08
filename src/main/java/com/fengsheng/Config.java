package com.fengsheng;

import com.fengsheng.protos.Common;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

public final class Config {
    public static final int ListenPort;
    public static final int TotalPlayerCount;
    public static final int HandCardCountBegin;
    public static final int HandCardCountEachTurn;
    public static final boolean IsGmEnable;
    public static final int GmListenPort;
    public static final int ClientVersion;
    public static final int MaxRoomCount;
    public static final Common.role[] DebugRoles;

    static {
        Properties pps = new Properties();
        try (InputStream in = new FileInputStream("application.properties")) {
            pps.load(in);
        } catch (Throwable ignored) {
        }
        pps.putIfAbsent("listen_port", "9091");
        pps.putIfAbsent("player.total_count", "5");
        pps.putIfAbsent("rule.hand_card_count_begin", "3");
        pps.putIfAbsent("rule.hand_card_count_each_turn", "3");
        pps.putIfAbsent("gm.enable", "false");
        pps.putIfAbsent("gm.listen_port", "9092");
        pps.putIfAbsent("client_version", "1");
        pps.putIfAbsent("room_count", "200");
        pps.putIfAbsent("gm.debug_roles", "22,26");
        ListenPort = Integer.parseInt(pps.getProperty("listen_port"));
        TotalPlayerCount = Integer.parseInt(pps.getProperty("player.total_count"));
        HandCardCountBegin = Integer.parseInt(pps.getProperty("rule.hand_card_count_begin"));
        HandCardCountEachTurn = Integer.parseInt(pps.getProperty("rule.hand_card_count_each_turn"));
        IsGmEnable = Boolean.parseBoolean(pps.getProperty("gm.enable"));
        GmListenPort = Integer.parseInt(pps.getProperty("gm.listen_port"));
        ClientVersion = Integer.parseInt(pps.getProperty("client_version"));
        MaxRoomCount = Integer.parseInt(pps.getProperty("room_count"));
        String debugRoleStr = pps.getProperty("gm.debug_roles");
        if (debugRoleStr.isBlank()) {
            DebugRoles = new Common.role[0];
        } else {
            String[] debugRoles = debugRoleStr.split(",");
            DebugRoles = new Common.role[debugRoles.length];
            for (int i = 0; i < DebugRoles.length; i++)
                DebugRoles[i] = Common.role.forNumber(Integer.parseInt(debugRoles[i]));
        }
        try (OutputStream out = new FileOutputStream("application.properties")) {
            pps.store(out, "application.properties");
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
