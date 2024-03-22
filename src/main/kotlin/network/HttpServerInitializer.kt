package com.fengsheng.network

import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.timeout.IdleStateHandler
import java.util.concurrent.TimeUnit

class HttpServerInitializer : ChannelInitializer<SocketChannel>() {
    override fun initChannel(ch: SocketChannel) {
        val pipeline = ch.pipeline()
        pipeline.addLast(IdleStateHandler(60, 0, 0, TimeUnit.SECONDS))
        pipeline.addLast("httpServerCodec", HttpServerCodec())
        pipeline.addLast("httpServerHandler", HttpServerChannelHandler())
        pipeline.addLast(HeartBeatServerHandler("http_server"))
    }
}
