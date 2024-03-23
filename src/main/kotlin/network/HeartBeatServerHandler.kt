package com.fengsheng.network

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.timeout.IdleState
import io.netty.handler.timeout.IdleStateEvent
import org.apache.logging.log4j.kotlin.logger

class HeartBeatServerHandler(private val name: String) : ChannelInboundHandlerAdapter() {
    @Throws(Exception::class)
    override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any) {
        if (evt is IdleStateEvent) {
            if (evt.state() == IdleState.READER_IDLE) {
                logger.info(ctx.channel().id().asShortText() + " heart timeout | $name")
                ctx.channel().close()
            } else {
                super.userEventTriggered(ctx, evt)
            }
        }
    }
}
