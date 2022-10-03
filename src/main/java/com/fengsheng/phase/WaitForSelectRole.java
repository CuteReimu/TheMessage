package com.fengsheng.phase;

import com.fengsheng.*;
import com.fengsheng.protos.Common;
import com.fengsheng.protos.Fengsheng;
import com.fengsheng.skill.RoleSkillsData;
import com.google.protobuf.GeneratedMessageV3;
import org.apache.log4j.Logger;

import java.util.concurrent.TimeUnit;

/**
 * 等待玩家选择角色
 */
public class WaitForSelectRole implements WaitingFsm {
    private static final Logger log = Logger.getLogger(WaitForSelectRole.class);

    private final Game game;
    private final RoleSkillsData[] options;
    private final RoleSkillsData[] selected;

    public WaitForSelectRole(Game game, RoleSkillsData[] options) {
        this.game = game;
        this.options = options;
        selected = new RoleSkillsData[game.getPlayers().length];
    }

    @Override
    public ResolveResult resolve() {
        for (Player player : game.getPlayers()) {
            if (player instanceof HumanPlayer p) {
                var builder = Fengsheng.wait_for_select_role_toc.newBuilder();
                builder.setPlayerCount(game.getPlayers().length);
                builder.setIdentity(p.getIdentity());
                builder.setSecretTask(p.getSecretTask());
                Common.role role1 = options[p.location()].getRole();
                Common.role role2 = options[p.location() + game.getPlayers().length].getRole();
                if (role1 == Common.role.unknown) {
                    builder.addRoles(role2);
                } else {
                    builder.addRoles(role1);
                    if (role2 != Common.role.unknown) builder.addRoles(role2);
                }
                builder.setWaitingSecond(30);
                p.send(builder.build());
                p.setTimeout(GameExecutor.post(game, () -> game.tryContinueResolveProtocol(p, Fengsheng.select_role_tos.newBuilder().setRole(builder.getRoles(0)).build()), p.getWaitSeconds(builder.getWaitingSecond() + 2), TimeUnit.SECONDS));
            } else {
                selected[player.location()] = options[player.location()];
                player.setRoleSkillsData(selected[player.location()]);
            }
        }
        for (var role : selected)
            if (role == null) return null;
        return new ResolveResult(new StartGame(game), true);
    }

    @Override
    public ResolveResult resolveProtocol(Player p, GeneratedMessageV3 message) {
        if (!(message instanceof Fengsheng.select_role_tos pb)) {
            log.error("正在等待选择角色");
            return null;
        }
        if (p.getRole() != Common.role.unknown) {
            log.error("你已经选了角色");
            return null;
        }
        Common.role role1 = options[p.location()].getRole();
        Common.role role2 = options[p.location() + game.getPlayers().length].getRole();
        if (pb.getRole() != role1 && pb.getRole() != role2) {
            log.error("你没有这个角色");
            return null;
        }
        p.incrSeq();
        selected[p.location()] = pb.getRole() == role1 ? options[p.location()] : options[p.location() + game.getPlayers().length];
        p.setRoleSkillsData(selected[p.location()]);
        if (p instanceof HumanPlayer humanPlayer)
            humanPlayer.send(Fengsheng.select_role_toc.newBuilder().setRole(selected[p.location()].getRole()).build());
        for (var role : selected)
            if (role == null) return null;
        return new ResolveResult(new StartGame(game), true);
    }
}
