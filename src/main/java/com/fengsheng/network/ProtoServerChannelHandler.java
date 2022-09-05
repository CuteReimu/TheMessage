package com.fengsheng.network;

import com.fengsheng.HumanPlayer;
import com.fengsheng.Player;
import com.fengsheng.protos.Fengsheng;
import com.fengsheng.protos.Role;
import com.google.protobuf.Descriptors;
import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.Parser;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.apache.log4j.Logger;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ProtoServerChannelHandler extends SimpleChannelInboundHandler<ByteBuf> {
    private static final Logger log = Logger.getLogger(ProtoServerChannelHandler.class);

    private static final Map<Short, ProtoInfo> ProtoInfoMap = new HashMap<>();

    private final ConcurrentMap<String, Player> playerCache = new ConcurrentHashMap<>();

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        Channel channel = ctx.channel();
        log.info("session connected: " + channel.id().asShortText());
        if (playerCache.putIfAbsent(channel.id().asLongText(), new HumanPlayer(channel)) != null) {
            log.error("already assigned channel id: " + channel.id().asLongText());
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        Channel channel = ctx.channel();
        log.info("session closed: " + channel.id().asShortText());
        if (playerCache.remove(channel.id().asLongText()) == null) {
            log.error("already unassigned channel id: " + channel.id().asLongText());
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        if (msg.readableBytes() < 2) {
            ctx.close();
            return;
        }
        short msgLen = msg.readShortLE();
        if (msgLen < 2) {
            log.error("incorrect msgLen: " + msgLen);
            ctx.close();
            return;
        }
        short id = msg.readShortLE();
        var protoInfo = ProtoInfoMap.get(id);
        if (protoInfo == null) {
            log.error("incorrect msg id: " + id);
            ctx.close();
            return;
        }
        byte[] buf = msg.readBytes(msgLen - 2).array();
        var message = (GeneratedMessageV3) protoInfo.parser().parseFrom(buf);
        log.debug("recv@%s len: %d %s | %s".formatted(ctx.channel().id().asShortText(), msgLen - 2, protoInfo.name(), message));
    }

    static {
        try {
            initProtocols(Fengsheng.class);
            initProtocols(Role.class);
        } catch (InvocationTargetException | NoSuchMethodException | IllegalAccessException |
                 ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private static void initProtocols(Class<?> protoCls) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, ClassNotFoundException {
        var descriptor = (Descriptors.FileDescriptor) protoCls.getDeclaredMethod("getDescriptor").invoke(null);
        for (Descriptors.Descriptor d : descriptor.getMessageTypes()) {
            String name = d.getName();
            short id = stringHash(name);
            if (id == 0) {
                throw new RuntimeException("message meta require 'ID' field: " + name);
            }
            String className = protoCls.getName() + "$" + name;
            Class<?> cls = protoCls.getClassLoader().loadClass(className);
            var parser = (Parser<?>) cls.getDeclaredMethod("parser").invoke(null);
            if (ProtoInfoMap.putIfAbsent(id, new ProtoInfo(name, parser)) != null) {
                throw new RuntimeException("Duplicate message meta register by id: " + id);
            }
        }
    }

    public static short stringHash(String s) {
        int hash = 0;
        for (byte c : s.getBytes()) {
            int i = c >= 0 ? (int) c : 256 + (int) c;
            hash = (short) (hash + (hash << 5) + i + (i << 7));
        }
        return (short) hash;
    }

    record ProtoInfo(String name, Parser<?> parser) {

    }
}
