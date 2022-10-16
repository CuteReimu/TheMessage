package com.fengsheng.skill;

import com.fengsheng.*;
import com.fengsheng.phase.OnChooseReceiveCard;
import com.fengsheng.phase.OnSendCard;
import com.fengsheng.protos.Common;
import com.fengsheng.protos.Role;
import com.google.protobuf.GeneratedMessageV3;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 张一挺技能【强令】：你传出情报后，或你决定接收情报后，可以宣言至多两个卡牌名称。本回合中，所有角色均不能使用被宣言的卡牌。
 */
public class QiangLing implements TriggeredSkill {
    @Override
    public SkillId getSkillId() {
        return SkillId.QIANG_LING;
    }

    @Override
    public ResolveResult execute(Game g) {
        if (g.getFsm() instanceof OnSendCard fsm) {
            Player r = fsm.whoseTurn();
            if (r.findSkill(getSkillId()) == null)
                return null;
            if (r.getSkillUseCount(getSkillId()) >= 1)
                return null;
            r.addSkillUseCount(getSkillId());
            return new ResolveResult(new executeQiangLing(fsm, r), true);
        } else if (g.getFsm() instanceof OnChooseReceiveCard fsm) {
            Player r = fsm.inFrontOfWhom();
            if (r.findSkill(getSkillId()) == null)
                return null;
            if (r.getSkillUseCount(getSkillId()) >= 2)
                return null;
            r.addSkillUseCount(getSkillId(), 2);
            return new ResolveResult(new executeQiangLing(fsm, r), true);
        }
        return null;
    }

    private record executeQiangLing(Fsm fsm, Player r) implements WaitingFsm {
        private static final Logger log = Logger.getLogger(executeQiangLing.class);

        @Override
        public ResolveResult resolve() {
            for (Player player : r.getGame().getPlayers()) {
                if (player instanceof HumanPlayer p) {
                    var builder = Role.skill_wait_for_qiang_ling_toc.newBuilder();
                    builder.setPlayerId(p.getAlternativeLocation(r.location()));
                    builder.setWaitingSecond(20);
                    if (p == r) {
                        final int seq2 = p.getSeq();
                        builder.setSeq(seq2);
                        GameExecutor.post(p.getGame(), () -> {
                            if (p.checkSeq(seq2))
                                p.getGame().tryContinueResolveProtocol(p, Role.skill_qiang_ling_tos.newBuilder().setEnable(false).setSeq(seq2).build());
                        }, p.getWaitSeconds(builder.getWaitingSecond() + 2), TimeUnit.SECONDS);
                    }
                    p.send(builder.build());
                }
            }
            if (r instanceof RobotPlayer) {
                GameExecutor.post(r.getGame(), () -> {
                    List<Common.card_type> result = new ArrayList<>();
                    if (r.getGame().getQiangLingTypes().isEmpty()) {
                        var cardTypes = new Common.card_type[]{Common.card_type.Jie_Huo, Common.card_type.Diao_Bao, Common.card_type.Wu_Dao};
                        int idx = ThreadLocalRandom.current().nextInt(cardTypes.length);
                        for (int i = 0; i < cardTypes.length; i++) {
                            if (i != idx)
                                result.add(cardTypes[i]);
                        }
                    } else {
                        var cardTypes = EnumSet.of(Common.card_type.Jie_Huo, Common.card_type.Diao_Bao, Common.card_type.Wu_Dao);
                        r.getGame().getQiangLingTypes().forEach(cardTypes::remove);
                        result.add(Common.card_type.Po_Yi);
                        for (var t : cardTypes) {
                            result.add(t);
                            break;
                        }
                    }
                    r.getGame().tryContinueResolveProtocol(r, Role.skill_qiang_ling_tos.newBuilder().setEnable(true).addAllTypes(result).build());
                }, 2, TimeUnit.SECONDS);
            }
            return null;
        }

        @Override
        public ResolveResult resolveProtocol(Player player, GeneratedMessageV3 message) {
            if (player != r) {
                log.error("不是你发技能的时机");
                return null;
            }
            if (!(message instanceof Role.skill_qiang_ling_tos pb)) {
                log.error("错误的协议");
                return null;
            }
            if (r instanceof HumanPlayer humanPlayer && !humanPlayer.checkSeq(pb.getSeq())) {
                log.error("操作太晚了, required Seq: " + humanPlayer.getSeq() + ", actual Seq: " + pb.getSeq());
                return null;
            }
            if (!pb.getEnable()) {
                r.incrSeq();
                return new ResolveResult(fsm, true);
            }
            if (pb.getTypesCount() == 0) {
                log.error("enable为true时types不能为0");
                return null;
            }
            for (var t : pb.getTypesList()) {
                if (t == Common.card_type.UNRECOGNIZED || t == null) {
                    log.error("未知的卡牌类型" + t);
                    return null;
                }
            }
            r.incrSeq();
            log.info(r + "发动了[强令]，禁止了" + Arrays.toString(pb.getTypesList().toArray(new Common.card_type[0])));
            r.getGame().getQiangLingTypes().addAll(pb.getTypesList());
            for (Player p : r.getGame().getPlayers()) {
                if (p instanceof HumanPlayer player1)
                    player1.send(Role.skill_qiang_ling_toc.newBuilder()
                            .setPlayerId(player1.getAlternativeLocation(r.location()))
                            .addAllTypes(pb.getTypesList()).build());
            }
            return new ResolveResult(fsm, true);
        }
    }

    public static void resetQiangLing(Game game) {
        game.getQiangLingTypes().clear();
    }
}
