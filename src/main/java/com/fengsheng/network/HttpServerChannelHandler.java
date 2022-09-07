package com.fengsheng.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;

import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class HttpServerChannelHandler extends SimpleChannelInboundHandler<HttpObject> {
    @SuppressWarnings("unchecked")
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) {
        if (msg instanceof HttpRequest request) {
            if (request.method() != HttpMethod.GET) {
                ByteBuf byteBuf = Unpooled.copiedBuffer("{\"error\": \"invalid method\"}", CharsetUtil.UTF_8);
                FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.METHOD_NOT_ALLOWED, byteBuf);
                response.headers().add(HttpHeaderNames.CONTENT_TYPE, "application/json");
                response.headers().add(HttpHeaderNames.CONTENT_LENGTH, byteBuf.readableBytes());
                ctx.writeAndFlush(response);
            } else {
                try {
                    URI uri = new URI(request.uri());
                    Map<String, String> form = new HashMap<>();
                    for (String s : uri.getQuery().split("&")) {
                        String[] arr = s.split("=", 2);
                        form.putIfAbsent(arr[0], arr.length >= 2 ? arr[1] : "");
                    }
                    String name = uri.getPath().replace("/", "");
                    Class<?> cls = this.getClass().getClassLoader().loadClass("com.fengsheng.gm." + name);
                    var handler = (Function<Map<String, String>, String>) cls.getDeclaredConstructor().newInstance();
                    ByteBuf byteBuf = Unpooled.copiedBuffer(handler.apply(form), CharsetUtil.UTF_8);
                    FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, byteBuf);
                    response.headers().add(HttpHeaderNames.CONTENT_TYPE, "application/json");
                    response.headers().add(HttpHeaderNames.CONTENT_LENGTH, byteBuf.readableBytes());
                    ctx.writeAndFlush(response);
                } catch (URISyntaxException e) {
                    ByteBuf byteBuf = Unpooled.copiedBuffer("{\"error\": \"parse form failed\"}", CharsetUtil.UTF_8);
                    FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST, byteBuf);
                    response.headers().add(HttpHeaderNames.CONTENT_TYPE, "application/json");
                    response.headers().add(HttpHeaderNames.CONTENT_LENGTH, byteBuf.readableBytes());
                    ctx.writeAndFlush(response);
                } catch (ClassNotFoundException | InvocationTargetException | InstantiationException |
                         IllegalAccessException | NoSuchMethodException e) {
                    ByteBuf byteBuf = Unpooled.copiedBuffer("{\"error\": \"404 not found\"}", CharsetUtil.UTF_8);
                    FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND, byteBuf);
                    response.headers().add(HttpHeaderNames.CONTENT_TYPE, "application/json");
                    response.headers().add(HttpHeaderNames.CONTENT_LENGTH, byteBuf.readableBytes());
                    ctx.writeAndFlush(response);
                }
            }
        }
    }
}
