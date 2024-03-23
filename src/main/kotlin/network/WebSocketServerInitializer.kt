package com.fengsheng.network

import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler
import io.netty.handler.timeout.IdleStateHandler
import java.util.concurrent.TimeUnit

class WebSocketServerInitializer : ChannelInitializer<SocketChannel>() {
    override fun initChannel(ch: SocketChannel) {
        val pipeline = ch.pipeline()
        pipeline.addLast(IdleStateHandler(60, 0, 0, TimeUnit.SECONDS))
        pipeline.addLast("httpServerCodec", HttpServerCodec())
        pipeline.addLast("http-aggregator", HttpObjectAggregator(65535))
        pipeline.addLast("ws-handler", WebSocketServerProtocolHandler("/ws"))
        pipeline.addLast("webSocketServerHandler", WebSocketServerChannelHandler())
        pipeline.addLast(HeartBeatServerHandler("websocket_server"))
    }
}
