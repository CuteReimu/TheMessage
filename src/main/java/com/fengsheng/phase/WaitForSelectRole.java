package com.fengsheng.phase;

import com.fengsheng.*;
import com.fengsheng.protos.Common;
import com.fengsheng.protos.Fengsheng;
import com.fengsheng.skill.JiBan;
import com.fengsheng.skill.RoleSkillsData;
import com.fengsheng.skill.YingBian;
import com.fengsheng.skill.YouDao;
import com.google.protobuf.GeneratedMessageV3;
import org.apache.log4j.Logger;

import java.util.EnumMap;
import java.util.concurrent.ThreadLocalRandom;
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
                    builderAddRole(builder, role2);
                } else {
                    builderAddRole(builder, role1);
                    if (role2 != Common.role.unknown) builderAddRole(builder, role2);
                }
                builder.setWaitingSecond(30);
                p.send(builder.build());
                p.setTimeout(GameExecutor.post(game, () -> game.tryContinueResolveProtocol(p, Fengsheng.select_role_tos.newBuilder().setRole(builder.getRoles(0)).build()), p.getWaitSeconds(builder.getWaitingSecond() + 2), TimeUnit.SECONDS));
            } else {
                RoleSkillsData spRole = spMap.get(options[player.location()].getRole());
                if (spRole != null && ThreadLocalRandom.current().nextBoolean())
                    selected[player.location()] = spRole;
                else
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
        RoleSkillsData roleSkillsData = getRole(p.location(), pb.getRole());
        if (roleSkillsData == null) {
            log.error("你没有这个角色");
            return null;
        }
        p.incrSeq();
        selected[p.location()] = roleSkillsData;
        p.setRoleSkillsData(roleSkillsData);
        if (p instanceof HumanPlayer humanPlayer)
            humanPlayer.send(Fengsheng.select_role_toc.newBuilder().setRole(roleSkillsData.getRole()).build());
        for (var role : selected)
            if (role == null) return null;
        return new ResolveResult(new StartGame(game), true);
    }

    private static void builderAddRole(Fengsheng.wait_for_select_role_toc.Builder builder, Common.role role) {
        builder.addRoles(role);
        RoleSkillsData spRoleSkillsData = spMap.get(role);
        if (spRoleSkillsData != null)
            builder.addRoles(spRoleSkillsData.getRole());
    }

    private RoleSkillsData getRole(int location, Common.role role) {
        if (options[location].getRole() == role)
            return options[location];
        else if (options[location + options.length / 2].getRole() == role)
            return options[location + options.length / 2];
        RoleSkillsData spRoleSkillsData = spMap.get(options[location].getRole());
        if (spRoleSkillsData != null && spRoleSkillsData.getRole() == role)
            return spRoleSkillsData;
        spRoleSkillsData = spMap.get(options[location + options.length / 2].getRole());
        if (spRoleSkillsData != null && spRoleSkillsData.getRole() == role)
            return spRoleSkillsData;
        return null;
    }

    private static final EnumMap<Common.role, RoleSkillsData> spMap = new EnumMap<>(Common.role.class);

    static {
        spMap.put(Common.role.gu_xiao_meng, new RoleSkillsData("SP顾小梦", Common.role.sp_gu_xiao_meng, true, true, new JiBan()));
        spMap.put(Common.role.li_ning_yu, new RoleSkillsData("SP李宁玉", Common.role.sp_li_ning_yu, true, true, new YingBian(), new YouDao()));
    }
}
