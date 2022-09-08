package com.fengsheng.handler;

import com.fengsheng.HumanPlayer;
import com.fengsheng.Player;
import com.fengsheng.card.Card;
import com.fengsheng.phase.OnSendCard;
import com.fengsheng.phase.SendPhaseStart;
import com.fengsheng.protos.Common;
import com.fengsheng.protos.Fengsheng;
import com.fengsheng.skill.SkillId;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class send_message_card_tos extends AbstractProtoHandler<Fengsheng.send_message_card_tos> {
    private static final Logger log = Logger.getLogger(send_message_card_tos.class);

    @Override
    protected void handle0(HumanPlayer r, Fengsheng.send_message_card_tos pb) {
        if (!r.checkSeq(pb.getSeq())) {
            log.error("操作太晚了, required Seq: " + r.getSeq() + ", actual Seq: " + pb.getSeq());
            return;
        }
        if (!(r.getGame().getFsm() instanceof SendPhaseStart fsm) || r != fsm.player()) {
            log.error("不是传递情报的时机");
            return;
        }
        Card card = r.findCard(pb.getCardId());
        if (card == null) {
            log.error("没有这张牌");
            return;
        }
        if (pb.getTargetPlayerId() <= 0 || pb.getTargetPlayerId() >= r.getGame().getPlayers().length) {
            log.error("目标错误: " + pb.getTargetPlayerId());
            return;
        }
        if (r.findSkill(SkillId.LIAN_LUO) == null && pb.getCardDir() != card.getDirection()) {
            log.error("方向错误: " + pb.getCardDir());
            return;
        }
        int targetLocation = switch (pb.getCardDir()) {
            case Left -> r.getNextLeftAlivePlayer().location();
            case Right -> r.getNextRightAlivePlayer().location();
            default -> 0;
        };
        if (pb.getCardDir() != Common.direction.Up && pb.getTargetPlayerId() != r.getAlternativeLocation(targetLocation)) {
            log.error("不能传给那个人: " + pb.getTargetPlayerId());
            return;
        }
        if (card.canLock()) {
            if (pb.getLockPlayerIdCount() > 1) {
                log.error("最多锁定一个目标");
                return;
            } else if (pb.getLockPlayerIdCount() == 1) {
                if (pb.getLockPlayerId(0) < 0 || pb.getLockPlayerId(0) >= r.getGame().getPlayers().length) {
                    log.error("锁定目标错误: " + pb.getLockPlayerId(0));
                    return;
                } else if (pb.getLockPlayerId(0) == 0) {
                    log.error("不能锁定自己");
                    return;
                }
            }
        } else {
            if (pb.getLockPlayerIdCount() > 0) {
                log.error("这张情报没有锁定标记");
                return;
            }
        }
        targetLocation = r.getAbstractLocation(pb.getTargetPlayerId());
        if (!r.getGame().getPlayers()[targetLocation].isAlive()) {
            log.error("目标已死亡");
            return;
        }
        List<Player> lockPlayers = new ArrayList<>();
        for (int lockPlayerId : pb.getLockPlayerIdList()) {
            Player lockPlayer = r.getGame().getPlayers()[r.getAbstractLocation(lockPlayerId)];
            if (!lockPlayer.isAlive()) {
                log.error("锁定目标已死亡：" + lockPlayer);
                return;
            }
            lockPlayers.add(lockPlayer);
        }
        r.incrSeq();
        r.getGame().resolve(new OnSendCard(fsm.player(), card, pb.getCardDir(), r.getGame().getPlayers()[targetLocation], lockPlayers.toArray(new Player[0])));
    }
}
