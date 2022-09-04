package com.fengsheng.network;

import com.fengsheng.protos.Fengsheng;
import com.fengsheng.protos.Role;
import com.google.protobuf.Descriptors;
import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.Parser;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.apache.log4j.Logger;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

public class ProtoServerChannelHandler extends SimpleChannelInboundHandler<ByteBuf> {
    private static final Logger log = Logger.getLogger(ProtoServerChannelHandler.class);

    private static final Map<Short, Parser<?>> ParserMap = new HashMap<>();

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        short msgLen = msg.readShortLE();
        if (msgLen < 2) {
            log.error("incorrect msgLen: " + msgLen);
            ctx.close();
            return;
        }
        short id = msg.readShortLE();
        var parser = ParserMap.get(id);
        if (parser == null) {
            log.error("incorrect msg id: " + id);
            ctx.close();
            return;
        }
        var byteBuf = Unpooled.buffer(msgLen - 2, msgLen - 2);
        msg.readBytes(byteBuf, msgLen - 2);
        byte[] buf = byteBuf.array();
        var message = (GeneratedMessageV3) parser.parseFrom(buf);
        System.out.println(message);
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
            short id = stringHash(d.getName());
            if (id == 0) {
                throw new RuntimeException("message meta require 'ID' field: " + d.getName());
            }
            String className = protoCls.getName() + "$" + d.getName();
            Class<?> cls = protoCls.getClassLoader().loadClass(className);
            var parser = (Parser<?>) cls.getDeclaredMethod("parser").invoke(null);
            if (ParserMap.putIfAbsent(id, parser) != null) {
                throw new RuntimeException("Duplicate message meta register by id: " + id);
            }
        }
    }

    private static short stringHash(String s) {
        int hash = 0;
        for (byte c : s.getBytes()) {
            int i = c >= 0 ? (int) c : 256 + (int) c;
            hash = (short) (hash + (hash << 5) + i + (i << 7));
        }
        return (short) hash;
    }
}
