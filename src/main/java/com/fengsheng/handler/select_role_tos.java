package com.fengsheng.handler;

import com.fengsheng.HumanPlayer;
import com.fengsheng.protos.Fengsheng;

public class select_role_tos extends AbstractProtoHandler<Fengsheng.select_role_tos> {
    @Override
    protected void handle0(HumanPlayer r, Fengsheng.select_role_tos pb) {
        r.getGame().tryContinueResolveProtocol(r, pb);
    }
}
