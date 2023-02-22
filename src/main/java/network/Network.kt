package com.fengsheng.networkimport

import com.fengsheng.Config
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import java.util.concurrent.CountDownLatch

com.fengsheng.protos.Common.card_type
import java.lang.Runnable
import java.lang.RuntimeException
import java.lang.InterruptedException

object Network {
    fun init() {
        Thread(Runnable { obj: Network? -> com.fengsheng.network.Network.initGameNetwork() }).start()
        Thread(Runnable { obj: Network? -> com.fengsheng.network.Network.initGameWebSocketNetwork() }).start()
        Thread(Runnable { obj: Network? -> com.fengsheng.network.Network.initGmNetwork() }).start()
        try {
            com.fengsheng.network.Network.cd.await()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw RuntimeException(e)
        }
    }

    private val cd = CountDownLatch(3)
    private fun initGameNetwork() {
        val bossGroup: EventLoopGroup = NioEventLoopGroup()
        val workerGroup: EventLoopGroup = NioEventLoopGroup()
        try {
            val bootstrap = ServerBootstrap()
            bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel::class.java)
                .childHandler(ProtoServerInitializer())
            val future = bootstrap.bind(Config.ListenPort)
            com.fengsheng.network.Network.cd.countDown()
            future.channel().closeFuture().sync()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw RuntimeException(e)
        } finally {
            bossGroup.shutdownGracefully()
            workerGroup.shutdownGracefully()
        }
    }

    private fun initGameWebSocketNetwork() {
        val bossGroup: EventLoopGroup = NioEventLoopGroup()
        val workerGroup: EventLoopGroup = NioEventLoopGroup()
        try {
            val bootstrap = ServerBootstrap()
            bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel::class.java)
                .childHandler(WebSocketServerInitializer())
            val future = bootstrap.bind(Config.ListenWebSocketPort)
            com.fengsheng.network.Network.cd.countDown()
            future.channel().closeFuture().sync()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw RuntimeException(e)
        } finally {
            bossGroup.shutdownGracefully()
            workerGroup.shutdownGracefully()
        }
    }

    private fun initGmNetwork() {
        if (!Config.IsGmEnable) {
            com.fengsheng.network.Network.cd.countDown()
            return
        }
        val bossGroup: EventLoopGroup = NioEventLoopGroup()
        val workerGroup: EventLoopGroup = NioEventLoopGroup()
        try {
            val bootstrap = ServerBootstrap()
            bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel::class.java)
                .childHandler(HttpServerInitializer())
            val future = bootstrap.bind(Config.GmListenPort)
            com.fengsheng.network.Network.cd.countDown()
            future.channel().closeFuture().sync()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw RuntimeException(e)
        } finally {
            bossGroup.shutdownGracefully()
            workerGroup.shutdownGracefully()
        }
    }
}