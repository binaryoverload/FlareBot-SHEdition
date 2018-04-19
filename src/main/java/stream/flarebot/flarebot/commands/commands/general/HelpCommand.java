package stream.flarebot.flarebot.commands.commands.general;

import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.entities.User;
import stream.flarebot.flarebot.DataHandler;
import stream.flarebot.flarebot.commands.*;
import stream.flarebot.flarebot.objects.GuildWrapper;
import stream.flarebot.flarebot.permissions.PerGuildPermissions;
import stream.flarebot.flarebot.permissions.Permission;
import stream.flarebot.flarebot.util.MessageUtils;
import stream.flarebot.flarebot.util.buttons.ButtonGroupConstants;
import stream.flarebot.flarebot.util.pagination.PagedEmbedBuilder;
import stream.flarebot.flarebot.util.pagination.PaginationList;
import stream.flarebot.flarebot.util.pagination.PaginationUtil;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@SuppressWarnings("Duplicates") // IntelliJ IDEA Ultimate is bitching about it.
public class HelpCommand implements Command {

    @Override
    public void onCommand(User sender, GuildWrapper guild, TextChannel channel, Message message, String[] args, Member member) {
        if (args.length == 1) {
            CommandType type;
            try {
                type = CommandType.valueOf(args[0].toUpperCase());
            } catch (IllegalArgumentException ignored) {
                channel.sendMessage(MessageUtils.getEmbed(sender).setDescription("No such category!").build()).queue();
                return;
            }
            if (type.isAdmin() && !PerGuildPermissions.isAdmin(sender)) {
                channel.sendMessage(MessageUtils.getEmbed(sender).setDescription("No such category!").build()).queue();
                return;
            }

            sendCommands(guild, channel, member, type);
        } else
            sendCommands(channel.getGuild(), channel, sender);
    }

    private void sendCommands(Guild guild, TextChannel channel, User sender) {
        List<String> pages = new ArrayList<>();
        for (CommandType c : CommandType.getTypes()) {
            List<String> help = c.getCommands()
                    .stream().filter(cmd -> cmd.getPermission() != null &&
                            DataHandler.getGuild(guild.getIdLong())
                                    .getPermissions()
                                    .hasPermission(guild
                                            .getMember(sender), cmd
                                            .getPermission()))
                    .map(command -> DataHandler.getGuild(guild.getIdLong()).getPrefix() + command.getCommand() + " - " + command
                            .getDescription() + '\n')
                    .collect(Collectors.toList());
            StringBuilder sb = new StringBuilder();
            sb.append("**").append(c).append("**\n");
            for (String s : help) {
                if (sb.length() + s.length() > 1024) {
                    pages.add(sb.toString());
                    sb.setLength(0);
                    sb.append("**").append(c).append("**\n");
                }
                sb.append(s);
            }
            if (sb.toString().trim().isEmpty()) continue;
            pages.add(sb.toString());
        }
        PagedEmbedBuilder<String> builder = new PagedEmbedBuilder<>(new PaginationList<>(pages));
        builder.setColor(Color.CYAN);
        PaginationUtil.sendEmbedPagedMessage(builder.build(), 0, channel, sender, ButtonGroupConstants.HELP);
    }

    public void sendCommands(GuildWrapper guild, TextChannel channel, Member member, CommandType type) {
        List<String> pages = new ArrayList<>();
        List<String> help = type.getCommands()
                .stream().filter(cmd -> getPermissions(channel)
                        .hasPermission(member, cmd.getPermission()))
                .map(command -> guild.getPrefix() + command.getCommand() + " - " + command
                        .getDescription() + '\n')
                .collect(Collectors.toList());
        StringBuilder sb = new StringBuilder();
        for (String s : help) {
            if (sb.length() + s.length() > 1024) {
                pages.add(sb.toString());
                sb.setLength(0);
            }
            sb.append(s);
        }
        pages.add(sb.toString());
        PagedEmbedBuilder<String> builder = new PagedEmbedBuilder<>(new PaginationList<>(pages));
        builder.setTitle("***FlareBot " + type + " commands!***")
                .setColor(Color.CYAN);
        PaginationUtil.sendEmbedPagedMessage(builder.build(), 0, channel, member.getUser(), ButtonGroupConstants.HELP);
    }

    @Override
    public String getCommand() {
        return "help";
    }

    @Override
    public String[] getAliases() {
        return new String[]{"commands"};
    }

    @Override
    public String getDescription() {
        return "See a list of all commands.";
    }

    @Override
    public String getUsage() {
        return "`{%}help` - Gives a list of commands.\n"
                + "`{%}help <category>` - Gives a list of commands in a specific category.";
    }

    @Override
    public Permission getPermission() {
        return Permission.HELP_COMMAND;
    }

    @Override
    public CommandType getType() {
        return CommandType.GENERAL;
    }
}
