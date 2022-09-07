package com.fengsheng.handler;

import com.fengsheng.HumanPlayer;
import com.fengsheng.Player;
import com.fengsheng.card.Card;
import com.fengsheng.phase.AfterDieGiveCard;
import com.fengsheng.phase.WaitForDieGiveCard;
import com.fengsheng.protos.Fengsheng;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class die_give_card_tos extends AbstractProtoHandler<Fengsheng.die_give_card_tos> {
    private static final Logger log = Logger.getLogger(die_give_card_tos.class);

    @Override
    protected void handle0(HumanPlayer r, Fengsheng.die_give_card_tos pb) {
        if (!r.checkSeq(pb.getSeq())) {
            log.error("操作太晚了, required Seq: " + r.getSeq() + ", actual Seq: " + pb.getSeq());
            return;
        }
        if (!(r.getGame().getFsm() instanceof WaitForDieGiveCard fsm) || r != fsm.diedQueue.get(fsm.diedIndex)) {
            log.error("你没有死亡");
            return;
        }
        if (pb.getTargetPlayerId() == 0) {
            r.incrSeq();
            r.getGame().resolve(new AfterDieGiveCard(fsm));
        } else if (pb.getTargetPlayerId() < 0 || pb.getTargetPlayerId() >= r.getGame().getPlayers().length) {
            log.error("目标错误: " + pb.getTargetPlayerId());
            return;
        }
        if (pb.getCardIdCount() == 0) {
            log.warn("参数似乎有些不对，姑且认为不给牌吧");
            r.incrSeq();
            r.getGame().resolve(new AfterDieGiveCard(fsm));
        }
        List<Card> cards = new ArrayList<>();
        for (int cardId : pb.getCardIdList()) {
            Card card = r.findCard(cardId);
            if (card == null) {
                log.error("没有这张牌");
                return;
            }
            cards.add(card);
        }
        for (Card card : cards) r.deleteCard(card.getId());
        Player target = r.getGame().getPlayers()[r.getAbstractLocation(pb.getTargetPlayerId())];
        target.addCard(cards.toArray(new Card[0]));
        log.info(r + "给了" + target + cards);
        for (Player p : r.getGame().getPlayers()) {
            if (p instanceof HumanPlayer player) {
                var builder = Fengsheng.notify_die_give_card_toc.newBuilder();
                builder.setPlayerId(p.getAlternativeLocation(r.location()));
                builder.setTargetPlayerId(p.getAlternativeLocation(target.location()));
                if (p == r || p == target)
                    for (Card card : cards) builder.addCard(card.toPbCard());
                else
                    builder.setUnknownCardCount(cards.size());
                player.send(builder.build());
            }
        }
        r.incrSeq();
        r.getGame().resolve(new AfterDieGiveCard(fsm));
    }
}
