package com.fengsheng.network;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.timeout.IdleStateHandler;

import java.nio.ByteOrder;
import java.util.concurrent.TimeUnit;

public class ProtoServerInitializer extends ChannelInitializer<SocketChannel> {
    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast(new IdleStateHandler(60, 0, 0, TimeUnit.SECONDS));
        pipeline.addLast(new LengthFieldBasedFrameDecoder(ByteOrder.LITTLE_ENDIAN, 65535, 0, 2, 0, 2, true));
        pipeline.addLast(new ProtoServerChannelHandler());
        pipeline.addLast(new HeartBeatServerHandler());
    }
}
