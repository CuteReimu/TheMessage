package com.fengsheng;

import com.fengsheng.card.Card;
import com.fengsheng.protos.Common;

public class RobotPlayer extends AbstractPlayer {
    public RobotPlayer() {

    }

    public RobotPlayer(AbstractPlayer player) {
        super(player);
    }

    @Override
    public void notifyAddHandCard(int location, int unknownCount, Card... cards) {

    }

    @Override
    public void notifyDrawPhase() {

    }

    @Override
    public void notifyMainPhase(int waitSecond) {

    }

    @Override
    public void notifySendPhaseStart(int waitSecond) {

    }

    public void notifySendMessageCard(Player player, Player targetPlayer, Player[] lockedPlayers, Card messageCard, Common.direction direction) {

    }

    @Override
    public void notifySendPhase(int waitSecond) {

    }

    @Override
    public void notifyChooseReceiveCard() {

    }

    @Override
    public void notifyFightPhase(int waitSecond) {

    }

    @Override
    public void notifyReceivePhase() {

    }

    @Override
    public void notifyReceivePhase(int waitSecond) {

    }

    @Override
    public void notifyDie(int location, boolean loseGame) {

    }

    @Override
    public void notifyWin(Player[] declareWinners, Player[] winners) {

    }

    @Override
    public void notifyAskForChengQing(Player whoDie, Player askWhom) {

    }

    @Override
    public void waitForDieGiveCard(Player whoDie) {

    }

    @Override
    public String toString() {
        return location + "号[" + (isRoleFaceUp() ? roleSkillsData.getName() : "机器人") + "]";
    }
}
