package com.fengsheng.handler;

import com.fengsheng.HumanPlayer;
import com.fengsheng.protos.Role;

public class skill_jin_bi_b_tos extends AbstractProtoHandler<Role.skill_jin_bi_b_tos> {
    @Override
    protected void handle0(HumanPlayer r, Role.skill_jin_bi_b_tos pb) {
        r.getGame().tryContinueResolveProtocol(r, pb);
    }
}
