package stream.flarebot.flarebot.commands.commands.music;

import com.arsenarsen.lavaplayerbridge.PlayerManager;
import com.arsenarsen.lavaplayerbridge.player.Track;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.entities.User;
import stream.flarebot.flarebot.Client;
import stream.flarebot.flarebot.FlareBot;
import stream.flarebot.flarebot.commands.Command;
import stream.flarebot.flarebot.commands.CommandType;
import stream.flarebot.flarebot.music.extractors.YouTubeExtractor;
import stream.flarebot.flarebot.objects.GuildWrapper;
import stream.flarebot.flarebot.permissions.Permission;
import stream.flarebot.flarebot.util.MessageUtils;
import stream.flarebot.flarebot.util.buttons.ButtonGroupConstants;
import stream.flarebot.flarebot.util.pagination.PagedEmbedBuilder;
import stream.flarebot.flarebot.util.pagination.PaginationUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class QueueCommand implements Command {

    @Override
    public void onCommand(User sender, GuildWrapper guild, TextChannel channel, Message message, String[] args, Member member) {
        if (message.getContentRaw().substring(1).startsWith("playlist")) {
            MessageUtils.sendWarningMessage("This command is deprecated! Please use `{%}queue` instead!", channel);
        }
        if (args.length < 1 || args.length > 2) {
            send(channel, member);
        } else {
            if (args.length == 1) {
                if (args[0].equalsIgnoreCase("clear")) {
                    if (!this.getPermissions(channel).hasPermission(member, Permission.QUEUE_CLEAR)) {
                        MessageUtils.sendErrorMessage("You need the `" + Permission.QUEUE_CLEAR + "` permission to do this!", channel, sender);
                        return;
                    }
                    Client.instance().getTracks(guild.getGuildId()).clear();
                    channel.sendMessage("Cleared the current playlist!").queue();
                } else if (args[0].equalsIgnoreCase("remove")) {
                    MessageUtils.sendUsage(this, channel, sender, args);
                } else if (args[0].equalsIgnoreCase("here")) {
                    send(channel, member);
                } else {
                    MessageUtils.sendUsage(this, channel, sender, args);
                }
            } else {
                if (args[0].equalsIgnoreCase("remove")) {
                    int number;
                    try {
                        number = Integer.parseInt(args[1]);
                    } catch (NumberFormatException e) {
                        MessageUtils.sendErrorMessage("That is an invalid number!", channel);
                        return;
                    }

                    List<AudioTrack> queue = Client.instance().getTracks(guild.getGuildId());

                    if (number < 1 || number > queue.size()) {
                        MessageUtils
                                .sendErrorMessage("There is no song with that index. Make sure your number is at least 1 and either " + queue
                                        .size() + " or below!", channel);
                        return;
                    }

                    queue.remove(number - 1);

                    channel.sendMessage(MessageUtils.getEmbed(sender)
                            .setDescription("Removed number " + number + " from the playlist!")
                            .build()).queue();
                }
            }
        }
    }

    private void send(TextChannel channel, Member sender) {
        AudioTrack currentTrack = Client.instance().getPlayer(channel.getGuild().getId()).getPlayingTrack();

        if (Client.instance().getTracks(channel.getGuild().getId()).size() > 0
                || currentTrack != null) {
            List<String> songs = new ArrayList<>();
            songs.add("Current Song: " + String.format("[`%s`](%s) | Requested by <@!%s>\n",
                    currentTrack.getInfo().title,
                    currentTrack.getInfo().uri,
                    currentTrack.getUserData()));

            AtomicInteger i = new AtomicInteger(1);
            Client.instance().getTracks(channel.getGuild().getId()).forEach(track ->
                    songs.add(String.format("%s. [`%s`](%s) | Requested by <@!%s>\n", i.getAndIncrement(),
                            track.getInfo().title,
                            track.getInfo().uri,
                            track.getUserData())));

            PagedEmbedBuilder pe = new PagedEmbedBuilder<>(PaginationUtil.splitStringToList(songs.stream()
                    // 21 for 10 per page. 2 new lines per song and 1 more because it's annoying
                    .collect(Collectors.joining("\n")) + "\n", PaginationUtil.SplitMethod.NEW_LINES, 21))
                    .setTitle("Queued Songs");
            PaginationUtil.sendEmbedPagedMessage(pe.build(), 0, channel, sender.getUser(), ButtonGroupConstants.QUEUE);
        } else {
            MessageUtils.sendErrorMessage(MessageUtils.getEmbed().setDescription("No songs in the playlist!"), channel);
        }
    }

    @Override
    public String getCommand() {
        return "queue";
    }

    // TODO: FIX THIS MONSTROSITY
    @Override
    public String getDescription() {
        return "View the songs currently on your playlist. " +
                "NOTE: If too many it shows only the amount that can fit. You can use `queue clear` to remove all songs." +
                " You can use `queue remove #` to remove a song under #.\n" +
                "To make it not send a DM do `queue here`";
    }

    @Override
    public String getUsage() {
        return "`{%}queue` - Lists the current items in the queue.\n" +
                "`{%}queue clear` - Clears the queue\n" +
                "`{%}queue remove <#>` - Removes an item from the queue.";
    }

    @Override
    public Permission getPermission() {
        return Permission.QUEUE_COMMAND;
    }

    @Override
    public String[] getAliases() {
        return new String[]{"playlist", "q"};
    }

    @Override
    public CommandType getType() {
        return CommandType.MUSIC;
    }
}
