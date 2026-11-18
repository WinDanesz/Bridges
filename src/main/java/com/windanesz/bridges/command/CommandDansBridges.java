package com.windanesz.bridges.command;

import com.windanesz.bridges.DansBridges;
import com.windanesz.bridges.worldgen.BridgeStyle;
import com.windanesz.bridges.worldgen.CustomBridge;
import com.windanesz.bridges.worldgen.WorldGenBridge;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class CommandDansBridges extends CommandBase {

    private static final String PREFIX = "commands." + DansBridges.MODID + ":";
    private static final double REACH = 64.0D;

    @Override
    public String getName() {
        return DansBridges.MODID;
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return PREFIX + "usage";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length >= 2 && args.length <= 3 && args[0].equals("place") && (args.length == 2 || args[2].equals("broken"))) {
            place(sender, args[1], args.length == 3);
        } else {
            throw new WrongUsageException(getUsage(sender));
        }
    }

    private void place(ICommandSender sender, String name, boolean broken) throws CommandException {
        CustomBridge custom = CustomBridge.get(name);
        BridgeStyle style = custom != null ? BridgeStyle.CUSTOM : builtIn(name);
        if (style == null) {
            throw new CommandException(PREFIX + "place.unknown", name);
        }
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        Vec3d eyes = player.getPositionEyes(1.0F);
        RayTraceResult hit = player.world.rayTraceBlocks(eyes, eyes.add(player.getLookVec().scale(REACH)), true, false, false);
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK) {
            throw new CommandException(PREFIX + "place.no_target");
        }
        // The bridge runs the way the player looks, across the river in front of them
        boolean alongX = player.getHorizontalFacing().getAxis() == EnumFacing.Axis.X;
        if (!WorldGenBridge.placeAt(player.world, hit.getBlockPos(), alongX, style, custom, broken, new Random())) {
            throw new CommandException(PREFIX + "place.failed", name);
        }
        notifyCommandListener(sender, this, PREFIX + "place.success", name);
    }

    @Nullable
    private static BridgeStyle builtIn(String name) {
        for (BridgeStyle style : BridgeStyle.values()) {
            if (style != BridgeStyle.CUSTOM && style.name().toLowerCase(Locale.ROOT).equals(name)) {
                return style;
            }
        }
        return null;
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "place");
        }
        if (args[0].equals("place")) {
            if (args.length == 2) {
                List<String> names = new ArrayList<>();
                for (CustomBridge bridge : CustomBridge.all()) {
                    names.add(bridge.id);
                }
                for (BridgeStyle style : BridgeStyle.values()) {
                    if (style != BridgeStyle.CUSTOM) {
                        names.add(style.name().toLowerCase(Locale.ROOT));
                    }
                }
                return getListOfStringsMatchingLastWord(args, names);
            }
            if (args.length == 3) {
                return getListOfStringsMatchingLastWord(args, "broken");
            }
        }
        return Collections.emptyList();
    }
}
