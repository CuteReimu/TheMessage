package com.fengsheng.handler;

import com.fengsheng.HumanPlayer;
import com.fengsheng.protos.Role;

public class skill_du_ji_c_tos extends AbstractProtoHandler<Role.skill_du_ji_c_tos> {
    @Override
    protected void handle0(HumanPlayer r, Role.skill_du_ji_c_tos pb) {
        r.getGame().tryContinueResolveProtocol(r, pb);
    }
}
