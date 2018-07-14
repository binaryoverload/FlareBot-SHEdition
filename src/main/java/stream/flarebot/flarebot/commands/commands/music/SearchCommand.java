package stream.flarebot.flarebot.commands.commands.music;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.entities.User;
import stream.flarebot.flarebot.Client;
import stream.flarebot.flarebot.commands.Command;
import stream.flarebot.flarebot.commands.CommandType;
import stream.flarebot.flarebot.music.VideoThread;
import stream.flarebot.flarebot.objects.GuildWrapper;
import stream.flarebot.flarebot.permissions.Permission;
import stream.flarebot.flarebot.util.MessageUtils;
import stream.flarebot.flarebot.util.buttons.ButtonRunnable;
import stream.flarebot.flarebot.util.buttons.ButtonUtil;
import stream.flarebot.flarebot.util.objects.ButtonGroup;

import java.util.List;
import java.util.Map;

public class SearchCommand implements Command {

    @Override
    public void onCommand(User sender, GuildWrapper guild, TextChannel channel, Message message, String[] args, Member member) {
        if(args.length == 0) {
            MessageUtils.sendUsage(this, channel, sender, args);
            return;
        }

        if (joinChannel(guild, channel, member)) return;

        String search = MessageUtils.getMessage(args);
        search = MessageUtils.escapeMarkdown(search);

        String finalSearch = search;
        channel.sendMessage("Searching for `" + search + "` on Youtube").queue(message1 -> {
            List<Map.Entry<String, String>> searchResults = VideoThread.getSearchResults(finalSearch, message1);
            if(searchResults != null && !searchResults.isEmpty()) {
                searchResults = searchResults.subList(0, 5);
                ButtonGroup group = new ButtonGroup(sender.getIdLong(), "search");
                StringBuilder sb = new StringBuilder();
                int i = 0;
                for (Map.Entry<String, String> searchResult: searchResults) {
                    i++;
                    String unicode = "";
                    switch (i) {
                        case 1:
                            unicode = "\u0031\u20E3";
                            break;
                        case 2:
                            unicode = "\u0032\u20E3";
                            break;
                        case 3:
                            unicode = "\u0033\u20E3";
                            break;
                        case 4:
                            unicode = "\u0034\u20E3";
                            break;
                        case 5:
                            unicode = "\u0035\u20E3";
                    }
                    String name = MessageUtils.escapeMarkdown(searchResult.getValue());
                    sb.append(unicode).append(": `").append(name).append("`\n");
                    group.addButton(new ButtonGroup.Button(unicode, (ownerID, user, message2) -> {
                        VideoThread.getThread(searchResult.getKey(), channel, sender).start();
                        message2.delete().queue();
                    }));
                }
                message1.delete().queue();
                ButtonUtil.sendButtonedMessage(channel, new EmbedBuilder().setTitle("Search Results").appendDescription(sb.toString()).build(), group);
            }
        });
    }

    public static boolean joinChannel(GuildWrapper guild, TextChannel channel, Member member) {
        if (member.getVoiceState().inVoiceChannel()) {
            if (channel.getGuild().getSelfMember().getVoiceState().inVoiceChannel() &&
                    !(channel.getGuild().getSelfMember().getVoiceState().getAudioChannel().getId()
                            .equals(member.getVoiceState().getAudioChannel().getId()))) {
                MessageUtils.sendErrorMessage("I cannot join your channel! I am already in a channel!", channel);
                return true;
            }
            Client.instance().getLink(guild.getGuildId()).connect(member.getVoiceState().getChannel());
        }
        return false;
    }

    @Override
    public String getCommand() {
        return "search";
    }

    @Override
    public String getDescription() {
        return "Search for a song on YouTube. Usage: `search WORDS`";
    }

    @Override
    public String getUsage() {
        return "`{%}search <words>` - Searches for a song on YouTube.";
    }

    @Override
    public Permission getPermission() {
        return Permission.SEARCH_COMMAND;
    }

    @Override
    public CommandType getType() {
        return CommandType.MUSIC;
    }

}
