package com.fengsheng.network

import com.fengsheng.Config
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

object Network {
    fun init() {
        runBlocking {
            listOf(
//                launch(Dispatchers.IO) { initGameNetwork() },
                launch(Dispatchers.IO) { initGameWebSocketNetwork() },
                launch(Dispatchers.IO) { initGmNetwork() },
            ).joinAll()
        }
    }

//    private fun initGameNetwork() {
//        val bossGroup: EventLoopGroup = NioEventLoopGroup()
//        val workerGroup: EventLoopGroup = NioEventLoopGroup()
//        try {
//            val bootstrap = ServerBootstrap()
//            bootstrap.group(bossGroup, workerGroup)
//                .channel(NioServerSocketChannel::class.java)
//                .childHandler(ProtoServerInitializer())
//            val future = bootstrap.bind(Config.ListenPort)
//            future.addListener { channelFuture ->
//                channelFuture.isSuccess || throw channelFuture.cause()
//            }
//            future.channel().closeFuture().sync()
//        } finally {
//            bossGroup.shutdownGracefully()
//            workerGroup.shutdownGracefully()
//        }
//    }

    private fun initGameWebSocketNetwork() {
        val bossGroup: EventLoopGroup = NioEventLoopGroup()
        val workerGroup: EventLoopGroup = NioEventLoopGroup()
        try {
            val bootstrap = ServerBootstrap()
            bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel::class.java)
                .childHandler(WebSocketServerInitializer())
            val future = bootstrap.bind(Config.ListenWebSocketPort)
            future.addListener { channelFuture ->
                channelFuture.isSuccess || throw channelFuture.cause()
            }
            future.channel().closeFuture().sync()
        } finally {
            bossGroup.shutdownGracefully()
            workerGroup.shutdownGracefully()
        }
    }

    private fun initGmNetwork() {
        val bossGroup: EventLoopGroup = NioEventLoopGroup()
        val workerGroup: EventLoopGroup = NioEventLoopGroup()
        try {
            val bootstrap = ServerBootstrap()
            bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel::class.java)
                .childHandler(HttpServerInitializer())
            val future = bootstrap.bind(Config.GmListenPort)
            future.addListener { channelFuture ->
                channelFuture.isSuccess || throw channelFuture.cause()
            }
            future.channel().closeFuture().sync()
        } finally {
            bossGroup.shutdownGracefully()
            workerGroup.shutdownGracefully()
        }
    }
}
