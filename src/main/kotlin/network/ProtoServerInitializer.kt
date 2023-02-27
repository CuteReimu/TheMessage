package com.fengsheng.network


import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import io.netty.handler.timeout.IdleStateHandler
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

class ProtoServerInitializer : ChannelInitializer<SocketChannel>() {
    override fun initChannel(ch: SocketChannel) {
        val pipeline = ch.pipeline()
        pipeline.addLast(IdleStateHandler(60, 0, 0, TimeUnit.SECONDS))
        pipeline.addLast(LengthFieldBasedFrameDecoder(ByteOrder.LITTLE_ENDIAN, 65535, 0, 2, 0, 2, true))
        pipeline.addLast(ProtoServerChannelHandler())
        pipeline.addLast(HeartBeatServerHandler())
    }
}