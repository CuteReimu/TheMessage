package com.fengsheng.handler;

import com.fengsheng.HumanPlayer;
import com.fengsheng.Player;
import com.fengsheng.card.Card;
import com.fengsheng.protos.Common;
import com.fengsheng.protos.Fengsheng;
import org.apache.log4j.Logger;

public class use_shi_tan_tos extends AbstractProtoHandler<Fengsheng.use_shi_tan_tos> {
    private static final Logger log = Logger.getLogger(use_shi_tan_tos.class);

    @Override
    protected void handle0(HumanPlayer r, Fengsheng.use_shi_tan_tos pb) {
        if (!r.checkSeq(pb.getSeq())) {
            log.error("操作太晚了, required Seq: " + r.getSeq() + ", actual Seq: " + pb.getSeq());
            return;
        }
        Card card = r.findCard(pb.getCardId());
        if (card == null) {
            log.error("没有这张牌");
            return;
        }
        if (card.getType() != Common.card_type.Shi_Tan) {
            log.error("这张牌不是试探，而是" + card);
            return;
        }
        if (pb.getPlayerId() < 0 || pb.getPlayerId() >= r.getGame().getPlayers().length) {
            log.error("目标错误: " + pb.getPlayerId());
            return;
        }
        Player target = r.getGame().getPlayers()[r.getAbstractLocation(pb.getPlayerId())];
        if (card.canUse(r.getGame(), r, target)) {
            r.incrSeq();
            card.execute(r.getGame(), r, target);
        }
    }
}
