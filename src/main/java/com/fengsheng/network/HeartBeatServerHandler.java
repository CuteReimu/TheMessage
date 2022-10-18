package com.fengsheng.network;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.apache.log4j.Logger;

public class HeartBeatServerHandler extends ChannelInboundHandlerAdapter {
    private static final Logger log = Logger.getLogger(HeartBeatServerHandler.class);

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent event) {
            if (event.state() == IdleState.READER_IDLE) {
                log.info(ctx.channel().id().asShortText() + " heart timeout");
                ctx.channel().close();
            } else {
                super.userEventTriggered(ctx, evt);
            }
        }
    }
}
