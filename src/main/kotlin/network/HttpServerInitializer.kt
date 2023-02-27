package com.fengsheng.network

import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http.HttpServerCodec

class HttpServerInitializer : ChannelInitializer<SocketChannel>() {
    override fun initChannel(ch: SocketChannel) {
        val pipeline = ch.pipeline()
        pipeline.addLast("httpServerCodec", HttpServerCodec())
        pipeline.addLast("httpServerHandler", HttpServerChannelHandler())
    }
}